package com.neptune.player.data.yandex

import com.neptune.player.model.ChartEntry
import com.neptune.player.model.ChartMenu
import com.neptune.player.model.ChartPage
import com.neptune.player.model.RemoteAlbum
import com.neptune.player.model.RemoteArtist
import com.neptune.player.model.RemoteSearch
import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.Track
import com.neptune.player.model.YandexAccount

/**
 * Maps Yandex Music JSON onto the app's own types.
 *
 * Every field below was read off the reference client's models (MarshalX/yandex-music-api) rather
 * than guessed, because the failure mode here is quiet: a renamed key yields an empty list, and an
 * empty list looks exactly like "ничего не нашлось".
 *
 * The mapping is pure and lives in its own object so it can be unit-tested against captured
 * responses — no Android classes, no network, no `org.json`.
 */
object YandexMap {
    /** Yandex serves covers from avatars.yandex.net with a `%%` size placeholder. */
    private const val COVER_SIZE = "400x400"

    /**
     * A track, from either a full `Track` object or the shorter shape that charts carry.
     *
     * Charts wrap each row as `{ position, progress, shift, track: {...} }` and search wraps them as
     * `{ track: {...} }` too, so unwrapping is one place instead of five call sites.
     */
    fun track(raw: Any?, premiumFallback: Boolean = false): RemoteTrack? {
        val root = MiniJson.obj(raw)
        val node = if (root.containsKey("track")) MiniJson.obj(root["track"]) else root
        val id = idOf(node["id"]) ?: return null
        val title = node["title"] as? String ?: return null
        val artistNodes = MiniJson.list(node["artists"]).map { MiniJson.obj(it) }
        val artists = artistNodes.mapNotNull { a -> a["name"] as? String }.filter { it.isNotBlank() }
        val album = MiniJson.list(node["albums"]).firstOrNull()?.let { MiniJson.obj(it) }
        val covers = album?.let { MiniJson.obj(it["coverUri"]) as? String }
            ?: node["coverUri"] as? String
        return RemoteTrack(
            id = id,
            title = title,
            artists = artists.joinToString(", ").ifBlank { "Неизвестный исполнитель" },
            album = (album?.get("title") as? String)?.ifBlank { null } ?: "",
            durationMs = (node["durationMs"] as? Number)?.toLong() ?: 0L,
            artworkUri = coverUrl(covers),
            year = (album?.get("year") as? Number)?.toInt(),
            // `availableForPremiumUsers == false` means everybody streams it. The field is missing on
            // plenty of objects, and a missing field must not be read as "premium only".
            premiumOnly = (node["availableForPremiumUsers"] as? Boolean) ?: premiumFallback,
            // A preview is 30 seconds of a track the service will not hand over in full. It is
            // playable, so it stays in the list, but the row says so instead of the user finding out
            // when the audio stops halfway through the first verse.
            preview = (node["previewDurationMs"] as? Number)?.toLong()?.takeIf { it > 0 } ?: 0L,
            albumId = album?.get("id")?.let(::idOf),
            artistId = artistNodes.firstOrNull()?.get("id")?.let(::idOf),
        )
    }

    /**
     * A chart page: the menu the service offers plus one page of its rows.
     *
     * The chart arrives as a *playlist* (`chart`) plus a menu of other charts. Its tracks come in as
     * `TrackShort` objects that carry the full track inline, so no second request is needed to render
     * the list — which is the difference between a chart that opens instantly and one that opens after
     * three round-trips.
     */
    fun chart(raw: Any?): ChartPage? {
        val root = MiniJson.obj(raw)
        val chart = MiniJson.obj(root["chart"])
        val menu = chartMenu(root["menu"])
        val entries = MiniJson.list(chart["tracks"]).mapNotNull { row ->
            val r = MiniJson.obj(row)
            val position = (MiniJson.obj(r["chart"])["position"] as? Number)?.toInt()
                ?: ((r["originalIndex"] as? Number)?.toInt()?.plus(1))
                ?: return@mapNotNull null
            val track = track(row) ?: return@mapNotNull null
            ChartEntry(
                position = position,
                shift = (MiniJson.obj(r["chart"])["shift"] as? Number)?.toInt() ?: 0,
                listeners = (MiniJson.obj(r["chart"])["listeners"] as? Number)?.toInt() ?: 0,
                track = track,
            )
        }.sortedBy { it.position }
        if (entries.isEmpty()) return null
        return ChartPage(
            title = (root["title"] as? String)?.ifBlank { null } ?: "Чарт",
            description = (root["chartDescription"] as? String)?.ifBlank { null },
            menu = menu,
            entries = entries,
        )
    }

    fun chartMenu(raw: Any?): ChartMenu {
        val items = MiniJson.list(MiniJson.obj(raw)["items"]).mapNotNull { item ->
            val i = MiniJson.obj(item)
            val title = i["title"] as? String ?: return@mapNotNull null
            // The service hands back the request postfix as a full URL suffix; only the last segment
            // is used, so `/landing3/chart/russia` and `russia` both work.
            val url = (i["url"] as? String)?.trim()?.trimEnd('/') ?: ""
            val postfix = url.substringAfterLast('/')
            ChartMenu.Item(title = title, postfix = postfix, selected = i["selected"] as? Boolean == true)
        }
        return ChartMenu(items)
    }

