package com.neptune.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconSize
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
