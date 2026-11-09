package com.neptune.player.data

import com.neptune.player.model.Track
import java.text.Normalizer

object LibraryLogic {
    fun normalize(value: String) = Normalizer.normalize(value.lowercase().replace('ё', 'е'), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").trim()
    fun matches(track: Track, query: String): Boolean {
        val terms = normalize(query).split("\\s+".toRegex()).filter { it.isNotBlank() }
        val haystack = normalize("${track.title} ${track.artist} ${track.album}")
        return terms.all { haystack.contains(it) }
    }
    fun search(tracks: List<Track>, query: String) = if (query.isBlank()) emptyList() else tracks.filter { matches(it, query) }
    /**
     * Albums, as the shelf means them.
     *
     * Tracks with no album tag at all are left out: a bare WAV has no metadata, and grouping by the
     * "Без альбома" placeholder produced one shelf card per untagged file — a shelf of albums that
     * are not albums. They stay reachable in "Треки" and under their artist.
     */
    fun albums(tracks: List<Track>) = tracks
        .filter { it.album.isNotBlank() && it.album != Track.NO_ALBUM }
        .groupBy { it.artist to it.album }
        .entries.map { (key, items) -> key.first to key.second to items }
    fun artists(tracks: List<Track>) = tracks.groupBy { it.artist }.entries.map { it.key to it.value }
}
