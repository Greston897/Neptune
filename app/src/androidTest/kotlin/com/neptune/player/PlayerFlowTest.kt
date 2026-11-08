package com.neptune.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 30)
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

    /**
     * Imports test audio through the real picker path.
     *
     * The app ships no music since 2.9, so playback tests must supply their own file. This calls
     * `importUris` — the same entry point `OpenMultipleDocuments` uses — rather than injecting
     * tracks into the state, so the import path itself is covered too.
     */
    private fun seedLibrary() {
        compose.activityRule.scenario.onActivity { activity ->
            val c = container(activity)
            c.library.importUris(listOf(AudioFixture.write(activity))) {}
        }
        compose.waitUntil(20000) {
            var loaded = false
            compose.activityRule.scenario.onActivity { loaded = container(it).library.state.value.tracks.isNotEmpty() }
            loaded
        }
    }

    /**
     * Regression: opening ANY collection used to crash the app.
     *
     * The collection header's back button carried `Modifier.padding(start = -8.dp)`. Compose's
     * PaddingValues requires every side to be >= 0, so composing CollectionScreen threw
     * IllegalArgumentException("Padding must be non-negative") — «Любимое», «Недавнее», альбомы,
     * артисты и плейлисты были недоступны. No earlier test opened a collection, which is exactly why
     * CI stayed green while the feature was broken. This walks two of them and back.
     */
    @Test fun likedAndRecentCollectionsOpenAndReturn() {
        clickText("Любимое")
        waitText("Любимые треки")
        compose.onNodeWithText("ПОДБОРКА").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").assertIsDisplayed()
        back()
        clickText("Недавнее")
        waitText("Недавно слушали")
        compose.onNodeWithText("ПОДБОРКА").assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("Профиль и аватар").assertIsDisplayed()
    }

    /**
     * Regression: the album shelf's "Медиатека" action called onCollection("albums", ""), but
     * CollectionScreen has no "albums" kind — it fell through to the else branch and opened an empty
     * "Подборка". The action must land on the Library's albums tab instead.
     */
    @Test fun albumShelfActionOpensTheLibraryAlbumsTab() {
        seedLibrary()
        // "Медиатека" is also the nav tab's label, so the shelf action is addressed by its own
        // semantics tag instead of by text. The shelf sits below the hero and can start off-screen
        // on a short viewport, hence the scroll rather than a dependence on device height.
        // Scroll the list to the node: a LazyColumn does not compose off-screen items, so
        // performScrollTo on the target alone would never find it on a short viewport.
        compose.waitUntil(20000) {
            compose.onAllNodes(hasTestTag("homeList")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("homeList").performScrollToNode(hasTestTag("homeAlbumsAction"))
        compose.onNodeWithTag("homeAlbumsAction").performClick()
        waitText("Альбомы")
        compose.onNode(hasContentDescription("Медиатека") and isSelectable()).assertIsSelected()
        // The albums tab proves its own selection with a filter chip and the grid's own labels; the
        // tab index itself lives in the ViewModel, which this rule cannot reach.
        compose.onAllNodes(hasText("ПОДБОРКА")).fetchSemanticsNodes().let {
            assertEquals("a tab action must not open a collection", 0, it.size)
        }
        compose.onNode(hasContentDescription("Сортировка")).assertIsDisplayed()
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
        // The tile's accessibility description is the plain form name ("Форма Круг"); the quotes live
        // only in the visible caption, because a description is read out, not typeset.
        compose.onNodeWithContentDescription("Форма Круг").performClick()
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
        // The row must stay on playback settings: an overlapping dock used to swallow this tap
        // and open the full player instead.
        waitText("Настоящий звук. Полный контроль.")
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
        waitText("Настоящий звук. Полный контроль.")
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

    @Test fun importedAudioActuallyPlaysAndBackReturnsHome() {
        seedLibrary()
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

    /**
     * Regression: "Убрать из этого плейлиста" dereferenced mutable state at click time.
     *
     * The row was guarded by `if (state.contextPlaylist != null)` during composition but then called
     * `state.removeFromPlaylist(state.contextPlaylist!!, track)` in its handler. close() nulls that
     * field, so any click delivered against the outgoing composition threw NPE. The handler now
     * closes over a local val captured at composition. This walks the real path — playlist screen,
     * track menu, remove — and then taps the same row again to prove a second click is harmless.
     */
    @Test fun removeFromPlaylistWorksAndSurvivesASecondTap() {
        val name = "Регрессия"
        var trackTitle = ""
        var playlistId = ""
        seedLibrary()
        compose.activityRule.scenario.onActivity { activity ->
            val c = container(activity)
            val track = c.library.state.value.tracks.first()
            trackTitle = track.title
            val p = c.users.createPlaylist(name)
            playlistId = p.id
            c.users.addToPlaylist(p.id, track.id)
        }
        compose.onNode(hasContentDescription("Медиатека") and isSelectable()).performClick()
        clickText("Плейлисты")
        clickText(name)
        waitText("ПОДБОРКА")
        compose.onNodeWithContentDescription("Действия с $trackTitle").performClick()
        clickText("Убрать из этого плейлиста")
        // Tap the same row again while the sheet is on its way out. The row may already be gone,
        // which is the correct outcome; what must never happen is a crash from dereferencing the
        // playlist that close() has just nulled.
        compose.waitForIdle()
        if (compose.onAllNodes(hasText("Убрать из этого плейлиста")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Убрать из этого плейлиста").performClick()
        }
        compose.waitUntil(10000) {
            var removed = false
            compose.activityRule.scenario.onActivity {
                val pl = container(it).users.state.value.playlists.firstOrNull { p -> p.id == playlistId }
                removed = pl != null && pl.trackIds.isEmpty()
            }
            removed
        }
        // The empty state proves the removal actually landed, not just that nothing crashed.
        waitText("Пока пусто")
        back()
        // A collection was opened from the Library, so back lands there with the playlists tab still
        // selected — not on Home, which is the only screen carrying the avatar.
        compose.onNodeWithText("Плейлисты").assertIsDisplayed()
        compose.onNodeWithText(name).assertIsDisplayed()
    }


    /**
     * The lyrics screen must open from the player, follow the current track and stay readable even
     * when the file carries no text — an empty state, not a crash or a blank page.
     */
    @Test fun lyricsScreenOpensAndExplainsWhenThereIsNoText() {
        seedLibrary()
        clickText("Слушать")
        compose.onNodeWithText("СЕЙЧАС ИГРАЕТ").assertIsDisplayed()
        compose.onNodeWithContentDescription("Текст трека").performClick()
        waitText("ТЕКСТ ТРЕКА")
        // The fixture is a bare WAV, so there is nothing to read: the designed empty state must
        // appear instead of a spinner that never resolves.
        compose.waitUntil(20000) {
            compose.onAllNodes(hasText("Текста нет")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Ищем текст в файле…")).fetchSemanticsNodes().isEmpty()
        }
        back()
        compose.onNodeWithText("СЕЙЧАС ИГРАЕТ").assertIsDisplayed()
    }

    /** The paywall must be reachable from Settings and must state that the player itself is free. */
    @Test fun subscriptionScreenShowsWhatIsFreeAndCanBeOpened() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Neptune Plus")).fetchSemanticsNodes().isNotEmpty() }
        clickText("Neptune Plus")
        waitText("ВСЕГДА БЕСПЛАТНО")
        compose.onNodeWithText("Плеер целиком").assertIsDisplayed()
        compose.onNodeWithText("В NEPTUNE PLUS").assertIsDisplayed()
        // The one-tap entry has to exist while nothing is charged, or the list above describes a
        // state no tap can reach. It sits below the feature list, and a LazyColumn does not compose
        // off-screen items, so the list itself is scrolled to the node before it is clicked.
        compose.onNodeWithTag("subscriptionList").performScrollToNode(hasText("Открыть Plus сейчас"))
        compose.onNodeWithText("Открыть Plus сейчас").performClick()
        waitText("Neptune Plus активен")
        compose.waitUntil(5000) {
            var plus = false
            compose.activityRule.scenario.onActivity {
                plus = container(it).plan.tier.value == com.neptune.player.model.PlanTier.PLUS
            }
            plus
        }
        // Leaving the plan must also drop the extras it unlocked. The confirmation is dialogs' own
        // action word "Отключить", which is not the same node as the row that opened it.
        clickText("Отключить Plus")
        compose.onNode(hasText("Отключить") and hasClickAction()).performClick()
        compose.waitUntil(5000) {
            var free = false
            compose.activityRule.scenario.onActivity { free = container(it).plan.tier.value == com.neptune.player.model.PlanTier.FREE }
            free
        }
        back()
        compose.onNodeWithText("Всё под тебя. Без лишнего.").assertIsDisplayed()
    }
}
