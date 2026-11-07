package com.neptune.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test fun settingsBackReturnsToPreviousPageThenHome() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.waitUntil(5000) { compose.activityRule.scenario.state.isResumed }
        compose.onNodeWithText("Внешний вид").assertIsDisplayed().performClick()
        compose.onNodeWithText("ЦВЕТ ОСНОВЫ").assertIsDisplayed()
        back()
        compose.onNodeWithText("Всё под тебя. Без лишнего.").assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
    }

    @Test fun vectorAvatarChoicePersistsAndBackWorks() {
        compose.onNodeWithContentDescription("Профиль и аватар").performClick()
        compose.onNodeWithText("Твой профиль").assertIsDisplayed()
        compose.onNodeWithText("Цветок").performClick()
        compose.waitUntil(5000) {
            var value = false
            compose.activityRule.scenario.onActivity { value = container(it).settings.state.value.avatarIndex == 1 }
            value
        }
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
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
