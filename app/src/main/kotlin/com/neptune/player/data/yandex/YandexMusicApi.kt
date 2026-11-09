package com.neptune.player.data.yandex

import com.neptune.player.model.ChartPage
import com.neptune.player.model.DeviceCode
import com.neptune.player.model.RemoteSearch
import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.Track
import com.neptune.player.model.YandexAccount
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The HTTP surface of Yandex Music that this app uses. Five calls, nothing else.
 *
 * ## What this is, plainly
 *
 * There is no public Yandex Music API. The endpoints below are the ones the official clients use and
 * the ones every open-source client has documented by reverse engineering; they are undocumented,
 * unversioned, and may change without notice. The app treats them as *best effort*:
 *
 *  * every failure is reported as a sentence the user can act on, never as a blank screen;
 *  * nothing here is required for the player to work — with the network off, or with the endpoints
 *    changed underneath us, the local library still plays exactly as before;
 *  * nothing is signed with a secret of ours. Anonymous requests work for search and charts; a pasted
 *    user token (optional) adds the account and higher-bitrate variants.
 *
 * ## Free tier, and what it really means
 *
 * Search, charts, metadata and streaming do not require a paid subscription *in this app*: they work
 * anonymously. On the service side, tracks the label licenses to Plus stream for everyone at a lower
 * bitrate (usually 128 kbps) and only Plus accounts get 320. Some regional licenses refuse the file
 * outright regardless of who asks — that case surfaces as "трек недоступен в этом регионе", which is
 * the truth and not a prompt to buy something in this app.
 *
 * ## Threading
 *
 * Every call is blocking and must be made from [Dispatchers.IO]; the repository is the only caller.
 * Connections are short-lived on purpose: a persistent pool would keep sockets to a third party open
 * while the app sits idle in the background.
 */
