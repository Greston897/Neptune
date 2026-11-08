package com.neptune.player.model

/**
 * Colour maths for the personal accent, written without `android.graphics.Color` so it stays
 * unit-testable on the JVM.
 *
 * A user-chosen accent is used for text, glyphs and progress on a pure black backdrop, so a very
 * dark pick would be invisible. [ensureVisible] lifts only the HSV value channel, keeping hue and
 * saturation exactly as chosen — the colour still reads as "their" colour, it just stays legible.
 */
object AccentColor {
    /** Below this HSV value an accent stops being readable on the fixed black backdrop. */
    const val MinValue = 0.55f
    const val Opaque = 0xFF000000L

    fun argbToHex(argb: Int): Long = argb.toLong() and 0xFFFFFFFFL

    fun hexToArgb(hex: Long): Int = (Opaque or (hex and 0xFFFFFFL)).toInt()

    fun rgbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> (60f * ((g - b) / delta) + 360f) % 360f
            max == g -> 60f * ((b - r) / delta) + 120f
            else -> 60f * ((r - g) / delta) + 240f
        }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(hue, saturation, max)
    }

    fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (h / 60f) {
            in 0f..1f -> Triple(c, x, 0f)
            in 1f..2f -> Triple(x, c, 0f)
            in 2f..3f -> Triple(0f, c, x)
            in 3f..4f -> Triple(0f, x, c)
            in 4f..5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(f: Float) = (((f + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** Keeps the chosen hue/saturation, raising brightness only when it would be unreadable. */
    fun ensureVisible(argb: Int): Int {
        val hsv = rgbToHsv(argb)
        return if (hsv[2] >= MinValue) argb or (0xFF shl 24)
        else hsvToArgb(hsv[0], hsv[1], MinValue)
    }

    fun formatHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Accepts `#RGB`, `RGB`, `#RRGGBB` and `RRGGBB`; returns null for anything else. */
    fun parseHex(raw: String): Int? {
        val text = raw.trim().removePrefix("#")
        val full = when (text.length) {
            3 -> text.map { "$it$it" }.joinToString("")
            6 -> text
            else -> return null
        }
        if (full.any { it !in "0123456789abcdefABCDEF" }) return null
        val value = full.toLongOrNull(16) ?: return null
        return ensureVisible((Opaque or value).toInt())
    }
}
