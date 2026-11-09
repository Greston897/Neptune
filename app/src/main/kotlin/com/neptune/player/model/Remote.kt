package com.neptune.player.model

/**
 * A track that lives on Yandex Music rather than on the phone.
 *
 * Kept separate from [Track] on purpose. A [Track] in this app is a *playable file* — its id is
 * namespaced by storage (`device:`, `import:`), its `uri` is a content:// or file:// URL, and the
 * queue persists those ids and resolves them against the local library. A stream has none of that:
 * its `uri` has to be signed seconds before it is played, and it may stop being playable at all
 * (region, removal, a lapsed subscription).
 *
 * So the queue entry for a stream is a `Track` built by [toTrack] whose uri is the *stable* API URL
 * `neptune://yandex/{id}`. ExoPlayer never sees that string — the data source resolves it to a
 * signed link when it opens it — and the local library stays the single place that maps ids to
 * metadata, which is what made the queue survive a restart in 3.0.
 */
data class RemoteTrack(
    val id: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationMs: Long,
    /** `avatars.yandex.net/...` template, already expanded to 400×400. */
    val artworkUri: String?,
    val year: Int? = null,
    /** True when Yandex says the track needs a paid subscription. Free accounts still stream these. */
    val premiumOnly: Boolean = false,
    /** Length of the preview the service offers instead of the whole track, or 0 for a full file. */
    val preview: Long = 0L,
    /** Album id, needed for the album route and for a later "в плейлист" write path. */
    val albumId: String? = null,
    /** First credited artist's id, for "все треки исполнителя". */
    val artistId: String? = null,
) {
    val uriId: String get() = "yandex:$id"

    fun toTrack(source: Track.Source = Track.Source.STREAM) = Track(
        id = uriId,
        title = title,
        artist = artists,
        album = album,
        durationMs = durationMs,
        // Not an audio URL: see this class's header. The playback data source expands it.
        uri = "$SCHEME$id",
        artworkUri = artworkUri,
        source = source,
        year = year,
        artSeed = id.hashCode(),
        previewMs = preview,
    )

    companion object {
        /** Custom scheme the playback data source accepts. Never handed to the network as-is. */
        const val SCHEME = "neptune://yandex/"

        fun idOf(track: Track): String? =
            if (track.source == Track.Source.STREAM) idOfUri(android.net.Uri.parse(track.uri)) else null

        /**
         * The track id behind a `neptune://yandex/{id}` handle.
         *
         * Two entry points on purpose: the player only ever has a [Uri] (Media3 hands it one), while
         * screens hold a [Track]. Parsing the uri once here keeps the scheme knowledge in one place
         * instead of in a `startsWith` at every call site.
         */
        fun idOfUri(uri: android.net.Uri): String? =
            if (uri.scheme == "neptune" && uri.host == "yandex") uri.lastPathSegment?.takeIf { it.isNotBlank() } else null
    }
}

/**
 * A streamed track saved to the device.
 *
 * [file] is an absolute path under the app's own files directory, and the size is kept so a truncated
 * download can be detected instead of being played and stopping halfway. The track's metadata rides
 * along because the catalogue cache is a *cache* — it may be evicted, and a saved song must still know
 * what it is when it is opened offline.
 */
data class DownloadedTrack(
    val trackId: String,
    val file: String,
    val bytes: Long,
    val track: RemoteTrack,
    val savedAt: Long = System.currentTimeMillis(),
)

/** One row of a chart, with the position the service reports for it. */
data class ChartEntry(
    val position: Int,
    val shift: Int,
    val listeners: Int,
    val track: RemoteTrack,
)

/**
 * The charts the landing endpoint offers, in the order it offers them.
 *
 * The service returns a menu of chart variants (world, russia, and whatever else it is running this
 * week) where each item is a title plus a URL postfix for `/landing3/chart/{postfix}`. Nothing here
 * is hardcoded: if the menu changes, the chips change with it.
 */
data class ChartMenu(val items: List<Item>) {
    data class Item(val title: String, val postfix: String, val selected: Boolean)
}

data class ChartPage(
    val title: String,
    val description: String?,
    val menu: ChartMenu,
    val entries: List<ChartEntry>,
)

/** What a search over the streaming catalogue returned. Empty when nothing was asked yet. */
data class RemoteSearch(
    val tracks: List<RemoteTrack> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val artists: List<RemoteArtist> = emptyList(),
    val query: String = "",
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

data class RemoteAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val artworkUri: String?,
    val trackCount: Int,
)

data class RemoteArtist(val id: String, val name: String, val artworkUri: String?, val trackCount: Int)

/** Where the app is allowed to look for a track. Both, by the user's choice. */
data class Sources(
    val local: Boolean = true,
    val yandex: Boolean = true,
) {
    /** The union the search screen reads. Collapsing to one library was the point of the rebuild. */
    val both: Boolean get() = local && yandex
    val any: Boolean get() = local || yandex

    companion object {
        /**
         * Turning the last source off is not a state the app supports: a player with nowhere to look
         * would show an empty search with no explanation. The switch that would take the second-to-last
         * source away is therefore refused, and the caller says why instead of silently ignoring it.
         */
        fun toggle(current: Sources, local: Boolean, on: Boolean): Sources {
            val next = if (local) current.copy(local = on) else current.copy(yandex = on)
            return if (!next.any) current else next
        }
    }
}

/**
 * What the account section of the app knows about a Yandex token.
 *
 * There is no server on our side and none of the credentials are ours to keep: the token is whatever
 * the user pasted, stored on the device, and every request is made with it. [absent] means the app
 * streams from the free anonymous tier — which works, because the free tier exists on the service
 * side; see the class that fetches data.
 */
data class YandexAccount(
    val token: String? = null,
    val verified: Boolean = false,
    val name: String? = null,
    val uid: String? = null,
    /** Yandex reports whether the account carries a paid subscription; false is a normal state. */
    val plus: Boolean = false,
    val error: String? = null,
) {
    val present: Boolean get() = !token.isNullOrBlank()
    val label: String get() = when {
        !present -> "Без аккаунта"
        !verified -> "Токен не проверен"
        name != null -> name
        else -> "Аккаунт подключён"
    }
}
