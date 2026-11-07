package com.neptune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.Track
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun LibraryScreen(state: NeptuneState, onCollection: (String, String) -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val actions = LocalLibraryActions.current
    var create by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val tracks = remember(library.tracks, state.librarySort) { state.sorted(library.tracks) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                NeText("Медиатека", Modifier.weight(1f), size = 30.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp)
                NeIconButton(NeIcon.Plus, "Создать плейлист", { create = true })
                androidx.compose.foundation.layout.Box {
                    NeIconButton(NeIcon.Sort, "Сортировка", { sortMenu = true }, color = Ne.Dim)
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = Ne.Elevated) {
                        listOf("По названию", "По исполнителю", "По длительности").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { NeText(label, size = 13.sp) }, onClick = { state.librarySort = i; sortMenu = false },
                                trailingIcon = { if (state.librarySort == i) NeIconView(NeIcon.Check, 17.dp, accent()) })
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Треки", "Альбомы", "Артисты", "Плейлисты").forEachIndexed { i, title -> NeChip(title, state.libraryTab == i, { state.libraryTab = i }) }
            }
        }
        if (!library.permission) item {
            Panel(Modifier.padding(vertical = 10.dp)) {
                SettingRow("Музыка с телефона", "Разрешите доступ, чтобы собрать вашу медиатеку", icon = NeIcon.Device, onClick = actions.permission,
                    trailing = { NeText("Добавить", size = 11.sp, color = accent()) })
            }
        }
        when (state.libraryTab) {
            0 -> {
                item { Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeText(trackCount(tracks.size), Modifier.weight(1f), size = 11.sp, color = Ne.Faint)
                    NeButton("Слушать всё", { state.play(tracks) }, icon = NeIcon.Play)
                } }
                items(tracks, key = { it.id }) { track -> TrackRow(track, playback.track?.id == track.id, playback.isPlaying,
                    { state.playTrack(track, tracks) }, onMore = { state.contextTrack = track }) }
            }
            1 -> {
                val albums = LibraryLogic.albums(tracks)
                items(albums.chunked(2), key = { it.first().first.second + it.first().first.first }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (meta, list) -> AlbumCard(meta.second, meta.first, list.first(),
                            { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            2 -> items(LibraryLogic.artists(tracks), key = { it.first }) { (artist, list) ->
                Row(Modifier.fillMaxWidth().clickable { onCollection("artist", artist) }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeptuneMark(Modifier.size(48.dp), animated = false)
                    Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { NeText(artist, size = 15.sp, weight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); NeText(trackCount(list.size), size = 11.sp, color = Ne.Dim) }
                    NeIconView(NeIcon.ChevronRight, 17.dp, Ne.Faint)
                }
            }
            else -> {
                item { CollectionLink("Любимые треки", trackCount(users.liked.size), NeIcon.HeartFill) { onCollection("liked", "") } }
                items(users.playlists, key = { it.id }) { p -> CollectionLink(p.name, trackCount(p.trackIds.size), NeIcon.Music) { onCollection("playlist", p.id) } }
                item { NeButton("Новый плейлист", { create = true }, Modifier.fillMaxWidth().padding(top = 18.dp), NeIcon.Plus, primary = false) }
            }
        }
        if (library.loading) item { NeText("Обновляем медиатеку…", Modifier.padding(vertical = 20.dp), size = 12.sp, color = Ne.Dim) }
        library.error?.let { error -> item { NeText(error, Modifier.padding(vertical = 16.dp), size = 12.sp, color = Ne.Error, maxLines = 3) } }
    }
    if (create) NameDialog("Новый плейлист", "Название", "", { create = false }) {
        val p = state.createPlaylist(it); create = false; onCollection("playlist", p.id)
    }
}

@Composable
private fun CollectionLink(title: String, subtitle: String, icon: NeIcon, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassPanel(Modifier.size(54.dp), corner = neRadius()) { NeIconView(icon, 23.dp, accent(), modifier = Modifier.align(Alignment.Center)) }
        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { NeText(title, size = 14.sp, weight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); NeText(subtitle, size = 11.sp, color = Ne.Dim) }
        NeIconView(NeIcon.ChevronRight, 17.dp, Ne.Faint)
    }
}
