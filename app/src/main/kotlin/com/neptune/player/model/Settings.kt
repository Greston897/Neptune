package com.neptune.player.model

enum class Accent(val title: String, val hex: Long) {
    WHITE("Белый", 0xFFF5F5F7),
    ICE("Лёд", 0xFFB9DEEB),
    BLUE("Нептун", 0xFF6FA8FF),
    CYAN("Циан", 0xFF79E0EE),
    MINT("Мята", 0xFFB8DDCB),
    GREEN("Лайм", 0xFFC6E58A),
    AMBER("Янтарь", 0xFFE8CCA5),
    ORANGE("Апельсин", 0xFFFFB27A),
    CORAL("Коралл", 0xFFFF9E97),
    ROSE("Роза", 0xFFF3B7D4),
    LILAC("Лаванда", 0xFFCBBCE8),
    VIOLET("Фиалка", 0xFFA99BF2);
    companion object { fun of(i: Int) = entries.getOrElse(i) { WHITE } }
}

enum class Surface(val title: String, val hex: Long) {
    OLED("Абсолютный чёрный", 0xFF000000),
    MIDNIGHT("Тёмная ночь", 0xFF050507),
    OBSIDIAN("Обсидиан", 0xFF0A0A0E)
}

enum class AvatarShape(val title: String) { CIRCLE("Круг"), SQUIRCLE("Скругление"), SQUARE("Панель") }

data class Settings(
    val accentIndex: Int = 0,
    val surfaceIndex: Int = 1,
    val radius: Float = 22f,
    val fontScale: Float = 1f,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val compact: Boolean = false,
    val navLabels: Boolean = true,
    val artworkMotion: Boolean = true,
    val showRemaining: Boolean = true,
    val avatarIndex: Int = 0,
    val avatarTone: Int = 0,
    val avatarSize: Float = 1f,
    val profileName: String = "Слушатель",
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val glassEnabled: Boolean = true,
    val glassBlur: Float = 40f,
    val glassTint: Float = 0.38f,
    val glassRim: Float = 0.85f,
    val ambientGlow: Boolean = true,
    val accentGlow: Boolean = true,
) {
    val accent get() = Accent.of(accentIndex)
    val surface get() = Surface.entries.getOrElse(surfaceIndex) { Surface.MIDNIGHT }
    val avatarShape get() = AvatarShape.entries.getOrElse(avatarIndex) { AvatarShape.SQUIRCLE }
    fun validated() = copy(
        accentIndex = accentIndex.coerceIn(0, Accent.entries.lastIndex),
        surfaceIndex = surfaceIndex.coerceIn(0, Surface.entries.lastIndex),
        radius = radius.coerceIn(12f, 32f), fontScale = fontScale.coerceIn(0.9f, 1.2f),
        avatarIndex = avatarIndex.coerceIn(0, AvatarShape.entries.lastIndex),
        avatarTone = avatarTone.coerceIn(0, Accent.entries.lastIndex),
        avatarSize = avatarSize.coerceIn(0.8f, 1.25f),
        profileName = profileName.trim().take(24).ifBlank { "Слушатель" },
        speed = speed.coerceIn(0.75f, 1.5f),
        glassBlur = glassBlur.coerceIn(8f, 56f),
        glassTint = glassTint.coerceIn(0.35f, 0.88f),
        glassRim = glassRim.coerceIn(0f, 1f),
    )
    fun resetAppearance() = copy(accentIndex = 0, surfaceIndex = 1, radius = 22f, fontScale = 1f,
        animations = true, haptics = true, compact = false, navLabels = true,
        artworkMotion = true, showRemaining = true, glassEnabled = true, glassBlur = 40f,
        glassTint = 0.38f, glassRim = 0.85f, ambientGlow = true, accentGlow = true)
}
