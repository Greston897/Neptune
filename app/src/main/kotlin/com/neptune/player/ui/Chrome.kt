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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neptune.player.model.PlaybackState
import com.neptune.player.state.NeptuneState

enum class Tab(val route: String, val title: String, val icon: NeIcon) {
    HOME("home", "Главная", NeIcon.Home), SEARCH("search", "Поиск", NeIcon.Search),
    LIBRARY("library", "Медиатека", NeIcon.Library), SETTINGS("settings", "Настройки", NeIcon.Settings)
}

/** Single source of truth for the floating dock geometry and the clearance every screen reserves. */
object DockMetrics {
    val Horizontal = NeSpace.lg
    val Gap = 10.dp
    val Bottom = NeSpace.md
    val NavHeight = 68.dp
    val MiniHeight = 72.dp
    val Breath = NeSpace.xxl
    val PillTop = NeSpace.sm
    val PillMaxWidth = 340.dp

    fun showsNavbar(route: String?): Boolean =
        route in Tab.entries.map { it.route } || route?.startsWith("collection/") == true

    fun showsMiniPlayer(route: String?, hasTrack: Boolean): Boolean = hasTrack && showsNavbar(route)

    /**
     * The top pill carries *identity* (artwork, title, artist); the bottom mini-player keeps
     * *transport*. Both are shown on tab/collection routes, neither on the immersive full player.
     */
    fun showsTrackPill(route: String?, hasTrack: Boolean): Boolean = hasTrack && showsNavbar(route)

    /** Top clearance a screen must reserve so its header never sits underneath the pill. */
    fun topHeight(route: String?, hasTrack: Boolean): Dp =
        if (showsTrackPill(route, hasTrack)) PillTop + NeRow.pill + Gap else 0.dp

    fun height(route: String?, hasTrack: Boolean): Dp {
        val nav = showsNavbar(route)
        val mini = showsMiniPlayer(route, hasTrack)
        if (!nav && !mini) return 0.dp
        return Bottom + (if (mini) MiniHeight + Gap else 0.dp) + (if (nav) NavHeight else 0.dp)
    }
}

/**
 * Floating tab bar.
 *
 * Selection is expressed by the glyph alone: the active tab paints solid white (and uses the
 * heavier stroke), inactive tabs stay dim grey. No capsule, no tinted plate behind the icon —
 * the bar itself is the only surface.
 */
@Composable
fun NeptuneNavBar(route: String?, onSelect: (Tab) -> Unit) {
    val animations = LocalSettings.current.animations
    val labels = LocalSettings.current.navLabels
    GlassPanel(
        Modifier.fillMaxWidth().height(DockMetrics.NavHeight), corner = 26.dp,
        haze = LocalContentHaze.current,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = NeSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = route == tab.route
                val color by animateColorAsState(
                    if (active) Ne.Text else Ne.Faint, tween(if (animations) 180 else 0), label = "tabColor",
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = tab.title) { onSelect(tab) }
                        .semantics { selected = active; contentDescription = tab.title },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    NeIconView(tab.icon, NeIconSize.lg, color, bold = active)
                    if (labels) {
                        Spacer(Modifier.height(NeSpace.xs))
                        NeText(
                            tab.title, size = NeType.micro, lineHeight = NeType.microLine, color = color,
                            weight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating now-playing capsule, centred at the top of the screen.
 *
 * Reads as a single rounded object: artwork on the left, title over artist, live equalizer on the
 * right. Tapping it opens the full player. The pill deliberately carries no transport buttons —
 * those stay in the mini-player, so the two surfaces do not duplicate each other's controls.
 */
@Composable
fun TrackPill(playback: PlaybackState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val track = playback.track ?: return
    GlassPanel(
        modifier.widthIn(max = DockMetrics.PillMaxWidth).fillMaxWidth().height(NeRow.pill),
        corner = NeRow.pill / 2, haze = LocalContentHaze.current,
    ) {
        Row(
            Modifier.fillMaxSize()
                .clickable(onClickLabel = "Открыть плеер", onClick = onOpen)
                .semantics { contentDescription = "Сейчас играет: ${track.title} — ${track.artist}" }
                .padding(start = NeSpace.xs, end = NeSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumb(track, NeRow.pillArt, 12.dp)
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText(
                    track.title, size = NeType.label, lineHeight = NeType.labelLine,
                    weight = FontWeight.Bold, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(
                    if (playback.isBuffering) "Загрузка…" else track.artist,
                    size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Dim, maxLines = 1,
                )
            }
            Spacer(Modifier.width(NeSpace.sm))
            EqualizerBars(playback.isPlaying, color = accent(), maxHeight = 14.dp)
        }
    }
}

/** Compact now-playing bar wired to the player state. */
@Composable
fun NeptuneMiniPlayer(state: NeptuneState, playback: PlaybackState, onOpen: () -> Unit) {
    MiniPlayerContent(playback, { state.togglePlayback() }, { state.nextTrack() }, onOpen)
}

/**
 * Stateless mini-player. Controls keep fixed squares so long titles can only ellipsize, and the
 * bar can be laid out in isolation by the instrumented layout tests.
 */
@Composable
fun MiniPlayerContent(playback: PlaybackState, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    val track = playback.track ?: return
    var drag by remember { mutableFloatStateOf(0f) }
    GlassPanel(
        Modifier.fillMaxWidth().height(DockMetrics.MiniHeight), corner = 20.dp,
        haze = LocalContentHaze.current,
    ) {
        Column(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag < -45) onOpen(); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { _, amount -> drag += amount }
                }
                .clickable(onClickLabel = "Открыть плеер", onClick = onOpen),
        ) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(start = NeSpace.sm, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackThumb(track, NeRow.thumbCompact, 10.dp)
                Spacer(Modifier.width(NeSpace.md))
                Column(Modifier.weight(1f)) {
                    NeText(track.title, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.height(NeSpace.xxs))
                    NeText(
                        if (playback.isBuffering) "Загрузка…" else track.artist,
                        size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                NeIconButton(
                    if (playback.isPlaying) NeIcon.Pause else NeIcon.Play,
                    if (playback.isPlaying) "Пауза" else "Воспроизвести", onToggle,
                    size = NeTouch.min, iconSize = NeIconSize.md, color = Ne.Black, background = accent(),
                )
                NeIconButton(
                    NeIcon.Next, "Следующий трек", onNext,
                    size = NeTouch.min, iconSize = NeIconSize.md, color = Ne.Dim,
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = NeSpace.md).padding(bottom = 6.dp)
                    .height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(playback.progress.coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(CircleShape).background(accent()),
                )
            }
        }
    }
}

@Composable
fun PlayerDock(
    modifier: Modifier, visible: Boolean, state: NeptuneState, playback: PlaybackState,
    onOpen: () -> Unit, route: String?, onTab: (Tab) -> Unit,
) {
    val hasTrack = playback.track != null
    if (!DockMetrics.showsNavbar(route) && !DockMetrics.showsMiniPlayer(route, hasTrack)) return
    Column(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = DockMetrics.Horizontal, end = DockMetrics.Horizontal, bottom = DockMetrics.Bottom),
    ) {
        AnimatedVisibility(
            visible && DockMetrics.showsMiniPlayer(route, hasTrack),
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                NeptuneMiniPlayer(state, playback, onOpen)
                Spacer(Modifier.height(DockMetrics.Gap))
            }
        }
        if (DockMetrics.showsNavbar(route)) NeptuneNavBar(route, onTab)
    }
}
