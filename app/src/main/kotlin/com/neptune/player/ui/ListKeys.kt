package com.neptune.player.ui

/**
 * Keys for LazyColumn items.
 *
 * Every key is namespaced by domain and separated by NUL. Two crashes motivated this:
 *
 *  1. Album keys used to be `artist + album` with no separator, so ("A","BC") and ("AB","C") both
 *     produced "ABC" and LazyColumn threw "Key ... was already used".
 *  2. The search screen puts tracks, albums and artists in ONE list. With an empty album tag — very
 *     common for local files — `artist + ""` equalled the bare artist key, colliding across domains.
 *
 * A screen must never build a key by hand; route it through here so the invariant stays testable.
 */
object ListKeys {
    private const val SEP = "\u0000"
    fun track(id: String) = "track:$id"
    fun album(artist: String, album: String) = "album:$artist$SEP$album"
    fun artist(name: String) = "artist:$name"
    fun playlist(id: String) = "playlist:$id"

    /**
     * The queue may hold the same track twice, so identity alone is not a key here — the position is
     * part of it. NUL-separated so a track id containing "-" or digits cannot alias another slot
     * (`"a-1" + 2` and `"a" + 12` both read as "a-1-2" when joined with a dash).
     */
    fun queueItem(id: String, index: Int) = "queue:$id$SEP$index"

    /**
     * A lyric line. Two identical lines legitimately appear in one text (a repeated chorus), so the
     * *index* is part of the key, never the text; the timestamp alone is not unique either, because
     * an unsynchronised text gives every line -1.
     */
    fun lyric(index: Int, timeMs: Long) = "lyric:$index$SEP$timeMs"
}
