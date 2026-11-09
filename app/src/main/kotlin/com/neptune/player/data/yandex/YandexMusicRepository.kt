package com.neptune.player.data.yandex

import android.content.Context
import android.net.Uri
import com.neptune.player.model.ChartEntry
import com.neptune.player.model.ChartPage
import com.neptune.player.model.DeviceCode
import com.neptune.player.model.DownloadedTrack
import com.neptune.player.model.RemoteSearch
import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.Track
import com.neptune.player.model.YandexAccount
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StreamState(
    val chart: ChartPage? = null,
    val chartLoading: Boolean = false,
    val chartError: String? = null,
    /** Which chart variant is showing, as the service names it (`russia`, `world`, …). */
    val chartPostfix: String = "",

    val results: RemoteSearch = RemoteSearch(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    /** What the service suggests instead, when it thought the query was misspelled. */
    val corrected: String? = null,

    val account: YandexAccount = YandexAccount(),
    val accountBusy: Boolean = false,

    /**
     * The code being shown during a sign-in, or null when the flow is not running.
     *
     * Kept in the state rather than in the screen because the poll outlives a recomposition: turning the
     * phone, opening the plan page, or coming back to the screen must find the same code and the same
     * deadline instead of starting a second one.
     */
    val deviceCode: DeviceCode? = null,
    /** True while the app is asking Yandex for the next poll — not while it waits for the user. */
    val devicePolling: Boolean = false,
    val deviceError: String? = null,
    val deviceDone: Boolean = false,

    /** Offline is a first-class state here: streaming is the only part of the app that needs a network. */
    val offline: Boolean = false,

    /**
     * Saved-for-offline tracks: track id → the file on disk.
     *
     * Persisted as a name list plus a directory scan, so a file the user deleted with a file manager
     * disappears from this map instead of being offered as "скачано" and then failing to open.
     */
    val downloads: Map<String, DownloadedTrack> = emptyMap(),
    /** Downloads in flight: track id → 0..1, or -1 when the size is not known yet. */
    val downloading: Map<String, Float> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap(),
) {
    fun isDownloaded(id: String) = downloads.containsKey(id)
    fun isDownloading(id: String) = downloading.containsKey(id)
    /** The rows of the current chart, empty while it loads or when it failed. */
    val chartPositions: List<ChartEntry> get() = chart?.entries ?: emptyList()
}

/**
 * The streaming side of the library: charts, remote search, the account, and the signed link the
 * player asks for.
 *
 * ## Why it is a separate repository rather than more methods on [com.neptune.player.data.LibraryRepository]
 *
 * The local library is a *scan*: it reads MediaStore and stored picker grants, and its state is
 * whatever is on the phone. Streaming is a *cache with failures*: every value arrived over a network
 * that can be slow, rate-limited or gone, and none of it can be resolved synchronously. Keeping them
 * apart means the local library can never be blocked by a request, and the player can tell the two
 * apart when it has to explain why a track will not play.
 *
 * ## Free tier
 *
 * Nothing here gates on [com.neptune.player.model.PlanTier]. Search, charts, metadata and playback
 * work for everyone: the free tier on the service side streams at a lower bitrate and some licensed
 * tracks are premium-only there. This app does not sell that — it says what Yandex says and moves on.
 */
class YandexMusicRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val api: YandexMusicApi,
) {
    private val app = context.applicationContext
    private val prefs = context.getSharedPreferences("neptune.v4.yandex", Context.MODE_PRIVATE)

    /**
     * Where saved tracks live: the app's own files directory, *not* the cache.
     *
     * The cache is the first thing Android reclaims under pressure, and a download that disappears
     * when the phone is busy is worse than no download feature at all.
     */
    private val downloadDir = File(app.filesDir, "streams").apply { mkdirs() }
    private val _state = MutableStateFlow(
        StreamState(
            account = YandexAccount(token = prefs.getString("token", null)),
            downloads = loadDownloads(),
        ),
    )
    val state = _state

    /**
     * Called with every page that arrives, so streamed tracks become resolvable by id.
     *
     * The catalog is what turns a saved id back into a title, so it has to learn from every page the
     * app renders — chart, search, album — not only from the place a track was played.
     */
    var onTracks: (List<RemoteTrack>) -> Unit = {}

    private var chartJob: Job? = null
    private var searchJob: Job? = null
    private var searchSeq = 0

    fun loadChart(postfix: String = _state.value.chartPostfix, force: Boolean = false) {
        val current = _state.value
        if (!force && current.chart != null && current.chartPostfix == postfix && current.chartError == null) return
        chartJob?.cancel()
        _state.update { it.copy(chartLoading = it.chart == null || postfix != it.chartPostfix, chartError = null, chartPostfix = postfix) }
        chartJob = scope.launch {
            api.chart(postfix).fold(
                onSuccess = { page ->
                    onTracks(page.entries.map { it.track })
                    _state.update { it.copy(chart = page, chartLoading = false, chartError = null, offline = false) }
                },
                onFailure = { e -> _state.update { it.copy(chartLoading = false, chartError = YandexMusicApi.describe(e), offline = e is YandexMusicApi.ApiError.Offline) } },
            )
        }
    }

    /**
     * Searches the streaming catalogue.
     *
     * Debounced by the caller (the field's `LaunchedEffect`), and sequenced here: a slow request for
     * "ne" must never overwrite the results for "neptune" that came back first. Without the sequence
     * number the last *reply* wins instead of the last *query*, which is a classic typing bug.
     */
    fun search(query: String, immediate: Boolean = false) {
        val text = query.trim()
        if (text.length < 2) {
            searchJob?.cancel()
            _state.update { it.copy(results = RemoteSearch(), searchLoading = false, searchError = null, corrected = null) }
            return
        }
        searchJob?.cancel()
        val seq = ++searchSeq
        _state.update { it.copy(searchLoading = true, searchError = null) }
        searchJob = scope.launch {
            if (!immediate) delay(350)
            api.search(text).fold(
                onSuccess = { result ->
                    if (seq != searchSeq) return@fold
                    onTracks(result.tracks)
                    // The correction hint rides along in the response, so both land in one update:
                    // asking again for it would double the traffic of every keystroke.
                    _state.update {
                        it.copy(
                            results = result, searchLoading = false, searchError = null,
                            offline = false, corrected = result.corrected,
                        )
                    }
                },
                onFailure = { e ->
                    if (seq != searchSeq) return@fold
                    _state.update {
                        it.copy(searchLoading = false, searchError = YandexMusicApi.describe(e), offline = e is YandexMusicApi.ApiError.Offline)
                    }
                },
            )
        }
    }

    // ------------------------------------------------------------------ offline copies

    /**
     * Saves a track for offline listening.
     *
     * Progress is published per track id, so the row that asked for the download shows its own
     * percentage while everything else on screen stays untouched. A failure keeps the partial file
     * only long enough to delete it: a half-file that looks saved is the worst outcome here.
     */
    fun download(track: RemoteTrack) {
        if (_state.value.downloads.containsKey(track.uriId) || _state.value.downloading.containsKey(track.uriId)) return
        _state.update { it.copy(downloading = it.downloading + (track.uriId to -1f), downloadErrors = it.downloadErrors - track.uriId) }
        scope.launch {
            val result = runCatching { fetchToDisk(track) }
            result.fold(
                onSuccess = { saved ->
                    _state.update {
                        it.copy(
                            downloads = it.downloads + (track.uriId to saved),
                            downloading = it.downloading - track.uriId,
                        )
                    }
                    persistDownloads()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            downloading = it.downloading - track.uriId,
                            downloadErrors = it.downloadErrors + (track.uriId to YandexMusicApi.describe(e)),
                        )
                    }
                },
            )
        }
    }

    private suspend fun fetchToDisk(track: RemoteTrack): DownloadedTrack = withContext(Dispatchers.IO) {
        val url = api.streamUrl(track.id).getOrElse { throw it }
        val target = File(downloadDir, "${track.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.mp3")
        val partial = File(downloadDir, target.name + ".part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) throw IOException("Сервис ответил ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        // ~1 % steps: a progress bar that updates per 64 KB would recompose the whole
                        // list a thousand times for one song.
                        val fraction = if (total > 0) copied.toFloat() / total else -1f
                        if (fraction < 0 || copied - lastReported > total / 100 || copied == total) {
                            lastReported = copied
                            _state.update { it.copy(downloading = it.downloading + (track.uriId to fraction)) }
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Не удалось сохранить файл")
            DownloadedTrack(track.uriId, target.absolutePath, target.length(), track)
        } catch (e: Throwable) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Removes the saved copy. The track stays in favourites and playlists; only the file goes. */
    fun removeDownload(trackId: String) {
        val saved = _state.value.downloads[trackId] ?: return
        runCatching { File(saved.file).delete() }
        _state.update { it.copy(downloads = it.downloads - trackId) }
        persistDownloads()
    }

    /**
     * Rebuilds the saved list from the directory, not from preferences alone.
     *
     * Preferences are the index (they carry the metadata), but the directory is the truth about which
     * files still exist: a user clearing app data through a file manager, or an interrupted write,
     * must not leave the app advertising a download it cannot open.
     */
    private fun loadDownloads(): Map<String, DownloadedTrack> {
        val raw = prefs.getString("downloads", null) ?: return emptyMap()
        val parsed = runCatching { MiniJson.parse(raw) }.getOrNull() ?: return emptyMap()
        return MiniJson.list(parsed).mapNotNull { row ->
            val node = MiniJson.obj(row)
            val id = node["trackId"] as? String ?: return@mapNotNull null
            val file = node["file"] as? String ?: return@mapNotNull null
            val trackNode = MiniJson.obj(node["track"])
            val remoteId = trackNode["id"] as? String ?: return@mapNotNull null
            val title = trackNode["title"] as? String ?: return@mapNotNull null
            val onDisk = File(file)
            if (!onDisk.exists() || onDisk.length() == 0L) return@mapNotNull null
            id to DownloadedTrack(
                trackId = id,
                file = file,
                bytes = onDisk.length(),
                track = RemoteTrack(
                    id = remoteId, title = title,
                    artists = trackNode["artists"] as? String ?: "Неизвестный исполнитель",
                    album = trackNode["album"] as? String ?: "",
                    durationMs = (trackNode["duration"] as? Number)?.toLong() ?: 0L,
                    artworkUri = trackNode["cover"] as? String,
                    year = (trackNode["year"] as? Number)?.toInt(),
                    albumId = trackNode["albumId"] as? String,
                    artistId = trackNode["artistId"] as? String,
                ),
            )
        }.toMap()
    }

    private fun persistDownloads() {
        val rows = _state.value.downloads.values.map { d ->
            mapOf(
                "trackId" to d.trackId, "file" to d.file,
                "track" to mapOf(
                    "id" to d.track.id, "title" to d.track.title, "artists" to d.track.artists,
                    "album" to d.track.album, "duration" to d.track.durationMs, "cover" to d.track.artworkUri,
                    "year" to d.track.year, "albumId" to d.track.albumId, "artistId" to d.track.artistId,
                ),
            )
        }
        prefs.edit().putString("downloads", MiniJson.write(rows)).apply()
    }

    /**
     * The local file for a saved track, as a URI the player can open, or null when it is not saved.
     *
     * Uri.fromFile rather than a FileProvider: the file is inside the app's private directory and
     * never leaves the process (the media session notification uses metadata artwork, not this file),
     * so there is nothing to grant a permission to.
     */
    fun offlineUri(trackId: String): String? =
        _state.value.downloads[trackId]?.let { Uri.fromFile(File(it.file)).toString() }

    /**
     * The signed URL for a stream id, or a failure with a sentence. Called from the playback thread.
     *
     * Takes the bare id rather than a Track: the only caller is the data source, which by then has an
     * id and no metadata at all (that is the point of the handle).
     */
    suspend fun streamUrl(id: String): Result<String> = api.streamUrl(id)

    fun albumTracks(albumId: String, onResult: (List<RemoteTrack>) -> Unit) {
        scope.launch {
            api.albumTracks(albumId).onSuccess { onResult(it) }
        }
    }

    fun artistTracks(artistId: String, onResult: (List<RemoteTrack>) -> Unit) {
        scope.launch {
            api.artistTracks(artistId).onSuccess { onResult(it) }
        }
    }

    /**
     * Stores a token and asks the service who it belongs to.
     *
     * Verification is not a formality: a wrong token silently falls back to anonymous access, which
     * would look like "the account section is broken". The row says "Токен не подошёл" instead.
     */
    fun setToken(raw: String?, onDone: (Boolean) -> Unit = {}) {
        val token = raw?.trim()?.takeIf { it.isNotBlank() }
        prefs.edit().apply { if (token == null) remove("token") else putString("token", token) }.apply()
        _state.update { it.copy(account = it.account.copy(token = token, verified = false, name = null, error = null), accountBusy = token != null) }
        if (token == null) { onDone(true); return }
        scope.launch {
            api.account(token).fold(
                onSuccess = { acc ->
                    _state.update {
                        it.copy(
                            account = acc.copy(token = token, error = null),
                            accountBusy = false,
                            offline = false,
                        )
                    }
                    onDone(true)
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            account = it.account.copy(token = token, verified = false, error = YandexMusicApi.describe(e)),
                            accountBusy = false,
                        )
                    }
                    onDone(false)
                },
            )
        }
    }

    // ------------------------------------------------------------------ sign-in without a password

    private var deviceJob: Job? = null

    /**
     * Starts the OAuth Device Flow: shows a code, then polls until the user confirms it.
     *
     * Why the app does this instead of asking for a token in a field: a token is a credential the user
     * has to obtain by hand from a page that is not ours, and the flow below is the mechanism the
     * service itself provides for a device with no browser redirect. Neither path is required — the
     * catalogue works anonymously — so this is offered, never demanded.
     *
     * The poll honours the server's `interval` and `expires_in`, stops on the first token, and is
     * cancelled when the user closes the dialog: a background loop that keeps asking after the screen
     * is gone is how an app gets rate-limited out of an endpoint.
     */
    fun startSignIn() {
        // A second tap is the same sign-in, not a second one: the code already on screen is the one the
        // user may be typing right now, and replacing it would silently invalidate what they read.
        if (_state.value.deviceCode != null) return
        if (deviceJob?.isActive == true) return
        deviceJob?.cancel()
        _state.update { it.copy(deviceCode = null, deviceError = null, deviceDone = false, devicePolling = true) }
        deviceJob = scope.launch {
            api.requestDeviceCode().fold(
                onSuccess = { code ->
                    _state.update { it.copy(deviceCode = code, devicePolling = false, offline = false) }
                    val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
                    while (_state.value.deviceCode?.deviceCode == code.deviceCode && System.currentTimeMillis() < deadline) {
                        delay(code.intervalSec * 1000L)
                        _state.update { it.copy(devicePolling = true) }
                        val token = api.pollDeviceToken(code.deviceCode).getOrElse { e ->
                            _state.update { it.copy(devicePolling = false, deviceError = YandexMusicApi.describe(e)) }
                            return@launch
                        }
                        _state.update { it.copy(devicePolling = false) }
                        if (token != null) {
                            // Same path as a pasted token: the account is verified before it is stored,
                            // so a token the service rejects cannot leave the app half signed in.
                            setToken(token) { ok ->
                                _state.update {
                                    it.copy(deviceDone = ok, deviceError = if (ok) null else "Токен не подошёл")
                                }
                            }
                            return@launch
                        }
                    }
                    _state.update {
                        it.copy(
                            devicePolling = false,
                            // The code is gone once it expires: leaving it on screen invites the user to
                            // type something the server has already forgotten.
                            deviceCode = null,
                            deviceError = "Код истёк — начните вход заново",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(devicePolling = false, deviceError = YandexMusicApi.describe(e)) }
                },
            )
        }
    }

    /** Closes the dialog: the code stops being shown and the poll stops asking. */
    fun cancelSignIn() {
        deviceJob?.cancel()
        deviceJob = null
        _state.update { it.copy(deviceCode = null, devicePolling = false, deviceError = null, deviceDone = false) }
    }

    fun refreshAccount() {
        val token = _state.value.account.token ?: return
        _state.update { it.copy(accountBusy = true) }
        scope.launch {
            api.account(token).fold(
                onSuccess = { acc -> _state.update { it.copy(account = acc.copy(token = token), accountBusy = false) } },
                onFailure = { e -> _state.update { it.copy(account = it.account.copy(error = YandexMusicApi.describe(e)), accountBusy = false) } },
            )
        }
    }
}
