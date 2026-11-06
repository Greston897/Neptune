package com.neptune.player.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.neptune.player.R

/**
 * Neptune — obsidian + liquid glass.
 * Single typeface (Manrope, OFL) with a very tight optical scale.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

object Ne {
    val Void = Color(0xFF06060A)
    val Ink = Color(0xFF0B0B11)
    val Text = Color(0xFFF7F8FB)
    val Dim = Color(0xA8F7F8FB)
    val Faint = Color(0x70F7F8FB)
    val Whisper = Color(0x3DF7F8FB)
    val Line = Color(0x1FFFFFFF)
    val Black = Color(0xFF000000)
}

/** Accent tones available in «Внешний вид». */
enum class Accent(val title: String, val color: Color) {
    ICE("Лёд", Color(0xFF7FE6FF)),
    AURORA("Аврора", Color(0xFF9E8CFF)),
    NEON("Неон", Color(0xFF5AFFB8)),
    SUNSET("Закат", Color(0xFFFF8A5C)),
    GOLD("Золото", Color(0xFFFFD479)),
    ROSE("Роза", Color(0xFFFF8FC7)),
    SKY("Небо", Color(0xFF6EA8FF));

    companion object {
        fun of(index: Int): Accent = entries[index.coerceIn(0, entries.lastIndex)]
    }
}

enum class Tab(val title: String) {
    Now("Сейчас"),
    Library("Медиатека"),
    Search("Поиск"),
    Tune("Вид"),
}

@Immutable
data class Settings(
    val accentIndex: Int = 0,
    val blur: Float = 24f,
    val tint: Float = 0.14f,
    val radius: Float = 26f,
    val bg: Float = 1f,
    val speed: Float = 1f,
    val grain: Boolean = true,
    val animations: Boolean = true,
    val glow: Boolean = true,
    val floatingNav: Boolean = true,
    val haptics: Boolean = true,
    val miniPlayer: Boolean = true,
    val showTimes: Boolean = true,
    val rings: Boolean = true,
    val labels: Boolean = true,
) {
    val accent: Accent get() = Accent.of(accentIndex)
}

/* ---------------------------------------------------------------------------------------------- */
/* Type                                                                                           */
/* ---------------------------------------------------------------------------------------------- */

@androidx.compose.runtime.Composable
fun NeText(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    size: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
    weight: FontWeight = FontWeight.SemiBold,
    color: Color = Ne.Text,
    tracking: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(-0.2f, androidx.compose.ui.unit.TextUnitType.Sp),
    align: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
    maxLines: Int = 1,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = Manrope,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = tracking,
        textAlign = align,
        maxLines = maxLines,
        lineHeight = lineHeight,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