class YandexMusicApi(
    private val base: String = BASE_URL,
    /** Read at call time: the user can paste or clear a token while the app is running. */
    private val token: () -> String?,
    /** Per-install OAuth device id. Supplied by the app wiring, which is what persists it. */
    private val deviceId: () -> String = { randomDeviceId() },
) {
    /**
     * The identity the service expects. It is the Android client string the reference library sends;
     * without it several endpoints answer with a wall or an empty body.
     */
    private val clientHeader get() = "X-Yandex-Music-Client" to "YandexMusicAndroid/24023621"

    suspend fun search(query: String, page: Int = 0): Result<RemoteSearch> = call {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        YandexMap.search(get("$base/search?text=$q&type=all&page=$page&nocorrect=false&playlist-in-best=false"))
    }

    /** The first chart page: the service's default (usually the world chart) plus its menu. */
    suspend fun chart(postfix: String = ""): Result<ChartPage> = call {
        val path = if (postfix.isBlank()) "$base/landing3/chart" else "$base/landing3/chart/$postfix"
        YandexMap.chart(get(path)) ?: throw ApiError.NotFound
    }

    /** Album with its tracks, for "об альбоме" on a streamed track. */
    suspend fun albumTracks(albumId: String): Result<List<RemoteTrack>> = call {
        val raw = MiniJson.obj(get("$base/albums/$albumId/with-tracks"))
        val volumes = MiniJson.list(MiniJson.obj(raw["result"])["volumes"])
        val rows = if (volumes.isEmpty()) MiniJson.list(MiniJson.obj(raw["result"])["tracks"])
        else volumes.flatMap { MiniJson.list(it) }
        rows.mapNotNull { YandexMap.track(it) }
    }

    /** An artist's most popular tracks — what a tap on an artist chip opens. */
    suspend fun artistTracks(artistId: String): Result<List<RemoteTrack>> = call {
        val raw = MiniJson.obj(get("$base/artists/$artistId/tracks?page=0&page-size=50"))
        MiniJson.list(MiniJson.obj(raw["result"])["tracks"]).mapNotNull { YandexMap.track(it) }
    }

    /**
     * The signed file link for one track, minted on demand.
     *
     * Two requests, because the service splits "which variants exist" from "the ticket for one of
     * them". Both happen in the player's data source, right before the first byte is read, so the
     * signature is never stale when it is used.
     */
    suspend fun streamUrl(trackId: String): Result<String> = call {
        val variants = YandexMap.downloadInfo(get("$base/tracks/$trackId/download-info"))
        val chosen = YandexSign.best(variants) ?: throw ApiError.NoVariants
        // The ticket XML lives on a storage host, so it is fetched with the same headers but read as
        // text: it is XML, not JSON.
        val xml = getText(chosen.infoUrl)
        val ticket = YandexSign.parseTicket(xml) ?: throw ApiError.NoVariants
        YandexSign.directLink(ticket)
    }

    /**
     * Validates a pasted token and reads the account.
     *
     * 401 means the token is wrong, which is a different message from "the network is down": one is
     * the user's typo, the other is not their fault.
     */
    suspend fun account(pasted: String? = null): Result<YandexAccount> = call {
        val raw = get("$base/account/status", explicitToken = pasted ?: token())
        YandexMap.account(raw)
    }

    // ------------------------------------------------------------------ OAuth Device Flow

    /**
     * Step one of the OAuth Device Flow: ask Yandex for a code the user types into a browser.
     *
     * The flow exists so that signing in does not require the app to see a password: it shows a short
     * code, the user confirms it on Yandex's own page — on this phone or on any other device — and the
     * app polls until a token appears. That is the only way to get a token on a phone without a
     * browser redirect back into the app, and the `client_id` is the one the official Android client
     * uses, which is what makes the confirmation page offer "Яндекс Музыка" rather than a nameless
     * third-party oauth app. Reproduced from `yandex-music` (MarshalX), `device_auth.py`; it is not a
     * secret of ours and confers no access by itself.
     */
    suspend fun requestDeviceCode(): Result<DeviceCode> = call {
        val body = YandexOAuth.codeRequest(deviceId())
        val raw = MiniJson.obj(postJson(YandexOAuth.DEVICE_CODE_URL, body))
        val code = raw["device_code"] as? String ?: throw ApiError.Unavailable
        val user = raw["user_code"] as? String ?: throw ApiError.Unavailable
        DeviceCode(
            deviceCode = code,
            userCode = user,
            verificationUrl = raw["verification_url"] as? String ?: "https://oauth.yandex.ru/device",
            expiresInSec = (raw["expires_in"] as? Number)?.toInt() ?: 300,
            intervalSec = ((raw["interval"] as? Number)?.toInt() ?: 5).coerceAtLeast(1),
        )
    }

    /**
     * Step two: one poll. `null` means the user has not confirmed yet — that is the normal answer for
     * as long as it takes them to type the code, and it must not be reported as an error.
     */
    suspend fun pollDeviceToken(deviceCode: String): Result<String?> = call {
        val (status, text) = postStatus(YandexOAuth.TOKEN_URL, YandexOAuth.tokenRequest(deviceCode))
        when (val poll = YandexOAuth.poll(status, text)) {
            is YandexOAuth.Poll.Token -> poll.value
            YandexOAuth.Poll.Pending -> null
            is YandexOAuth.Poll.Failed -> throw ApiError.SignIn(poll.message)
        }
    }

    private suspend fun postJson(url: String, body: String): Any? =
        MiniJson.parse(postStatus(url, body).second)

    /** Returns the status and the body: OAuth answers 400 with the reason in the body, not in a header. */
    private suspend fun postStatus(url: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.outputStream.use { it.write(body.toByteArray()) }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            status to text
        } catch (e: java.net.UnknownHostException) {
            throw ApiError.Offline
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiError.Timeout
        } catch (e: IOException) {
            throw ApiError.Offline
        }
    }

    /**
     * Runs [block] on the IO pool and folds every failure into one of [ApiError], so no screen has to
     * know that a socket timeout and a 503 are different exceptions.
     *
     * Written as a plain try/catch rather than `runCatching { }.recoverCatching { }`: the chained form
     * looks tidier but re-throws from inside a recovery block, which is easy to get subtly wrong and
     * impossible to read at the call site.
     */
    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Throwable) {
            // A cancelled coroutine must stay cancelled; swallowing it would keep the UI "loading"
            // forever after the user left the screen.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(translate(e))
        }
    }

    private fun translate(e: Throwable): Throwable = when (e) {
        is ApiError -> e
        is java.net.UnknownHostException -> ApiError.Offline
        is java.net.SocketTimeoutException -> ApiError.Timeout
        is IOException -> ApiError.Offline
        else -> e
    }

    private fun connection(url: String, explicitToken: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty(clientHeader.first, clientHeader.second)
        c.setRequestProperty("Accept", "application/json")
        // Anonymous access is the normal case: no Authorization header at all, rather than an empty
        // one, which the service rejects outright.
        (explicitToken ?: token())?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "OAuth $it") }
        return c
    }

    private fun get(path: String, explicitToken: String? = null): Any? = MiniJson.parse(getText(path, explicitToken))

    private fun getText(path: String, explicitToken: String? = null): String {
        val c = connection(path, explicitToken)
        try {
            val code = c.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw if (explicitToken != null) ApiError.BadToken else ApiError.Unavailable
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw ApiError.NotFound
            if (code !in 200..299) throw ApiError.Http(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Everything that can go wrong, named once so the UI can say one sentence per case. */
    sealed class ApiError(message: String) : Exception(message) {
        data object Offline : ApiError("Нет связи с Яндекс Музыкой")
        data object Timeout : ApiError("Яндекс Музыка не ответила вовремя")
        data object NotFound : ApiError("Ничего не найдено")
        data object NoVariants : ApiError("У трека нет доступного файла")
        data object BadToken : ApiError("Токен не подошёл")
        data object Unavailable : ApiError("Сервис не ответил — попробуйте позже")
        /** Anything the sign-in flow itself decided, with the sentence the dialog shows. */
        data class SignIn(val reason: String) : ApiError(reason)
        data class Http(val code: Int) : ApiError("Сервис ответил $code")
    }

    companion object {
        const val BASE_URL = "https://api.music.yandex.net"
        private const val USER_AGENT = "Yandex-Music-API"
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        /** What the reference client sends: ten characters of mixed case and digits. */
        private fun randomDeviceId(): String = (1..10).map { ALPHABET.random() }.joinToString("")

        /**
         * A stable per-install device id, kept beside the token.
         *
         * Yandex lists a device per id, so generating a fresh one per sign-in would fill the account's
         * device list with ghosts. It is an identifier, not a credential: it says "this installation",
         * nothing more.
         */
        fun deviceId(context: android.content.Context): String {
            val prefs = context.getSharedPreferences("neptune.v4.yandex", android.content.Context.MODE_PRIVATE)
            prefs.getString("deviceId", null)?.let { return it }
            return randomDeviceId().also { prefs.edit().putString("deviceId", it).apply() }
        }

        /** One sentence per failure, for the screens that show them. */
        fun describe(error: Throwable): String = when (error) {
            is ApiError -> error.message ?: "Не получилось"
            else -> "Не получилось: ${error.message?.take(80) ?: "неизвестная ошибка"}"
        }

        /** True when a track handle belongs to the streaming catalogue rather than to a file. */
        fun isStream(track: Track): Boolean = RemoteTrack.idOf(track) != null
    }
}
