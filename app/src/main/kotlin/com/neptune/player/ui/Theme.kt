package com.neptune.player.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.R
import com.neptune.player.model.Settings

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_semibold, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold), Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)
object Ne {
    val Void = Color(0xFF050505)
    val Ink = Color(0xFF121212)
    val Elevated = Color(0xFF1C1C1C)
    val Text = Color(0xFFF5F5F5)
    val Dim = Color(0xFFA5A5A5)
    val Faint = Color(0xFF777777)
    val Whisper = Color(0xFF555555)
    val Line = Color(0xFF282828)
    val Black = Color(0xFF080808)
    val Error = Color(0xFFE6A3A3)
}
val LocalSettings = staticCompositionLocalOf { Settings() }
val LocalHaptics = staticCompositionLocalOf<() -> Unit> { { } }
/**
 * Material 3 "emphasized" curve: leaves quickly, arrives slowly.
 *
 * Every transition in the app shares it, so motion has one character instead of each call site
 * picking its own. The long deceleration tail is what makes a 480ms transition feel calm rather
 * than slow — the eye follows the object into place instead of watching it snap.
 */
val NeEase: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Composable fun accent() = LocalSettings.current.accent.let { Color(it.hex) }
@Composable fun neRadius() = LocalSettings.current.radius.dp

@Composable
fun NeptuneTheme(settings: Settings, content: @Composable () -> Unit) {
    val accent = Color(settings.accent.hex)
    val background = Color(settings.surface.hex)
    val scheme = darkColorScheme(primary = accent, onPrimary = Ne.Black, background = background,
        surface = background, surfaceVariant = Ne.Ink, onBackground = Ne.Text, onSurface = Ne.Text,
        onSurfaceVariant = Ne.Dim, outline = Ne.Line, outlineVariant = Ne.Line, error = Ne.Error,
        surfaceContainer = Ne.Ink, surfaceContainerHigh = Ne.Elevated, surfaceContainerHighest = Ne.Line,
        secondary = accent, tertiary = accent)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalSettings provides settings,
        LocalDensity provides Density(density.density, combinedFontScale(density.fontScale, settings.fontScale))) {
        MaterialTheme(colorScheme = scheme, typography = NeptuneTypography, content = content)
    }
}
private val NeptuneTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)
/**
 * The single text primitive.
 *
 * Three things used to make labels sit "то ниже, то выше":
 *  1. `includeFontPadding` added a phantom, font-dependent gap above and below the glyphs.
 *  2. `LineHeightStyle.Trim.Both` trimmed the box back to ascent+descent, which *silently ignored*
 *     the explicit `lineHeight`. Every label therefore had a slightly different box height and
 *     `Alignment.CenterVertically` centred a different box each time.
 *  3. `Alignment.Proportional` distributed the leading asymmetrically, shifting the glyphs inside
 *     their own box.
 *
 * With `Trim.None` + `Alignment.Center` the box is exactly `lineHeight` tall with the leading split
 * evenly, so every label of a given token has an identical, predictable box and centres cleanly
 * against icons and neighbouring text.
 */
@Composable
fun NeText(
    text: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    size: TextUnit = NeType.label, weight: FontWeight = FontWeight.SemiBold, color: Color = Ne.Text,
    tracking: TextUnit = (-0.15).sp, align: TextAlign = TextAlign.Start, maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val resolvedLine = if (lineHeight == TextUnit.Unspecified) size * 1.3f else lineHeight
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontFamily = Manrope, fontWeight = weight, fontSize = size,
            letterSpacing = tracking, textAlign = align, lineHeight = resolvedLine,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
