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
) {
    val byId get() = tracks.associateBy { it.id }
    val localCount get() = tracks.count { it.isLocal }
}

/** Real local audio sources. The bundled PCM sessions are produced by the audio backend, not the UI. */
class LibraryRepository(private val context: Context, private val scope: CoroutineScope) {
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
                val tracks = DemoSource.tracks(context).toMutableList()
                var error: String? = null
                if (hasPermission()) runCatching { tracks += scanDevice() }
                    .onFailure { error = "Не удалось прочитать музыку на устройстве" }
                importedUris().forEach { raw ->
                    runCatching {
                        val uri = Uri.parse(raw)
                        if (tracks.none { it.uri == raw }) tracks += readImported(uri)
                    }.onFailure { error = "Часть файлов недоступна. Остальная музыка загружена." }
                }
                LibraryState(tracks.distinctBy { it.id }.sortedWith(compareBy({ !it.isLocal }, { it.title.lowercase() })),
                    false, hasPermission(), error)
            }
            _state.value = result
        }
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
                uri.toString(), source = Track.Source.IMPORTED, artSeed = uri.hashCode())
        } finally { meta.release() }
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

object DemoSource {
    private data class Session(val file: String, val title: String, val album: String, val seconds: Int, val seed: Int)
    private val sessions = listOf(
        Session("afterhours", "After Hours", "Ночные сессии", 36, 0),
        Session("weightless", "Невесомость", "Ночные сессии", 36, 1),
        Session("north", "Северный свет", "Тихие координаты", 36, 2),
        Session("slowmotion", "Замедление", "Тихие координаты", 36, 3),
        Session("orbit", "Малая орбита", "Между станциями", 36, 4),
        Session("home", "Почти дома", "Между станциями", 36, 5),
    )
    fun tracks(context: Context): List<Track> = sessions.map {
        Track("demo:${it.file}", it.title, "Neptune Studio", it.album, it.seconds * 1000L,
            "asset:///audio/${it.file}.m4a", source = Track.Source.DEMO, year = 2026, artSeed = it.seed)
    }
}
