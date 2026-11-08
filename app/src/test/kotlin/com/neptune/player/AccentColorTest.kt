package com.neptune.player

import com.neptune.player.model.AccentColor
import com.neptune.player.model.Settings
import org.junit.Assert.*
import org.junit.Test

/**
 * The personal accent is user input, so it has to survive round-trips, bad typing and unreadable
 * picks. These run on the JVM: the colour maths deliberately avoids `android.graphics.Color`.
 */
class AccentColorTest {
    @Test fun hsvRoundTripPreservesEveryPreset() {
        com.neptune.player.model.Accent.entries.forEach { preset ->
            val argb = AccentColor.hexToArgb(preset.hex)
            val hsv = AccentColor.rgbToHsv(argb)
            val back = AccentColor.hsvToArgb(hsv[0], hsv[1], hsv[2])
            assertEquals("${preset.name} did not survive HSV round-trip", argb, back)
        }
    }

    @Test fun primaryColoursMapToTheirCanonicalHues() {
        assertEquals(0f, AccentColor.rgbToHsv(0xFFFF0000.toInt())[0], 0.5f)
        assertEquals(120f, AccentColor.rgbToHsv(0xFF00FF00.toInt())[0], 0.5f)
        assertEquals(240f, AccentColor.rgbToHsv(0xFF0000FF.toInt())[0], 0.5f)
        // Grey has no hue; the contract is that it stays grey rather than becoming a random colour.
        val grey = AccentColor.rgbToHsv(0xFF808080.toInt())
        assertEquals(0f, grey[1], 0.001f)
    }

    @Test fun hexParsingAcceptsShortLongAndBareForms() {
        val expected = 0xFF6FA8FF.toInt()
        assertEquals(expected, AccentColor.parseHex("#6FA8FF"))
        assertEquals(expected, AccentColor.parseHex("6fa8ff"))
        assertEquals(expected, AccentColor.parseHex("  #6FA8FF  "))
        // #abc expands to #aabbcc, which is bright enough to pass through untouched.
        assertEquals(0xFFAABBCC.toInt(), AccentColor.parseHex("#abc"))
    }

    @Test fun hexParsingRejectsJunkInsteadOfGuessing() {
        listOf("", "#", "#12", "#12345", "#1234567", "gggggg", "#-12345", "12 45 67").forEach {
            assertNull("must reject '$it'", AccentColor.parseHex(it))
        }
    }

    @Test fun darkPicksAreLiftedWithoutChangingHue() {
        val dark = 0xFF102040.toInt()
        val lifted = AccentColor.ensureVisible(dark)
        val before = AccentColor.rgbToHsv(dark)
        val after = AccentColor.rgbToHsv(lifted)
        assertTrue("a dark accent must be brightened", after[2] > before[2])
        assertEquals("hue must be preserved", before[0], after[0], 1.0f)
        assertEquals("saturation must be preserved", before[1], after[1], 0.02f)
        assertTrue("result must reach the readability floor", after[2] >= AccentColor.MinValue - 0.01f)
    }

    @Test fun brightPicksAreLeftExactlyAsChosen() {
        val bright = 0xFF6FA8FF.toInt()
        assertEquals(bright, AccentColor.ensureVisible(bright))
    }

    @Test fun ensureVisibleIsIdempotentAndAlwaysOpaque() {
        listOf(0xFF000000.toInt(), 0xFF010101.toInt(), 0xFF123456.toInt(), 0xFFABCDEF.toInt()).forEach {
            val once = AccentColor.ensureVisible(it)
            assertEquals("must be stable under a second pass", once, AccentColor.ensureVisible(once))
            assertEquals("alpha must stay opaque", 0xFF, (once ushr 24) and 0xFF)
        }
    }

    @Test fun hexFormattingIsSixDigitsAndUppercase() {
        assertEquals("#6FA8FF", AccentColor.formatHex(0xFF6FA8FF.toInt()))
        assertEquals("#000000", AccentColor.formatHex(0xFF000000.toInt()))
    }

    @Test fun settingsResolveCustomAccentOnlyWhenEnabled() {
        val preset = Settings(accentIndex = 2, customAccent = false, accentArgb = 0xFFFF0000.toInt())
        assertEquals("disabled custom colour must not leak", com.neptune.player.model.Accent.of(2).hex, preset.accentHexValue)
        val custom = preset.copy(customAccent = true)
        assertEquals(0xFFFF0000L, custom.accentHexValue)
    }

    @Test fun validationClampsAnUnreadableStoredColour() {
        // A value written by an older build or restored from a backup must still be legible.
        val validated = Settings(customAccent = true, accentArgb = 0xFF000000.toInt()).validated()
        val hsv = AccentColor.rgbToHsv(validated.accentArgb)
        assertTrue("validated accent must be readable", hsv[2] >= AccentColor.MinValue - 0.01f)
    }

    @Test fun resetAppearanceTurnsTheCustomColourOff() {
        val reset = Settings(customAccent = true, accentArgb = 0xFFFF0000.toInt(), artShapeIndex = 2, thumbScale = 1.2f).resetAppearance()
        assertFalse(reset.customAccent)
        assertEquals("artwork shape must return to the round default", 0, reset.artShapeIndex)
        assertEquals(1f, reset.thumbScale, 0.0001f)
    }
}
