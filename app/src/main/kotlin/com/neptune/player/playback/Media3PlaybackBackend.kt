package com.neptune.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.neptune.player.data.LibraryRepository
import com.neptune.player.data.SettingsStore
import com.neptune.player.data.UserStore
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Media3PlaybackBackend(
    context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val users: UserStore,
    private val scope: CoroutineScope,
) : PlaybackBackend {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("neptune.v2.playback", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PlaybackState(volume = prefs.getFloat("volume", 0.8f)))
    override val state = _state
    private val connection = CompletableDeferred<MediaController?>()
    private val commandMutex = Mutex()
    private var controller: MediaController? = null
    private var timerJob: Job? = null
    private var timerDeadline = 0L
    private var lastRemembered: String? = null
    private var restoring = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish(); persist() }
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(isPlaying = false, isBuffering = false,
                error = "Не удалось воспроизвести файл. Проверьте доступ к хранилищу и повторите.")
        }
    }

    init {
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, NeptunePlaybackService::class.java))).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                c.volume = _state.value.volume
                c.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
                c.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
                connection.complete(c)
                publish()
                scope.launch {
                    library.state.first { !it.loading }
                    restore(c)
                }
                scope.launch {
                    while (true) {
                        delay(250)
                        if (c.isPlaying) {
                            _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0),
                                durationMs = c.duration.coerceAtLeast(0))
                        }
                        if (timerDeadline > 0) _state.value = _state.value.copy(sleepRemainingMs = (timerDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    }
                }
            } catch (e: Exception) {
                connection.complete(null)
                _state.value = _state.value.copy(error = "Аудиосервис не подключился. Попробуйте ещё раз.")
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Builds a queue entry that always carries artwork.
     *
     * A real cover is passed as `artworkUri`; when there is none, the app mark is attached as
     * `artworkData` so the media session never reports "no artwork" and the system overlays stop
     * substituting the play/pause glyph. See [SessionArtwork] for why bytes are required here.
     */
    private fun item(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                val art = track.artworkUri
                if (art != null) {
                    setArtworkUri(android.net.Uri.parse(art))
                } else {
                    SessionArtwork.brandPng(app)?.let {
                        setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
            }.build()
        return MediaItem.Builder().setMediaId(track.id).setUri(track.uri).setMediaMetadata(metadata).build()
    }

    private fun command(block: suspend (MediaController) -> Unit) {
        scope.launch {
            commandMutex.withLock {
                val c = connection.await() ?: return@withLock
                try { block(c); publish(); persist() }
                catch (e: Exception) { _state.value = _state.value.copy(error = "Команда не выполнена. Попробуйте ещё раз.") }
            }
        }
    }

    private suspend fun restore(c: MediaController) {
        if (c.mediaItemCount > 0 || prefs.getString("queue", null) == null) return
        val catalog = library.state.value.byId
        val ids = runCatching {
            val a = JSONArray(prefs.getString("queue", "[]"))
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        val tracks = ids.mapNotNull(catalog::get)
        if (tracks.isEmpty()) return
        restoring = true
        c.setMediaItems(tracks.map(::item), prefs.getInt("index", 0).coerceIn(0, tracks.lastIndex), prefs.getLong("position", 0))
        c.prepare()
        c.playWhenReady = false
        restoring = false
        publish()
    }

    private fun publish() {
        val c = controller ?: return
        val catalog = library.state.value.byId
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val track = c.currentMediaItem?.mediaId?.let(catalog::get)
        if (!restoring && track != null && track.id != lastRemembered) {
            lastRemembered = track.id
            users.rememberTrack(track.id)
        }
        _state.value = _state.value.copy(connected = true, track = track,
            queue = ids.mapNotNull(catalog::get), queueIndex = c.currentMediaItemIndex,
            isPlaying = c.isPlaying, isBuffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady,
            positionMs = c.currentPosition.coerceAtLeast(0), durationMs = c.duration.coerceAtLeast(0),
            volume = c.volume, speed = c.playbackParameters.speed, shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode, error = if (c.playerError == null) null else _state.value.error)
    }

    override fun play(tracks: List<Track>, index: Int, shuffled: Boolean) {
        if (tracks.isEmpty()) return
        command { c ->
            c.shuffleModeEnabled = shuffled
            c.setMediaItems(tracks.map(::item), index.coerceIn(0, tracks.lastIndex), 0)
            c.prepare(); c.playWhenReady = true
        }
    }
    override fun toggle() = command {
        if (it.playerError != null) it.prepare()
        if (it.playWhenReady) it.pause() else {
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
            it.playWhenReady = true
        }
    }
    override fun next() = command { it.seekToNextMediaItem() }
    override fun previous() = command { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    override fun seek(fraction: Float) = command { if (it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) it.seekTo((it.duration * fraction.coerceIn(0f, 1f)).toLong()) }
    override fun setVolume(volume: Float) = command { it.volume = volume.coerceIn(0f, 1f) }
    override fun toggleShuffle() = command { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    override fun cycleRepeat() = command {
        it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }
    override fun playNext(track: Track) = command {
        if (it.mediaItemCount == 0) { it.setMediaItem(item(track)); it.prepare(); it.playWhenReady = true }
        else it.addMediaItem(it.currentMediaItemIndex + 1, item(track))
    }
    override fun enqueue(track: Track) = command {
        it.addMediaItem(item(track))
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
    }
    override fun jumpTo(index: Int) = command { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.playWhenReady = true } }
    override fun move(from: Int, to: Int) = command {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }
    override fun remove(index: Int) = command { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    override fun clear() = command { it.stop(); it.clearMediaItems() }
    override fun setSleepTimer(minutes: Int) {
        timerJob?.cancel()
        timerDeadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0
        _state.value = _state.value.copy(sleepRemainingMs = minutes * 60_000L)
        if (minutes > 0) timerJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause(); timerDeadline = 0
            _state.value = _state.value.copy(sleepRemainingMs = 0)
            publish()
        }
    }
    override fun retry() {
        if (!connection.isCompleted || connection.getCompleted() == null) {
            _state.value = _state.value.copy(error = "Перезапустите приложение, чтобы переподключить аудиосервис.")
            return
        }
        command { it.prepare(); it.playWhenReady = true }
    }
    override fun persist() {
        val c = controller ?: return
        prefs.edit().putString("queue", JSONArray((0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }).toString())
            .putInt("index", c.currentMediaItemIndex.coerceAtLeast(0)).putLong("position", c.currentPosition.coerceAtLeast(0))
            .putFloat("volume", c.volume).putBoolean("shuffle", c.shuffleModeEnabled).putInt("repeat", c.repeatMode).apply()
    }
}
