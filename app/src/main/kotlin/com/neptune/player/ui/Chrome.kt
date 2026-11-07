package com.neptune.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.PlaybackState
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.screens.PlayPauseButton

enum class Tab(val route: String, val title: String, val icon: NeIcon) {
    HOME("home", "Главная", NeIcon.Home), SEARCH("search", "Поиск", NeIcon.Search),
    LIBRARY("library", "Медиатека", NeIcon.Library), SETTINGS("settings", "Настройки", NeIcon.Settings)
}

@Composable
fun NeptuneNavBar(route: String?, onSelect: (Tab) -> Unit) {
    val animations = LocalSettings.current.animations
    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), corner = 26.dp, haze = LocalContentHaze.current) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Tab.entries.forEach { tab ->
            val active = route == tab.route
            val color by animateColorAsState(if (active) Ne.Text else Ne.Faint, tween(if (animations) 180 else 0), label = "tab")
            Column(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = tab.title) { onSelect(tab) }
                .semantics { selected = active; contentDescription = tab.title }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(width = 46.dp, height = 28.dp).clip(CircleShape).background(if (active) Ne.Elevated else Color.Transparent), contentAlignment = Alignment.Center) {
                    NeIconView(tab.icon, 21.dp, color, weight = if (active) 2.1f else 1.8f)
                }
                if (LocalSettings.current.navLabels) { Spacer(Modifier.height(4.dp)); NeText(tab.title, size = 9.5.sp, color = color, weight = if (active) FontWeight.Bold else FontWeight.Medium) }
            }
        }
    }
    }
}

@Composable private fun MaterialBackground() = androidx.compose.material3.MaterialTheme.colorScheme.background

@Composable
fun NeptuneMiniPlayer(state: NeptuneState, playback: PlaybackState, onOpen: () -> Unit) {
    val track = playback.track ?: return
    var drag by remember { mutableFloatStateOf(0f) }
    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), haze = LocalContentHaze.current) {
    Column(Modifier.fillMaxWidth()
        .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (drag < -45) onOpen(); drag = 0f }, onDragCancel = { drag = 0f }) { _, amount -> drag += amount } }
        .clickable(onClickLabel = "Открыть плеер", onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackThumb(track, 46.dp, 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NeText(track.title, size = 13.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                NeText(if (playback.isBuffering) "Загрузка…" else track.artist, size = 11.sp, color = Ne.Dim)
            }
            NeIconButton(if (playback.isPlaying) NeIcon.Pause else NeIcon.Play,
                if (playback.isPlaying) "Пауза" else "Воспроизвести", { state.togglePlayback() }, iconSize = 22.dp)
            NeIconButton(NeIcon.Next, "Следующий трек", { state.nextTrack() }, iconSize = 19.dp, color = Ne.Dim)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 5.dp).height(2.dp).clip(CircleShape).background(Ne.Line)) {
            Box(Modifier.fillMaxWidth(playback.progress).fillMaxSize().clip(CircleShape).background(accent()))
        }
    }
    }
}

@Composable
fun PlayerDock(visible: Boolean, state: NeptuneState, playback: PlaybackState, onOpen: () -> Unit, route: String?, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        AnimatedVisibility(visible && playback.track != null && route != "queue", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            NeptuneMiniPlayer(state, playback, onOpen)
        }
        if (route in Tab.entries.map { it.route } || route?.startsWith("collection/") == true) NeptuneNavBar(route, onTab)
        else Spacer(Modifier.height(4.dp))
    }
}
