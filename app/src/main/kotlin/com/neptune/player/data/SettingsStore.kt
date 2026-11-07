package com.neptune.player.data

import android.content.Context
import com.neptune.player.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("neptune.v2.settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(runCatching {
        Settings(
            accentIndex = prefs.getInt("accent", 0), surfaceIndex = prefs.getInt("surface", 1),
            radius = prefs.getFloat("radius", 22f), fontScale = prefs.getFloat("fontScale", 1f),
            animations = prefs.getBoolean("animations", true), haptics = prefs.getBoolean("haptics", true),
            compact = prefs.getBoolean("compact", false), navLabels = prefs.getBoolean("navLabels", true),
            artworkMotion = prefs.getBoolean("artworkMotion", true), showRemaining = prefs.getBoolean("showRemaining", true),
            avatarIndex = prefs.getInt("avatar", 1), avatarTone = prefs.getInt("avatarTone", 0),
            avatarSize = prefs.getFloat("avatarSize", 1f),
            profileName = prefs.getString("profileName", "Слушатель") ?: "Слушатель",
            speed = prefs.getFloat("speed", 1f), skipSilence = prefs.getBoolean("skipSilence", false),
            glassEnabled = prefs.getBoolean("glassEnabled", true), glassBlur = prefs.getFloat("glassBlur", 30f),
            glassTint = prefs.getFloat("glassTint", 0.42f), glassRim = prefs.getFloat("glassRim", 0.85f),
            ambientGlow = prefs.getBoolean("ambientGlow", true), accentGlow = prefs.getBoolean("accentGlow", true),
        ).validated()
    }.getOrDefault(Settings()).let { loaded ->
        // One-time migration to the visible black-glass recipe; later user tweaks are preserved.
        if (prefs.getBoolean("glassRecipeV2", false)) loaded else loaded.copy(
            glassEnabled = true, glassBlur = 30f, glassTint = 0.42f, glassRim = 0.85f,
            ambientGlow = true, accentGlow = true,
        ).validated()
    })
    val state = _state
    init {
        if (!prefs.getBoolean("glassRecipeV2", false)) {
            val s = _state.value
            prefs.edit().putBoolean("glassRecipeV2", true)
                .putBoolean("glassEnabled", s.glassEnabled).putFloat("glassBlur", s.glassBlur)
                .putFloat("glassTint", s.glassTint).putFloat("glassRim", s.glassRim)
                .putBoolean("ambientGlow", s.ambientGlow).putBoolean("accentGlow", s.accentGlow).apply()
        }
    }
    fun edit(block: (Settings) -> Settings) {
        _state.update {
            val s = block(it).validated()
            prefs.edit().putInt("accent", s.accentIndex).putInt("surface", s.surfaceIndex)
                .putFloat("radius", s.radius).putFloat("fontScale", s.fontScale)
                .putBoolean("animations", s.animations).putBoolean("haptics", s.haptics)
                .putBoolean("compact", s.compact).putBoolean("navLabels", s.navLabels)
                .putBoolean("artworkMotion", s.artworkMotion).putBoolean("showRemaining", s.showRemaining)
                .putInt("avatar", s.avatarIndex).putInt("avatarTone", s.avatarTone).putFloat("avatarSize", s.avatarSize)
                .putString("profileName", s.profileName).putFloat("speed", s.speed)
                .putBoolean("skipSilence", s.skipSilence)
                .putBoolean("glassEnabled", s.glassEnabled).putFloat("glassBlur", s.glassBlur)
                .putFloat("glassTint", s.glassTint).putFloat("glassRim", s.glassRim)
                .putBoolean("ambientGlow", s.ambientGlow).putBoolean("accentGlow", s.accentGlow).apply()
            s
        }
    }
}
