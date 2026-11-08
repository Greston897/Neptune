package com.neptune.player.data

import android.content.Context
import android.net.Uri
import com.neptune.player.model.LyricsDoc
import com.neptune.player.model.LyricsParser
import com.neptune.player.model.LyricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Lyrics per track: read from the file once, then kept in memory, and persisted when saved.
 *
 * Reading a tag means opening the audio file, which is I/O against a `content://` provider — that
 * must never happen on the frame that opens the player. [request] therefore resolves on an IO
 * dispatcher and publishes through a [MutableStateFlow]; the UI shows a placeholder until the value
 * lands, exactly like the artwork does.
 *
 * Two different lifetimes live here on purpose:
 *
 *  * **extracted** text is a cache. It can be rebuilt from the file at any time, so it is never
 *    written to disk;
 *  * **saved** text is user data (Plus). It is stored as JSON and wins over whatever the tag says,
 *    because the user edited it deliberately.
 */
class LyricsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("neptune.v3.lyrics", Context.MODE_PRIVATE)
    private val saved = MutableStateFlow(loadSaved())

    /** trackId → document, including the "no lyrics" result so a file is only scanned once. */
    private val cache = MutableStateFlow<Map<String, LyricsDoc>>(emptyMap())
    private val inFlight = mutableSetOf<String>()

    val docs = cache

    fun get(trackId: String): LyricsDoc? =
        saved.value[trackId] ?: cache.value[trackId]

    /** Synchronous check used by the UI to decide between "loading" and "not found". */
    fun isKnown(trackId: String): Boolean = trackId in saved.value || trackId in cache.value

    /**
     * Loads lyrics for [trackId] if they were never looked at. Idempotent: the playback position
     * updates 4× a second and the player calls this from a `LaunchedEffect(track.id)`.
     */
    fun request(trackId: String, uri: String) {
        if (isKnown(trackId)) return
        synchronized(inFlight) { if (!inFlight.add(trackId)) return }
        scope.launch {
            val doc = withContext(Dispatchers.IO) { extract(uri) }
            cache.update { it + (trackId to doc) }
            synchronized(inFlight) { inFlight.remove(trackId) }
        }
    }

    fun set(trackId: String, text: String) {
        val doc = LyricsParser.fromText(text, LyricsSource.SAVED)
        if (doc == null) {
            // An empty or whitespace-only save clears the stored text instead of keeping a blank.
            clear(trackId)
            return
        }
        cache.update { it + (trackId to doc) }
        val next = saved.value + (trackId to doc)
        saved.value = next
        persist(next)
    }

    fun clear(trackId: String) {
        val next = saved.value - trackId
        saved.value = next
        persist(next)
        cache.update { it - trackId }
    }

    /**
     * Reads at most [LyricsParser.MaxScanBytes] from the head of the file.
     *
     * ID3 sits at the front by definition, and a 300 MB FLAC opened fully would be a visible stall
     * on the player. The cap keeps the worst case at a few milliseconds, and a text that lives past
     * the cap is exactly the case where a saved text (Plus) is the right answer anyway.
     */
    private fun extract(uri: String): LyricsDoc = runCatching {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@runCatching LyricsDoc.None
        stream.use { input ->
            val head = ByteArray(LyricsParser.MaxScanBytes)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n <= 0) break
                read += n
            }
            if (read == 0) return@use LyricsDoc.None
            LyricsParser.fromBytes(head.copyOf(read)) ?: LyricsDoc.None
        }
    }.getOrDefault(LyricsDoc.None)

    private fun loadSaved(): Map<String, LyricsDoc> = runCatching {
        val raw = prefs.getString("data", "") ?: ""
        if (raw.isBlank()) return@runCatching emptyMap<String, LyricsDoc>()
        val root = JSONObject(raw)
        root.keys().asSequence().mapNotNull { id ->
            val text = root.optString(id, "")
            LyricsParser.fromText(text, LyricsSource.SAVED)?.let { id to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun persist(map: Map<String, LyricsDoc>) {
        val root = JSONObject()
        map.forEach { (id, doc) -> root.put(id, doc.raw) }
        prefs.edit().putString("data", root.toString()).apply()
    }
}
