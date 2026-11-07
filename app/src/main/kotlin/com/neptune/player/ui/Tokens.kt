package com.neptune.player.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for spacing, icon sizes, touch targets and type.
 *
 * Screens used to hardcode drifting values (17.dp, 11.5.sp, 34.dp…), which produced uneven
 * icons and clipped labels. Every surface now picks from these scales instead, so rhythm and
 * optical weight stay identical across Home, Library, Search, Player and Settings.
 */
object NeSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    /** Horizontal gutter shared by every scrolling screen. */
    val screen: Dp = 20.dp
}

/** Icon sizes come from a 4dp scale so glyphs never land on fractional pixels. */
object NeIconSize {
    val xs: Dp = 16.dp
    val sm: Dp = 18.dp
    val md: Dp = 20.dp
    val lg: Dp = 24.dp
    val xl: Dp = 28.dp
    val xxl: Dp = 32.dp
}

/** Material recommends 48dp; nothing tappable goes below 44dp. */
object NeTouch {
    val min: Dp = 44.dp
    val standard: Dp = 48.dp
}

object NeRow {
    /**
     * Horizontal padding *inside* a grouped glass panel. Group captions use the same value so the
     * caption and the row content share one left edge.
     */
    val panelInset: Dp = 16.dp
    val compact: Dp = 60.dp
    val track: Dp = 72.dp
    val setting: Dp = 68.dp
    val header: Dp = 56.dp
    /** Input field height; the row inside is centred by GlassPanel. */
    val field: Dp = 56.dp
    /** Top now-playing pill: a fully rounded capsule, artwork on the left. */
    val pill: Dp = 52.dp
    val pillArt: Dp = 36.dp
    val thumbCompact: Dp = 44.dp
    val thumb: Dp = 52.dp
    val leadingBadge: Dp = 40.dp
    /** Bare leading icon cell: keeps the text start line aligned without a plate behind the glyph. */
    val leadingIcon: Dp = 28.dp
}

/** Type scale. Line height is explicit so Manrope never clips descenders or Cyrillic diacritics. */
object NeType {
    val display: TextUnit = 30.sp
    val displayLine: TextUnit = 36.sp
    val headline: TextUnit = 24.sp
    val headlineLine: TextUnit = 30.sp
    val title: TextUnit = 20.sp
    val titleLine: TextUnit = 26.sp
    val section: TextUnit = 17.sp
    val sectionLine: TextUnit = 22.sp
    val body: TextUnit = 15.sp
    val bodyLine: TextUnit = 21.sp
    val label: TextUnit = 14.sp
    val labelLine: TextUnit = 19.sp
    val caption: TextUnit = 12.sp
    val captionLine: TextUnit = 16.sp
    val micro: TextUnit = 11.sp
    val microLine: TextUnit = 14.sp
}

/**
 * Text must stay readable when the system font scale and the in-app scale multiply.
 * Layouts use flexible heights, and the combined factor is bounded so labels wrap instead of
 * being cut in half.
 */
fun combinedFontScale(system: Float, app: Float): Float = (system * app).coerceIn(0.85f, 1.5f)
