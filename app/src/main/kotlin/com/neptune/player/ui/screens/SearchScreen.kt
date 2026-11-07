package com.neptune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
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
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = LocalBottomInset.current)) {
        item { NeText("Поиск", Modifier.padding(vertical = 14.dp), size = 30.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp) }
        item {
            GlassPanel(Modifier.fillMaxWidth().height(54.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NeIconView(NeIcon.Search, 20.dp, Ne.Dim); Spacer(Modifier.width(12.dp))
                BasicTextField(state.search, { state.search = it.take(100) }, Modifier.weight(1f), singleLine = true,
                    textStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ne.Text),
                    cursorBrush = SolidColor(accent()), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { state.submitSearch() }),
                    decorationBox = { field -> if (state.search.isEmpty()) NeText("Трек, исполнитель или альбом", size = 13.sp, color = Ne.Faint); field() })
                if (state.search.isNotEmpty()) NeIconButton(NeIcon.Close, "Очистить поиск", { state.search = "" }, size = 40.dp, iconSize = 16.dp, color = Ne.Dim)
            }
            }
        }
        if (state.search.isBlank()) {
            if (users.searches.isNotEmpty()) {
                item { SectionHeader("Недавние запросы", action = "Очистить", onAction = state::clearSearches) }
                items(users.searches) { q -> Row(Modifier.fillMaxWidth().clickable { state.search = q }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeIconView(NeIcon.Clock, 18.dp, Ne.Faint); Spacer(Modifier.width(12.dp)); NeText(q, Modifier.weight(1f), size = 13.sp, color = Ne.Dim); NeIconView(NeIcon.ChevronRight, 15.dp, Ne.Whisper)
                } }
            }
            item { SectionHeader("Исследуй альбомы", Modifier.padding(top = 18.dp)) }
            items(LibraryLogic.albums(library.tracks).chunked(2)) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            item { Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Всё", "Треки", "Альбомы", "Артисты").forEachIndexed { i, title -> NeChip(title, filter == i, { filter = i; state.submitSearch() }) }
            } }
            if (results.isEmpty()) item { EmptyState(NeIcon.Search, "Ничего не найдено", "Попробуйте другое название или имя исполнителя. Поиск работает по всей вашей медиатеке.", action = "Сбросить", onAction = { state.search = "" }) }
            if (filter == 0 || filter == 1) items(results, key = { it.id }) { track -> TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, results); state.submitSearch() }, onMore = { state.contextTrack = track }) }
            if (filter == 0 || filter == 2) items(albums) { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            if (filter == 0 || filter == 3) items(artists) { (artist, tracks) -> SettingRow(artist, "${tracks.size} треков", NeIcon.Artist, onClick = { onCollection("artist", artist) }) }
        }
    }
}
