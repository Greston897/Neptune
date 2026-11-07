package com.neptune.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neptune.player.R
import com.neptune.player.model.AvatarShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import com.neptune.player.model.Track
import kotlin.math.cos
import kotlin.math.sin

/** Original vector artwork. Local embedded covers are loaded by Coil; these are their fallbacks. */
@Composable
fun TrackArtwork(track: Track, modifier: Modifier = Modifier, corner: Dp = 12.dp, animate: Boolean = false) {
    val settings = LocalSettings.current
    val tint = accent()
    val moving = animate && settings.animations && settings.artworkMotion
    val phase = if (moving) {
        val t = rememberInfiniteTransition(label = "cover")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(48000, easing = LinearEasing), RepeatMode.Restart), label = "orbit").value
    } else 0f
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))) {
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 400f, size.height / 400f, Offset.Zero) { drawCover(track.artSeed, tint, phase) }
        }
        if (track.artworkUri != null) AsyncImage(model = track.artworkUri, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private fun DrawScope.drawCover(seed: Int, tint: Color, phase: Float) {
    val paper = Color(0xFFE4E2DE)
    val ink = Color(0xFF171717)
    when (seed.mod(6)) {
        0 -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF343434), Color(0xFF0C0C0C))))
            rotate(-28f + phase * 0.06f) {
                for (i in 0..10) drawOval(tint.copy(alpha = 0.22f + i * 0.045f),
                    topLeft = Offset(35f + i * 9f, 95f + i * 5f), size = Size(330f - i * 18f, 200f - i * 10f), style = Stroke(2f))
            }
            drawCircle(paper, 38f, Offset(200f, 200f))
            drawCircle(ink, 29f, Offset(211f, 192f))
        }
        1 -> {
            drawRect(paper)
            for (i in 0..17) {
                val p = Path().apply { moveTo(-30f, 50f + i * 19f)
                    cubicTo(110f, -40f + i * 20f, 220f, 420f - i * 8f, 440f, 110f + i * 17f) }
                drawPath(p, ink, style = Stroke(7f))
            }
            drawCircle(tint, 25f, Offset(305f, 92f))
        }
        2 -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFCBCDCB), Color(0xFF717975))))
            drawCircle(paper, 78f, Offset(205f, 145f))
            for (i in 0..6) {
                val p = Path().apply { moveTo(0f, 240f + i * 25f)
                    cubicTo(100f, 150f + i * 28f, 210f, 340f - i * 12f, 400f, 210f + i * 27f)
                    lineTo(400f, 400f); lineTo(0f, 400f); close() }
                drawPath(p, Color(0xFF26302C).copy(alpha = 0.22f + i * 0.1f))
            }
        }
        3 -> {
            drawRect(ink)
            rotate(-20f) {
                for (i in 0..8) drawRoundRect(paper.copy(alpha = 0.16f + i * 0.09f),
                    topLeft = Offset(-80f + i * 35f, 65f), size = Size(180f, 310f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(90f), style = Stroke(3f))
            }
            drawCircle(tint, 13f, Offset(310f, 310f))
        }
        4 -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF252A30), Color(0xFF080A0C))))
            for (i in 0..80) {
                val a = i * 2.399963f + phase * 0.002f
                val r = 12f + i * 1.8f
                drawCircle(paper.copy(alpha = 0.3f + (i % 5) * 0.13f), 1.5f + i % 3,
                    Offset(200f + cos(a) * r, 200f + sin(a) * r))
            }
            drawCircle(tint, 52f, Offset(200f, 200f), style = Stroke(2f))
            drawCircle(ink, 43f, Offset(200f, 200f))
            drawCircle(paper, 8f, Offset(249f, 181f))
        }
        else -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFFD8D3CA), Color(0xFF9C968C))))
            for (i in 0..9) {
                val x = 30f + i * 38f
                val p = Path().apply { moveTo(x, 400f); lineTo(x, 230f)
                    cubicTo(x, 110f, x + 70f, 110f, x + 70f, 230f); lineTo(x + 70f, 400f) }
                drawPath(p, ink.copy(alpha = 0.15f + i * 0.07f), style = Stroke(12f, cap = StrokeCap.Round))
            }
            drawCircle(paper, 34f, Offset(295f, 92f))
        }
    }
}

@Composable
fun TrackThumb(track: Track, size: Dp = 48.dp, corner: Dp = 8.dp) = TrackArtwork(track, Modifier.size(size), corner)

@Composable
fun NeptuneMark(modifier: Modifier = Modifier, tone: Color = Ne.Text, animated: Boolean = false) {
    val settings = LocalSettings.current
    val moving = animated && settings.animations && settings.artworkMotion
    val scale by if (moving) rememberInfiniteTransition(label = "neptune").animateFloat(0.97f, 1.03f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
        else androidx.compose.runtime.mutableFloatStateOf(1f)
    Image(painterResource(R.drawable.neptune_white), contentDescription = null,
        modifier = modifier.scale(scale), colorFilter = if (tone == Ne.Text) null else ColorFilter.tint(tone))
}

@Composable
fun ProfileAvatar(modifier: Modifier = Modifier, animated: Boolean = false) {
    val s = LocalSettings.current
    val tone = Color(com.neptune.player.model.Accent.of(s.avatarTone).hex)
    val shape = when (s.avatarShape) {
        AvatarShape.CIRCLE -> CircleShape
        AvatarShape.SQUIRCLE -> androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
        AvatarShape.SQUARE -> androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    }
    Box(modifier.clip(shape).background(Color(0xFF000000)).border(1.dp, Color.White.copy(alpha = 0.08f * s.glassRim), shape), contentAlignment = Alignment.Center) {
        NeptuneMark(Modifier.fillMaxSize(0.62f * s.avatarSize), tone, animated)
    }
}

@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Ne.Text, maxHeight: Dp = 16.dp) {
    val animated = playing && LocalSettings.current.animations
    val phase = if (animated) rememberInfiniteTransition(label = "equalizer").animateFloat(0f, 6.28f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "bars").value else 0f
    Canvas(modifier.size(width = 18.dp, height = maxHeight)) {
        for (i in 0..2) {
            val h = if (animated) size.height * (0.35f + 0.6f * ((sin((phase + i * 1.7).toDouble()).toFloat() + 1f) / 2f)) else size.height * (0.3f + i * 0.15f)
            drawRoundRect(color, Offset(i * size.width / 3f, size.height - h), Size(size.width / 5f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
        }
    }
}
