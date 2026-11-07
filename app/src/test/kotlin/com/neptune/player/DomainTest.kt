package com.neptune.player

import com.neptune.player.data.LibraryLogic
import com.neptune.player.data.UserData
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Settings
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import com.neptune.player.model.trackCount
import com.neptune.player.ui.DockMetrics
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val track = Track("1", "Невесомость", "Neptune Studio", "Ночные сессии", 36000, "asset:///audio/test.m4a")
    @Test fun searchNormalizesCaseAndYo() { assertTrue(LibraryLogic.matches(track, "НЕВЕСОМОСТЬ neptune")) }
    @Test fun searchRequiresAllTerms() { assertFalse(LibraryLogic.matches(track, "невесомость unknown")) }
    @Test fun blankSearchDoesNotReturnFakeResults() { assertTrue(LibraryLogic.search(listOf(track), " ").isEmpty()) }
    @Test fun settingsRecoverInvalidStoredValues() {
        val s = Settings(accentIndex = 999, radius = -4f, fontScale = 8f, profileName = " ", speed = 9f).validated()
        assertEquals("accent", 11, s.accentIndex)
        assertEquals("radius", 12f, s.radius, 0.0001f)
        assertEquals("fontScale", 1.2f, s.fontScale, 0.0001f)
        assertEquals("profile", "Слушатель", s.profileName)
        assertEquals("speed", 1.5f, s.speed, 0.0001f)
    }
    @Test fun glassSettingsClampToSafeRanges() {
        val s = Settings(glassBlur = 999f, glassTint = -1f, glassRim = 5f, avatarSize = 9f).validated()
        assertEquals(36f, s.glassBlur, 0.0001f)
        assertEquals(0.35f, s.glassTint, 0.0001f)
        assertEquals(1f, s.glassRim, 0.0001f)
        assertEquals(1.25f, s.avatarSize, 0.0001f)
    }
    @Test fun appearanceResetPreservesProfile() {
        val s = Settings(profileName = "Саша", avatarIndex = 2, accentIndex = 2).resetAppearance()
        assertEquals("Саша", s.profileName); assertEquals(2, s.avatarIndex); assertEquals(0, s.accentIndex)
    }
    @Test fun playlistDoesNotDuplicateTrack() {
        val (data, p) = UserData().createPlaylist("Ночь")
        val next = data.addToPlaylist(p.id, "1").addToPlaylist(p.id, "1")
        assertEquals(listOf("1"), next.playlists.single().trackIds)
        assertEquals(emptyList<String>(), next.removeFromPlaylist(p.id, "1").playlists.single().trackIds)
    }
    @Test fun historyIsUniqueAndBounded() {
        var data = UserData()
        repeat(80) { data = data.rememberTrack(it.toString()) }
        data = data.rememberTrack("79")
        assertEquals(60, data.recent.size); assertEquals("79", data.recent.first()); assertEquals(1, data.recent.count { it == "79" })
    }
    @Test fun progressNeverDividesByZero() { assertEquals(0f, PlaybackState().progress, 0.0001f); assertEquals(0.5f, PlaybackState(positionMs = 500, durationMs = 1000).progress, 0.0001f) }
    @Test fun timeAndPlurals() { assertEquals("0:00", formatTime(-10)); assertEquals("1:01:01", formatTime(3661000)); assertEquals("21 трек", trackCount(21)); assertEquals("11 треков", trackCount(11)) }
    @Test fun visibleGlassRecipeIsTheDefault() {
        val s = Settings()
        assertTrue(s.glassEnabled); assertTrue(s.ambientGlow); assertTrue(s.accentGlow)
        assertEquals(30f, s.glassBlur, 0.0001f)
        assertEquals(0.42f, s.glassTint, 0.0001f)
        assertEquals(0.85f, s.glassRim, 0.0001f)
        val reset = Settings(glassBlur = 9f, glassTint = 0.8f, glassRim = 0.1f).resetAppearance()
        assertEquals(s.glassBlur, reset.glassBlur, 0.0001f)
        assertEquals(s.glassTint, reset.glassTint, 0.0001f)
        assertEquals(s.glassRim, reset.glassRim, 0.0001f)
    }
    @Test fun dockClearanceMatchesFloatingChrome() {
        assertEquals(0.dp, DockMetrics.height("player", hasTrack = true))
        assertEquals(0.dp, DockMetrics.height("queue", hasTrack = true))
        assertEquals(DockMetrics.Bottom + DockMetrics.NavHeight, DockMetrics.height("home", hasTrack = false))
        assertEquals(DockMetrics.Bottom + DockMetrics.MiniHeight + DockMetrics.Gap + DockMetrics.NavHeight, DockMetrics.height("home", hasTrack = true))
        assertEquals(DockMetrics.Bottom + DockMetrics.MiniHeight + DockMetrics.Gap, DockMetrics.height("appearance", hasTrack = true))
        assertTrue(DockMetrics.showsNavbar("collection/liked/-"))
        assertFalse(DockMetrics.showsMiniPlayer("player", hasTrack = true))
    }
}
