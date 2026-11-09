package com.neptune.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.neptune.player.model.RemoteTrack
import java.io.IOException

/**
 * Teaches ExoPlayer to play Yandex Music streams without ever handing it a URL that expires.
 *
 * A stream's queue entry carries `neptune://yandex/{id}`, not an audio URL, because a signed link is
 * valid for about a minute: a queue persisted across a restart would otherwise be a list of dead
 * links, and seeking re-opens the source with a new byte range, which would re-sign on every drag of
 * the progress bar. So the handle is stable and the link is minted where Media3 resolves the request:
 *
 * ```
 * ExoPlayer → ResolvingDataSource → neptune://yandex/123 → https://storage.../get-mp3/<md5>/...
 * ```
 *
 * `resolveDataSpec` runs on Media3's loading thread, so blocking there is correct and blocking on the
 * main thread is not — which is why this is a data source and not something the UI calls.
 */
@UnstableApi
class StreamSource(
    private val context: Context,
    private val directUrl: (String) -> String?,
    /**
     * Saved-for-offline copies, asked before the network.
     *
     * This is the whole point of a download: with the phone in flight mode, opening a saved track must
     * not touch the internet at all. A `file://` uri is returned as-is and the resolver steps aside.
     */
    private val offlineFile: (String) -> String? = { null },
) {

    /**
     * Links minted in this session.
     *
     * A seek is a close-then-open, so without this every drag of the slider would cost two HTTP round
     * trips and a new signature. The ticket XML is only valid for about a minute, but the link it
     * produces is served for far longer, so the cache is deliberately shorter than the link's life
     * and longer than a track: 20 minutes.
     */
    private val cache = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>) = size > 32
    }

    fun factory(): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                // The service is picky about unknown agents on the storage hosts, and this also makes
                // the app's own traffic identifiable in a server log instead of looking like a bot.
                .setUserAgent("Neptune/4.0 (Android)")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true),
        ),
    ) { spec -> resolve(spec) }

    private fun resolve(spec: DataSpec): DataSpec {
        val id = RemoteTrack.idOfUri(spec.uri) ?: return spec
        val url = offlineFile(id) ?: link(id) ?: throw IOException("Трек недоступен в Яндекс Музыке")
        return spec.buildUpon().setUri(Uri.parse(url)).build()
    }

    private fun link(id: String): String? {
        synchronized(cache) {
            val hit = cache[id]
            if (hit != null && System.currentTimeMillis() - hit.second < LINK_TTL_MS) return hit.first
        }
        val url = directUrl(id) ?: return null
        synchronized(cache) { cache[id] = url to System.currentTimeMillis() }
        return url
    }

    private companion object {
        /** 20 minutes: comfortably longer than a track, far shorter than a link stays valid. */
        const val LINK_TTL_MS = 20 * 60 * 1000L
    }
}
