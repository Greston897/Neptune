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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
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
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = NeRow.header), verticalAlignment = Alignment.CenterVertically) {
                NeText("Медиатека", Modifier.weight(1f), size = NeType.display, lineHeight = NeType.displayLine, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 1)
                NeIconButton(NeIcon.Plus, "Создать плейлист", { create = true })
                androidx.compose.foundation.layout.Box {
                    NeIconButton(NeIcon.Sort, "Сортировка", { sortMenu = true }, color = Ne.Dim)
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = Ne.Elevated) {
                        listOf("По названию", "По исполнителю", "По длительности").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { NeText(label, size = NeType.label, lineHeight = NeType.labelLine) }, onClick = { state.librarySort = i; sortMenu = false },
                                trailingIcon = { if (state.librarySort == i) NeIconView(NeIcon.Check, NeIconSize.sm, accent()) })
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = NeSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(NeSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Треки", "Альбомы", "Артисты", "Плейлисты").forEachIndexed { i, title -> NeChip(title, state.libraryTab == i, { state.libraryTab = i }) }
            }
        }
        if (!library.permission) item {
            Panel(Modifier.padding(vertical = 10.dp)) {
                SettingRow("Музыка с телефона", "Разрешите доступ, чтобы собрать вашу медиатеку", icon = NeIcon.Device, onClick = actions.permission,
                    trailing = { NeText("Добавить", size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.Bold) })
            }
        }
        when (state.libraryTab) {
            0 -> {
                item { Row(Modifier.fillMaxWidth().padding(top = NeSpace.sm, bottom = NeSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    NeText(trackCount(tracks.size), Modifier.weight(1f), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 1)
                    NeButton("Слушать всё", { state.play(tracks) }, icon = NeIcon.Play)
                } }
                items(tracks, key = { it.id }) { track -> TrackRow(track, playback.track?.id == track.id, playback.isPlaying,
                    { state.playTrack(track, tracks) }, onMore = { state.contextTrack = track }) }
            }
            1 -> {
                val albums = LibraryLogic.albums(tracks)
                items(albums.chunked(2), key = { it.first().first.second + it.first().first.first }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                        verticalAlignment = Alignment.Top) {
                        row.forEach { (meta, list) -> AlbumCard(meta.second, meta.first, list.first(),
                            { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            2 -> items(LibraryLogic.artists(tracks), key = { it.first }) { (artist, list) ->
                Row(Modifier.fillMaxWidth().heightIn(min = NeRow.compact).clip(RoundedCornerShape(12.dp))
                    .clickable { onCollection("artist", artist) }.padding(vertical = NeSpace.sm, horizontal = NeSpace.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    NeptuneMark(Modifier.size(NeRow.thumbCompact), animated = false)
                    Spacer(Modifier.width(NeSpace.md))
                    Column(Modifier.weight(1f)) {
                        NeText(artist, size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.height(NeSpace.xxs))
                        NeText(trackCount(list.size), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                    }
                    Spacer(Modifier.width(NeSpace.sm)); NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
                }
            }
            else -> {
                item { CollectionLink("Любимые треки", trackCount(users.liked.size), NeIcon.HeartFill) { onCollection("liked", "") } }
                items(users.playlists, key = { it.id }) { p -> CollectionLink(p.name, trackCount(p.trackIds.size), NeIcon.Music) { onCollection("playlist", p.id) } }
                item { NeButton("Новый плейлист", { create = true }, Modifier.fillMaxWidth().padding(top = NeSpace.lg), NeIcon.Plus, primary = false) }
            }
        }
        if (library.loading) item { NeText("Обновляем медиатеку…", Modifier.padding(vertical = NeSpace.xl), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim) }
        library.error?.let { error -> item { NeText(error, Modifier.padding(vertical = NeSpace.lg), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Error, maxLines = 3) } }
    }
    if (create) NameDialog("Новый плейлист", "Название", "", { create = false }) {
        val p = state.createPlaylist(it); create = false; onCollection("playlist", p.id)
    }
}

@Composable
private fun CollectionLink(title: String, subtitle: String, icon: NeIcon, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = NeRow.compact).clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = NeSpace.sm, horizontal = NeSpace.xs),
        verticalAlignment = Alignment.CenterVertically) {
        GlassPanel(Modifier.size(NeRow.thumb), corner = 12.dp) {
            NeIconView(icon, NeIconSize.lg, accent(), modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            NeText(title, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(NeSpace.xxs))
            NeText(subtitle, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
        }
        Spacer(Modifier.width(NeSpace.sm)); NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
    }
}
