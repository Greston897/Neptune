package com.neptune.player.data

import android.content.Context
import com.neptune.player.data.yandex.MiniJson
import com.neptune.player.model.RemoteTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Metadata for every streamed track the app has seen, so the rest of the app can keep working with
 * plain [com.neptune.player.model.Track] ids.
 *
 * The local library resolves ids through a scan of the phone. Streams have no scan: their metadata
 * arrives with a search result or a chart row and is gone the moment that screen is recomposed. Since
 * a queue, a favourite and a playlist all store *ids*, the app needs somewhere to answer "what is
 * track yandex:1234" after a restart — this is that place.
 *
 * It is a cache, not user data, and that distinction is load-bearing:
 *
 *  * it can be dropped at any time and the worst outcome is a missing title in the queue;
 *  * it is capped, because a long session of browsing the chart could otherwise grow it without limit;
 *  * nothing here is ever shown as "your library". Saved streams are handled by the user store, and
 *    their metadata is exactly what this cache answers.
 */
class RemoteCatalog(context: Context) {
    private val prefs = context.getSharedPreferences("neptune.v4.catalog", Context.MODE_PRIVATE)
    private val _tracks = MutableStateFlow(load())
    val tracks = _tracks

    val byId: Map<String, RemoteTrack> get() = _tracks.value

    fun resolve(id: String): RemoteTrack? = _tracks.value[id]

    /** Stores every track of a page. Idempotent: re-opening the chart must not grow the cache. */
    fun remember(tracks: List<RemoteTrack>) {
        if (tracks.isEmpty()) return
        val added = tracks.filter { it.id.isNotBlank() && _tracks.value[it.id] != it }
        if (added.isEmpty()) return
        _tracks.update { current ->
            val merged = LinkedHashMap(current)
            added.forEach { merged[it.id] = it }
            // Oldest first out. 2000 rows is dozens of chart pages — far more than a session touches,
            // small enough that the whole file stays under a few hundred kilobytes.
            while (merged.size > LIMIT) merged.remove(merged.keys.first())
            merged
        }
        persist()
    }

    fun clear() {
        _tracks.value = emptyMap()
        prefs.edit().remove("tracks").apply()
    }

    private fun persist() {
        val rows = _tracks.value.values.map { t ->
            mapOf(
                "id" to t.id, "title" to t.title, "artists" to t.artists, "album" to t.album,
                "duration" to t.durationMs, "cover" to t.artworkUri, "year" to t.year,
                "premium" to t.premiumOnly, "albumId" to t.albumId, "preview" to t.preview,
                "artistId" to t.artistId,
            )
        }
        prefs.edit().putString("tracks", MiniJson.write(rows)).apply()
    }

    private fun load(): Map<String, RemoteTrack> {
        val raw = prefs.getString("tracks", null) ?: return emptyMap()
        val parsed = runCatching { MiniJson.parse(raw) }.getOrNull() ?: return emptyMap()
        return MiniJson.list(parsed).mapNotNull { row ->
            val node = MiniJson.obj(row)
            val id = node["id"] as? String ?: return@mapNotNull null
            val title = node["title"] as? String ?: return@mapNotNull null
            id to RemoteTrack(
                id = id,
                title = title,
                artists = node["artists"] as? String ?: "Неизвестный исполнитель",
                album = node["album"] as? String ?: "",
                durationMs = (node["duration"] as? Number)?.toLong() ?: 0L,
                artworkUri = node["cover"] as? String,
                year = (node["year"] as? Number)?.toInt(),
                premiumOnly = node["premium"] as? Boolean == true,
                albumId = node["albumId"] as? String,
                artistId = node["artistId"] as? String,
                preview = (node["preview"] as? Number)?.toLong() ?: 0L,
            )
        }.toMap()
    }

    private companion object {
        const val LIMIT = 2000
    }
}
