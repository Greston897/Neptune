package com.neptune.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun NowPlayingScreen(state: NeptuneState, onBack: () -> Unit, onQueue: () -> Unit, onCollection: (String, String) -> Unit) {
    val playback by state.playback.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val track = playback.track
    var scrub by remember { mutableStateOf<Float?>(null) }
    var sleep by remember { mutableStateOf(false) }
    var volumeOpen by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(NeIcon.ChevronDown, "Вернуться назад", onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                NeText("СЕЙЧАС ИГРАЕТ", size = 9.sp, color = Ne.Faint, tracking = 1.6.sp)
                Spacer(Modifier.height(4.dp)); NeText(track?.album ?: "Neptune", size = 11.sp, weight = FontWeight.Bold, maxLines = 1)
            }
            NeIconButton(NeIcon.More, "Действия с треком", { state.contextTrack = track }, enabled = track != null)
        }
        if (track == null) {
            EmptyState(NeIcon.Music, "Выбери свой ритм", "Открой медиатеку и включи первый трек.", action = "В медиатеку", onAction = onBack)
            return@Column
        }
        Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp), contentAlignment = Alignment.Center) {
            TrackArtwork(track, Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f)
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(onDragEnd = { if (drag < -90) state.nextTrack() else if (drag > 90) state.previousTrack(); drag = 0f }, onDragCancel = { drag = 0f }) { _, amount -> drag += amount }
                }, (settings.radius * 1.25f).dp, animate = playback.isPlaying)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NeText(track.title, size = 27.sp, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 2, lineHeight = 32.sp)
                Spacer(Modifier.height(6.dp))
                NeText(track.artist, Modifier.clip(CircleShape).clickable { onCollection("artist", track.artist) }.padding(vertical = 3.dp), size = 14.sp, color = Ne.Dim)
            }
            NeIconButton(if (track.id in users.liked) NeIcon.HeartFill else NeIcon.Heart,
                if (track.id in users.liked) "Убрать из любимого" else "В любимое", { state.like(track) },
                color = if (track.id in users.liked) accent() else Ne.Dim, iconSize = 25.dp)
        }
        Spacer(Modifier.height(20.dp))
        val fraction = scrub ?: playback.progress
        Slider(value = fraction, onValueChange = { scrub = it }, onValueChangeFinished = { scrub?.let(state::seek); scrub = null },
            enabled = playback.durationMs > 0, modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Позиция трека" },
            colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Ne.Line, disabledThumbColor = Ne.Faint, disabledActiveTrackColor = Ne.Faint, disabledInactiveTrackColor = Ne.Line))
        Row(Modifier.fillMaxWidth().clickable { state.edit { it.copy(showRemaining = !it.showRemaining) } }.padding(vertical = 4.dp)) {
            NeText(formatTime(if (scrub != null) (playback.durationMs * fraction).toLong() else playback.positionMs), Modifier.weight(1f), size = 10.5.sp, color = Ne.Faint)
            NeText(if (settings.showRemaining) "−${formatTime((playback.durationMs * (1 - fraction)).toLong())}" else formatTime(playback.durationMs), size = 10.5.sp, color = Ne.Faint)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            NeIconButton(NeIcon.Shuffle, if (playback.shuffle) "Выключить перемешивание" else "Перемешать", state::toggleShuffle, selected = playback.shuffle, color = if (playback.shuffle) accent() else Ne.Faint, iconSize = 20.dp)
            NeIconButton(NeIcon.Prev, "Предыдущий трек", state::previousTrack, size = 52.dp, iconSize = 29.dp)
            PlayPauseButton(playback, { state.togglePlayback() })
            NeIconButton(NeIcon.Next, "Следующий трек", state::nextTrack, size = 52.dp, iconSize = 29.dp)
            NeIconButton(if (playback.repeat == 2) NeIcon.RepeatOne else NeIcon.Repeat,
                when (playback.repeat) { 1 -> "Повторять один трек"; 2 -> "Выключить повтор"; else -> "Повторять очередь" },
                { state.cycleRepeat() }, selected = playback.repeat != 0, color = if (playback.repeat != 0) accent() else Ne.Faint, iconSize = 20.dp)
        }
        Spacer(Modifier.height(22.dp))
        playback.error?.let { error ->
            GlassPanel(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                NeText(error, Modifier.weight(1f), size = 11.sp, color = Ne.Error, maxLines = 3, lineHeight = 16.sp)
                NeText("Ещё раз", Modifier.clip(CircleShape).clickable { state.retryPlayback() }.padding(10.dp), size = 12.sp, weight = FontWeight.Bold)
            } }
        }
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            NeIconButton(if (playback.volume < 0.02f) NeIcon.Mute else NeIcon.Volume, "Громкость", { volumeOpen = !volumeOpen }, color = Ne.Dim, iconSize = 20.dp)
            NeButton(if (playback.sleepRemainingMs > 0) "Сон · ${formatTime(playback.sleepRemainingMs)}" else "Таймер сна", { sleep = true }, icon = NeIcon.Timer, primary = false)
            NeIconButton(NeIcon.Queue, "Очередь воспроизведения", onQueue, color = Ne.Dim, iconSize = 22.dp)
        }
        if (volumeOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeText("${(playback.volume * 100).toInt()}%", Modifier.width(42.dp), size = 11.sp, color = Ne.Dim)
            Slider(playback.volume, { state.setVolume(it) }, Modifier.weight(1f).height(36.dp),
                colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Ne.Line))
        }
        Spacer(Modifier.height(16.dp))
        NeText(if (track.source == Track.Source.DEMO) "NEPTUNE STUDIO · ORIGINAL SESSION" else "ЛОКАЛЬНЫЙ АУДИОФАЙЛ", Modifier.fillMaxWidth().padding(bottom = 16.dp), size = 8.5.sp, color = Ne.Whisper, tracking = 1.4.sp, align = TextAlign.Center)
    }
    if (sleep) SleepDialog(playback, { sleep = false }) { state.setSleepTimer(it); sleep = false }
}

@Composable
fun PlayPauseButton(playback: PlaybackState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHaptics.current
    val animated = LocalSettings.current.animations
    val scale by animateFloatAsState(if (playback.isPlaying) 1f else 0.96f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), label = "play")
    Box(modifier.size(70.dp).scale(if (animated) scale else 1f).clip(CircleShape).background(accent())
        .clickable(enabled = playback.connected) { haptic(); onClick() }.semantics { contentDescription = if (playback.isPlaying) "Пауза" else "Воспроизвести" }, contentAlignment = Alignment.Center) {
        if (playback.isBuffering) CircularProgressIndicator(Modifier.size(24.dp), color = Ne.Black, strokeWidth = 2.5.dp)
        else Crossfade(playback.isPlaying, animationSpec = androidx.compose.animation.core.tween(if (animated) 160 else 0), label = "transport") { playing ->
            NeIconView(if (playing) NeIcon.Pause else NeIcon.Play, 28.dp, Ne.Black)
        }
    }
}
