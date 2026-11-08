package com.neptune.player

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*
import com.neptune.player.ui.screens.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun NeptuneApp(state: NeptuneState, nav: NavHostController = rememberNavController()) {
    val settings by state.settings.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val toast by state.toast.current.collectAsStateWithLifecycle()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.setAvatarPhoto(uri)
    }
    val lyricsImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = state.pendingLyricsTrack
        state.pendingLyricsTrack = null
        if (uri != null && track != null) {
            // The picked file is plain text or LRC; anything else is rejected by the plan gate's
            // caller rather than stored as mojibake.
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    val bytes = it.readBytes()
                    String(bytes.take(80_000).toByteArray(), Charsets.UTF_8)
                }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                // The words typed before the picker are still in the editor's draft; keep them so an
                // unreadable file cannot cost the user what they already wrote.
                state.toast("Текст не прочитан", NeIcon.Info, ToastTone.WARN)
            } else {
                // `saveLyrics` decides what Free may keep and opens the plan page itself when the
                // save is refused; the host only supplies the text.
                state.saveLyrics(track, text)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.refreshLibrary()
        state.toast(if (it) "Доступ открыт" else "Можно выбрать файлы вручную", NeIcon.Info, if (it) ToastTone.DONE else ToastTone.INFO)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) (context.applicationContext as NeptuneApplication).container.library.importUris(uris) { added ->
            // Every confirmation in 3.0 goes to the pill, including the import result that used to be
            // a snackbar at the opposite end of the screen from the button that caused it.
            state.toast(
                if (added > 0) "Добавлено: $added" else "Уже в медиатеке",
                if (added > 0) NeIcon.Plus else NeIcon.Info,
                if (added > 0) ToastTone.DONE else ToastTone.INFO,
            )
        }
    }
    val actions = LibraryActions(
        permission = { permission.launch((context.applicationContext as NeptuneApplication).container.library.audioPermission) },
        import = { importer.launch(arrayOf("audio/*")) },
        appSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
        avatar = { avatarPicker.launch("image/*") },
        lyricsFile = { lyricsImporter.launch(arrayOf("text/*", "application/octet-stream")) },
    )
    // One timer for the pill notice. Keyed on the notice id so a second confirmation restarts the
    // two seconds instead of being cut short by the first one's deadline.
    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        // Test builds keep the notice up ten times longer. The suite has to *see* a confirmation that
        // is otherwise gone in two seconds, and the alternative — scaling the test clock — stretches
        // every animation the layout measurements rely on. Release builds are untouched, so the
        // two-second promise the product makes is still guarded by `ToastDurationMs` and the gate.
        kotlinx.coroutines.delay(if (BuildConfig.DEBUG) ToastDurationMs * 10 else ToastDurationMs)
        state.toast.clear(id)
    }
    NeptuneTheme(settings) {
        CompositionLocalProvider(
            LocalLibraryActions provides actions,
            LocalHaptics provides { if (settings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) },
        ) {
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val fullPlayer = route == "player"
            val backgroundHaze = remember { HazeState() }
            val contentHaze = remember { HazeState() }
            val ambientPhase = rememberAmbientPhase()
            val sheenPhase = rememberSheenPhase()
            val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val hasTrack = playback.track != null
            val bottomInset = navigationBottom + DockMetrics.height(route, hasTrack) + DockMetrics.Breath
            val topInset = DockMetrics.topHeight(route, hasTrack)
            Box(Modifier.fillMaxSize()) {
                // Sibling backdrop source: glass panels inside the NavHost sample this layer.
                AmbientBackground(Modifier.fillMaxSize().haze(backgroundHaze), ambientPhase)
                CompositionLocalProvider(LocalBackgroundHaze provides backgroundHaze, LocalContentHaze provides contentHaze,
                    LocalBottomInset provides bottomInset, LocalTopInset provides topInset, LocalSheenPhase provides sheenPhase) {
                    // Full-size content source: the floating dock samples everything that scrolls under it.
                    Box(Modifier.fillMaxSize().haze(contentHaze)) {
                        AmbientBackground(Modifier.fillMaxSize(), ambientPhase)
                        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
                            // One motion character everywhere: the emphasized curve leaves fast and
                            // settles slowly, so a longer duration reads as calm rather than laggy.
                            enterTransition = {
                                if (!settings.animations) fadeIn(tween(0))
                                else if (targetState.destination.route == "player")
                                    slideInVertically(tween(NeMotion.Emphasized, easing = NeEase)) { it / 4 } + fadeIn(tween(NeMotion.Standard, easing = NeEase))
                                else slideInHorizontally(tween(NeMotion.Emphasized, easing = NeEase)) { it / 6 } + fadeIn(tween(NeMotion.Standard, easing = NeEase))
                            },
                            exitTransition = { if (!settings.animations) fadeOut(tween(0)) else fadeOut(tween(NeMotion.Standard, easing = NeEase)) },
                            popEnterTransition = { if (!settings.animations) fadeIn(tween(0)) else fadeIn(tween(NeMotion.Standard, easing = NeEase)) },
                            popExitTransition = {
                                if (!settings.animations) fadeOut(tween(0))
                                else if (initialState.destination.route == "player")
                                    slideOutVertically(tween(NeMotion.Emphasized, easing = NeEase)) { it / 4 } + fadeOut(tween(NeMotion.Standard, easing = NeEase))
                                else slideOutHorizontally(tween(NeMotion.Emphasized, easing = NeEase)) { it / 6 } + fadeOut(tween(NeMotion.Standard, easing = NeEase))
                            }) {
                            composable("home") {
                                HomeScreen(
                                    state, { kind, key -> nav.navigateCollection(kind, key) },
                                    { nav.navigateOnce("player") }, { nav.navigateOnce("profile") },
                                    { nav.navigateTab(Tab.SETTINGS) },
                                    // "Альбомы → Медиатека" must land on the albums tab, not on a
                                    // collection route: "albums" is a Library tab, not a kind.
                                    { state.libraryTab = 1; nav.navigateTab(Tab.LIBRARY) },
                                )
                            }
                            composable("search") { SearchScreen(state) { kind, key -> nav.navigateCollection(kind, key) } }
                            composable("library") { LibraryScreen(state) { kind, key -> nav.navigateCollection(kind, key) } }
                            composable("settings") { SettingsScreen(state, { nav.navigateOnce("profile") }, { nav.navigateOnce("appearance") }, { nav.navigateOnce("playback-settings") }, { nav.navigateOnce("data") }, { nav.navigateOnce("about") }, { state.openPlanManager() }) }
                            composable("player") { NowPlayingScreen(state, { nav.popBackStack() }, { nav.navigateOnce("queue") }, { kind, key -> nav.navigateCollection(kind, key) }, { nav.navigateOnce("lyrics") }) }
                            composable("queue") { QueueScreen(state) { nav.popBackStack() } }
                            composable("profile") { ProfileScreen(state, { nav.popBackStack() }, { state.openPlanManager() }) }
                            composable("appearance") { AppearanceScreen(state) { nav.popBackStack() } }
                            composable("playback-settings") { PlaybackSettingsScreen(state) { nav.popBackStack() } }
                            composable("data") { DataScreen(state, { nav.popBackStack() }) { nav.navigateCollection("recent", "") } }
                            composable("about") { AboutScreen { nav.popBackStack() } }
                            composable("lyrics") {
                                LyricsScreen(
                                    state,
                                    { state.lyricsTarget = null; nav.popBackStack() },
                                    // A save that Plus refused leaves the screen open behind the plan
                                    // page, so the text is still there when the user comes back — the
                                    // notice in the pill says it was not stored.
                                    { state.openPlanManager() },
                                )
                            }
                            composable("collection/{kind}/{key}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("key") { type = NavType.StringType })) { backStack ->
                                CollectionScreen(state, backStack.arguments?.getString("kind") ?: "", (backStack.arguments?.getString("key") ?: "").let { if (it == "-") "" else it }, { nav.popBackStack() }) { nav.popBackStack() }
                            }
                        }
                    }
                    // Identity up top, transport down bottom: the pill never covers a header because
                    // every screen reserves LocalTopInset in its content padding.
                    // The pill follows the user everywhere a track is loaded — Home, the Library,
                    // Settings and every sub-screen inside it (appearance, playback, data, about,
                    // profile) — and steps aside only for two genuinely full-bleed pages: the
                    // immersive player (which *is* the track) and the plan screen (which is opaque).
                    // A notice that only appears on some screens is a notice nobody trusts.
                    AnimatedVisibility(
                        visible = hasTrack && route != "player" && !state.paywallOpen,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(tween(if (settings.animations) NeMotion.Emphasized else 0, easing = NeEase)) +
                            slideInVertically(tween(if (settings.animations) NeMotion.Emphasized else 0, easing = NeEase)) { -it / 2 },
                        exit = fadeOut(tween(if (settings.animations) NeMotion.Standard else 0, easing = NeEase)) +
                            slideOutVertically(tween(if (settings.animations) NeMotion.Standard else 0, easing = NeEase)) { -it / 2 },
                    ) {
                        TrackPill(
                            playback, { nav.navigateOnce("player") },
                            Modifier.statusBarsPadding()
                                .padding(top = DockMetrics.PillTop, start = NeSpace.xl, end = NeSpace.xl),
                            toast = toast,
                        )
                    }
                    if (!fullPlayer && !state.paywallOpen) PlayerDock(Modifier.align(Alignment.BottomCenter), true, state, playback, { nav.navigateOnce("player") }, route) { tab -> nav.navigateTab(tab) }
                    state.contextTrack?.let { track ->
                        TrackActionsSheet(
                            state, track,
                            { state.contextTrack = null; state.contextPlaylist = null },
                            { kind, key -> nav.navigateCollection(kind, key) },
                            { nav.navigateOnce("lyrics") },
                        )
                    }
                    // One paywall, drawn above everything else: it is an opaque page, so a second
                    // copy underneath it (or a blurred panel showing through) is impossible.
                    // The plan page is its own window, not a Box on top of the app.
                    //
                    // In one window, "on top" only means "painted later": the screen underneath is
                    // still composed, still measured in the same coordinates, and every transparent
                    // pixel of the page — glass tints, rims, the sheen band — reveals it. That is
                    // exactly the report: "Внешний вид" and "Твой профиль" showing through
                    // "Подписка", two headers in one picture. A Dialog is a separate window with
                    // its own surface, so the two can never share a frame, whatever the panel alpha.
                    if (state.paywallOpen) {
                        Dialog(
                            onDismissRequest = { state.closePaywall() },
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                                dismissOnClickOutside = false,
                            ),
                        ) {
                            // Still no backdrop to sample: a panel here has nothing legitimate to blur.
                            CompositionLocalProvider(
                                LocalBackgroundHaze provides null,
                                LocalContentHaze provides null,
                            ) {
                                Box(Modifier.fillMaxSize().background(Ne.Void)) {
                                    SubscriptionScreen(state) { state.closePaywall() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateOnce(route: String) { if (currentDestination?.route != route) navigate(route) { launchSingleTop = true } }
private fun NavHostController.navigateCollection(kind: String, key: String) = navigateOnce("collection/${Uri.encode(kind)}/${Uri.encode(key.ifEmpty { "-" })}")
private fun NavHostController.navigateTab(tab: Tab) {
    if (currentDestination?.route == tab.route) return
    // Every section is a real back-stack entry: system Back returns to the actual previous page.
    navigate(tab.route) { launchSingleTop = true }
}
