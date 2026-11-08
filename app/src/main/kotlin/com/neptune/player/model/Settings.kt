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

/**
 * The backdrop is part of the identity, not a preference.
 *
 * [OLED] is the single fixed base: every channel is equal, so the glass above it stays strictly
 * neutral black with no blue/graphite cast. The interest comes from the ambient layer (aurora arcs,
 * orbital rings, starfield, vignette) drawn on top of it, not from tinting the base. [MIDNIGHT] and
 * [OBSIDIAN] remain only so old stored preferences still deserialise; `validated()` folds every
 * value back to [OLED], which is why the picker is no longer offered in Appearance.
 */
enum class Surface(val title: String, val hex: Long) {
    OLED("Абсолютный чёрный", 0xFF000000),
    MIDNIGHT("Тёмная ночь", 0xFF050507),
    OBSIDIAN("Обсидиан", 0xFF0A0A0E);
    companion object { val FIXED = OLED }
}

enum class AvatarShape(val title: String) { CIRCLE("Круг"), SQUIRCLE("Скругление"), SQUARE("Панель") }

/**
 * Corner treatment for track thumbnails and album tiles.
 *
 * [CIRCLE] is the 2.9 default: the brief asked for track art and avatars rounded "fully, like a
 * circle". The other two stay available so the look is still a preference, not a decree.
 */
enum class ArtShape(val title: String) { CIRCLE("Круг"), ROUNDED("Скругление"), SQUARE("Панель") }

data class Settings(
    val accentIndex: Int = 0,
    // Personal accent: when enabled it overrides the preset palette at every `accent()` call site.
    val customAccent: Boolean = false,
    val accentArgb: Int = 0xFF6FA8FF.toInt(),
    val surfaceIndex: Int = 0,
    val radius: Float = 22f,
    val fontScale: Float = 1f,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val compact: Boolean = false,
    val navLabels: Boolean = true,
    val artworkMotion: Boolean = true,
    val showRemaining: Boolean = true,
    val avatarIndex: Int = 0,
    val artShapeIndex: Int = 0,
    val thumbScale: Float = 1f,
    val showAlbumRow: Boolean = true,
    val showRecentPanel: Boolean = true,
    /**
     * Custom avatar picture.
     *
     * The file lives in the app's private directory and is referenced by name only, so the value
     * survives a restart, a cache wipe and a revoked picker grant. An empty [avatarPhoto] means the
     * brand mark is drawn instead. [avatarPhotoVersion] is bumped on every successful import: Coil
     * caches by key, and a stateless path would keep showing the previous picture after a change.
     */
    val avatarPhoto: String = "",
    val avatarPhotoVersion: Int = 0,
    /**
     * Zoom of the chosen picture inside its frame (1f = fills, larger crops in).
     *
     * Kept in the model rather than in the composition so the crop survives navigation, and
     * validated so a wild stored value cannot zoom the face out of its own circle.
     */
    val avatarZoom: Float = 1f,
    val avatarSize: Float = 1f,
    val profileName: String = "Слушатель",
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    /** Night timer: ramp the volume down before pausing instead of stopping dead. Plus feature. */
    val sleepFade: Boolean = true,
    val glassEnabled: Boolean = true,
    val glassBlur: Float = 40f,
    val glassTint: Float = 0.38f,
    val glassRim: Float = 0.85f,
    val ambientGlow: Boolean = true,
    val accentGlow: Boolean = true,
) {
    val accent get() = Accent.of(accentIndex)
    val surface get() = Surface.FIXED
    val avatarShape get() = AvatarShape.entries.getOrElse(avatarIndex) { AvatarShape.CIRCLE }
    val artShape get() = ArtShape.entries.getOrElse(artShapeIndex) { ArtShape.CIRCLE }
    /** The colour every accent surface reads: the personal pick when enabled, the preset otherwise. */
    val accentHexValue: Long get() = if (customAccent) AccentColor.argbToHex(accentArgb) else accent.hex
    fun validated() = copy(
        accentIndex = accentIndex.coerceIn(0, Accent.entries.lastIndex),
        accentArgb = AccentColor.ensureVisible(accentArgb),
        artShapeIndex = artShapeIndex.coerceIn(0, ArtShape.entries.lastIndex),
        thumbScale = thumbScale.coerceIn(0.85f, 1.25f),
        // Locked: a stored or injected value can never move the app off its fixed backdrop.
        surfaceIndex = Surface.FIXED.ordinal,
        radius = radius.coerceIn(12f, 32f), fontScale = fontScale.coerceIn(0.9f, 1.2f),
        avatarIndex = avatarIndex.coerceIn(0, AvatarShape.entries.lastIndex),
        avatarSize = avatarSize.coerceIn(0.8f, 1.25f),
        // Only a bare file name is ever stored: a stored path with separators (or "..") would be a
        // traversal waiting to happen, and the value comes from preferences that can be written by
        // an older build or a restored backup.
        avatarPhoto = sanitizeAvatarName(avatarPhoto),
        avatarPhotoVersion = avatarPhotoVersion.coerceAtLeast(0),
        avatarZoom = avatarZoom.coerceIn(1f, 2.5f),
        profileName = profileName.trim().take(24).ifBlank { "Слушатель" },
        speed = speed.coerceIn(0.75f, 1.5f),
        glassBlur = glassBlur.coerceIn(8f, 56f),
        glassTint = glassTint.coerceIn(0.35f, 0.88f),
        glassRim = glassRim.coerceIn(0f, 1f),
    )
    /**
     * Drops everything the subscription can unlock.
     *
     * Called when a plan ends: a personal accent and a custom photo must not survive as ghost
     * entitlements, otherwise "Plus is over" would be untrue on screen. Playback, playlists and
     * likes are untouched — they were never paid.
     */
    fun withoutPaidExtras() = copy(
        customAccent = false,
        accentArgb = 0xFF6FA8FF.toInt(),
        avatarPhoto = "",
        avatarPhotoVersion = 0,
        avatarZoom = 1f,
    )

    fun resetAppearance() = copy(accentIndex = 0, surfaceIndex = Surface.FIXED.ordinal, radius = 22f, fontScale = 1f,
        animations = true, haptics = true, compact = false, navLabels = true,
        artworkMotion = true, showRemaining = true, glassEnabled = true, glassBlur = 40f,
        glassTint = 0.38f, glassRim = 0.85f, ambientGlow = true, accentGlow = true,
        customAccent = false, accentArgb = 0xFF6FA8FF.toInt(), artShapeIndex = 0, thumbScale = 1f,
        showAlbumRow = true, showRecentPanel = true, avatarZoom = 1f)

    companion object {
        private val safeAvatar = Regex("^[A-Za-z0-9._-]{1,64}$")

        /**
         * Keeps only a bare file name for the stored avatar.
         *
         * The value names a file this app owns inside `filesDir`, so it must not be able to point
         * anywhere else: no separators, no `..`, nothing outside `[A-Za-z0-9._-]`. Anything that does
         * not match is dropped instead of being repaired into something surprising — losing a
         * profile photo is recoverable, reading an arbitrary path is not.
         */
        fun sanitizeAvatarName(raw: String): String =
            raw.trim().takeIf { safeAvatar.matches(it) && !it.contains("..") } ?: ""
    }
}
