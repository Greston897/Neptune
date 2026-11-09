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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** Length of the night fade-in-seconds. Five is enough to be imperceptible, short enough to keep. */
private const val SleepFadeSeconds = 20

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
    /** Volume to restore after the night fade; null when no fade is running. */
    private var volumeBeforeFade: Float? = null
    private var lastRemembered: String? = null
    private var restoring = false
    /**
     * The entry that failed last.
     *
     * A queue where several rows are unplayable would otherwise re-enter the skip path for the entry
     * it just left — the error arrives again, the player seeks to the same next item, and the two
     * ping-pong forever. One skip per entry, remembered here, makes a run of failures walk the queue
     * exactly once and stop at the end.
     */
    private var skippedItem: String? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // The queue advance is done here rather than left to Media3's own auto-advance: a next
            // track that fails to open (an expired link, a dropped connection, a track pulled from
            // the service) would otherwise end playback silently, because auto-advance from a failed
            // item is not attempted. Stepping explicitly keeps a broken row in the middle of an album
            // from killing the rest of it.
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) advanceIfEnded(player)
            publish()
            persist()
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(isPlaying = false, isBuffering = false, error = failureText(error))
            controller?.let { advanceAfterFailure(it) }
        }
    }

    /**
     * Finishes a queue the way a listener expects: end of a track is end of a track.
     *
     * Media3 does this itself when playback simply reaches the end of an item, but the *last* item
     * sits at [Player.STATE_ENDED], and the transport's play button treats ENDED as "restart this
     * track" — which is right for a single-track queue and wrong for a queue whose next entry exists.
     */
    private fun advanceIfEnded(player: Player) {
        if (player.playbackState != Player.STATE_ENDED) return
        // The verdict is taken *before* the queue moves: after `seekToNextMediaItem()` the current item
        // is the next track, and asking it whether it was a preview would describe the wrong song.
        val endedId = player.currentMediaItem?.mediaId
        val hasNext = player.hasNextMediaItem()
        if (hasNext) {
            player.seekToNextMediaItem()
            player.play()
        }
        announcePreviewEnd(player, endedId)
    }

    /**
     * Says why the song stopped halfway, the moment it does.
     *
     * A preview ends at [Player.STATE_ENDED] exactly like a finished track, so without this the free
     * tier looks broken: a three-minute song ends at thirty seconds and the next one starts, with
     * nothing on screen to explain it. The notice names the track and what it was — an excerpt the
     * service offers instead of the file. It is said every time such an item ends, which is the only
     * moment the fact is useful: a replayed preview is explained again, and the pill still only holds
     * one message at a time, so a queue of previews updates the same sentence instead of stacking.
     */
    private fun announcePreviewEnd(player: Player, endedId: String?) {
        val mediaId = endedId ?: return
        val track = library.state.value.byId[mediaId] ?: return
        if (track.previewMs <= 0) return
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()?.ifBlank { null }
            ?.takeIf { player.currentMediaItem?.mediaId == mediaId } ?: track.title
        val seconds = (track.previewMs / 1000).coerceAtLeast(1)
        _state.value = _state.value.copy(
            message = "«$title» — только ${seconds} секунд: сервис отдаёт превью, а не файл",
            messageSeq = _state.value.messageSeq + 1,
        )
    }

    /**
     * A track that will not open is skipped, not fatal.
     *
     * Streaming fails for reasons a local file never does, and the common one — a link that expired
     * between two tracks of an album — is not a reason to stop listening. The user is told which one
     * was skipped and the queue carries on; if there is nothing left, the error stays on screen with
     * its retry button, which is the only case where the message is actionable.
     *
     * Guarded by [skippedItem] so a queue where several entries fail in a row cannot recurse: each
     * skip is one step, and the run ends when a track plays or the queue does. The guard is cleared by
     * the next successful transition, so a link that expires later in the same album is still skipped
     * rather than treated as already handled.
     */
    private fun advanceAfterFailure(player: Player) {
        val failed = player.currentMediaItem?.mediaId
        if (failed != null && failed == skippedItem) return
        skippedItem = failed
        if (!player.hasNextMediaItem()) return
        // The queue item already carries the title it was built with. Looking it up in the library
        // instead returned null for exactly the rows that fail most often — a file that is gone, a
        // track the user just removed — and the notice then could not name the track.
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()?.ifBlank { null }
            ?: failed?.let { library.state.value.byId[it]?.title }
        _state.value = _state.value.copy(
            error = null,
            message = if (title != null) "«$title» не открылся — играю дальше" else "Трек не открылся — играю дальше",
            messageSeq = _state.value.messageSeq + 1,
        )
        player.seekToNextMediaItem()
        player.play()
    }

    /**
     * One sentence that fits the cause.
     *
     * A stream fails for reasons a local file never does (no network, the link expired mid-track, the
     * licensing region), and telling the user to "проверить доступ к хранилищу" for all of them sends
     * them looking in the wrong place. The current item is inspected, not the exception, because the
     * exception is a stack of IO wrappers by the time it gets here.
     */
    private fun failureText(error: PlaybackException): String {
        val streaming = _state.value.track?.source == Track.Source.STREAM
        if (!streaming) return "Не удалось воспроизвести файл. Проверьте доступ к хранилищу и повторите."
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Нет связи с Яндекс Музыкой. Проверьте интернет."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Файл больше не отдаётся: ссылка устарела или трек снят."
            else -> "Трек не воспроизвёлся. Попробуйте ещё раз или выберите локальный файл."
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
            // Whatever is playing now is working, so the previous failure is history: the same track
            // failing later (a link expiring mid-album) must be skipped again, not silently ignored.
            if (skippedItem != null && skippedItem != track.id) skippedItem = null
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
        skippedItem = null
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
    /**
     * Removes by walking backwards so the shifting indices cannot skip an entry. Removing the
     * *current* item is what stops a deleted track from being resurrected by "next".
     */
    override fun dropTrack(id: String) = command { c ->
        var i = c.mediaItemCount - 1
        while (i >= 0) {
            if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
            i -= 1
        }
        if (c.mediaItemCount == 0) c.stop()
    }
    override fun clear() = command { it.stop(); it.clearMediaItems() }
    /**
     * Sleep timer, optionally with a fade.
     *
     * The previous version called `pause()` at the deadline. A track stopped dead at 00:00 is the
     * single most jarring thing a sleep timer can do while someone is falling asleep, so with
     * [fade] the volume is stepped down over the last [SleepFadeSeconds] and returned to the user's
     * level afterwards — a muted player the next morning would look like a broken volume slider.
     *
     * The ramp is a coroutine that reads the *current* controller volume each step and re-checks the
     * deadline, so cancelling or shortening the timer mid-fade takes effect immediately instead of
     * being overwritten by a stale local value.
     */
    override fun setSleepTimer(minutes: Int, fade: Boolean) {
        timerJob?.cancel()
        restoreVolumeAfterFade()
        timerDeadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0
        _state.value = _state.value.copy(sleepRemainingMs = minutes * 60_000L)
        if (minutes <= 0) return
        val total = minutes * 60_000L
        val fadeMs = if (fade) minOf(SleepFadeSeconds * 1000L, total / 2) else 0L
        timerJob = scope.launch {
            val target = total - fadeMs
            delay(target)
            if (fadeMs > 0) {
                val startVolume = controller?.volume ?: _state.value.volume
                volumeBeforeFade = startVolume
                val steps = (fadeMs / 250).coerceAtLeast(1)
                for (step in 1..steps) {
                    if (timerDeadline == 0L) return@launch
                    val c = controller ?: break
                    c.volume = (startVolume * (1f - step.toFloat() / steps)).coerceIn(0f, 1f)
                    _state.value = _state.value.copy(volume = c.volume)
                    delay(250)
                }
            }
            controller?.pause()
            timerDeadline = 0
            restoreVolumeAfterFade()
            _state.value = _state.value.copy(sleepRemainingMs = 0)
            publish()
        }
    }

    /** Puts the user's volume back after a fade so the next listen is not silent. */
    private fun restoreVolumeAfterFade() {
        val previous = volumeBeforeFade ?: return
        volumeBeforeFade = null
        controller?.let { c ->
            c.volume = previous
            _state.value = _state.value.copy(volume = previous)
        }
    }

    // `getCompleted()` is @ExperimentalCoroutinesApi: it throws when the future is still pending, and
    // the guard below relies on that. Opting in keeps the intent explicit instead of leaving a
    // warning that invites the next reader to delete a check that actually matters.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
