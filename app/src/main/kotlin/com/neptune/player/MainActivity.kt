package com.neptune.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.neptune.player.state.NeptuneState

class MainActivity : ComponentActivity() {
    private val state: NeptuneState by viewModels()

    /**
     * The instance the UI is driven by.
     *
     * Exposed because the instrumented suite has to act on the *same* object the composition reads:
     * `by viewModels()` hands out one instance per activity, and a second one built in a test would
     * own its own search query, its own catalog and its own notices — i.e. a test that proves nothing.
     */
    fun uiState(): NeptuneState = state
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // An instrumented run measures geometry (bounds, insets, gaps); endless ambient drift, sheen
        // and breath animations only cost frames there. The suite turns motion off up front instead
        // of leaving a software-GPU guest rendering three infinite transitions through 41 tests.
        // `BuildConfig.DEBUG` is how the test build is recognisable here: the runner loads its test
        // classes in its own process, so `javaClass` of the activity says nothing about the suite.
        if (BuildConfig.DEBUG) {
            (application as NeptuneApplication).container.settings.edit { it.copy(animations = false) }
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { NeptuneApp(state) }
    }
    override fun onResume() { super.onResume(); state.refreshLibrary() }
    override fun onStop() { state.persist(); super.onStop() }
}
