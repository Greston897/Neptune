package com.neptune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun SearchScreen(state: NeptuneState, onCollection: (String, String) -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }
    val results = remember(state.search, library.tracks) { state.search(library.tracks) }
    val albums = remember(results) { LibraryLogic.albums(results) }
    val artists = remember(results) { LibraryLogic.artists(results) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md + LocalTopInset.current, bottom = LocalBottomInset.current)) {
        item { NeText("Поиск", Modifier.padding(vertical = NeSpace.sm), size = NeType.headline, lineHeight = NeType.headlineLine, weight = FontWeight.ExtraBold, tracking = (-0.6).sp) }
        item {
            GlassPanel(Modifier.fillMaxWidth().heightIn(min = NeRow.field)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeIconView(NeIcon.Search, NeIconSize.lg, Ne.Dim)
                    Spacer(Modifier.width(NeSpace.md))
                    // The field's own text style must match NeText's line box, otherwise the typed
                    // text and the placeholder sit on different baselines inside the same row.
                    BasicTextField(
                        value = state.search,
                        onValueChange = { state.search = it.take(100) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = neFieldTextStyle(),
                        cursorBrush = SolidColor(accent()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { state.submitSearch() }),
                        decorationBox = { field ->
                            Box(Modifier.heightIn(min = NeIconSize.lg), contentAlignment = Alignment.CenterStart) {
                                if (state.search.isEmpty()) {
                                    NeText(
                                        "Трек, исполнитель или альбом", size = NeType.body,
                                        lineHeight = NeType.bodyLine, color = Ne.Faint,
                                    )
                                }
                                field()
                            }
                        },
                    )
                    if (state.search.isNotEmpty()) {
                        Spacer(Modifier.width(NeSpace.xs))
                        NeIconButton(
                            NeIcon.Close, "Очистить поиск", { state.search = "" },
                            size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim,
                        )
                    }
                }
            }
        }
        if (state.search.isBlank()) {
            if (users.searches.isNotEmpty()) {
                item { SectionHeader("Недавние запросы", action = "Очистить", onAction = state::clearSearches) }
                items(users.searches) { q ->
                    Row(Modifier.fillMaxWidth().heightIn(min = NeTouch.standard).clickable { state.search = q }.padding(vertical = NeSpace.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        NeIconView(NeIcon.Clock, NeIconSize.sm, Ne.Faint); Spacer(Modifier.width(NeSpace.md))
                        NeText(q, Modifier.weight(1f), size = NeType.label, lineHeight = NeType.labelLine, color = Ne.Dim, maxLines = 1)
                        Spacer(Modifier.width(NeSpace.sm)); NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
                    }
                }
            }
            item { SectionHeader("Альбомы", Modifier.padding(top = NeSpace.lg)) }
            items(LibraryLogic.albums(library.tracks).chunked(2)) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.sm),
                    horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                    verticalAlignment = Alignment.Top) {
                    row.forEach { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            // The filter row scrolls horizontally: four chips previously overflowed a 360dp
            // screen and the last one was clipped mid-label.
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = NeSpace.lg),
                    horizontalArrangement = Arrangement.spacedBy(NeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("Всё", "Треки", "Альбомы", "Артисты").forEachIndexed { i, title ->
                        NeChip(title, filter == i, { filter = i; state.submitSearch() })
                    }
                }
            }
            if (results.isEmpty()) item { EmptyState(NeIcon.Search, "Ничего не найдено", "Попробуйте другое название или имя исполнителя. Поиск работает по всей вашей медиатеке.", action = "Сбросить", onAction = { state.search = "" }) }
            if (filter == 0 || filter == 1) items(results, key = { ListKeys.track(it.id) }) { track -> TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, results); state.submitSearch() }, onMore = { state.contextTrack = track }) }
            if (filter == 0 || filter == 2) items(albums.chunked(2), key = { ListKeys.album(it.first().first.first, it.first().first.second) }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.sm),
                    horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                    verticalAlignment = Alignment.Top) {
                    row.forEach { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (filter == 0 || filter == 3) items(artists, key = { ListKeys.artist(it.first) }) { (artist, tracks) ->
                Panel(Modifier.padding(vertical = NeSpace.xs)) { SettingRow(artist, trackCount(tracks.size), NeIcon.Artist, onClick = { onCollection("artist", artist) }) }
            }
        }
    }
}
