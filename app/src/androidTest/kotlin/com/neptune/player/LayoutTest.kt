package com.neptune.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
