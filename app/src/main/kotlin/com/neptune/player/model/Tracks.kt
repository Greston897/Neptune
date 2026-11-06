package com.neptune.player.model

import androidx.compose.ui.graphics.Color

data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val palette: List<Color>,
    val quality: String,
    val year: Int,
)

object DemoTracks {

    val all: List<Track> = listOf(
        Track(
            id = 0,
            title = "Midnight Drive",
            artist = "KAIRO",
            album = "Neon Atlas",
            durationMs = 222_000,
            palette = listOf(Color(0xFF2B3A8F), Color(0xFF6D5BFF), Color(0xFFFF5FA2)),
            quality = "FLAC · 24/96",
            year = 2026,
        ),
        Track(
            id = 1,
            title = "Glass Horizon",
            artist = "Nova Aera",
            album = "Liquid Sun",
            durationMs = 198_000,
            palette = listOf(Color(0xFF0A3D4C), Color(0xFF19C8E8), Color(0xFF5A8CFF)),
            quality = "FLAC · 24/48",
            year = 2025,
        ),
        Track(
            id = 2,
            title = "Тишина",
            artist = "Волна",
            album = "Стекло",
            durationMs = 254_000,
            palette = listOf(Color(0xFF2A1240), Color(0xFF9E8CFF), Color(0xFF4E7BFF)),
            quality = "ALAC · 16/44",
            year = 2026,
        ),
        Track(
            id = 3,
            title = "Velvet Static",
            artist = "Lumen",
            album = "Afterglow",
            durationMs = 181_000,
            palette = listOf(Color(0xFF3A0D17), Color(0xFFFF5252), Color(0xFFFF9E6B)),
            quality = "FLAC · 24/96",
            year = 2024,
        ),
        Track(
            id = 4,
            title = "Deep Blue",
            artist = "Aeon Mode",
            album = "Neptune Tapes",
            durationMs = 276_000,
            palette = listOf(Color(0xFF07203A), Color(0xFF3D8BFF), Color(0xFF7FE6FF)),
            quality = "FLAC · 24/192",
            year = 2026,
        ),
        Track(
            id = 5,
            title = "Полночь",
            artist = "Сигнал",
            album = "Полночь",
            durationMs = 208_000,
            palette = listOf(Color(0xFF14161F), Color(0xFF6E7B96), Color(0xFFB9C6DD)),
            quality = "AAC · 256",
            year = 2025,
        ),
        Track(
            id = 6,
            title = "Amber Rain",
            artist = "Solstice",
            album = "Golden Hours",
            durationMs = 233_000,
            palette = listOf(Color(0xFF3B1E05), Color(0xFFFF9E40), Color(0xFFFFD479)),
            quality = "FLAC · 24/96",
            year = 2023,
        ),
        Track(
            id = 7,
            title = "Crystal Room",
            artist = "Auréa",
            album = "Prism",
            durationMs = 194_000,
            palette = listOf(Color(0xFF07342A), Color(0xFF22E39B), Color(0xFF6EF7D2)),
            quality = "FLAC · 24/48",
            year = 2026,
        ),
    )

    val albums: List<Pair<String, List<Track>>> =
        all.groupBy { it.album }.map { (album, list) -> album to list }

    val artists: List<Pair<String, List<Track>>> =
        all.groupBy { it.artist }.map { (artist, list) -> artist to list }

    val genres = listOf("Ночной дрив", "Эмбиент", "Синти", "Электроника", "Лоу-фай", "Классика")
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
