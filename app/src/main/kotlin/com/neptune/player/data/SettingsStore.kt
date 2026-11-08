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
            customAccent = prefs.getBoolean("customAccent", false),
            accentArgb = prefs.getInt("accentArgb", 0xFF6FA8FF.toInt()),
            radius = prefs.getFloat("radius", 22f), fontScale = prefs.getFloat("fontScale", 1f),
            animations = prefs.getBoolean("animations", true), haptics = prefs.getBoolean("haptics", true),
            compact = prefs.getBoolean("compact", false), navLabels = prefs.getBoolean("navLabels", true),
            artworkMotion = prefs.getBoolean("artworkMotion", true), showRemaining = prefs.getBoolean("showRemaining", true),
            avatarIndex = prefs.getInt("avatar", 0), avatarTone = prefs.getInt("avatarTone", 0),
            artShapeIndex = prefs.getInt("artShape", 0), thumbScale = prefs.getFloat("thumbScale", 1f),
            showAlbumRow = prefs.getBoolean("showAlbumRow", true),
            showRecentPanel = prefs.getBoolean("showRecentPanel", true),
            avatarSize = prefs.getFloat("avatarSize", 1f),
            profileName = prefs.getString("profileName", "Слушатель") ?: "Слушатель",
            speed = prefs.getFloat("speed", 1f), skipSilence = prefs.getBoolean("skipSilence", false),
            glassEnabled = prefs.getBoolean("glassEnabled", true), glassBlur = prefs.getFloat("glassBlur", 40f),
            glassTint = prefs.getFloat("glassTint", 0.38f), glassRim = prefs.getFloat("glassRim", 0.85f),
            ambientGlow = prefs.getBoolean("ambientGlow", true), accentGlow = prefs.getBoolean("accentGlow", true),
        ).validated()
    }.getOrDefault(Settings()).let { loaded ->
        // One-time migration to the stronger black-glass recipe. V3 raises the blur radius and
        // lowers the tint so the frost is actually visible; later user tweaks are preserved.
        if (prefs.getBoolean("glassRecipeV3", false)) loaded else loaded.copy(
            glassEnabled = true, glassBlur = 40f, glassTint = 0.38f, glassRim = 0.85f,
            ambientGlow = true, accentGlow = true,
        ).validated()
    })
    val state = _state
    init {
        if (!prefs.getBoolean("glassRecipeV3", false)) {
            val s = _state.value
            prefs.edit().putBoolean("glassRecipeV3", true).putBoolean("glassRecipeV2", true)
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
                .putBoolean("customAccent", s.customAccent).putInt("accentArgb", s.accentArgb)
                .putInt("artShape", s.artShapeIndex).putFloat("thumbScale", s.thumbScale)
                .putBoolean("showAlbumRow", s.showAlbumRow).putBoolean("showRecentPanel", s.showRecentPanel)
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
