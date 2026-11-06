package com.neptune.player.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.GlassAction
import com.neptune.player.ui.GlassSlider
import com.neptune.player.ui.LiquidGlass
import com.neptune.player.ui.LocalGlassStyle
import com.neptune.player.ui.Ne
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconView
import com.neptune.player.ui.NeText
import com.neptune.player.ui.NeptuneChip
import com.neptune.player.ui.TrackArtwork
import com.neptune.player.ui.rememberHaptics

@Composable
fun NowPlayingScreen(state: NeptuneState, modifier: Modifier = Modifier) {
    val settings = state.settings
    val accent = settings.accent.color
    val haptic = rememberHaptics()
    var dragAccum by remember { mutableStateOf(0f) }

    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
        val coverSide = minOf(maxWidth - 44.dp, maxHeight * 0.42f)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---------- top bar ----------
            Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                GlassAction(
                    icon = NeIcon.ChevronDown,
                    onClick = { haptic(); state.tab = com.neptune.player.ui.Tab.Library },
                    size = 42.dp,
                    iconSize = 17.dp,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Box(Modifier.align(Alignment.Center)) {
                    NeptuneChip(
                        text = "СЕЙЧАС · ${state.current.quality.uppercase()}",
                        color = Ne.Dim,
                    )
                }
                GlassAction(
                    icon = NeIcon.More,
                    onClick = { haptic() },
                    size = 42.dp,
                    iconSize = 17.dp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Spacer(Modifier.weight(0.6f))

            // ---------- artwork ----------
            Box(
                Modifier
                    .size(coverSide)
                    .pointerInput(state.currentIndex) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragAccum < -110f) state.next()
                                else if (dragAccum > 110f) state.prev()
                                dragAccum = 0f
                            }
                        ) { _, delta ->
                            dragAccum += delta
                            if (dragAccum > 110f || dragAccum < -110f) {
                                haptic()
                                if (dragAccum < 0) state.next() else state.prev()
                                dragAccum = 0f
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to accent.copy(alpha = if (settings.glow) 0.34f else 0.12f),
                            0.55f to accent.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.minDimension * 0.78f,
                        ),
                        radius = size.minDimension * 0.78f,
                        center = center,
                    )
                }
                TrackArtwork(
                    track = state.current,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    corner = (settings.radius * 1.15f).dp,
                    rings = settings.rings,
                    animate = settings.animations,
                )
            }

            Spacer(Modifier.weight(0.35f))

            // ---------- title ----------
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NeText(
                        text = state.current.title,
                        size = 25.sp,
                        weight = FontWeight.ExtraBold,
                        tracking = (-0.7).sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    NeText(
                        text = state.current.artist,
                        size = 13.5.sp,
                        weight = FontWeight.SemiBold,
                        color = Ne.Dim,
                        tracking = 0.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                GlassAction(
                    icon = if (state.current.id in state.liked) NeIcon.HeartFill else NeIcon.Heart,
                    onClick = { haptic(); state.toggleLike() },
                    size = 44.dp,
                    iconSize = 19.dp,
                    color = if (state.current.id in state.liked) accent else Ne.Text,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- progress ----------
            val shown = state.progress
            GlassSlider(
                value = shown,
                onValueChange = { state.seekTo(it) },
                accent = accent,
                trackHeight = 7.dp,
                thumb = 14.dp,
            )
            if (settings.showTimes) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val pos = (state.current.durationMs * shown).toLong()
                    NeText(
                        text = formatTime(pos),
                        size = 10.5.sp,
                        weight = FontWeight.Bold,
                        color = Ne.Faint,
                        tracking = 0.4.sp,
                    )
                    NeText(
                        text = "-" + formatTime(state.current.durationMs - pos),
                        size = 10.5.sp,
                        weight = FontWeight.Bold,
                        color = Ne.Faint,
                        tracking = 0.4.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.weight(0.45f))

            // ---------- transport pill ----------
            LiquidGlass(Modifier.fillMaxWidth().height(84.dp), shape = CircleShape, accentEdge = true) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlassAction(
                        icon = NeIcon.Shuffle,
                        onClick = { haptic(); state.toggleShuffle() },
                        size = 40.dp,
                        iconSize = 18.dp,
                        color = if (state.shuffle) accent else Ne.Faint,
                        active = state.shuffle,
                    )
                    GlassAction(
                        icon = NeIcon.Prev,
                        onClick = { haptic(); state.prev() },
                        size = 48.dp,
                        iconSize = 24.dp,
                    )
                    PlayOrb(
                        playing = state.isPlaying,
                        onClick = { haptic(); state.toggle() },
                    )
                    GlassAction(
                        icon = NeIcon.Next,
                        onClick = { haptic(); state.next() },
                        size = 48.dp,
                        iconSize = 24.dp,
                    )
                    GlassAction(
                        icon = if (state.repeatMode == 2) NeIcon.RepeatOne else NeIcon.Repeat,
                        onClick = { haptic(); state.cycleRepeat() },
                        size = 40.dp,
                        iconSize = 18.dp,
                        color = if (state.repeatMode > 0) accent else Ne.Faint,
                        active = state.repeatMode > 0,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- volume ----------
            LiquidGlass(Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeIconView(
                        icon = if (state.volume < 0.02f) NeIcon.Mute else NeIcon.Volume,
                        size = 18.dp,
                        color = Ne.Dim,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.changeVolume(if (state.volume > 0.02f) 0f else 0.72f) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        GlassSlider(
                            value = state.volume,
                            onValueChange = { state.changeVolume(it) },
                            accent = accent,
                            trackHeight = 5.dp,
                            thumb = 11.dp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    NeText(
                        text = "${(state.volume * 100).toInt()}",
                        size = 10.5.sp,
                        weight = FontWeight.Bold,
                        color = Ne.Faint,
                        tracking = 0.5.sp,
                        align = TextAlign.End,
                    )
                }
            }

            Spacer(Modifier.weight(0.5f))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                NeptuneChip(text = state.current.album.uppercase())
                Spacer(Modifier.width(8.dp))
                NeptuneChip(text = "${state.current.year}")
            }
        }
    }
}

@Composable
private fun PlayOrb(playing: Boolean, onClick: () -> Unit) {
    val accent = LocalGlassStyle.current.accent
    val haptic = rememberHaptics()
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "orb",
    )
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                    center = c,
                    radius = r * 1.05f,
                ),
                radius = r * 1.05f,
                center = c,
            )
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.92f),
                        accent.copy(alpha = 0.98f),
                        accent,
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
                radius = r * 0.93f,
                center = c,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                    center = Offset(c.x - r * 0.25f, c.y - r * 0.55f),
                    radius = r * 0.9f,
                ),
                radius = r * 0.93f,
                center = c,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = r * 0.93f - 0.5.dp.toPx(),
                center = c,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        NeIconView(
            icon = if (playing) NeIcon.Pause else NeIcon.Play,
            size = 24.dp,
            color = Color(0xFF07070B),
            weight = 2.2f,
            modifier = Modifier.padding(start = if (playing) 0.dp else 3.dp),
        )
    }
}
