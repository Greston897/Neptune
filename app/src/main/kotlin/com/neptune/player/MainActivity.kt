package com.neptune.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.Tab

class MainActivity : ComponentActivity() {

    private lateinit var state: NeptuneState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        state = NeptuneState(applicationContext)

        // Deep links used by the design previews / screenshots pipeline.
        when (intent?.getStringExtra("tab")) {
            "library" -> state.tab = Tab.Library
            "search" -> state.tab = Tab.Search
            "tune" -> state.tab = Tab.Tune
            null -> Unit
            else -> state.tab = Tab.Now
        }
        val track = intent?.getIntExtra("track", -1) ?: -1
        if (track >= 0) {
            state.play(track)
            state.seekBy(intent?.getLongExtra("position", 84_000L) ?: 84_000L)
        }
        if (intent?.getBooleanExtra("playing", false) == true) state.play()

        setContent {
            NeptuneApp(state)
        }
    }
}
