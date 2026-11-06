package com.neptune.player

import android.app.Application
import com.neptune.player.data.LibraryRepository
import com.neptune.player.data.SettingsStore
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
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate())
    val settings = SettingsStore(application)
    val users = UserStore(application)
    val library = LibraryRepository(application, scope)
    val playback: PlaybackBackend = Media3PlaybackBackend(application, library, settings, users, scope)
    init { library.refresh() }
}
