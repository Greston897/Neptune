package com.neptune.player.state

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neptune.player.NeptuneApplication
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.Settings
import com.neptune.player.model.Track
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** UI coordinator. Playback, metadata and persistence live behind repository boundaries. */
class NeptuneState(application: Application) : AndroidViewModel(application) {
    private val container = (application as NeptuneApplication).container
    val settings = container.settings.state
    val users = container.users.state
    val library = container.library.state
    val playback = container.playback.state
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    var search by mutableStateOf("")
    var libraryTab by mutableStateOf(0)
    var librarySort by mutableStateOf(0)
    var contextTrack by mutableStateOf<Track?>(null)
    var contextPlaylist by mutableStateOf<String?>(null)

    fun edit(block: (Settings) -> Settings) = container.settings.edit(block)
    fun resetAppearance() { edit { it.resetAppearance() }; message("Внешний вид восстановлен") }
    fun refreshLibrary() = container.library.refresh()
    fun hasAudioPermission() = container.library.hasPermission()
    fun message(text: String) { viewModelScope.launch { _messages.send(text) } }
    fun play(tracks: List<Track>, index: Int = 0, shuffled: Boolean = false) {
        if (tracks.isEmpty()) { message("Сначала добавьте музыку в медиатеку"); return }
        container.playback.play(tracks, index, shuffled)
    }
    fun playTrack(track: Track, context: List<Track>) {
        val queue = if (context.any { it.id == track.id }) context else listOf(track)
        play(queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }
    fun togglePlayback() = container.playback.toggle()
    fun nextTrack() = container.playback.next()
    fun previousTrack() = container.playback.previous()
    fun toggleShuffle() = container.playback.toggleShuffle()
    fun cycleRepeat() = container.playback.cycleRepeat()
    fun setVolume(v: Float) = container.playback.setVolume(v)
    fun setSleepTimer(minutes: Int) = container.playback.setSleepTimer(minutes)
    fun retryPlayback() = container.playback.retry()
    fun seek(fraction: Float) = container.playback.seek(fraction)
    fun like(track: Track) = container.users.toggleLike(track.id)
    fun submitSearch() = container.users.rememberSearch(search)
    fun clearSearches() = container.users.clearSearches()
    fun clearHistory() { container.users.clearHistory(); message("История прослушивания очищена") }
    fun createPlaylist(name: String) = container.users.createPlaylist(name).also { message("Плейлист «${it.name}» создан") }
    fun renamePlaylist(id: String, name: String) = container.users.renamePlaylist(id, name)
    fun deletePlaylist(id: String) { container.users.deletePlaylist(id); message("Плейлист удалён") }
    fun addToPlaylist(id: String, track: Track) { container.users.addToPlaylist(id, track.id); message("Добавлено в плейлист") }
    fun removeFromPlaylist(id: String, track: Track) = container.users.removeFromPlaylist(id, track.id)
    fun playNext(track: Track) { container.playback.playNext(track); message("Будет играть следующим") }
    fun enqueue(track: Track) { container.playback.enqueue(track); message("Добавлено в очередь") }
    fun sorted(tracks: List<Track>) = when (librarySort) {
        1 -> tracks.sortedBy { it.artist.lowercase() }
        2 -> tracks.sortedByDescending { it.durationMs }
        else -> tracks.sortedBy { it.title.lowercase() }
    }
    fun search(tracks: List<Track>) = LibraryLogic.search(tracks, search)
    fun playbackJump(index: Int) = container.playback.jumpTo(index)
    fun moveQueue(from: Int, to: Int) = container.playback.move(from, to)
    fun removeQueue(index: Int) = container.playback.remove(index)
    fun clearQueue() = container.playback.clear()
    fun persist() = container.playback.persist()
    override fun onCleared() { persist(); super.onCleared() }
}
