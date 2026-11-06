package com.neptune.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.Track
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural cover art — no bitmaps, supersampled vector gradients that always match the palette.
 */
@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    rings: Boolean = true,
    animate: Boolean = true,
) {
    val shape = remember(corner) { SquircleShape(corner) }
    val transition = rememberInfiniteTransition(label = "art")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (animate) 34_000 else 10_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    Box(modifier.clip(shape)) {
        Canvas(Modifier.matchParentSize()) {
            val p = track.palette
            val w = size.width
            val h = size.height
            val ang = drift * 6.2831855f

            drawRect(
                Brush.linearGradient(
                    listOf(p[0], p[0].copy(alpha = 0.9f), Ne.Ink),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                )
            )

            val c1 = Offset(w * (0.30f + 0.06f * cos(ang)), h * (0.26f + 0.05f * sin(ang)))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to p[1].copy(alpha = 0.95f),
                    0.55f to p[1].copy(alpha = 0.35f),
                    1f to p[1].copy(alpha = 0f),
                    center = c1,
                    radius = w * 0.92f,
                ),
                radius = w * 0.92f,
                center = c1,
            )

            val c2 = Offset(w * (0.78f - 0.05f * sin(ang)), h * (0.74f - 0.05f * cos(ang)))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to p[2].copy(alpha = 0.80f),
                    0.6f to p[2].copy(alpha = 0.22f),
                    1f to p[2].copy(alpha = 0f),
                    center = c2,
                    radius = w * 0.78f,
                ),
                radius = w * 0.78f,
                center = c2,
            )

            if (rings) {
                val center = Offset(w / 2f, h / 2f)
                for (i in 1..4) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f - i * 0.017f),
                        radius = w * (0.14f + i * 0.115f),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.20f),
                    0.45f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(w * 0.95f, h * 0.95f),
                )
            )
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.30f),
                )
            )
            drawRect(brush = grainBrush, alpha = 0.06f, blendMode = BlendMode.Overlay)
            drawRect(color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/** Small square variant used in rows and the mini player. */
@Composable
fun TrackThumb(track: Track, size: Dp = 46.dp, corner: Dp = 15.dp) {
    TrackArtwork(
        track = track,
        modifier = Modifier.size(size),
        corner = corner,
        rings = false,
        animate = false,
    )
}

/** Round artist avatar — initials over the palette. */
@Composable
fun ArtistAvatar(name: String, palette: List<Color>, size: Dp = 52.dp) {
    Box(
        Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            drawRect(Brush.linearGradient(listOf(palette[0], palette[1]), start = Offset.Zero, end = Offset(w, w)))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to palette[2].copy(alpha = 0.7f),
                    1f to palette[2].copy(alpha = 0f),
                    center = Offset(w * 0.7f, w * 0.3f),
                    radius = w * 0.8f,
                ),
                radius = w * 0.8f,
                center = Offset(w * 0.7f, w * 0.3f),
            )
            drawRect(Color.White.copy(alpha = 0.14f), style = Stroke(width = 1.dp.toPx()))
        }
        NeText(
            text = name.trim().take(1).uppercase(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            size = (size.value * 0.38f).sp,
            weight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            color = Color.White,
            tracking = 0.sp,
        )
    }
}
