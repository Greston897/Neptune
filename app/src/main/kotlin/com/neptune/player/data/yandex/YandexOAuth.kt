package com.neptune.player.data.yandex

import java.net.URLEncoder

/**
 * OAuth Device Flow, as far as this app needs it: two requests and the reading of one answer.
 *
 * Kept separate from [YandexMusicApi] for one reason — the reading of the token response is the part
 * that can be wrong in a way nobody notices. "authorization_pending" is a *normal* answer while the
 * user types the code; treating it as an error closes the window the user is still looking at, and the
 * difference is one `contains` call in a `when`. Here it can be tested without a network.
 *
 * The credentials are those of the official Android client, published in every open-source client
 * (`yandex-music`, `device_auth.py`). They identify the application, never a person: holding them
 * grants nothing, and the token this flow returns belongs to whoever confirmed the code on Yandex's
 * page.
 */
object YandexOAuth {
    const val DEVICE_CODE_URL = "https://oauth.yandex.ru/device/code"
    const val TOKEN_URL = "https://oauth.yandex.ru/token"

    /** The Android client the confirmation page names, so the user sees "Яндекс Музыка" and not a stranger. */
    const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"

    /** Not a user secret: it is what the client sends with the device grant, published in the same place. */
    const val CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"

    /** `application/x-www-form-urlencoded`, which is what both endpoints accept. */
    fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }

    fun codeRequest(deviceId: String): String =
        form("client_id" to CLIENT_ID, "device_id" to deviceId, "device_name" to "Neptune")

    fun tokenRequest(deviceCode: String): String =
        form(
            "grant_type" to "device_code",
            "code" to deviceCode,
            "client_id" to CLIENT_ID,
            "client_secret" to CLIENT_SECRET,
        )

    /** What one poll meant. Three outcomes, because the caller does three different things with them. */
    sealed class Poll {
        /** A token: the account is signed in. */
        data class Token(val value: String) : Poll()
        /** The user has not confirmed yet. Not an error, and not the end of the flow. */
        data object Pending : Poll()
        /** The flow is over: the code expired, the user refused, or the client was rejected. */
        data class Failed(val message: String) : Poll()
    }

    /**
     * Reads the answer to one poll.
     *
     * A successful answer is JSON with `access_token`; a pending one is a 400 whose *body* carries
     * `authorization_pending`, which is why the status alone is not enough to decide.
     */
    fun poll(status: Int, body: String): Poll {
        val token = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        if (status in 200..299) {
            return if (token != null) Poll.Token(token) else Poll.Failed("Сервис не вернул токен")
        }
        return when {
            body.contains("authorization_pending") -> Poll.Pending
            body.contains("expired_token") -> Poll.Failed("Код истёк — начните вход заново")
            body.contains("access_denied") -> Poll.Failed("Вход отменён на странице Яндекса")
            else -> Poll.Failed("Вход не удался — попробуйте ещё раз")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
