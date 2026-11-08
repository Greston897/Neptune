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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.roundToInt
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

/** Top clearance reserved for the floating now-playing pill, so headers never sit underneath it. */
val LocalTopInset = staticCompositionLocalOf { 0.dp }

/**
 * Shared specular sweep phase, as a *getter* rather than a value.
 *
 * Every panel shimmer uses one clock, so the highlights travel across the UI as one wave instead of
 * each cell flickering on its own. It is published as `() -> Float` on purpose: a per-frame `Float`
 * in a composition local would recompose every panel on every frame, while reading the underlying
 * State inside the draw lambda invalidates only the draw phase.
 */
val LocalSheenPhase = staticCompositionLocalOf<() -> Float> { { 0.35f } }

@Composable
fun rememberSheenPhase(): () -> Float {
    val s = LocalSettings.current
    return if (s.animations) {
        val phase = rememberInfiniteTransition(label = "sheen").animateFloat(
            0f, 1f, infiniteRepeatable(tween(NeMotion.Sheen, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
        )
        remember(phase) { { phase.value } }
    } else remember { { 0.35f } }
}

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
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalSettings.current
    val shape = RoundedCornerShape(corner)
    val base = Modifier.clip(shape)
    val sheenPhase = LocalSheenPhase.current
    val sheenTint = accent()
    val style = glassStyle(s)
    // Uniform RenderEffect blur only. Haze's progressive/shader path looks marginally nicer but
    // collapses to a crawl on software renderers (and some low-end GPUs), so it is not worth it.
    val surface = if (s.glassEnabled && haze != null) base.hazeChild(haze, style) {
        // RenderEffect blur needs API 31+; older devices get the opaque fallback instead of a crash.
        blurEnabled = Build.VERSION.SDK_INT >= 31
    } else base.background(Color(0xFF121212))
    // No `haze` means there is deliberately nothing to sample — a full-screen overlay provides
    // `null` on purpose (see NeptuneApp). The panel is then painted solid, because 94% black over a
    // blurred *other screen* is exactly the smear this was reported as.
    // A Box defaults to TopStart. When the panel is taller than its content (a search field with
    // heightIn(min = 52.dp), a quick tile with heightIn(min = 80.dp)) the row was pinned to the top
    // and looked "завышено". Centring by default puts icon + label on the panel's centre line.
    Box(modifier.then(surface), contentAlignment = contentAlignment) {
        // Sheen sits *under* the content so icons and text stay crisp, and is always present: a
        // static pearlescent lift plus a travelling accent-tinted highlight. Drawn with two rounded
        // rects, so it costs nothing like the shader blur that had to be removed in 2.4.
        if (s.glassEnabled) Canvas(Modifier.matchParentSize()) {
            val phase = sheenPhase()
            val r = CornerRadius(corner.toPx().coerceAtMost(size.minDimension / 2f))
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.055f), Color.Transparent, sheenTint.copy(alpha = 0.05f)),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                ),
                cornerRadius = r,
            )
            val band = size.width * 0.55f + size.height * 0.45f
            val head = -size.height + (size.width + size.height * 2f) * phase
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.42f to sheenTint.copy(alpha = 0.10f),
                    0.5f to Color.White.copy(alpha = 0.13f),
                    0.58f to sheenTint.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    start = Offset(head - band / 2f, 0f), end = Offset(head + band / 2f, size.height),
                ),
                cornerRadius = r,
            )
        }
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

/** Discrete phases in one 26s ambient drift. See [rememberAmbientPhase]. */
private const val AmbientSteps = 120
private const val Tau = 6.2831853f

/**
 * Shared drift phase so every sampled copy of the ambient backdrop stays pixel-aligned.
 *
 * The value is deliberately **quantised**. This layer is expensive — gradient aurora arcs plus a
 * full-screen radial vignette — and it is rasterised twice per invalidation, once as the background
 * haze source and once as the content haze source. Returning the raw animated value recomposed and
 * redrew both copies at display rate, which is what made the richer 2.7 backdrop crawl on a software
 * renderer. Over a 26s drift, 120 steps move a glow by well under a pixel, so snapping to them cuts
 * redraws to roughly five per second with no visible stepping.
 *
 * The glass sheen is *not* quantised: it is two rounded rects per panel and must stay smooth.
 */
@Composable
fun rememberAmbientPhase(): Float {
    val s = LocalSettings.current
    return if (s.animations && s.ambientGlow) {
        val raw = rememberInfiniteTransition(label = "ambient").animateFloat(
            0f, Tau, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "drift",
        ).value
        val step = Tau / AmbientSteps
        (raw / step).roundToInt() * step
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
            // Aurora ribbons replace the old straight streaks. Curvature is what sells the depth:
            // a Gaussian blur turns a straight line back into a straight line, but smears an arc
            // into a soft directional glow. drawArc is used instead of a cubic Path so no Path is
            // allocated per frame. Each ribbon is a wide faint pass plus a narrow brighter core.
            for (i in 0 until 2) {
                val drift = phase * (0.6f + i * 0.25f)
                val oy = h * (0.18f + i * 0.42f) + sin(drift) * h * 0.03f
                val hue = if (i == 1) Color.White else tint
                val tl = Offset(-w * 0.25f, oy - h * 0.42f)
                val sz = Size(w * 1.5f, h * 0.84f)
                val start = 196f + i * 8f
                drawArc(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, hue.copy(alpha = 0.05f), Color.Transparent)),
                    startAngle = start, sweepAngle = 148f, useCenter = false, topLeft = tl, size = sz,
                    style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, hue.copy(alpha = 0.10f), Color.Transparent)),
                    startAngle = start, sweepAngle = 148f, useCenter = false, topLeft = tl, size = sz,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Two-stage vignette: a top lift for air, then a radial pull to black at the edges so the
            // fixed OLED base reads as a deep room rather than a flat rectangle.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.025f), Color.Transparent, Color.Black.copy(alpha = 0.42f))))
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = Offset(w * 0.5f, h * 0.42f), radius = maxOf(w, h) * 0.78f,
                ),
            )
        }
    }
}
