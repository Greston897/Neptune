package com.neptune.player.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
    val permission: Boolean = false,
    val error: String? = null,
    /**
     * Streamed tracks the user saved (favourites, playlists), as metadata.
     *
     * They live here rather than in a second list the UI has to remember to join: every screen
     * already resolves ids through [byId], so folding them in makes a saved Yandex track behave
     * exactly like a file everywhere — in the queue, in a playlist, in "Любимое".
     */
    val saved: List<Track> = emptyList(),
) {
    /** Everything the app can resolve, local files and saved streams alike. */
    val byId get() = (tracks + saved).associateBy { it.id }
    val localCount get() = tracks.count { it.source == Track.Source.DEVICE }
    val streamCount get() = saved.size
    /** The whole catalogue, in the order every screen wants it: own files first, then streams. */
    val all get() = tracks + saved.filter { s -> tracks.none { it.id == s.id } }
}

/**
 * The user's own music, and nothing else.
 *
 * 2.9 removed the bundled demo sessions: the library is built only from files the user picked with
 * the system document picker (one or many at a time) plus an optional MediaStore scan. An empty
 * library is a real, designed state now — Home and Library both offer the import action instead of
 * showing somebody else's audio.
 */
class LibraryRepository(private val context: Context, private val scope: CoroutineScope) {

    /**
     * Metadata for streams the user saved. Set by the container after construction, because the
     * catalog needs nothing from the library and a constructor cycle would only make both harder to
     * test. Null means "no streaming in this build" and every stream path degrades to "unknown".
     */
    var remote: RemoteCatalog? = null
        set(value) { field = value; refreshRemote() }

