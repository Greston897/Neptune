package com.neptune.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import com.neptune.player.R
import com.neptune.player.MainActivity
import com.neptune.player.AppContainer
import com.neptune.player.NeptuneApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Like the ExoPlayer/MediaSession builders below, the notification provider and the media source
// factory are @UnstableApi surfaces in Media3 1.5.1. Opting in at the class is honest about that: the
// app builds against those contracts on purpose, and every one of them is exercised by the suite.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NeptunePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate() {
        super.onCreate()
        val container = (application as NeptuneApplication).container
        val settings = container.settings.state
        // Streams have no file to open: their queue entry is a `neptune://yandex/` handle that the
        // resolving source expands into a signed link while Media3 loads. Wiring it here rather than
        // in the UI means background playback and the notification controls take the same path as
        // the app's own transport.
        val sources = StreamSource(
            this,
            directUrl = { id -> streamLink(container, id) },
            offlineFile = { id -> container.yandex.offlineUri(id) },
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(sources.factory())
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = settings.value.skipSilence
        player.setPlaybackSpeed(settings.value.speed)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        // Media3's default small icon is a generic note. Use the brand mark so the notification,
        // the shade and the lock screen read as Neptune rather than as an anonymous audio stream.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ne_orbit)
            },
        )
        scope.launch { settings.map { it.skipSilence }.distinctUntilChanged().collect { player.skipSilenceEnabled = it } }
        scope.launch { settings.map { it.speed }.distinctUntilChanged().collect { player.setPlaybackSpeed(it) } }
    }
    /**
     * Blocks the *loading* thread while the link is minted (two HTTP round trips the first time, zero
     * afterwards). That is the only place blocking is correct: Media3 calls the resolver off the main
     * thread, and returning null there means "this stream cannot be opened", which the player then
     * reports through the normal error path.
     */
    private fun streamLink(container: AppContainer, id: String): String? = runCatching {
        runBlocking { container.yandex.streamUrl(id).getOrNull() }
    }.getOrNull()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
