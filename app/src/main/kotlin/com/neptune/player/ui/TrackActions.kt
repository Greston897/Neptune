package com.neptune.player.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.Track
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.screens.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(state: NeptuneState, track: Track, onDismiss: () -> Unit, onCollection: (String, String) -> Unit) {
    val users by state.users.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var playlistPicker by remember { mutableStateOf(false) }
    var createPlaylist by remember { mutableStateOf(false) }
    val close: () -> Unit = { state.contextPlaylist = null; onDismiss() }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ne.Ink, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackThumb(track, NeRow.thumb, 10.dp); Spacer(Modifier.width(NeSpace.md))
                Column(Modifier.weight(1f)) {
                    NeText(track.title, size = NeType.section, lineHeight = NeType.sectionLine, weight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(NeSpace.xs))
                    NeText(track.artist, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                }
            }
            SettingRow(if (track.id in users.liked) "Убрать из любимого" else "В любимое", icon = if (track.id in users.liked) NeIcon.HeartFill else NeIcon.Heart) { state.like(track); close() }
            SettingRow("Играть следующим", icon = NeIcon.Next) { state.playNext(track); close() }
            SettingRow("В очередь", icon = NeIcon.Queue) { state.enqueue(track); close() }
            SettingRow("В плейлист", icon = NeIcon.Music) { playlistPicker = true }
            SettingRow("Об альбоме", track.album, NeIcon.Disc) { close(); onCollection("album", "${track.artist}\u0000${track.album}") }
            SettingRow("Все треки исполнителя", track.artist, NeIcon.Artist) { close(); onCollection("artist", track.artist) }
            // Capture the playlist during composition instead of asserting non-null in the handler.
            // `if (state.contextPlaylist != null) ... { state.contextPlaylist!! }` checks mutable
            // state at composition and dereferences it later at click time: close() nulls it, so any
            // click delivered against the outgoing composition would throw. Every dismiss path does
            // null it today, which makes this latent rather than reproducible — but the guarantee
            // should come from the type, not from every caller remembering to clear the field.
            // A local val smart-casts, so the handler cannot see a value the row was not built for.
            val ownerPlaylist = state.contextPlaylist
            if (ownerPlaylist != null) SettingRow("Убрать из этого плейлиста", icon = NeIcon.Trash) { state.removeFromPlaylist(ownerPlaylist, track); close() }
            SettingRow("Поделиться треком", "Название и исполнитель", NeIcon.Share) {
                val send = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}"), "Поделиться музыкой")
                context.startActivity(send); close()
            }
        }
    }
    if (playlistPicker) AlertDialog(onDismissRequest = { playlistPicker = false }, containerColor = Ne.Elevated,
        title = { NeText("В плейлист", size = NeType.title, lineHeight = NeType.titleLine, weight = FontWeight.Bold) },
        text = { Column {
            SettingRow("Новый плейлист", icon = NeIcon.Plus) { playlistPicker = false; createPlaylist = true }
            users.playlists.forEach { p -> SettingRow(p.name, trackCount(p.trackIds.size), if (track.id in p.trackIds) NeIcon.Check else NeIcon.Music) {
                state.addToPlaylist(p.id, track); playlistPicker = false; close()
            } }
            if (users.playlists.isEmpty()) NeText("Создай первый плейлист — он появится здесь.", Modifier.padding(NeSpace.lg), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 3)
        } }, confirmButton = {})
    if (createPlaylist) NameDialog("Новый плейлист", "Название", "", { createPlaylist = false }) {
        val p = state.createPlaylist(it); state.addToPlaylist(p.id, track); createPlaylist = false; close()
    }
}