    fun search(raw: Any?): RemoteSearch {
        val root = MiniJson.obj(raw)
        val tracks = MiniJson.list(MiniJson.obj(root["tracks"])["results"]).mapNotNull { track(it) }
        val albums = MiniJson.list(MiniJson.obj(root["albums"])["results"]).mapNotNull { album(it) }
        val artists = MiniJson.list(MiniJson.obj(root["artists"])["results"]).mapNotNull { artist(it) }
        val typed = root["text"] as? String ?: ""
        return RemoteSearch(
            tracks = tracks,
            albums = albums,
            artists = artists,
            query = typed,
            corrected = correctedQuery(raw, typed),
        )
    }

    /**
     * The corrected query, when the service decided the user's spelling was wrong.
     *
     * Returns null when there was nothing to correct, so the UI can show the hint only when it
     * actually differs from what was typed.
     */
    fun correctedQuery(raw: Any?, typed: String): String? {
        val root = MiniJson.obj(raw)
        val corrected = root["misspellResult"] as? String
        if (corrected.isNullOrBlank() || corrected.equals(typed, ignoreCase = true)) return null
        return corrected
    }

    fun album(raw: Any?): RemoteAlbum? {
        val node = MiniJson.obj(raw)
        val id = idOf(node["id"]) ?: return null
        val title = (node["title"] as? String)?.ifBlank { null } ?: return null
        val artists = MiniJson.list(node["artists"]).mapNotNull { MiniJson.obj(it)["name"] as? String }
        return RemoteAlbum(
            id = id,
            title = title,
            artist = artists.joinToString(", ").ifBlank { "Неизвестный исполнитель" },
            year = (node["year"] as? Number)?.toInt(),
            artworkUri = coverUrl(node["coverUri"] as? String),
            trackCount = (node["trackCount"] as? Number)?.toInt() ?: 0,
        )
    }

    fun artist(raw: Any?): RemoteArtist? {
        val node = MiniJson.obj(raw)
        val id = idOf(node["id"]) ?: return null
        val name = (node["name"] as? String)?.ifBlank { null } ?: return null
        return RemoteArtist(
            id = id,
            name = name,
            artworkUri = coverUrl((node["cover"] as? Map<*, *>)?.get("uri") as? String ?: node["coverUri"] as? String),
            trackCount = (node["trackCount"] as? Number)?.toInt() ?: 0,
        )
    }

    /** `avatars.yandex.net/get-music-content/1234/abc/%%` → a concrete 400×400 URL. */
    fun coverUrl(template: String?): String? {
        if (template.isNullOrBlank()) return null
        val path = if (template.startsWith("http")) template else "https://$template"
        return if (path.contains("%%")) path.replace("%%", COVER_SIZE) else path
    }

    /**
     * Ids arrive as numbers and as strings ("123", 123, "123:456" for a track of a foreign album).
     * The app keys everything by string, so both are folded here.
     */
    fun idOf(value: Any?): String? = when (value) {
        is Number -> value.toLong().toString()
        is String -> value.trim().ifBlank { null }
        else -> null
    }

    /**
     * Builds the `/download-info` list.
     *
     * The response is an envelope: `{ "result": [ { codec, bitrateInKbps, downloadInfoUrl, ... } ] }`.
     * Some responses wrap it once more in `result` again, so the reader tolerates both instead of
     * returning an empty list and making the player say "трек недоступен" for a track that is fine.
     */
    fun downloadInfo(raw: Any?): List<YandexSign.DownloadInfo> {
        val root = MiniJson.obj(raw)
        val result = root["result"] ?: raw
        val rows = MiniJson.list(result).ifEmpty { MiniJson.list(MiniJson.obj(result)["result"]) }
        return rows.mapNotNull { row ->
            val node = MiniJson.obj(row)
            val url = node["downloadInfoUrl"] as? String ?: return@mapNotNull null
            YandexSign.DownloadInfo(
                codec = (node["codec"] as? String) ?: "mp3",
                bitrateKbps = (node["bitrateInKbps"] as? Number)?.toInt() ?: 0,
                infoUrl = if (url.startsWith("http")) url else "https://$url",
            )
        }
    }

    /** Account status: what the settings row shows after a token is pasted. */
    fun account(raw: Any?): YandexAccount {
        val root = MiniJson.obj(raw)
        val result = if (root.containsKey("result")) MiniJson.obj(root["result"]) else root
        val account = MiniJson.obj(result["account"])
        val plus = MiniJson.obj(result["plus"])
        return YandexAccount(
            name = (account["displayName"] as? String)?.ifBlank { null }
                ?: (account["login"] as? String)?.ifBlank { null },
            uid = idOf(account["uid"]),
            plus = plus["hasPlus"] as? Boolean == true,
            verified = true,
        )
    }

    /**
     * The queue entry the player will hold for a stream.
     *
     * Note what it does *not* do: it never stores a signed URL. Those expire in about a minute, so a
     * queue persisted across a restart would be full of dead links; the handle is stable and the link
     * is minted when the file is actually opened.
     */
    fun queueTrack(remote: RemoteTrack): Track = remote.toTrack()
}
