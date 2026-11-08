package com.neptune.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun NowPlayingScreen(
    state: NeptuneState, onBack: () -> Unit, onQueue: () -> Unit, onCollection: (String, String) -> Unit,
    onLyrics: () -> Unit = {},
) {
    val playback by state.playback.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val track = playback.track
    var scrub by remember { mutableStateOf<Float?>(null) }
    var sleep by remember { mutableStateOf(false) }
    var volumeOpen by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = NeSpace.xxl),
    ) {
        Row(Modifier.fillMaxWidth().height(NeRow.header), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(NeIcon.ChevronDown, "Вернуться назад", onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                NeText(
                    "СЕЙЧАС ИГРАЕТ", size = NeType.micro, lineHeight = NeType.microLine,
                    color = Ne.Faint, tracking = 1.2.sp, weight = FontWeight.Bold,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(
                    track?.album ?: "Neptune", size = NeType.caption, lineHeight = NeType.captionLine,
                    weight = FontWeight.Bold, maxLines = 1,
                )
            }
            // Text sits next to the overflow button: it is the second thing people reach for while a
            // song plays, and burying it in a sheet would cost a tap on every listen.
            NeIconButton(NeIcon.Mic, "Текст трека", onLyrics, enabled = track != null, color = Ne.Dim)
            NeIconButton(NeIcon.More, "Действия с треком", { state.contextTrack = track }, enabled = track != null)
        }
        // Bound once, right after the empty-state guard: everything below (the lyric preview
        // included) can then use a non-null track, so there is no second null check to keep in sync
        // with this one — the compiler used to warn about exactly that redundancy.
        val nowPlaying = track
        if (nowPlaying == null) {
            EmptyState(
                NeIcon.Music, "Выбери свой ритм", "Открой медиатеку и включи первый трек.",
                action = "В медиатеку", onAction = onBack,
            )
            return@Column
        }
        Box(Modifier.fillMaxWidth().padding(top = NeSpace.xl, bottom = NeSpace.xxl), contentAlignment = Alignment.Center) {
            TrackArtwork(
                track,
                Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f).pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (drag < -90) state.nextTrack() else if (drag > 90) state.previousTrack(); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { _, amount -> drag += amount }
                },
                (settings.radius * 1.25f).dp, animate = playback.isPlaying,
            )
        }
        // Title column is flexible; the like button keeps its square so a long title ellipsizes.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NeText(
                    track.title, size = NeType.headline, lineHeight = NeType.headlineLine,
                    weight = FontWeight.ExtraBold, tracking = (-0.6).sp, maxLines = 2,
                )
                Spacer(Modifier.height(NeSpace.xs))
                NeText(
                    track.artist,
                    Modifier.clip(CircleShape).clickable { onCollection("artist", track.artist) }.padding(vertical = NeSpace.xxs),
                    size = NeType.label, lineHeight = NeType.labelLine, color = Ne.Dim, maxLines = 1,
                )
            }
            Spacer(Modifier.width(NeSpace.sm))
            NeIconButton(
                if (track.id in users.liked) NeIcon.HeartFill else NeIcon.Heart,
                if (track.id in users.liked) "Убрать из любимого" else "В любимое", { state.like(track) },
                color = if (track.id in users.liked) accent() else Ne.Dim, iconSize = NeIconSize.lg,
            )
        }
        // A single live lyric line, tappable to open the full text. Only when the file actually
        // carries timings — an unsynchronised text would show a line that does not follow the song,
        // which reads as a bug rather than a feature.
        // The repository map is what makes a scan finishing while this screen is open repaint; the
        // track is bound once so the effect and the block below share one non-null value.
        val lyricsDocs by state.lyricsDocs.collectAsStateWithLifecycle()
        val doc = state.lyricsFor(nowPlaying) ?: lyricsDocs[nowPlaying.id]
        LaunchedEffect(nowPlaying.id) { state.requestLyrics(nowPlaying) }
        if (doc != null && doc.synced && !doc.isEmpty) {
            val index = doc.activeIndex(playback.positionMs)
            val line = doc.lines.getOrNull(index)?.text
                ?: doc.lines.firstOrNull { it.timeMs > playback.positionMs }?.text
            if (line != null) {
                Spacer(Modifier.height(NeSpace.sm))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onLyrics)
                        .padding(horizontal = NeSpace.xs, vertical = NeSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeIconView(NeIcon.Mic, NeIconSize.xs, accent())
                    Spacer(Modifier.width(NeSpace.sm))
                    NeText(
                        line, Modifier.weight(1f), size = NeType.caption, lineHeight = NeType.captionLine,
                        color = Ne.Dim, maxLines = 1,
                    )
                    NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
                }
            }
        }
        Spacer(Modifier.height(NeSpace.lg))
        val fraction = scrub ?: playback.progress
        Slider(
            value = fraction, onValueChange = { scrub = it },
            onValueChangeFinished = { scrub?.let(state::seek); scrub = null },
            enabled = playback.durationMs > 0,
            modifier = Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = "Позиция трека" },
            colors = SliderDefaults.colors(
                thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Ne.Line,
                disabledThumbColor = Ne.Faint, disabledActiveTrackColor = Ne.Faint, disabledInactiveTrackColor = Ne.Line,
            ),
        )
        Row(
            Modifier.fillMaxWidth().clickable { state.edit { it.copy(showRemaining = !it.showRemaining) } }
                .padding(vertical = NeSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeText(
                formatTime(if (scrub != null) (playback.durationMs * fraction).toLong() else playback.positionMs),
                Modifier.weight(1f), size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint,
            )
            NeText(
                if (settings.showRemaining) "−${formatTime((playback.durationMs * (1 - fraction)).toLong())}"
                else formatTime(playback.durationMs),
                size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint,
            )
        }
        Spacer(Modifier.height(NeSpace.lg))
        // Transport: every cell owns a fixed share, so nothing collapses on narrow screens.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NeIconButton(
                    NeIcon.Shuffle, if (playback.shuffle) "Выключить перемешивание" else "Перемешать", state::toggleShuffle,
                    selected = playback.shuffle, color = if (playback.shuffle) accent() else Ne.Faint, iconSize = NeIconSize.md,
                )
            }
            NeIconButton(NeIcon.Prev, "Предыдущий трек", state::previousTrack, size = 56.dp, iconSize = NeIconSize.xl)
            PlayPauseButton(playback, { state.togglePlayback() }, Modifier.padding(horizontal = NeSpace.md))
            NeIconButton(NeIcon.Next, "Следующий трек", state::nextTrack, size = 56.dp, iconSize = NeIconSize.xl)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NeIconButton(
                    if (playback.repeat == 2) NeIcon.RepeatOne else NeIcon.Repeat,
                    when (playback.repeat) { 1 -> "Повторять один трек"; 2 -> "Выключить повтор"; else -> "Повторять очередь" },
                    { state.cycleRepeat() }, selected = playback.repeat != 0,
                    color = if (playback.repeat != 0) accent() else Ne.Faint, iconSize = NeIconSize.md,
                )
            }
        }
        Spacer(Modifier.height(NeSpace.xl))
        playback.error?.let { error ->
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(NeSpace.md), verticalAlignment = Alignment.CenterVertically) {
                    NeText(error, Modifier.weight(1f), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Error, maxLines = 3)
                    Spacer(Modifier.width(NeSpace.sm))
                    NeText(
                        "Ещё раз",
                        Modifier.clip(CircleShape).clickable { state.retryPlayback() }.padding(NeSpace.sm),
                        size = NeType.caption, lineHeight = NeType.captionLine, weight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(NeSpace.md))
        }
        // The sleep-timer button owns the flexible middle cell, so its label can never be squeezed
        // to nothing by the two icon buttons (the old SpaceBetween row did exactly that).
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(
                if (playback.volume < 0.02f) NeIcon.Mute else NeIcon.Volume, "Громкость", { volumeOpen = !volumeOpen },
                color = if (volumeOpen) accent() else Ne.Dim, iconSize = NeIconSize.md,
            )
            Spacer(Modifier.width(NeSpace.sm))
            NeButton(
                if (playback.sleepRemainingMs > 0) "Сон · ${formatTime(playback.sleepRemainingMs)}" else "Таймер сна",
                { sleep = true }, Modifier.weight(1f), icon = NeIcon.Timer, primary = false,
            )
            Spacer(Modifier.width(NeSpace.sm))
            NeIconButton(NeIcon.Queue, "Очередь воспроизведения", onQueue, color = Ne.Dim, iconSize = NeIconSize.md)
        }
        if (volumeOpen) {
            Row(Modifier.fillMaxWidth().padding(top = NeSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                NeText(
                    "${(playback.volume * 100).toInt()}%", Modifier.width(44.dp),
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim,
                )
                Slider(
                    playback.volume, { state.setVolume(it) }, Modifier.weight(1f).height(36.dp),
                    colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Ne.Line),
                )
            }
        }
        Spacer(Modifier.height(NeSpace.xl))
    }
    if (sleep) SleepDialog(playback, { sleep = false }) { state.setSleepTimer(it); sleep = false }
}

@Composable
fun PlayPauseButton(playback: PlaybackState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHaptics.current
    val animated = LocalSettings.current.animations
    val scale by animateFloatAsState(
        if (playback.isPlaying) 1f else 0.96f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), label = "play",
    )
    Box(
        modifier.size(72.dp).scale(if (animated) scale else 1f).clip(CircleShape).background(accent())
            .clickable(enabled = playback.connected) { haptic(); onClick() }
            .semantics { contentDescription = if (playback.isPlaying) "Пауза" else "Воспроизвести" },
        contentAlignment = Alignment.Center,
    ) {
        if (playback.isBuffering) CircularProgressIndicator(Modifier.size(NeIconSize.lg), color = Ne.Black, strokeWidth = 2.5.dp)
        else Crossfade(playback.isPlaying, animationSpec = tween(if (animated) NeMotion.Quick else 0, easing = NeEase), label = "transport") { playing ->
            NeIconView(if (playing) NeIcon.Pause else NeIcon.Play, NeIconSize.xxl, Ne.Black)
        }
    }
}
