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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.neptune.player.R
import com.neptune.player.model.AvatarShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import com.neptune.player.model.Track
import kotlin.math.sin

/**
 * Cover art with a guaranteed fallback.
 *
 * The brand mark is the base layer and the real cover is composited on top of it. Coil draws nothing
 * while a request is in flight and nothing when it fails, so a track with no embedded art — or with
 * an unreadable `content://` URI — shows the app avatar rather than an empty rectangle. Loading and
 * error need no separate handling: both leave the base layer visible.
 *
 * Once the cover actually decodes, the brand layer is dropped. Leaving it in place would repaint a
 * gradient and a vector under every thumbnail in every list on every frame — invisible overdraw
 * that the glass blur then has to sample again.
 */
@Composable
fun TrackArtwork(track: Track, modifier: Modifier = Modifier, corner: Dp = 12.dp, animate: Boolean = false) {
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))) {
        var covered by remember(track.artworkUri) { mutableStateOf(false) }
        if (!covered) BrandCover(track.artSeed, animate)
        if (track.artworkUri != null) {
            AsyncImage(
                model = track.artworkUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onState = { covered = it is AsyncImagePainter.State.Success },
            )
        }
    }
}

/**
 * The app avatar standing in for missing cover art.
 *
 * A seed-positioned accent glow keeps a grid of these from reading as one repeated tile, while the
 * glyph itself stays exactly the launcher mark, so missing art still looks deliberate.
 */
@Composable
private fun BrandCover(seed: Int, animated: Boolean) {
    val tint = accent()
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * (0.30f + seed.mod(5) * 0.10f)
            val cy = size.height * (0.26f + seed.mod(3) * 0.16f)
            drawRect(
                Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, cy), radius = size.minDimension * 0.78f,
                ),
            )
        }
        NeptuneMark(Modifier.fillMaxSize(0.46f), tint, animated)
    }
}

@Composable
fun TrackThumb(track: Track, size: Dp = 48.dp, corner: Dp = 8.dp) = TrackArtwork(track, Modifier.size(size), corner)

/**
 * In-app brand mark.
 *
 * Uses the very same `orbit` glyph as the adaptive launcher icon, so the avatar, the Home wordmark
 * and the About page all read as one brand. The previous asset was a flat white disc with no
 * internal detail, which looked like a placeholder next to the stroked icon set.
 */
@Composable
fun NeptuneMark(modifier: Modifier = Modifier, tone: Color = Ne.Text, animated: Boolean = false) {
    val settings = LocalSettings.current
    val moving = animated && settings.animations && settings.artworkMotion
    val scale by if (moving) rememberInfiniteTransition(label = "neptune").animateFloat(0.97f, 1.03f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
        else androidx.compose.runtime.mutableFloatStateOf(1f)
    Image(
        painter = painterResource(R.drawable.ne_orbit),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.scale(scale),
        colorFilter = ColorFilter.tint(tone),
    )
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
