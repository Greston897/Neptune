package com.neptune.player.model

enum class Accent(val title: String, val hex: Long) {
    WHITE("Белый", 0xFFF5F5F5),
    ICE("Лёд", 0xFFB9DEEB),
    LILAC("Лаванда", 0xFFCBBCE8),
    MINT("Мята", 0xFFB8DDCB),
    AMBER("Янтарь", 0xFFE8CCA5);
    companion object { fun of(i: Int) = entries.getOrElse(i) { WHITE } }
}

enum class AvatarStyle(val title: String) {
    ORBIT("Орбита"), BLOOM("Цветок"), WAVE("Волна"), SPARK("Искра"), ECHO("Эхо"), PRISM("Призма")
}

enum class Surface(val title: String, val hex: Long) {
    OLED("Глубокий чёрный", 0xFF050505), GRAPHITE("Графит", 0xFF101010)
}

data class Settings(
    val accentIndex: Int = 0,
    val surfaceIndex: Int = 0,
    val radius: Float = 16f,
    val fontScale: Float = 1f,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val compact: Boolean = false,
    val navLabels: Boolean = true,
    val artworkMotion: Boolean = true,
    val showRemaining: Boolean = true,
    val avatarIndex: Int = 0,
    val avatarTone: Int = 0,
    val profileName: String = "Слушатель",
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
) {
    val accent get() = Accent.of(accentIndex)
    val surface get() = Surface.entries.getOrElse(surfaceIndex) { Surface.OLED }
    val avatar get() = AvatarStyle.entries.getOrElse(avatarIndex) { AvatarStyle.ORBIT }
    fun validated() = copy(
        accentIndex = accentIndex.coerceIn(0, Accent.entries.lastIndex),
        surfaceIndex = surfaceIndex.coerceIn(0, Surface.entries.lastIndex),
        radius = radius.coerceIn(8f, 28f), fontScale = fontScale.coerceIn(0.9f, 1.15f),
        avatarIndex = avatarIndex.coerceIn(0, AvatarStyle.entries.lastIndex),
        avatarTone = avatarTone.coerceIn(0, Accent.entries.lastIndex),
        profileName = profileName.trim().take(24).ifBlank { "Слушатель" },
        speed = speed.coerceIn(0.75f, 1.5f),
    )
    fun resetAppearance() = copy(accentIndex = 0, surfaceIndex = 0, radius = 16f, fontScale = 1f,
        animations = true, haptics = true, compact = false, navLabels = true,
        artworkMotion = true, showRemaining = true)
}
