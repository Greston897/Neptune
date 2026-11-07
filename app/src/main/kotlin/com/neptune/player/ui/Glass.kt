package com.neptune.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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

/**
 * Black liquid-glass surface.
 *
 * The visual recipe was studied from Telegram Android's blur3 pipeline (rounded SDF panels,
 * blurred backdrop, bright top rim / dark bottom rim, scroll-aware invalidation). This is an
 * original Compose implementation: backdrop blur is provided by Apache-2.0 Haze, the rim and
 * specular highlight are drawn here. On Android < 12 Haze falls back to a solid dark scrim.
 */
val LocalBackgroundHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalContentHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun glassStyle(settings: Settings): HazeStyle = HazeStyle(
    backgroundColor = Color(0xFF050507),
    tints = listOf(HazeTint(Color(0xFF0B0B10).copy(alpha = settings.glassTint))),
    blurRadius = settings.glassBlur.dp,
    noiseFactor = 0.035f,
    fallbackTint = HazeTint(Color(0xFF101015).copy(alpha = 0.94f)),
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
    val surface = if (s.glassEnabled && haze != null) base.hazeChild(haze, glassStyle(s))
        else base.background(Color(0xFF101015).copy(alpha = if (s.glassEnabled) 0.92f else 1f))
    Box(modifier.then(surface)) {
        content()
        if (rim && s.glassEnabled) Canvas(Modifier.matchParentSize()) {
            val r = corner.toPx().coerceAtMost(size.minDimension / 2f)
            val inset = 0.75.dp.toPx()
            val top = s.glassRim * 0.22f
            val bottom = s.glassRim * 0.07f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))),
                topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(r - inset), style = Stroke(1.25.dp.toPx()),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = s.glassRim * 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.18f))),
                topLeft = Offset.Zero, size = size, cornerRadius = CornerRadius(r),
            )
        }
    }
}

@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val tint = if (s.accentGlow) accent() else Color(0xFF8A93A6)
    Box(modifier.background(Color(s.surface.hex))) {
        if (s.ambientGlow) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.9f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF3A4152).copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.9f), radius = size.width * 0.8f))
        }
    }
}
