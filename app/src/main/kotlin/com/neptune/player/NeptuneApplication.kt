package com.neptune.player

import android.app.Application
import com.neptune.player.data.LibraryRepository
import com.neptune.player.data.LyricsRepository
import com.neptune.player.data.PlanStore
import com.neptune.player.data.SettingsStore
import com.neptune.player.model.PlanFeature
import com.neptune.player.data.UserStore
import com.neptune.player.playback.Media3PlaybackBackend
import com.neptune.player.playback.PlaybackBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val playback: PlaybackBackend = Media3PlaybackBackend(application, library, settings, users, scope)
    init { library.refresh() }
}
