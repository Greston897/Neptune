package com.neptune.player.model

/** A playable item. Metadata belongs to the repository, never to a composable. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String? = null,
    val source: Source = Source.IMPORTED,
    val year: Int? = null,
    val artSeed: Int = 0,
    /**
     * Length of the preview the catalogue offers instead of a whole file, or 0 for a full track.
     *
     * Only meaningful for [Source.STREAM]. A preview is playable, and the row says so with "30 сек"
     * rather than letting the audio stop on its own mid-verse.
     */
    val previewMs: Long = 0L,
) {
    /**
     * Where a track came from.
     *
     * `DEMO` was removed in 2.9: the app ships no audio. [STREAM] arrived in 4.0 with the Yandex
     * Music catalogue: such a track has no file of its own, and its `uri` is the `neptune://yandex/`
     * handle that the playback data source expands into a signed link at open time. Everything that
     * needs a *file* (lyrics from tags, "убрать из приложения", sharing) must therefore check the
     * source instead of assuming a content:// URL.
     */
    enum class Source { DEVICE, IMPORTED, STREAM }

    companion object {
        /**
         * The album name a file with no album tag is shown under.
         *
         * A named constant rather than a literal because the *shelf* has to be able to tell it apart
         * from a real album: grouping untagged files by it produced one card per file, i.e. a shelf of
         * albums that are not albums. Everything else (search, "Треки", the artist page) keeps
         * showing them, under this name.
         */
        const val NO_ALBUM = "Без альбома"
    }
}

data class Playlist(val id: String, val name: String, val trackIds: List<String> = emptyList())

data class PlaybackState(
    val connected: Boolean = false,
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 0.8f,
    val speed: Float = 1f,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val sleepRemainingMs: Long = 0,
    val error: String? = null,
    /**
     * A one-shot notice from the playback layer — currently "this track did not open, playing on".
     *
     * It lives here rather than in the UI because the *player* is what decides to skip; the pill is
     * only how it is said out loud. [messageSeq] makes it deliverable exactly once: a repeated
     * message must not be swallowed as "no change", and a recomposition must not re-show it.
     */
    val message: String? = null,
    val messageSeq: Long = 0,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

fun trackCount(count: Int): String {
    val tens = count % 100
    val word = when {
        tens in 11..14 -> "треков"
        count % 10 == 1 -> "трек"
        count % 10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$count $word"
}
