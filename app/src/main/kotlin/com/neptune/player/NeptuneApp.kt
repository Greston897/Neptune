package com.neptune.player

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val snackbar = remember { SnackbarHostState() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.refreshLibrary(); state.message(if (it) "Доступ открыт. Собираем твою музыку." else "Можно выбрать файлы вручную через импорт.")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) (context.applicationContext as NeptuneApplication).container.library.importUris(uris) { state.message(if (it > 0) "Добавлено файлов: $it" else "Эти файлы уже в медиатеке") }
    }
    val actions = LibraryActions(permission = { permission.launch((context.applicationContext as NeptuneApplication).container.library.audioPermission) },
        import = { importer.launch(arrayOf("audio/*")) }, appSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
    LaunchedEffect(state) { state.messages.collect { snackbar.showSnackbar(it) } }
    NeptuneTheme(settings) {
        CompositionLocalProvider(LocalLibraryActions provides actions, LocalHaptics provides { if (settings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }) {
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
                            enterTransition = { if (!settings.animations) fadeIn(tween(0)) else if (targetState.destination.route == "player") slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(220)) else slideInHorizontally(tween(260)) { it / 7 } + fadeIn(tween(220)) },
                            exitTransition = { if (!settings.animations) fadeOut(tween(0)) else fadeOut(tween(160)) },
                            popEnterTransition = { if (!settings.animations) fadeIn(tween(0)) else fadeIn(tween(220)) },
                            popExitTransition = { if (!settings.animations) fadeOut(tween(0)) else if (initialState.destination.route == "player") slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)) else slideOutHorizontally(tween(260)) { it / 7 } + fadeOut(tween(180)) }) {
                            composable("home") { HomeScreen(state, { kind, key -> nav.navigateCollection(kind, key) }, { nav.navigateOnce("player") }, { nav.navigateOnce("profile") }, { nav.navigateTab(Tab.SETTINGS) }) }
                            composable("search") { SearchScreen(state) { kind, key -> nav.navigateCollection(kind, key) } }
                            composable("library") { LibraryScreen(state) { kind, key -> nav.navigateCollection(kind, key) } }
                            composable("settings") { SettingsScreen(state, { nav.navigateOnce("profile") }, { nav.navigateOnce("appearance") }, { nav.navigateOnce("playback-settings") }, { nav.navigateOnce("data") }, { nav.navigateOnce("about") }) }
                            composable("player") { NowPlayingScreen(state, { nav.popBackStack() }, { nav.navigateOnce("queue") }) { kind, key -> nav.navigateCollection(kind, key) } }
                            composable("queue") { QueueScreen(state) { nav.popBackStack() } }
                            composable("profile") { ProfileScreen(state) { nav.popBackStack() } }
                            composable("appearance") { AppearanceScreen(state) { nav.popBackStack() } }
                            composable("playback-settings") { PlaybackSettingsScreen(state) { nav.popBackStack() } }
                            composable("data") { DataScreen(state, { nav.popBackStack() }) { nav.navigateCollection("recent", "") } }
                            composable("about") { AboutScreen { nav.popBackStack() } }
                            composable("collection/{kind}/{key}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("key") { type = NavType.StringType })) { backStack ->
                                CollectionScreen(state, backStack.arguments?.getString("kind") ?: "", (backStack.arguments?.getString("key") ?: "").let { if (it == "-") "" else it }, { nav.popBackStack() }) { nav.popBackStack() }
                            }
                        }
                    }
                    // Identity up top, transport down bottom: the pill never covers a header because
                    // every screen reserves LocalTopInset in its content padding.
                    if (DockMetrics.showsTrackPill(route, hasTrack)) {
                        TrackPill(
                            playback, { nav.navigateOnce("player") },
                            Modifier.align(Alignment.TopCenter).statusBarsPadding()
                                .padding(top = DockMetrics.PillTop, start = NeSpace.xl, end = NeSpace.xl),
                        )
                    }
                    if (!fullPlayer) PlayerDock(Modifier.align(Alignment.BottomCenter), true, state, playback, { nav.navigateOnce("player") }, route) { tab -> nav.navigateTab(tab) }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp, start = 16.dp, end = 16.dp)) {
                        Snackbar(containerColor = Ne.Text, contentColor = Ne.Black) { NeText(it.visuals.message, size = 12.sp, color = Ne.Black, maxLines = 3) }
                    }
                    state.contextTrack?.let { track -> TrackActionsSheet(state, track, { state.contextTrack = null; state.contextPlaylist = null }) { kind, key -> nav.navigateCollection(kind, key) } }
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
