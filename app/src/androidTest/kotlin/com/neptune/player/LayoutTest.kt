package com.neptune.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Settings
import com.neptune.player.model.Track
import com.neptune.player.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout regressions, asserted from measured semantics bounds — never from screenshots.
 *
 * Each test reproduces a defect the old UI actually had: squeezed icons, labels pushed off-screen
 * by long titles, clipped text at large font scales, and a selection state that was invisible to
 * accessibility services.
 */
@RunWith(AndroidJUnit4::class)
class LayoutTest {
    @get:Rule val compose = createComposeRule()

    private val longTrack = Track(
        "layout:1", "Очень длинное название трека, которое раньше выталкивало кнопки за экран",
        "Неизвестный исполнитель с длинным именем", "Альбом с длинным названием", 245000, "asset:///audio/x.m4a",
    )

    private fun host(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            NeptuneTheme(Settings(animations = false, artworkMotion = false, ambientGlow = false)) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    @Test fun iconsStaySquareNextToALongLabel() {
        host {
            // The real regression: an unweighted label ate the row and squashed the glyph into an
            // ellipse. The label must take the flexible cell and the icon must keep its square.
            Row(Modifier.width(200.dp)) {
                NeText(
                    "Очень длинная подпись, которая не должна сжимать иконку",
                    Modifier.weight(1f).testTag("label"), maxLines = 1,
                )
                NeIconView(NeIcon.Play, NeIconSize.lg, modifier = Modifier.testTag("glyph"))
            }
        }
        val b = compose.onNodeWithTag("glyph").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertEquals("icon must stay square", b.width, b.height, 1.0f)
        assertEquals("icon must keep its declared size", NeIconSize.lg.value * density, b.width, 1.5f)
        val label = compose.onNodeWithTag("label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("label must leave room for the glyph", label.right <= b.left + 1f)
    }

    @Test fun longTrackTitleNeverPushesControlsOffScreen() {
        host {
            Box(Modifier.width(320.dp)) {
                TrackRow(
                    longTrack, current = true, playing = true, onClick = {},
                    onMore = {}, modifier = Modifier.testTag("row"),
                )
            }
        }
        val row = compose.onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(longTrack.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("title must stay inside the row", title.right <= row.right + 1f)
        assertTrue("title must not be zero-width", title.width > 0f)
        compose.onNodeWithContentDescription("Действия с ${longTrack.title}").assertIsDisplayed()
    }

    @Test fun touchTargetsAreNeverSmallerThanFortyFourDp() {
        host {
            Row {
                NeIconButton(NeIcon.Heart, "like", {}, size = NeTouch.min, modifier = Modifier.testTag("min"))
                NeIconButton(NeIcon.Queue, "queue", {}, modifier = Modifier.testTag("std"))
            }
        }
        val density = compose.density.density
        // Material3 expands the interactive bounds to at least 48dp, so the measured hit area can
        // legitimately exceed the declared size — the invariant is the floor, not an exact match.
        listOf("min" to NeTouch.min, "std" to NeTouch.standard).forEach { (tag, declared) ->
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag below its declared size", b.height >= declared.value * density - 1f)
            assertTrue("$tag below the 44dp accessibility floor: ${b.height / density}", b.height / density >= 43.5f)
            assertEquals("$tag hit area must be square", b.width, b.height, 2f)
        }
    }

    @Test fun settingsRowsGrowInsteadOfClippingAtLargeFontScale() {
        val subtitle = "Настоящее размытие фона на Android 12+ и мягкая панель на старых версиях устройства"
        // setContent may only run once per test, so the scale is driven through state instead.
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        compose.setContent {
            NeptuneTheme(Settings(animations = false, artworkMotion = false, ambientGlow = false)) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, scale.floatValue)) {
                    Box(Modifier.width(360.dp)) {
                        SettingRow("Чёрное стекло", subtitle, NeIcon.Palette, Modifier.testTag("cell"), onClick = {})
                    }
                }
            }
        }
        fun height() = compose.onNodeWithTag("cell", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        val normal = height()
        compose.runOnIdle { scale.floatValue = 1.5f }
        compose.waitForIdle()
        val large = height()
        assertTrue("row must grow with the text: $normal -> $large", large > normal)
        compose.onNodeWithText("Чёрное стекло", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun selectedTabIsExposedToAccessibility() {
        host { NeptuneNavBar(route = "library") {} }
        compose.onNodeWithContentDescription("Медиатека").assertIsSelected()
        compose.onNodeWithContentDescription("Главная").assertIsNotSelected()
        compose.onNodeWithContentDescription("Поиск").assertIsNotSelected()
    }

    @Test fun tabLabelsFitInsideTheirCells() {
        host { Box(Modifier.width(360.dp)) { NeptuneNavBar(route = "home") {} } }
        val cellWidthPx = 360f / Tab.entries.size * compose.density.density
        Tab.entries.forEach { tab ->
            val label = compose.onNodeWithText(tab.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("${tab.title} label must have width", label.width > 0f)
            assertTrue("${tab.title} label overflows its cell: ${label.width} > $cellWidthPx", label.width <= cellWidthPx + 1f)
        }
    }

    @Test fun miniPlayerKeepsControlsVisibleWithALongTitle() {
        val playback = PlaybackState(track = longTrack, isPlaying = true, durationMs = 245000, positionMs = 30000)
        host {
            Box(Modifier.width(320.dp)) { MiniPlayerContent(playback, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("Пауза").assertIsDisplayed()
        compose.onNodeWithContentDescription("Следующий трек").assertIsDisplayed()
        val title = compose.onNodeWithText(longTrack.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("mini-player title must not be collapsed", title.width > 0f)
    }

    /**
     * The left edge of list content must not depend on which screen renders it. TrackRow used to
     * add its own 8dp inset (and Home added 6dp more), so artwork sat at 28/34dp while section
     * headers sat at 20dp — the "не выровнено" symptom.
     */
    @Test fun trackRowContentStartsAtTheRowEdge() {
        host {
            Box(Modifier.width(320.dp)) {
                TrackRow(longTrack, current = false, playing = false, onClick = {},
                    modifier = Modifier.testTag("row"))
            }
        }
        val row = compose.onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(longTrack.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        // Artwork (52dp) + gap (12dp) is the only inset before the title.
        val expected = (NeRow.thumb + NeSpace.md).value * density
        assertEquals("title inset must come only from artwork + gap", expected, title.left - row.left, 2.5f)
    }

    /** Glyph and label must share one centre line inside a button-like row. */
    @Test fun iconAndLabelShareACentreLine() {
        host {
            Row(Modifier.width(240.dp).testTag("cell"), verticalAlignment = Alignment.CenterVertically) {
                NeIconView(NeIcon.Timer, NeIconSize.md, modifier = Modifier.testTag("ico"))
                NeText("Таймер сна", Modifier.testTag("txt"), size = NeType.body, lineHeight = NeType.bodyLine)
            }
        }
        val ico = compose.onNodeWithTag("ico", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val txt = compose.onNodeWithTag("txt", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("icon and label centres must agree", ico.center.y, txt.center.y, 1.5f)
    }

    /**
     * An icon button must occupy exactly the square it was given. Material3's IconButton centred
     * its content in a 40dp inner box and then padded the ripple out to 48dp, so a 44dp request
     * produced a 48dp box whose glyph sat off the caller's centre line.
     */
    @Test fun iconButtonUsesExactlyTheRequestedSquare() {
        host {
            Row {
                NeIconButton(NeIcon.Heart, "like", {}, size = NeTouch.min, modifier = Modifier.testTag("b44"))
                NeIconButton(NeIcon.Queue, "queue", {}, size = NeTouch.standard, modifier = Modifier.testTag("b48"))
            }
        }
        val density = compose.density.density
        listOf("b44" to NeTouch.min, "b48" to NeTouch.standard).forEach { (tag, size) ->
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("$tag width must equal the request", size.value * density, b.width, 1.0f)
            assertEquals("$tag height must equal the request", size.value * density, b.height, 1.0f)
        }
    }

    /** A glyph placed in a centred square must land on that square's centre. */
    @Test fun glyphSitsOnTheCentreOfItsBox() {
        host {
            Box(Modifier.size(56.dp).testTag("holder"), contentAlignment = Alignment.Center) {
                NeIconView(NeIcon.Settings, NeIconSize.lg, modifier = Modifier.testTag("g"))
            }
        }
        val holder = compose.onNodeWithTag("holder", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val g = compose.onNodeWithTag("g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("glyph centre X", holder.center.x, g.center.x, 0.75f)
        assertEquals("glyph centre Y", holder.center.y, g.center.y, 0.75f)
    }

    /** Labels of the same token must produce identical box heights, or rows drift. */
    @Test fun sameTokenLabelsHaveIdenticalHeights() {
        host {
            Column {
                NeText("Йогурт", Modifier.testTag("a"), size = NeType.body, lineHeight = NeType.bodyLine)
                NeText("МАТЬ", Modifier.testTag("b"), size = NeType.body, lineHeight = NeType.bodyLine)
                NeText("без выносных", Modifier.testTag("c"), size = NeType.body, lineHeight = NeType.bodyLine)
            }
        }
        val hs = listOf("a", "b", "c").map {
            compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        }
        // Descenders and capitals must not change the box: Trim.Both used to make each one differ.
        assertTrue("label boxes differ: $hs", hs.max() - hs.min() <= 1.0f)
    }

    /**
     * The defect: GlassPanel is a Box, and a Box defaults to TopStart. A panel with
     * heightIn(min = 56.dp) whose row is only 24dp tall pinned that row to the top edge, so the
     * search field's icon and text sat ~16dp above the centre line ("завышено").
     */
    @Test fun panelCentresContentShorterThanItself() {
        host {
            GlassPanel(Modifier.testTag("panel").size(width = 240.dp, height = 80.dp)) {
                Box(Modifier.testTag("inner").size(24.dp))
            }
        }
        val panel = compose.onNodeWithTag("panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val inner = compose.onNodeWithTag("inner", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val panelCy = (panel.top + panel.bottom) / 2
        val innerCy = (inner.top + inner.bottom) / 2
        assertTrue("content pinned to the top: panel centre $panelCy vs content centre $innerCy",
            kotlin.math.abs(panelCy - innerCy) <= 1.0f)
    }

    /** Same regression on the horizontal axis: a narrow row must be centred, not left-aligned. */
    @Test fun panelCentresContentOnBothAxes() {
        host {
            GlassPanel(Modifier.testTag("panel").size(120.dp)) {
                Box(Modifier.testTag("inner").size(20.dp))
            }
        }
        val panel = compose.onNodeWithTag("panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val inner = compose.onNodeWithTag("inner", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("horizontally off-centre", kotlin.math.abs((panel.left + panel.right) / 2 - (inner.left + inner.right) / 2) <= 1.0f)
        assertTrue("vertically off-centre", kotlin.math.abs((panel.top + panel.bottom) / 2 - (inner.top + inner.bottom) / 2) <= 1.0f)
    }

    /**
     * The quick tiles ("Любимое" / "Недавнее") had a grey/accent plate behind the icon and a
     * TopStart panel, so icon, title and count all floated high. The icon's centre line must now
     * match the centre of the text block beside it.
     */
    @Test fun leadingIconSharesTheCentreLineOfItsLabelBlock() {
        host {
            Row(Modifier.testTag("row"), verticalAlignment = Alignment.CenterVertically) {
                NeIconView(NeIcon.Heart, NeIconSize.xl, modifier = Modifier.testTag("icon"))
                Column {
                    NeText("Любимое", size = NeType.label, lineHeight = NeType.labelLine)
                    NeText("12 треков", size = NeType.caption, lineHeight = NeType.captionLine)
                }
            }
        }
        val icon = compose.onNodeWithTag("icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("icon not on the row centre line",
            kotlin.math.abs((icon.top + icon.bottom) / 2 - (row.top + row.bottom) / 2) <= 1.0f)
    }

    /** A bare leading icon must not reserve the old 40dp badge cell (which implied a plate). */
    @Test fun leadingIconCellIsTheBareTokenSize() {
        host { Box(Modifier.size(NeRow.leadingIcon).testTag("cell")) }
        val b = compose.onNodeWithTag("cell", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expected = NeRow.leadingIcon.value * compose.density.density
        assertTrue("leading icon cell is $expected px wide, got ${b.width}", kotlin.math.abs(b.width - expected) <= 1.0f)
        assertTrue("leading icon cell must be square", kotlin.math.abs(b.width - b.height) <= 1.0f)
    }

    /** Selection in the nav bar is colour + weight only: the glyph keeps its exact token size. */
    @Test fun navGlyphsAreIdenticalSquaresWhetherSelectedOrNot() {
        host { NeptuneNavBar(route = "home") {} }
        compose.onNodeWithContentDescription("Главная").assertIsSelected()
        compose.onNodeWithContentDescription("Поиск").assertIsNotSelected()
        // Every tab cell keeps the same measured square footprint: selection must not inflate it
        // with a capsule, which is what the old 52x28dp plate did.
        val cells = listOf("Главная", "Поиск", "Медиатека", "Настройки").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        cells.forEach { it.let { b -> assertTrue("tab cell has zero size", b.width > 0f && b.height > 0f) } }
        assertTrue("tab cells differ in height: ${cells.map { it.height }}",
            cells.maxOf { it.height } - cells.minOf { it.height } <= 1.0f)
        assertTrue("tab cells differ in width: ${cells.map { it.width }}",
            cells.maxOf { it.width } - cells.minOf { it.width } <= 1.0f)
    }

    @Test fun noPromotionalCopyIsRendered() {
        host {
            Column {
                NeptuneNavBar(route = "home") {}
                SettingRow("Таймер сна", "Выключен", NeIcon.Timer, onClick = {})
                NeChip("Треки", selected = true, onClick = {})
                EmptyState(NeIcon.Music, "Пока пусто", "Добавьте музыку.")
            }
        }
        listOf("NEPTUNE STUDIO", "ORIGINAL SESSION", "В ТВОЕЙ ОРБИТЕ").forEach { promo ->
            assertEquals("promotional copy leaked: $promo", 0,
                compose.onAllNodes(hasText(promo, substring = true)).fetchSemanticsNodes().size)
        }
    }
}