    private val prefs = context.getSharedPreferences("neptune.v2.sources", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(LibraryState(permission = hasPermission()))
    val state = _state
    val audioPermission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasPermission() = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    private var refreshJob: kotlinx.coroutines.Job? = null
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _state.update { it.copy(loading = true, permission = hasPermission(), error = null) }
            val result = withContext(Dispatchers.IO) {
                val tracks = mutableListOf<Track>()
                var error: String? = null
                if (hasPermission()) runCatching { tracks += scanDevice() }
                    .onFailure { error = "Не удалось прочитать музыку на устройстве" }
                importedUris().forEach { raw ->
                    runCatching {
                        val uri = Uri.parse(raw)
                        if (tracks.none { it.uri == raw }) tracks += readImported(uri)
                    }.onFailure { error = "Часть файлов недоступна. Остальная музыка загружена." }
                }
                LibraryState(tracks.distinctBy { it.id }.sortedBy { it.title.lowercase() },
                    false, hasPermission(), error)
            }
            _state.value = result.copy(saved = resolveSaved(result.tracks))
        }
    }

    /**
     * Stream ids stored by favourites and playlists, turned back into queue-ready tracks.
     *
     * Resolved against [remote], so a saved stream survives a restart with its title, artist and
     * cover. An id the catalog has forgotten is simply left out: showing a row called "yandex:1234"
     * would be worse than not showing it.
     */
    private fun resolveSaved(local: List<Track>): List<Track> {
        val catalog = remote ?: return emptyList()
        val ids = linkedSetOf<String>()
        ids += savedStreamIds
        return ids.mapNotNull { catalog.resolve(it.substringAfter("yandex:"))?.toTrack() }
            .filter { t -> local.none { it.id == t.id } }
    }

    /** Stream ids the user put into a playlist or into favourites; the owner writes this set. */
    private val savedStreamIds = linkedSetOf<String>()

    fun rememberSavedStream(ids: Collection<String>) {
        savedStreamIds.clear()
        savedStreamIds += ids.filter { it.startsWith("yandex:") }
        _state.update { it.copy(saved = resolveSaved(it.tracks)) }
    }

    private fun refreshRemote() {
        _state.update { it.copy(saved = resolveSaved(it.tracks)) }
    }

    /** Rebuilds [LibraryState.saved] after the catalog learned a new track. */
    fun refreshStreams() = refreshRemote()

    /**
     * Adds a track to the *local* library by reference — the build-time catalogue row the release
     * build replaces with nothing.
     *
     * Only the Debug source set calls this: it seeds a searchable, playable "catalog" track so the
     * instrumented suite can prove search, playback and saving for a stream without a network. It is
     * deliberately a *file* uri rather than a `neptune://` handle, so the test exercises the real
     * search/save/queue path and not the signing code, which is covered by unit tests.
     */
    fun seedSynthetic(track: Track) {
        if (_state.value.tracks.any { it.id == track.id }) return
        _state.update { it.copy(tracks = (it.tracks + track).sortedBy { t -> t.title.lowercase() }) }
    }

    private fun scanDevice(): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            while (c.moveToNext()) {
                val mediaId = c.getLong(id)
                val artId = c.getLong(albumId)
                tracks += Track("device:$mediaId", c.getString(title) ?: "Без названия",
                    c.getString(artist)?.takeUnless { it == "<unknown>" || it.isBlank() } ?: "Неизвестный исполнитель",
                    c.getString(album)?.ifBlank { "Без альбома" } ?: "Без альбома", c.getLong(duration),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString(),
                    if (artId > 0) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), artId).toString() else null,
                    Track.Source.DEVICE, c.getInt(year).takeIf { it > 0 }, mediaId.toInt())
            }
        }
        return tracks
    }

    private fun importedUris(): List<String> = runCatching {
        val a = JSONArray(prefs.getString("imports", "[]") ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun readImported(uri: Uri): Track {
        val meta = MediaMetadataRetriever()
        try {
            meta.setDataSource(context, uri)
            fun value(key: Int) = meta.extractMetadata(key)
            return Track("import:${uri}", value(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Без названия",
                value(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null } ?: "Локальный файл",
                value(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.ifBlank { null } ?: "Импортированная музыка",
                value(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                uri.toString(), artworkUri = extractCover(uri, meta.embeddedPicture),
                source = Track.Source.IMPORTED, artSeed = uri.hashCode())
        } finally { meta.release() }
    }

    /**
     * Persists embedded cover art next to the cache and returns a `file://` URI for it.
     *
     * The bytes have to outlive the picker's transient read grant: a `content://` artwork URI stops
     * resolving once the grant lapses, which is exactly when the media notification tries to load
     * it. A cached file keeps the cover available to Coil, the notification and the lock screen.
     */
    private fun extractCover(uri: Uri, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val dir = java.io.File(context.cacheDir, "covers").apply { mkdirs() }
            val file = java.io.File(dir, "${uri.toString().hashCode().toUInt().toString(16)}.img")
            if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    /**
     * Removes a track from the app *without touching the file*.
     *
     * An imported track exists only as a stored `content://` URI, so forgetting it means dropping
     * that URI and releasing the persistable grant — the user's file stays where it is. A device
     * track cannot be hidden from a MediaStore scan, so it is left alone; the UI explains that
     * instead of pretending the deletion worked.
     */
    fun forget(track: Track): Boolean {
        // A stream is not stored here at all: it is in favourites or a playlist, and "убрать из
        // приложения" would have to edit that list. Returning false keeps the two paths honest
        // instead of pretending a file was released.
        if (track.source != Track.Source.IMPORTED) return false
        val existing = importedUris().toMutableSet()
        if (!existing.remove(track.uri)) return false
        prefs.edit().putString("imports", JSONArray().apply { existing.forEach { put(it) } }.toString()).apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(track.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        refresh()
        return true
    }

    fun importUris(uris: List<Uri>, onResult: (Int) -> Unit) {
        val existing = importedUris().toMutableSet()
        var added = 0
        uris.forEach {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (existing.add(it.toString())) added++
        }
        prefs.edit().putString("imports", JSONArray(existing.toList()).toString()).apply()
        refresh()
        onResult(added)
    }
}
