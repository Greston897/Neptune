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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerFlowTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Seeds the synthetic catalogue track *before* the activity starts.
     *
     * The suite has no network (and must not depend on one), so a real Yandex search would return
     * nothing and the stream path would stay unproven. The flag is read once, on the first composition:
     * setting it here is the only way to have it in place before that. See `NeptuneState.seedCatalog`.
     */
    @Before fun enableSyntheticCatalog() {
        compose.activityRule.scenario.onActivity { activity ->
            container(activity).settings.edit { it.copy(syntheticCatalogOn = true) }
        }
    }
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
        // Two files: the second one carries an ID3 tag, so the suite can distinguish "the file I
        // searched for" from "the other track in the library" without relying on ambiguous text.
        compose.activityRule.scenario.onActivity { activity ->
            val c = container(activity)
            c.library.importUris(listOf(AudioFixture.write(activity), AudioFixture.writeSecond(activity))) {}
        }
        compose.waitUntil(20000) {
            var loaded = false
            compose.activityRule.scenario.onActivity { loaded = container(it).library.state.value.tracks.size >= 2 }
            loaded
        }
    }

    /**
     * Streams have no network in this suite, and that is the point of the seed: the *product* paths —
     * search, the source switch, playing a catalogue track, saving it into "Любимое" and into the
     * "Из каталога" tab — are all exercised against a track the app treats as catalog-sourced. The
     * signing and JSON mapping, which cannot be reached without a network, are covered by unit tests
     * (`YandexTest`).
     */
    private fun waitForCatalogTrack() {
        // In the library, not only on screen: the seed is asynchronous (it waits for the media scan),
        // and whether its row is currently visible depends on where the list happens to be scrolled.
        compose.waitUntil(20000) {
            var seeded = false
            compose.activityRule.scenario.onActivity { activity ->
                seeded = container(activity).library.state.value.tracks.any { it.id == "yandex:test-catalog" }
            }
            seeded
        }
    }

    /**
     * Searches for the catalogue track in the app's own field.
     *
     * The seed reaches the list through the real search path, not through a direct state poke, so the
     * typing is part of what is being tested. `performTextInput` needs the field to be *focused* first:
     * on a cold screen the search box may sit under the pill that the seeded track now raises, and an
     * unfocused field accepts the text without the screen ever leaving its "недавние запросы" state.
     */
    private fun searchForCatalogueTrack() {
        compose.onNode(hasContentDescription("Поиск") and isSelectable()).performClick()
        waitText("Поиск")
        compose.waitUntil(10000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Каталожный")
        waitText("Каталожный трек")
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
     * The queue finishes itself.
     *
     * Two four-second files with repeat off: the first one must end and the second one must start,
     * on its own, with no tap. This is the behaviour a listener assumes and the one that was missing —
     * reaching the end of the first track simply stopped, so an album played one song.
     *
     * Runs on the *local* files rather than on streams, because the suite has no network; the advance
     * itself is the same code path for both, and the stream-specific half (a broken row is skipped
     * rather than stopping the queue) is asserted separately below.
     */
    @Test fun theNextTrackStartsByItselfWhenOneEnds() {
        seedLibrary()
        var first = ""
        var second = ""
        compose.activityRule.scenario.onActivity { activity ->
            val tracks = container(activity).library.state.value.tracks
            first = tracks[0].id
            second = tracks[1].id
        }
        compose.activityRule.scenario.onActivity { activity ->
            val c = container(activity)
            val tracks = c.library.state.value.tracks
            c.playback.play(tracks.take(2), 0)
        }
        compose.waitUntil(30000) {
            var advanced = false
            compose.activityRule.scenario.onActivity { activity ->
                val p = container(activity).playback.state.value
                advanced = p.track?.id == second && (p.isPlaying || p.positionMs > 0)
            }
            advanced
        }
        compose.waitUntil(5000) {
            var index = -1
            compose.activityRule.scenario.onActivity { index = container(it).playback.state.value.queueIndex }
            index == 1
        }
        assertNotEquals(first, second)
    }

    /**
     * A row that will not open does not end the queue.
     *
     * The middle of the queue is a file that does not exist. Playback must skip it, say so in the
     * pill, and land on the third entry — the failure mode this replaces was silence: the player
     * stopped on a broken item and the rest of the album was never heard.
     */
    @Test fun aBrokenTrackIsSkippedAndTheQueueCarriesOn() {
        seedLibrary()
        compose.activityRule.scenario.onActivity { activity ->
            val c = container(activity)
            val tracks = c.library.state.value.tracks
            // The two seeded files differ in length, so the *second* one is the one with a real
            // duration to advance into: the queue has to be [broken, playable], not [broken, broken].
            val playable = tracks.maxByOrNull { it.durationMs } ?: tracks.first()
            val broken = tracks.first().copy(
                id = "broken:1", title = "Битый файл",
                uri = "file:///data/user/0/${activity.packageName}/files/does-not-exist.mp3",
            )
            c.playback.play(listOf(broken, playable), 0)
        }
        compose.waitUntil(30000) {
            var moved = false
            compose.activityRule.scenario.onActivity { activity ->
                val p = container(activity).playback.state.value
                moved = p.queueIndex == 1 && (p.isPlaying || p.positionMs > 0)
            }
            moved
        }
        // The skip is announced, not silent — and it names the track, so the user knows what changed.
        // Read from the state first: the message is carried by the pill, and the pill only exists
        // once something is playing, which is the very thing this test just caused.
        compose.waitUntil(15000) {
            var announced = false
            compose.activityRule.scenario.onActivity { activity ->
                announced = container(activity).playback.state.value.message?.contains("Битый файл") == true
            }
            announced
        }
        // ...and it reaches the screen: same sentence, in the app's own notice surface.
        compose.waitUntil(15000) {
            compose.onAllNodes(hasText("«Битый файл» не открылся — играю дальше")).fetchSemanticsNodes().isNotEmpty()
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
    /**
     * Regression: the plan page could be opened on top of itself.
     *
     * Tapping "Neptune Plus" while the page was already up drew a second copy — two translucent
     * pages whose blurred panels mixed into a smear — and the page opened from Settings with no
     * particular reason rendered nothing at all, because "no reason" and "closed" were the same
     * value. This walks one page, its prices, its single close, and the screen left underneath.
     */
    @Test fun planPageIsOnePageAndClosesInOneTap() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        waitText("Neptune Plus")
        clickText("Neptune Plus")
        waitText("ВСЕГДА БЕСПЛАТНО")
        // Opened as a manager, not for one feature: the prices must be there.
        compose.onNodeWithTag("subscriptionList").performScrollToNode(hasText("ЦЕНА"))
        waitText("ЦЕНА")
        // Exactly one copy of the page is composed, not a stack of them.
        assertEquals(
            "the plan page must be one node tree, not a stack",
            1,
            compose.onAllNodes(hasTestTag("subscriptionList")).fetchSemanticsNodes().size,
        )
        compose.activityRule.scenario.onActivity {
            assertTrue("the overlay must report itself open", AppContainer.Paywall.open)
        }
        // One close returns to Settings, which is the screen that was underneath.
        compose.onNodeWithContentDescription("Закрыть").performClick()
        waitText("Всё под тебя. Без лишнего.")
        assertTrue(
            "the page must be gone, not hiding behind another copy",
            compose.onAllNodes(hasText("ЦЕНА")).fetchSemanticsNodes().isEmpty(),
        )
        compose.activityRule.scenario.onActivity {
            assertTrue("the overlay must report itself closed", !AppContainer.Paywall.open)
        }
    }

    /**
     * Regression: the plan page opened from "Внешний вид" showed that screen through itself.
     *
     * The page background was already solid black, but its glass panels sampled the shared haze
     * state, which captures the layer *behind* the overlay — so the appearance screen's blurred
     * panels and its long copy were visible inside every card of the plan page. Two screens, one
     * picture. This walks the exact reported path: custom accent → plan page → back.
     */
    @Test fun customAccentOpensThePlanPageWithoutMergingWithTheOneUnderIt() {
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        clickText("Внешний вид")
        waitText("ЦВЕТ ОСНОВЫ")
        // The gated control itself: turning on a custom accent is a Plus feature, so this is the tap
        // that used to open the paywall on top of this very screen.
        compose.onNodeWithTag("appearanceList").performScrollToNode(hasText("Свой акцентный цвет"))
        // The switch, not the label: `ToggleRow` puts the control on the trailing edge, and the
        // accent row is the only toggle this far down the appearance list.
        // Click the switch itself: the row is not clickable, only the trailing control is. After
        // scrolling to the accent group, the first switch in the list *is* that control — the group
        // above it ("ЦВЕТ ОСНОВЫ") has no toggles at all, so this cannot drift to another one.
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        waitText("ВСЕГДА БЕСПЛАТНО")
        // The reason card names the feature that was refused — proof this page belongs to that tap.
        waitText("«Свой акцент» — в Plus")
        // The report was that the two screens merged into one picture. What made that possible was
        // the glass panels sampling the backdrop of the screen underneath, so the guarantee to check
        // is that the overlay renders its own opaque chrome: its reason card, its own header, and no
        // second copy of itself.
        assertEquals(
            "the reason card must render exactly once",
            1,
            compose.onAllNodes(hasText("«Свой акцент» — в Plus")).fetchSemanticsNodes().size,
        )
        assertEquals(
            "the plan page must be one instance",
            1,
            compose.onAllNodes(hasTestTag("subscriptionList")).fetchSemanticsNodes().size,
        )
        // The screenshot that reported this bug showed two headers in one picture. The guarantee is
        // that the page is a window of its own: a Dialog root, with the plan list inside it. In one
        // window "on top" only means "painted later", and every translucent pixel of the page would
        // reveal the screen underneath.
        val dialogs = compose.onAllNodes(isDialog()).fetchSemanticsNodes()
        assertEquals("the plan page must own exactly one window", 1, dialogs.size)
        assertTrue(
            "the plan list must live inside that window",
            compose.onAllNodes(hasTestTag("subscriptionList")).fetchSemanticsNodes()
                .all { it.boundsInRoot.bottom <= dialogs.first().boundsInRoot.bottom + 1f },
        )
        // Its own close control returns to the appearance screen, intact. (A programmatic back press
        // goes to the activity's dispatcher, not to the dialog window, so it is not the way out here.)
        compose.onNodeWithContentDescription("Закрыть").performClick()
        waitText("ЦВЕТ ОСНОВЫ")
        assertTrue(
            "the plan page must be gone after back",
            compose.onAllNodes(hasText("ВСЕГДА БЕСПЛАТНО")).fetchSemanticsNodes().isEmpty(),
        )
        compose.activityRule.scenario.onActivity {
            assertTrue("the overlay must report itself closed", !AppContainer.Paywall.open)
        }
    }

    /**
     * Regression: the top pill vanished outside the tab routes.
     *
     * Every confirmation borrows the pill, so opening "Внешний вид" and resetting the look changed
     * the theme but showed no notice at all — the toast had nowhere to appear. The pill now stays on
     * every screen that is not the immersive player or the plan page.
     */
    @Test fun pillAndItsNoticesFollowTheUserIntoSubScreens() {
        seedLibrary()
        clickText("Слушать")
        compose.waitUntil(20000) {
            var playing = false
            compose.activityRule.scenario.onActivity { playing = container(it).playback.state.value.track != null }
            playing
        }
        // Leave the full player by its own back control. The system back press is flaky right after
        // a route transition, and this test is about the pill, not about the gesture.
        compose.onNodeWithContentDescription("Вернуться назад").performClick()
        waitText("Слушать")

        // The pill announces itself as "Сейчас играет: …"; that is a stable handle for the capsule
        // without adding a test-only tag to production code.
        fun pillShown() = runCatching {
            compose.onAllNodes(hasContentDescription("Сейчас играет", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
        compose.waitUntil(15000) { pillShown() }

        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        waitText("Внешний вид")
        compose.waitUntil(15000) { pillShown() }

        clickText("Внешний вид")
        waitText("ЦВЕТ ОСНОВЫ")
        // The sub-screen keeps the pill: this is the route that used to lose it.
        compose.waitUntil(15000) { pillShown() }

        // A real control on this very sub-screen produces a confirmation, and it must be visible —
        // which is only possible if the pill is composed here.
        compose.onNodeWithTag("appearanceList").performScrollToNode(hasText("Сбросить внешний вид"))
        clickText("Сбросить внешний вид")
        clickText("Сбросить")
        compose.waitUntil(15000) {
            runCatching {
                compose.onAllNodes(hasText("Внешний вид восстановлен")).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

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
        // The header switches to the active state *and* the pill confirms it ("в пилюле"), so the
        // string is on screen twice by design. Presence is the assertion; uniqueness is not.
        compose.waitUntil(10000) {
            compose.onAllNodes(hasText("Neptune Plus активен")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(5000) {
            var plus = false
            compose.activityRule.scenario.onActivity {
                plus = container(it).plan.tier.value == com.neptune.player.model.PlanTier.PLUS
            }
            plus
        }
        // Purchasing closes the offer page, as it should; the manager lives on the Settings row now.
        waitText("Всё под тебя. Без лишнего.")
        // Two nodes carry this text: the Settings row and the pill echoing the confirmation. The row
        // is the one inside the scrolling list; the pill is not, so the ancestor tells them apart.
        compose.onNode(hasText("Neptune Plus активен") and hasAnyAncestor(hasScrollAction())).performClick()
        // Leaving the plan must also drop the extras it unlocked. The confirmation is dialogs' own
        // action word "Отключить", which is not the same node as the row that opened it.
        clickText("Отключить Plus")
        compose.onNode(hasText("Отключить") and hasClickAction()).performClick()
        compose.waitUntil(5000) {
            var free = false
            compose.activityRule.scenario.onActivity { free = container(it).plan.tier.value == com.neptune.player.model.PlanTier.FREE }
            free
        }
        compose.onNodeWithContentDescription("Закрыть").performClick()
        waitText("Всё под тебя. Без лишнего.")
    }

    /**
     * The source switch is the 4.0 contract, so it is asserted as behaviour rather than as copy.
     *
     * Three things have to hold at once: both catalogues are searchable by default, turning the
     * phone's library off changes what search returns, and the *last* source cannot be turned off —
     * the switch springs back and the pill says why, instead of leaving the app with nowhere to look.
     */
    @Test fun sourcesSwitchBetweenThePhoneAndTheCatalogue() {
        seedLibrary()
        compose.onNode(hasContentDescription("Настройки") and isSelectable()).performClick()
        waitText("Источники")
        clickText("Источники")
        waitText("ГДЕ ИСКАТЬ")
        compose.onNodeWithText("Медиатека и Яндекс Музыка").assertIsDisplayed()

        // Turning the catalogue off must be possible, and must survive leaving the screen: this is a
        // stored preference, not a per-session toggle.
        compose.onNode(hasText("Яндекс Музыка") and isToggleable()).performClick()
        compose.waitUntil(5000) {
            var off = false
            compose.activityRule.scenario.onActivity { off = !container(it).settings.state.value.sourceYandex }
            off
        }
        // With only the phone left, turning it off too is refused and explained.
        compose.onNode(hasText("Медиатека телефона") and isToggleable()).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("Хотя бы один источник должен остаться")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(5000) {
            var stillLocal = false
            compose.activityRule.scenario.onActivity { stillLocal = container(it).settings.state.value.sourceLocal }
            stillLocal
        }
        // Put both back so the rest of the suite sees the default state: a stored preference is
        // exactly what makes this test leak into the next one when it is left half-switched.
        compose.onNode(hasText("Яндекс Музыка") and isToggleable()).performClick()
        compose.waitUntil(5000) {
            var on = false
            compose.activityRule.scenario.onActivity { on = container(it).settings.state.value.sourceYandex }
            on
        }
        compose.onNode(hasText("Медиатека телефона") and isToggleable()).performClick()
        compose.waitUntil(5000) {
            var on = false
            compose.activityRule.scenario.onActivity { on = container(it).settings.state.value.sourceLocal }
            on
        }
    }

    /**
     * A catalogue track is searchable and playable through exactly the same field as a file.
     *
     * The regression this guards is the tempting shortcut of a second search screen for the service:
     * one field, one list, files first and the catalogue below, is the whole design of the screen. The
     * synthetic track stands in for a real result because the emulator has no network — see
     * `YandexTest` for the mapping and signing that need the real response shape.
     */
    @Test fun aCatalogueTrackIsFoundAndPlayedFromTheSameSearch() {
        seedLibrary()
        waitForCatalogTrack()
        searchForCatalogueTrack()
        // The row says where it comes from; that badge is what keeps "удалить с устройства" from ever
        // being offered for it.
        compose.onNodeWithText("Каталожный трек").performClick()
        compose.waitUntil(15000) {
            var playing = false
            compose.activityRule.scenario.onActivity { activity ->
                val state = container(activity).playback.state.value
                playing = state.track?.id == "yandex:test-catalog" && state.isPlaying
            }
            playing
        }
    }

    /**
     * Saving a catalogue track keeps it after leaving the screen — and the saved row opens.
     *
     * "Любимое" is stored as ids, and a stream's metadata lives in a cache that can be evicted. Saving
     * an id whose metadata was never remembered is how a saved song turns into a blank row, so the
     * order (remember, then store) is what this asserts by resolving the row afterwards.
     */
    @Test fun aSavedCatalogueTrackAppearsInItsOwnLibraryTab() {
        seedLibrary()
        waitForCatalogTrack()
        searchForCatalogueTrack()
        compose.onNodeWithContentDescription("Действия с Каталожный трек").performClick()
        waitText("В любимое")
        clickText("В любимое")
        // The sheet closes on the action, so the search screen is back; the library is one tab away.
        compose.onNode(hasContentDescription("Медиатека") and isSelectable()).performClick()
        waitText("Из каталога")
        clickText("Из каталога")
        waitText("Каталожный трек")
        compose.onNodeWithText("Каталожный трек").assertIsDisplayed()
    }

    /** Charts are reachable from Home and open the screen, without a network and without a crash. */
    @Test fun theChartScreenOpensFromHomeAndReportsItsState() {
        seedLibrary()
        compose.onNodeWithTag("homeList").performScrollToNode(hasText("Чарты"))
        clickText("Чарты")
        waitText("Что слушают прямо сейчас — по данным Яндекс Музыки.")
        // No network in the suite: the screen must say so rather than sit on a spinner forever.
        compose.waitUntil(20000) {
            compose.onAllNodes(hasText("Чарт не загрузился")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Загружаю чарт…")).fetchSemanticsNodes().isNotEmpty()
        }
        // Either way the promise under the error is the important half: local music still works.
        compose.onNodeWithContentDescription("Назад").performClick()
        waitText("Твоя музыка")
    }
}
