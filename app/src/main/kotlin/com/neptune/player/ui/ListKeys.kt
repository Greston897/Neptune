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
}
