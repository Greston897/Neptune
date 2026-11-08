package com.neptune.player.playback

import com.neptune.player.model.PlaybackState
import com.neptune.player.model.Track
import kotlinx.coroutines.flow.StateFlow

/** The only playback boundary used by the frontend. No simulated clock or fake transport. */
interface PlaybackBackend {
    val state: StateFlow<PlaybackState>
    fun play(tracks: List<Track>, index: Int = 0, shuffled: Boolean = false)
    fun toggle()
    fun next()
    fun previous()
    fun seek(fraction: Float)
    fun setVolume(volume: Float)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playNext(track: Track)
    fun enqueue(track: Track)
    fun jumpTo(index: Int)
    fun move(from: Int, to: Int)
    fun remove(index: Int)
    fun clear()
    fun setSleepTimer(minutes: Int)
    fun retry()
    fun persist()
}
