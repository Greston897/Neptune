package com.neptune.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.ui.DockMetrics
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconSize
import com.neptune.player.ui.NeMotion
import com.neptune.player.ui.NeRow
import com.neptune.player.ui.NeSpace
import com.neptune.player.ui.NeTouch
import com.neptune.player.ui.NeType
import com.neptune.player.ui.combinedFontScale
import org.junit.Assert.*
import org.junit.Test

/**
 * Guards the design system numerically. These are the invariants that broke the previous UI:
 * drifting icon sizes, sub-44dp touch targets, fractional type sizes and a font scale that could
 * multiply past the point where fixed-height rows clipped their labels.
 */
class DesignSystemTest {
    @Test fun spacingScaleIsMonotonicAndOnGrid() {
        val scale = listOf(NeSpace.xxs, NeSpace.xs, NeSpace.sm, NeSpace.md, NeSpace.lg, NeSpace.xl, NeSpace.xxl, NeSpace.xxxl)
        scale.zipWithNext().forEach { (a, b) -> assertTrue("$a must be below $b", a < b) }
        scale.forEach { assertEquals("spacing must sit on a 2dp grid: $it", 0f, it.value % 2f, 0.0001f) }
        assertTrue(NeSpace.screen >= NeSpace.lg)
    }

    @Test fun iconSizesAreWholeDpAndOrdered() {
        val scale = listOf(NeIconSize.xs, NeIconSize.sm, NeIconSize.md, NeIconSize.lg, NeIconSize.xl, NeIconSize.xxl)
        scale.zipWithNext().forEach { (a, b) -> assertTrue(a < b) }
        scale.forEach { assertEquals("icon size must be whole dp: $it", it.value, kotlin.math.round(it.value), 0.0001f) }
        // A glyph must never fill its whole hit area, otherwise buttons look cramped.
        assertTrue(NeIconSize.lg < NeTouch.min * 0.65f)
    }

    @Test fun touchTargetsMeetAccessibilityFloor() {
        assertTrue(NeTouch.min >= 44.dp)
        assertTrue(NeTouch.standard >= 48.dp)
        assertTrue("row must fit a touch target", NeRow.compact >= NeTouch.min)
        assertTrue(NeRow.setting >= NeTouch.standard)
        assertTrue(NeRow.header >= NeTouch.standard)
    }

    @Test fun typeScaleUsesWholeSpWithRoomForDescenders() {
        val pairs = listOf(
            NeType.display to NeType.displayLine, NeType.headline to NeType.headlineLine,
            NeType.title to NeType.titleLine, NeType.section to NeType.sectionLine,
            NeType.body to NeType.bodyLine, NeType.label to NeType.labelLine,
            NeType.caption to NeType.captionLine, NeType.micro to NeType.microLine,
        )
        pairs.forEach { (size, line) ->
            assertEquals("font size must be whole sp: $size", size.value, kotlin.math.round(size.value), 0.0001f)
            assertTrue("line height must exceed size: $size / $line", line > size)
            val ratio = line.value / size.value
            assertTrue("line ratio out of range for $size: $ratio", ratio in 1.2f..1.45f)
        }
        assertTrue("smallest label must stay legible", NeType.micro >= 11.sp)
    }

    @Test fun combinedFontScaleIsBoundedBothWays() {
        assertEquals(1f, combinedFontScale(1f, 1f), 0.0001f)
        assertEquals(1.5f, combinedFontScale(2f, 1.2f), 0.0001f)
        assertEquals(0.85f, combinedFontScale(0.5f, 0.9f), 0.0001f)
        assertEquals(1.3f, combinedFontScale(1.3f, 1f), 0.0001f)
        // Worst case must still fit: a two-line settings title inside a flexible row.
        val worst = combinedFontScale(2f, 1.2f)
        assertTrue(NeType.body.value * worst * 2 < NeRow.setting.value)
    }

    /**
     * The top pill and the bottom dock must reserve clearance exactly when they are visible —
     * otherwise a header hides under the pill, or a list ends beneath the navbar.
     */
    @Test fun trackPillClearanceMatchesItsVisibility() {
        assertTrue("no track means no pill", DockMetrics.topHeight("home", false) == 0.dp)
        assertTrue("immersive player hides the pill", DockMetrics.topHeight("player", true) == 0.dp)
        assertTrue("settings subpage hides the pill", DockMetrics.topHeight("appearance", true) == 0.dp)
        listOf("home", "search", "library", "settings", "collection/liked/-").forEach { route ->
            val inset = DockMetrics.topHeight(route, true)
            assertTrue("$route must clear the pill", inset > NeRow.pill)
        }
        // Identity up top and transport down bottom are independent surfaces.
        assertTrue(DockMetrics.showsTrackPill("home", true))
        assertTrue(DockMetrics.showsMiniPlayer("home", true))
    }

    /**
     * Motion is a scale, not a pile of magic numbers. The sweep must be visibly faster than the
     * 9000ms it shipped with, yet slow enough to read as a reflection instead of a flicker; screen
     * transitions must be slower than colour changes so hierarchy stays legible.
     */
    @Test fun motionScaleIsOrderedAndTheSheenIsBriskNotFlickery() {
        assertTrue("colour < layout < screen", NeMotion.Quick < NeMotion.Standard)
        assertTrue("layout < screen", NeMotion.Standard < NeMotion.Emphasized)
        assertTrue("screen transitions must stay under half a second", NeMotion.Emphasized <= 500)
        assertTrue("colour changes must not feel sluggish", NeMotion.Quick in 180..300)
        assertTrue("sheen must be faster than the original 9000ms", NeMotion.Sheen < 9000)
        assertTrue("sheen must not strobe", NeMotion.Sheen in 2500..4500)
    }

    @Test fun pillTokensAreOnGridAndLeaveRoomForArtwork() {
        assertEquals("pill height must be whole dp", NeRow.pill.value, kotlin.math.round(NeRow.pill.value), 0.0001f)
        assertTrue("pill must be a touch target", NeRow.pill >= NeTouch.min)
        assertTrue("artwork must fit inside the capsule", NeRow.pillArt < NeRow.pill)
        assertTrue("artwork must stay tappable-sized", NeRow.pillArt >= 32.dp)
        assertTrue(DockMetrics.PillMaxWidth > NeRow.pill * 3)
        // 2.7: the capsule was too wide; it must stay a compact centre object, not a full-width bar.
        assertTrue("pill must stay compact", DockMetrics.PillMaxWidth <= 260.dp)
        assertTrue("transport bar must still hold a 48dp button plus progress",
            DockMetrics.MiniHeight >= NeTouch.standard + 8.dp)
    }

    @Test fun everyIconHasAResourceAndTabsHaveBoldTwins() {
        val all = NeIcon.entries
        assertTrue("icon set shrank unexpectedly", all.size >= 49)
        all.forEach { assertNotEquals("${it.name} has no drawable", 0, it.res) }
        assertEquals("icon resources must be unique", all.size, all.map { it.res }.toSet().size)
        listOf(NeIcon.Home, NeIcon.Search, NeIcon.Library, NeIcon.Settings).forEach {
            assertNotEquals("${it.name} needs a heavier selected twin", it.res, it.boldRes)
        }
        // Non-tab icons intentionally reuse the regular weight.
        assertEquals(NeIcon.Timer.res, NeIcon.Timer.boldRes)
    }
}
