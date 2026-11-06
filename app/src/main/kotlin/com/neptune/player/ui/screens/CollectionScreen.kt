package com.neptune.player.ui.screens

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun CollectionScreen(state: NeptuneState, kind: String, key: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val playlist = users.playlists.find { it.id == key }
    val tracks = remember(library.tracks, users, kind, key) {
        val map = library.byId
        when (kind) {
            "liked" -> library.tracks.filter { it.id in users.liked }
            "recent" -> users.recent.mapNotNull(map::get)
            "album" -> library.tracks.filter { "${it.artist}\u0000${it.album}" == key }
            "artist" -> library.tracks.filter { it.artist == key }
            "playlist" -> playlist?.trackIds?.mapNotNull(map::get) ?: emptyList()
            "demo" -> library.tracks.filter { it.source == Track.Source.DEMO }
            else -> emptyList()
        }
    }
    val title = when (kind) { "liked" -> "Любимые треки"; "recent" -> "Недавно слушали"; "album" -> key.substringAfter('\u0000'); "artist" -> key; "playlist" -> playlist?.name ?: "Плейлист"; "demo" -> "Neptune Sessions"; else -> "Подборка" }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item { Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(NeIcon.Back, "Назад", onBack, modifier = Modifier.padding(start = -8.dp))
            NeText("ПОДБОРКА", Modifier.weight(1f).padding(horizontal = 8.dp), size = 10.sp, color = Ne.Dim, tracking = 1.5.sp)
            if (kind == "playlist" && playlist != null) Box {
                NeIconButton(NeIcon.More, "Действия с плейлистом", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Ne.Elevated) {
                    DropdownMenuItem(text = { NeText("Переименовать") }, leadingIcon = { NeIconView(NeIcon.Edit, 19.dp) }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { NeText("Удалить плейлист", color = Ne.Error) }, leadingIcon = { NeIconView(NeIcon.Trash, 19.dp, Ne.Error) }, onClick = { menu = false; delete = true })
                }
            }
        } }
        item { Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (tracks.firstOrNull() != null) TrackArtwork(tracks.first(), Modifier.size(190.dp), neRadius())
            else Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) { NeIconView(if (kind == "liked") NeIcon.Heart else NeIcon.Music, 64.dp, Ne.Whisper) }
            Spacer(Modifier.height(22.dp))
            NeText(title, size = 27.sp, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 2, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            NeText("${trackCount(tracks.size)} · ${formatTime(tracks.sumOf { it.durationMs })}", size = 12.sp, color = Ne.Dim)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeButton("Слушать", { state.play(tracks) }, icon = NeIcon.Play, enabled = tracks.isNotEmpty())
                NeButton("Вперемешку", { state.play(tracks, shuffled = true) }, icon = NeIcon.Shuffle, primary = false, enabled = tracks.isNotEmpty())
            }
        } }
        items(tracks, key = { it.id }) { track -> TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, tracks) },
            onMore = { state.contextTrack = track; state.contextPlaylist = if (kind == "playlist") key else null }) }
        if (tracks.isEmpty()) item { EmptyState(NeIcon.Music, if (kind == "liked") "Здесь будут любимые" else "Пока пусто",
            if (kind == "playlist") "Откройте меню любого трека и выберите «В плейлист»." else "Добавляйте музыку сердечком, и она всегда будет под рукой.") }
    }
    if (rename) NameDialog("Переименовать", "Название", title, { rename = false }) { state.renamePlaylist(key, it); rename = false }
    if (delete) ConfirmDialog("Удалить плейлист?", "Треки останутся в медиатеке. Это действие нельзя отменить.", "Удалить", { delete = false }) { state.deletePlaylist(key); delete = false; onDeleted() }
}
