package com.neptune.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun container(activity: MainActivity) = (activity.application as NeptuneApplication).container
    private fun waitText(text: String, timeout: Long = 20000) {
        compose.waitUntil(timeout) {
            runCatching { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun waitGone(text: String, timeout: Long = 20000) {
        compose.waitUntil(timeout) {
            runCatching { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
        }
    }

    @Test fun settingsBackReturnsToPreviousPageThenHome() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Внешний вид")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Внешний вид").assertIsDisplayed().performClick()
        compose.onNodeWithText("ЦВЕТ ОСНОВЫ").assertIsDisplayed()
        back()
        compose.onNodeWithText("Всё под тебя. Без лишнего.").assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
    }

    @Test fun avatarShapeChoicePersistsAndBackWorks() {
        compose.onNodeWithContentDescription("Профиль и аватар").performClick()
        compose.onNodeWithText("Твой профиль").assertIsDisplayed()
        compose.onNodeWithText("Круг").performClick()
        compose.waitUntil(5000) {
            var value = false
            compose.activityRule.scenario.onActivity { value = container(it).settings.state.value.avatarIndex == 0 }
            value
        }
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
    }

    @Test fun sleepTimerOpensOnlyFromItsRowAndAlwaysCloses() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        waitText("Воспроизведение")
        compose.onNodeWithText("Воспроизведение").performClick()
        waitText("Настоящий звук. Полный контроль.")
        // Other rows must not open the sleep dialog.
        compose.onNodeWithText("Повтор очереди").performClick()
        compose.onAllNodes(hasText("Музыка мягко остановится по таймеру.")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithText("Таймер сна").performClick()
        waitText("Музыка мягко остановится по таймеру.")
        compose.onNodeWithText("15 минут").performClick()
        waitGone("Музыка мягко остановится по таймеру.")
        compose.waitUntil(5000) {
            var active = false
            compose.activityRule.scenario.onActivity { active = container(it).playback.state.value.sleepRemainingMs > 0 }
            active
        }
        compose.onNodeWithText("Таймер сна").performClick()
        waitText("Закрыть")
        compose.onNodeWithText("Закрыть").performClick()
        waitGone("Музыка мягко остановится по таймеру.")
        compose.onNodeWithText("Таймер сна").performClick()
        compose.onNodeWithText("Выключить таймер").performClick()
        compose.waitUntil(5000) {
            var off = false
            compose.activityRule.scenario.onActivity { off = container(it).playback.state.value.sleepRemainingMs == 0L }
            off
        }
        back()
        waitText("Всё под тебя. Без лишнего.")
    }

    @Test fun bundledAudioActuallyPlaysAndBackReturnsHome() {
        compose.onNodeWithText("Слушать").performClick()
        compose.onNodeWithText("СЕЙЧАС ИГРАЕТ").assertIsDisplayed()
        compose.waitUntil(20000) {
            var playing = false
            compose.activityRule.scenario.onActivity {
                val p = container(it).playback.state.value
                playing = p.isPlaying && p.positionMs > 500 && p.track != null
            }
            playing
        }
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
        compose.onNodeWithContentDescription("Пауза").assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            var paused = false
            compose.activityRule.scenario.onActivity { paused = !container(it).playback.state.value.isPlaying }
            paused
        }
    }
}
