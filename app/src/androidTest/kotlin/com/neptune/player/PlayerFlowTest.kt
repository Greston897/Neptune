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
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFlowTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun container(activity: MainActivity) = (activity.application as NeptuneApplication).container
    private fun waitText(text: String, timeout: Long = 20000) {
        try {
            compose.waitUntil(timeout) {
                runCatching { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val visible = runCatching {
                compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString { it.config.toString() }
            }.getOrDefault("unavailable")
            throw AssertionError("Timed out waiting for '$text'. Roots: $visible", e)
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun waitGone(text: String, timeout: Long = 20000) {
        compose.waitUntil(timeout) {
            runCatching { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
        }
    }
    private fun clickText(text: String) {
        waitText(text)
        compose.onNodeWithText(text).performClick()
    }

    @Test fun settingsBackReturnsToPreviousPageThenHome() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Внешний вид")).fetchSemanticsNodes().isNotEmpty() }
        clickText("Внешний вид")
        compose.onNodeWithText("ЦВЕТ ОСНОВЫ").assertIsDisplayed()
        back()
        compose.onNodeWithText("Всё под тебя. Без лишнего.").assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
    }

    @Test fun avatarShapeChoicePersistsAndBackWorks() {
        compose.onNodeWithContentDescription("Профиль и аватар").performClick()
        compose.onNodeWithText("Твой профиль").assertIsDisplayed()
        clickText("Круг")
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
        clickText("Воспроизведение")
        waitText("Настоящий звук. Полный контроль.")
        // Other rows must not open the sleep dialog.
        clickText("Повтор очереди")
        compose.onAllNodes(hasText("Музыка мягко остановится по таймеру.")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        clickText("Таймер сна")
        waitText("Музыка мягко остановится по таймеру.")
        clickText("15 минут")
        waitGone("Музыка мягко остановится по таймеру.")
        compose.waitUntil(5000) {
            var active = false
            compose.activityRule.scenario.onActivity { active = container(it).playback.state.value.sleepRemainingMs > 0 }
            active
        }
        clickText("Таймер сна")
        waitText("Закрыть")
        clickText("Закрыть")
        waitGone("Музыка мягко остановится по таймеру.")
        clickText("Таймер сна")
        clickText("Выключить таймер")
        compose.waitUntil(5000) {
            var off = false
            compose.activityRule.scenario.onActivity { off = container(it).playback.state.value.sleepRemainingMs == 0L }
            off
        }
        back()
        waitText("Всё под тебя. Без лишнего.")
    }

    @Test fun bundledAudioActuallyPlaysAndBackReturnsHome() {
        clickText("Слушать")
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
