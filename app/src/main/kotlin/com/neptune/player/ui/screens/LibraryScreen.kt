package com.neptune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.DemoTracks
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.ArtistAvatar
import com.neptune.player.ui.EqualizerBars
import com.neptune.player.ui.GlassSegments
import com.neptune.player.ui.LiquidGlass
import com.neptune.player.ui.Ne
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconView
import com.neptune.player.ui.NeText
import com.neptune.player.ui.NeptuneChip
import com.neptune.player.ui.TrackArtwork
import com.neptune.player.ui.TrackThumb
import com.neptune.player.ui.rememberHaptics

@Composable
fun LibraryScreen(state: NeptuneState, modifier: Modifier = Modifier) {
    val accent = state.settings.accent.color
    val haptic = rememberHaptics()
    val tabs = listOf("Треки", "Альбомы", "Артисты")

    LazyColumn(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    NeText("Медиатека", size = 30.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp)
                    Spacer(Modifier.height(4.dp))
                    NeText(
                        text = "${state.tracks.size} трека · демо-библиотека",
                        size = 11.5.sp,
                        weight = FontWeight.SemiBold,
                        color = Ne.Faint,
                        tracking = 0.1.sp,
                    )
                }
                NeptuneChip("FLAC")
            }
        }

        item {
            GlassSegments(
                items = tabs,
                selected = state.libraryTab,
                onSelect = { haptic(); state.libraryTab = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Spacer(Modifier.height(2.dp)) }

        when (state.libraryTab) {
            0 -> items(state.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    playing = track.id == state.current.id,
                    isPlaying = state.isPlaying,
                    onClick = { haptic(); state.play(track.id) },
                )
            }

            1 -> items(DemoTracks.albums.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    pair.forEach { (album, list) ->
                        AlbumCard(
                            album = album,
                            artist = list.first().artist,
                            count = list.size,
                            track = list.first(),
                            modifier = Modifier.weight(1f),
                            onClick = { haptic(); state.play(list.first().id) },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            else -> items(DemoTracks.artists, key = { it.first }) { (artist, list) ->
                ArtistRow(
                    name = artist,
                    count = list.size,
                    palette = list.first().palette,
                    onClick = { haptic(); state.play(list.first().id) },
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    playing: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
) {
    val accent = com.neptune.player.ui.LocalGlassStyle.current.accent
    LiquidGlass(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(com.neptune.player.ui.squircle(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = com.neptune.player.ui.squircle(20.dp),
        highlight = if (playing) 1.6f else 1f,
        accentEdge = playing,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumb(track, 46.dp, 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NeText(
                    text = track.title,
                    size = 14.5.sp,
                    weight = FontWeight.Bold,
                    color = if (playing) accent else Ne.Text,
                )
                Spacer(Modifier.height(2.dp))
                NeText(
                    text = "${track.artist} · ${track.album}",
                    size = 11.sp,
                    weight = FontWeight.Medium,
                    color = Ne.Faint,
                    tracking = 0.sp,
                )
            }
            if (playing) {
                EqualizerBars(playing = isPlaying, color = accent, maxHeight = 15.dp)
                Spacer(Modifier.width(12.dp))
            }
            NeText(
                text = formatTime(track.durationMs),
                size = 11.sp,
                weight = FontWeight.Bold,
                color = Ne.Faint,
                tracking = 0.4.sp,
                align = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: String,
    artist: String,
    count: Int,
    track: Track,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LiquidGlass(
        modifier = modifier
            .height(196.dp)
            .clip(com.neptune.player.ui.squircle(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = com.neptune.player.ui.squircle(22.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(9.dp)) {
            TrackArtwork(
                track = track,
                modifier = Modifier.fillMaxWidth().height(116.dp),
                corner = 15.dp,
                animate = false,
            )
            Spacer(Modifier.height(9.dp))
            NeText(album, size = 13.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            NeText(
                text = "$artist · $count",
                size = 10.5.sp,
                weight = FontWeight.Medium,
                color = Ne.Faint,
                tracking = 0.sp,
            )
        }
    }
}

@Composable
private fun ArtistRow(
    name: String,
    count: Int,
    palette: List<Color>,
    onClick: () -> Unit,
) {
    LiquidGlass(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(com.neptune.player.ui.squircle(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = com.neptune.player.ui.squircle(22.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistAvatar(name, palette, 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                NeText(name, size = 14.5.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                NeText(
                    text = "$count ${if (count == 1) "трек" else "трека"}",
                    size = 11.sp,
                    weight = FontWeight.Medium,
                    color = Ne.Faint,
                    tracking = 0.sp,
                )
            }
            NeIconView(NeIcon.ChevronDown, size = 17.dp, color = Ne.Whisper)
        }
    }
}
