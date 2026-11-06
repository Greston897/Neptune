package com.neptune.player.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.DemoTracks
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.LiquidGlass
import com.neptune.player.ui.Manrope
import com.neptune.player.ui.Ne
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconView
import com.neptune.player.ui.NeText
import com.neptune.player.ui.rememberHaptics

@Composable
fun SearchScreen(state: NeptuneState, modifier: Modifier = Modifier) {
    val accent = state.settings.accent.color
    val haptic = rememberHaptics()
    val query = state.search.trim().lowercase()
    val results = remember(query) {
        if (query.isEmpty()) emptyList()
        else state.tracks.filter {
            it.title.lowercase().contains(query) ||
                it.artist.lowercase().contains(query) ||
                it.album.lowercase().contains(query)
        }
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                NeText("Поиск", size = 30.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp)
                Spacer(Modifier.height(4.dp))
                NeText(
                    text = "Демо-каталог Neptune",
                    size = 11.5.sp,
                    weight = FontWeight.SemiBold,
                    color = Ne.Faint,
                    tracking = 0.1.sp,
                )
            }
        }

        item { SearchField(state, accent) }

        item { Spacer(Modifier.height(4.dp)) }

        if (query.isEmpty()) {
            item {
                NeText(
                    "Жанры",
                    size = 10.sp,
                    weight = FontWeight.ExtraBold,
                    color = Ne.Faint,
                    tracking = 2.2.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(DemoTracks.genres.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { genre ->
                        LiquidGlass(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { haptic(); state.search = genre },
                            shape = CircleShape,
                        ) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                NeText(genre, size = 12.5.sp, weight = FontWeight.Bold)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                NeText(
                    "Недавнее",
                    size = 10.sp,
                    weight = FontWeight.ExtraBold,
                    color = Ne.Faint,
                    tracking = 2.2.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(state.tracks.take(3)) { track ->
                TrackRow(
                    track = track,
                    playing = track.id == state.current.id,
                    isPlaying = state.isPlaying,
                    onClick = { haptic(); state.play(track.id) },
                )
            }
        } else if (results.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NeIconView(NeIcon.Search, size = 26.dp, color = Ne.Whisper)
                    Spacer(Modifier.height(12.dp))
                    NeText("Ничего не найдено", size = 14.sp, weight = FontWeight.Bold, color = Ne.Dim)
                    Spacer(Modifier.height(4.dp))
                    NeText(
                        "Попробуйте «неон» или «KAIRO»",
                        size = 11.5.sp,
                        weight = FontWeight.Medium,
                        color = Ne.Faint,
                        tracking = 0.sp,
                    )
                }
            }
        } else {
            item {
                NeText(
                    "Результаты · ${results.size}",
                    size = 10.sp,
                    weight = FontWeight.ExtraBold,
                    color = Ne.Faint,
                    tracking = 2.2.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(results) { track ->
                TrackRow(
                    track = track,
                    playing = track.id == state.current.id,
                    isPlaying = state.isPlaying,
                    onClick = { haptic(); state.play(track.id) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(state: NeptuneState, accent: androidx.compose.ui.graphics.Color) {
    LiquidGlass(Modifier.fillMaxWidth().height(54.dp), shape = CircleShape, highlight = 1.2f) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeIconView(NeIcon.Search, size = 18.dp, color = Ne.Faint)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (state.search.isEmpty()) {
                    NeText(
                        "Трек, артист или альбом",
                        size = 13.sp,
                        weight = FontWeight.Medium,
                        color = Ne.Whisper,
                        tracking = 0.sp,
                    )
                }
                BasicTextField(
                    value = state.search,
                    onValueChange = { state.search = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Ne.Text,
                        fontSize = 13.5.sp,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.search.isNotEmpty()) {
                NeIconView(
                    NeIcon.Close,
                    size = 13.dp,
                    color = Ne.Faint,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.search = "" }
                        .padding(8.dp),
                )
            }
        }
    }
}
