package com.neptune.player

import android.app.Application
import com.neptune.player.data.LibraryRepository
import com.neptune.player.data.LyricsRepository
import com.neptune.player.data.PlanStore
import com.neptune.player.data.RemoteCatalog
import com.neptune.player.data.SettingsStore
import com.neptune.player.data.UserData
import com.neptune.player.data.UserStore
import com.neptune.player.data.yandex.YandexMusicApi
import com.neptune.player.data.yandex.YandexMusicRepository
import com.neptune.player.model.PlanFeature
import com.neptune.player.playback.Media3PlaybackBackend
import com.neptune.player.playback.PlaybackBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NeptuneApplication : Application() {
    lateinit var container: AppContainer private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(application: Application) {
    /**
     * The plan page's open/closed rule, kept here rather than inside the ViewModel so it can be
     * exercised without a device.
     *
     * Regression it encodes: the page used to be addressed by a *nullable reason* alone, where "no
     * particular reason" and "closed" were the same value — a tap on "Neptune Plus" therefore opened
     * nothing, and two entry points could each believe they owned the page, stacking two translucent
     * copies whose blurred panels mixed into a smear.
     *
     * `open()` is a no-op when the page is already up, so there is exactly one overlay at a time and
     * no second copy can be composed. `reason` is captured on the opening call only.
     */
    object Paywall {
        var open: Boolean = false
            private set
        var reason: PlanFeature? = null
            private set

        /** Returns true when this call actually opened the page. */
        fun openFor(feature: PlanFeature? = null): Boolean {
            if (open) return false
            reason = feature
            open = true
            return true
        }

        fun close() {
            open = false
            reason = null
        }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsStore(application)
    val users = UserStore(application)
    val plan = PlanStore(application)
    val lyrics = LyricsRepository(application, scope)
    val library = LibraryRepository(application, scope)

    /**
     * The streaming side (4.0).
     *
     * The API reads its token through the repository at call time, and the repository is the one
     * object the UI talks to — so a token pasted in settings takes effect on the next request without
     * anything being rebuilt.
     */
    val remote = RemoteCatalog(application)
    val yandex = YandexMusicRepository(application, scope, YandexMusicApi(token = { remoteToken() }))
    val playback: PlaybackBackend = Media3PlaybackBackend(application, library, settings, users, scope)

    init {
        // One wiring point: the library answers "what is yandex:1234" through the catalogue, and the
        // user store's saved ids are what it resolves. Doing this in the constructor of either class
        // would leave one of them holding a half-initialised reference.
        library.remote = remote
        // Whatever the catalogue shows becomes resolvable by id; the library is told so a saved track
        // can appear in "Из каталога" the moment it is saved, without a restart.
        yandex.onTracks = { tracks ->
            remote.remember(tracks)
            library.refreshStreams()
        }
        /**
         * Everything the user store holds that may be a stream: favourites and every playlist.
         *
         * The queue's own history is included, and that is not a detail. Following the user from
         * "Любимое" to "Из каталога" means the saved row must resolve on the *first* frame after the
         * write, not on the next restart — and a saved id whose metadata has not arrived yet resolves
         * to nothing, which is exactly the blank row this wiring exists to prevent.
         */
        fun savedStreamIds(data: UserData): List<String> =
            data.liked + data.recent + data.playlists.flatMap { it.trackIds }

        library.rememberSavedStream(savedStreamIds(users.state.value))
        scope.launch {
            users.state.collect { data -> library.rememberSavedStream(savedStreamIds(data)) }
        }
        library.refresh()
    }

    /** Read *after* construction; the lambda is only called from a network request. */
    private fun remoteToken(): String? = yandex.state.value.account.token
}
