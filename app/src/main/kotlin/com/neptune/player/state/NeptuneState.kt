package com.neptune.player.state

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neptune.player.model.DemoTracks
import com.neptune.player.model.Track
import com.neptune.player.ui.Accent
import com.neptune.player.ui.Settings
import com.neptune.player.ui.Tab

private class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("neptune.settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        accentIndex = prefs.getInt("accent", 0),
        blur = prefs.getFloat("blur", 24f),
        tint = prefs.getFloat("tint", 0.14f),
        radius = prefs.getFloat("radius", 26f),
        bg = prefs.getFloat("bg", 1f),
        speed = prefs.getFloat("speed", 1f),
        grain = prefs.getBoolean("grain", true),
        animations = prefs.getBoolean("animations", true),
        glow = prefs.getBoolean("glow", true),
        floatingNav = prefs.getBoolean("floatingNav", true),
        haptics = prefs.getBoolean("haptics", true),
        miniPlayer = prefs.getBoolean("miniPlayer", true),
        showTimes = prefs.getBoolean("showTimes", true),
        rings = prefs.getBoolean("rings", true),
        labels = prefs.getBoolean("labels", true),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putInt("accent", s.accentIndex)
            .putFloat("blur", s.blur)
            .putFloat("tint", s.tint)
            .putFloat("radius", s.radius)
            .putFloat("bg", s.bg)
            .putFloat("speed", s.speed)
            .putBoolean("grain", s.grain)
            .putBoolean("animations", s.animations)
            .putBoolean("glow", s.glow)
            .putBoolean("floatingNav", s.floatingNav)
            .putBoolean("haptics", s.haptics)
            .putBoolean("miniPlayer", s.miniPlayer)
            .putBoolean("showTimes", s.showTimes)
            .putBoolean("rings", s.rings)
            .putBoolean("labels", s.labels)
            .apply()
    }
}

@Stable
class NeptuneState(context: Context) {

    private val store = SettingsStore(context)

    var settings by mutableStateOf(store.load())
        private set

    fun edit(block: (Settings) -> Settings) {
        val next = block(settings)
        if (next == settings) return
        settings = next
        store.save(next)
    }

    fun resetLook() = edit { Settings(accentIndex = it.accentIndex) }

    val tracks: List<Track> = DemoTracks.all

    var tab by mutableStateOf(Tab.Now)
    var libraryTab by mutableStateOf(0)
    var search by mutableStateOf("")
    var searchFocused by mutableStateOf(false)

    var currentIndex by mutableStateOf(0)
        private set
    val current: Track get() = tracks[currentIndex]

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(0) // 0 — выкл, 1 — вся медиатека, 2 — трек
        private set
    var volume by mutableStateOf(0.72f)
        private set
    var liked by mutableStateOf(setOf(0, 4))
        private set

    val accent: Accent get() = settings.accent
    val progress: Float
        get() = (positionMs.toFloat() / current.durationMs.toFloat()).coerceIn(0f, 1f)

    fun play(index: Int = currentIndex) {
        if (index != currentIndex) {
            currentIndex = index.coerceIn(0, tracks.lastIndex)
            positionMs = 0L
        }
        isPlaying = true
    }

    fun toggle() {
        isPlaying = !isPlaying
    }

    fun next(manual: Boolean = true) {
        when {
            repeatMode == 2 && !manual -> positionMs = 0L; return
            shuffle -> {
                var n = currentIndex
                while (n == currentIndex && tracks.size > 1) n = (0 until tracks.size).random()
                currentIndex = n
            }
            else -> currentIndex = (currentIndex + 1) % tracks.size
        }
        positionMs = 0L
    }

    fun prev() {
        if (positionMs > 4000) {
            positionMs = 0L
            return
        }
        currentIndex = if (currentIndex == 0) tracks.lastIndex else currentIndex - 1
        positionMs = 0L
    }

    fun seekTo(fraction: Float) {
        positionMs = (current.durationMs * fraction.coerceIn(0f, 1f)).toLong()
    }

    fun seekBy(ms: Long) {
        positionMs = (positionMs + ms).coerceIn(0L, current.durationMs)
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    fun toggleLike() {
        liked = if (current.id in liked) liked - current.id else liked + current.id
    }

    fun toggleShuffle() {
        shuffle = !shuffle
    }

    fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
    }

    fun tick(deltaMs: Long) {
        if (!isPlaying) return
        val next = positionMs + deltaMs
        if (next >= current.durationMs) {
            next(manual = false)
        } else {
            positionMs = next
        }
    }
}
