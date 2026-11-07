package com.neptune.player

import com.neptune.player.data.LibraryLogic
import com.neptune.player.data.UserData
import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Settings
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import com.neptune.player.model.trackCount
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val track = Track("1", "Невесомость", "Neptune Studio", "Ночные сессии", 36000, "asset:///audio/test.m4a")
    @Test fun searchNormalizesCaseAndYo() { assertTrue(LibraryLogic.matches(track, "НЕВЕСОМОСТЬ neptune")) }
    @Test fun searchRequiresAllTerms() { assertFalse(LibraryLogic.matches(track, "невесомость unknown")) }
    @Test fun blankSearchDoesNotReturnFakeResults() { assertTrue(LibraryLogic.search(listOf(track), " ").isEmpty()) }
    @Test fun settingsRecoverInvalidStoredValues() {
        val s = Settings(accentIndex = 999, radius = -4f, fontScale = 8f, profileName = " ", speed = 9f).validated()
        assertEquals(0, s.accentIndex); assertEquals(8f, s.radius, 0.0001f); assertEquals(1.15f, s.fontScale, 0.0001f); assertEquals("Слушатель", s.profileName); assertEquals(1.5f, s.speed, 0.0001f)
    }
    @Test fun appearanceResetPreservesProfile() {
        val s = Settings(profileName = "Саша", avatarIndex = 3, accentIndex = 2).resetAppearance()
        assertEquals("Саша", s.profileName); assertEquals(3, s.avatarIndex); assertEquals(0, s.accentIndex)
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
}
