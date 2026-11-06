package com.neptune.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.state.NeptuneState

internal fun Tab.icon(): NeIcon = when (this) {
    Tab.Now -> NeIcon.Wave
    Tab.Library -> NeIcon.Library
    Tab.Search -> NeIcon.Search
    Tab.Tune -> NeIcon.Sliders
}

/** Floating glass navigation bar with a sliding lens. */
@Composable
fun NeptuneNavBar(
    tab: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    labels: Boolean = false,
) {
    val accent = LocalGlassStyle.current.accent
    val haptic = rememberHaptics()
    val items = Tab.entries

    LiquidGlass(modifier.fillMaxWidth(), shape = CircleShape, highlight = 1.15f) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
            val itemWidth = maxWidth / items.size
            val x by animateDpAsState(
                targetValue = itemWidth * tab.ordinal,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                label = "navLens",
            )
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.10f))
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.34f), CircleShape)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    val active = item == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (labels) 52.dp else 50.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!active) haptic()
                                onSelect(item)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NeIconView(
                                icon = item.icon(),
                                size = if (labels) 19.dp else 21.dp,
                                color = if (active) Ne.Text else Ne.Faint,
                                weight = if (active) 2.15f else 1.8f,
                            )
                            if (labels) {
                                Spacer(Modifier.height(3.dp))
                                NeText(
                                    text = item.title,
                                    size = 9.5.sp,
                                    weight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (active) Ne.Text else Ne.Faint,
                                    tracking = 0.2.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact glass player docked right above the nav bar. */
@Composable
fun NeptuneMiniPlayer(
    state: NeptuneState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalGlassStyle.current.accent
    val haptic = rememberHaptics()
    LiquidGlass(modifier.fillMaxWidth(), shape = CircleShape) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpen() }
                    .padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackThumb(state.current, 40.dp, 13.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    NeText(state.current.title, size = 13.5.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(1.dp))
                    NeText(
                        state.current.artist,
                        size = 11.sp,
                        weight = FontWeight.Medium,
                        color = Ne.Faint,
                        tracking = 0.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                NeIconView(
                    icon = if (state.isPlaying) NeIcon.Pause else NeIcon.Play,
                    size = 17.dp,
                    color = Ne.Text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic()
                            state.toggle()
                        }
                        .padding(9.dp),
                )
                Spacer(Modifier.width(4.dp))
                NeIconView(
                    icon = NeIcon.Next,
                    size = 16.dp,
                    color = Ne.Faint,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.next() }
                        .padding(9.dp),
                )
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .height(2.5.dp)
            ) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r)
                val w = size.width * state.progress
                if (w > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent)),
                        size = Size(w, size.height),
                        cornerRadius = r,
                    )
                }
            }
        }
    }
}

/** Little wordmark used in headers. */
@Composable
fun NeptuneWordmark(modifier: Modifier = Modifier, color: Color = Ne.Text) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 13.dp, height = 17.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.width * 0.30f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.86f)
                lineTo(size.width * 0.16f, size.height * 0.14f)
                lineTo(size.width * 0.84f, size.height * 0.86f)
                lineTo(size.width * 0.84f, size.height * 0.14f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
        Spacer(Modifier.width(7.dp))
        NeText("NEPTUNE", size = 11.sp, weight = FontWeight.ExtraBold, color = color, tracking = 3.4.sp)
    }
}

/** Floating progress / meta chip. */
@Composable
fun NeptuneChip(text: String, modifier: Modifier = Modifier, color: Color = Ne.Faint) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        NeText(text, size = 9.5.sp, weight = FontWeight.Bold, color = color, tracking = 0.6.sp)
    }
}

/** Hairline separator. */
@Composable
fun NeDivider(modifier: Modifier = Modifier, alpha: Float = 0.08f) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}

/** Empty spacer helper for rows. */
@Composable
fun HSpace(w: androidx.compose.ui.unit.Dp) = Spacer(Modifier.width(w))
