package com.neptune.player.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neptune.player.model.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.graphics.BlendMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Neutral black liquid-glass surface (no blue/graphite cast: every channel is equal).
 *
 * The visual recipe was studied from Telegram Android's blur3 pipeline (rounded panels, blurred
 * backdrop, bright top rim / dark bottom rim, scroll-aware invalidation). This is an original
 * Compose implementation: backdrop blur is provided by Apache-2.0 Haze, the rim and specular
 * highlight are drawn here. Blur is explicitly enabled on RenderEffect-capable devices instead of
 * relying on the library default, which excludes API 31 and can silently fall back to a flat scrim.
 */
val LocalBackgroundHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalContentHaze = staticCompositionLocalOf<HazeState?> { null }

/** Bottom clearance reserved for the floating dock, so lists never end underneath the navbar. */
val LocalBottomInset = staticCompositionLocalOf { 24.dp }

/**
 * Blur recipe.
 *
 * Two layers matter: a neutral dark tint that keeps the glass black, and a very soft `Overlay`
 * pass that lifts the blurred highlights so the surface reads as glass rather than a flat scrim.
 * `noiseFactor` adds the fine frost that makes a strong blur legible on a dark, low-detail
 * backdrop — without it a heavy blur of a near-black gradient looks like nothing happened.
 */
@Composable
fun glassStyle(settings: Settings): HazeStyle = HazeStyle(
    backgroundColor = Color(0xFF060606),
    tints = listOf(
        HazeTint(Color(0xFF0D0D0D).copy(alpha = settings.glassTint)),
        HazeTint(Color.White.copy(alpha = 0.05f), BlendMode.Overlay),
    ),
    blurRadius = settings.glassBlur.dp,
    noiseFactor = 0.10f,
    fallbackTint = HazeTint(Color(0xFF121212).copy(alpha = 0.96f)),
)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = neRadius(),
    haze: HazeState? = LocalBackgroundHaze.current,
    rim: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalSettings.current
    val shape = RoundedCornerShape(corner)
    val base = Modifier.clip(shape)
    val style = glassStyle(s)
    // Uniform RenderEffect blur only. Haze's progressive/shader path looks marginally nicer but
    // collapses to a crawl on software renderers (and some low-end GPUs), so it is not worth it.
    val surface = if (s.glassEnabled && haze != null) base.hazeChild(haze, style) {
        // RenderEffect blur needs API 31+; older devices get the opaque fallback instead of a crash.
        blurEnabled = Build.VERSION.SDK_INT >= 31
    } else base.background(Color(0xFF121212).copy(alpha = if (s.glassEnabled) 0.94f else 1f))
    Box(modifier.then(surface)) {
        content()
        if (rim && s.glassEnabled) Canvas(Modifier.matchParentSize()) {
            val r = corner.toPx().coerceAtMost(size.minDimension / 2f)
            val inset = 0.6.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f * s.glassRim), Color.White.copy(alpha = 0.07f * s.glassRim))),
                topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)), style = Stroke(1.1.dp.toPx()),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f * s.glassRim), Color.Transparent, Color.Black.copy(alpha = 0.16f))),
                topLeft = Offset.Zero, size = size, cornerRadius = CornerRadius(r),
            )
        }
    }
}

/** Shared drift phase so every sampled copy of the ambient backdrop stays pixel-aligned. */
@Composable
fun rememberAmbientPhase(): Float {
    val s = LocalSettings.current
    return if (s.animations && s.ambientGlow) {
        rememberInfiniteTransition(label = "ambient").animateFloat(
            0f, 6.2831853f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "drift",
        ).value
    } else 0.8f
}

/**
 * Near-black backdrop with enough structure that the blur is actually visible: two drifting glows
 * and a soft vignette. Panels sample this through Haze, so the glass reads as glass even on screens
 * with no scrolling content behind them. The dock samples the full content layer instead.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, phase: Float = rememberAmbientPhase()) {
    val s = LocalSettings.current
    val tint = if (s.accentGlow) accent() else Color(0xFF8FB4FF)
    Box(modifier.background(Color(s.surface.hex))) {
        if (s.ambientGlow) Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val c1 = Offset(w * (0.82f + 0.10f * cos(phase)), h * (0.10f + 0.05f * sin(phase)))
            val c2 = Offset(w * (0.12f + 0.08f * sin(phase * 0.7f)), h * (0.86f + 0.06f * cos(phase * 0.9f)))
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.06f), Color.Transparent),
                center = c1, radius = w * 0.95f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9A7BFF).copy(alpha = 0.15f), Color.Transparent),
                center = c2, radius = w * 0.85f))
            // Fine orbital detail gives the blur something to resolve: smooth gradients alone look
            // identical before/after a Gaussian blur, which is why the glass previously read as flat.
            val ring = Stroke(width = 1.4.dp.toPx())
            drawCircle(Color.White.copy(alpha = 0.07f), radius = w * 0.62f, center = c1, style = ring)
            drawCircle(Color.White.copy(alpha = 0.055f), radius = w * 0.44f, center = c1, style = ring)
            drawCircle(Color.White.copy(alpha = 0.06f), radius = w * 0.52f, center = c2, style = ring)
            drawCircle(tint.copy(alpha = 0.10f), radius = w * 0.30f, center = c2, style = ring)
            // Dense starfield: a blur has to smudge *something*. Sparse dots on a flat gradient
            // survive a Gaussian almost unchanged, which is why the glass used to read as flat.
            for (i in 0 until 72) {
                val x = ((i * 73) % 997) / 997f * w
                val y = ((i * 131) % 991) / 991f * h
                val r = when (i % 5) { 0 -> 2.2.dp.toPx(); 1 -> 1.5.dp.toPx(); else -> 1.dp.toPx() }
                drawCircle(Color.White.copy(alpha = 0.06f + (i % 4) * 0.035f), radius = r, center = Offset(x, y))
            }
            // A few soft light streaks give the blur directional smear, like real frosted glass.
            for (i in 0 until 3) {
                val y = h * (0.22f + i * 0.26f)
                drawLine(
                    Color.White.copy(alpha = 0.035f),
                    start = Offset(w * 0.05f, y), end = Offset(w * 0.95f, y + h * 0.03f),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.03f), Color.Transparent, Color.Black.copy(alpha = 0.25f))))
        }
    }
}
