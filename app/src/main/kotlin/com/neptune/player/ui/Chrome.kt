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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.PlaybackState
import com.neptune.player.state.NeptuneState

enum class Tab(val route: String, val title: String, val icon: NeIcon) {
    HOME("home", "Главная", NeIcon.Home), SEARCH("search", "Поиск", NeIcon.Search),
    LIBRARY("library", "Медиатека", NeIcon.Library), SETTINGS("settings", "Настройки", NeIcon.Settings)
}

/** Single source of truth for the floating dock geometry and the clearance every screen reserves. */
object DockMetrics {
    val Horizontal = 16.dp
    val Gap = 10.dp
    val Bottom = 12.dp
    val NavHeight = 72.dp
    val MiniHeight = 76.dp
    val Breath = 24.dp

    fun showsNavbar(route: String?): Boolean =
        route in Tab.entries.map { it.route } || route?.startsWith("collection/") == true

    fun showsMiniPlayer(route: String?, hasTrack: Boolean): Boolean =
        hasTrack && route != "player" && route != "queue"

    fun height(route: String?, hasTrack: Boolean): Dp {
        val nav = showsNavbar(route)
        val mini = showsMiniPlayer(route, hasTrack)
        if (!nav && !mini) return 0.dp
        return Bottom + (if (mini) MiniHeight + Gap else 0.dp) + (if (nav) NavHeight else 0.dp)
    }
}

@Composable
fun NeptuneNavBar(route: String?, onSelect: (Tab) -> Unit) {
    val animations = LocalSettings.current.animations
    val labels = LocalSettings.current.navLabels
    val activeTint = accent()
    GlassPanel(Modifier.fillMaxWidth().height(DockMetrics.NavHeight), corner = 30.dp, haze = LocalContentHaze.current) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val active = route == tab.route
                val color by animateColorAsState(if (active) activeTint else Ne.Dim, tween(if (animations) 180 else 0), label = "tab")
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(22.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = tab.title) { onSelect(tab) }
                        .semantics { selected = active; contentDescription = tab.title },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(width = 54.dp, height = 30.dp).clip(CircleShape)
                            .background(if (active) Brush.horizontalGradient(listOf(activeTint.copy(alpha = 0.20f), activeTint.copy(alpha = 0.08f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
                        contentAlignment = Alignment.Center,
                    ) {
                        NeIconView(tab.icon, 22.dp, color, weight = if (active) 2.15f else 1.9f)
                    }
                    if (labels) {
                        Spacer(Modifier.height(4.dp))
                        NeText(tab.title, size = 10.sp, color = color, weight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun NeptuneMiniPlayer(state: NeptuneState, playback: PlaybackState, onOpen: () -> Unit) {
    val track = playback.track ?: return
    var drag by remember { mutableFloatStateOf(0f) }
    GlassPanel(Modifier.fillMaxWidth().height(DockMetrics.MiniHeight), corner = 24.dp, haze = LocalContentHaze.current) {
        Column(Modifier.fillMaxSize()
            .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (drag < -45) onOpen(); drag = 0f }, onDragCancel = { drag = 0f }) { _, amount -> drag += amount } }
            .clickable(onClickLabel = "Открыть плеер", onClick = onOpen)) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackThumb(track, 48.dp, 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    NeText(track.title, size = 14.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    NeText(if (playback.isBuffering) "Загрузка…" else track.artist, size = 11.sp, color = Ne.Dim)
                }
                NeIconButton(if (playback.isPlaying) NeIcon.Pause else NeIcon.Play,
                    if (playback.isPlaying) "Пауза" else "Воспроизвести", { state.togglePlayback() },
                    size = 44.dp, iconSize = 22.dp, color = Ne.Black, background = accent())
                NeIconButton(NeIcon.Next, "Следующий трек", { state.nextTrack() }, size = 40.dp, iconSize = 19.dp, color = Ne.Dim)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxWidth(playback.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(accent()))
            }
        }
    }
}

@Composable
fun PlayerDock(modifier: Modifier, visible: Boolean, state: NeptuneState, playback: PlaybackState, onOpen: () -> Unit, route: String?, onTab: (Tab) -> Unit) {
    val hasTrack = playback.track != null
    if (!DockMetrics.showsNavbar(route) && !DockMetrics.showsMiniPlayer(route, hasTrack)) return
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(start = DockMetrics.Horizontal, end = DockMetrics.Horizontal, bottom = DockMetrics.Bottom)) {
        AnimatedVisibility(visible && DockMetrics.showsMiniPlayer(route, hasTrack), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                NeptuneMiniPlayer(state, playback, onOpen)
                Spacer(Modifier.height(DockMetrics.Gap))
            }
        }
        if (DockMetrics.showsNavbar(route)) NeptuneNavBar(route, onTab)
    }
}
