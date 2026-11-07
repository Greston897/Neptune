package com.neptune.player.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun QueueScreen(state: NeptuneState, onBack: () -> Unit) {
    val playback by state.playback.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = LocalBottomInset.current)) {
        item { Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(NeIcon.Back, "Назад", onBack)
            NeText("Очередь", Modifier.weight(1f).padding(start = 8.dp), size = 26.sp, weight = FontWeight.ExtraBold)
            NeIconButton(NeIcon.Edit, if (editing) "Завершить изменение порядка" else "Изменить порядок", { editing = !editing }, selected = editing, enabled = playback.queue.isNotEmpty())
            NeIconButton(NeIcon.Trash, "Очистить очередь", { confirm = true }, color = Ne.Dim, enabled = playback.queue.isNotEmpty())
        } }
        item { NeText(if (editing) "Стрелки меняют порядок. Нажмите на трек, чтобы включить его." else "${trackCount(playback.queue.size)} · следующий трек всегда под рукой", Modifier.padding(vertical = 14.dp), size = 12.sp, color = Ne.Dim, maxLines = 2) }
        itemsIndexed(playback.queue, key = { index, track -> "${track.id}-$index" }) { index, track ->
            TrackRow(track, index == playback.queueIndex, playback.isPlaying, { state.playbackJump(index) },
                subtitle = if (index == playback.queueIndex) "Сейчас играет · ${track.artist}" else track.artist,
                trailing = {
                    if (editing) Row {
                        NeIconButton(NeIcon.ArrowUp, "Выше", { state.moveQueue(index, index - 1) }, size = 36.dp, iconSize = 16.dp, enabled = index > 0)
                        NeIconButton(NeIcon.ArrowDown, "Ниже", { state.moveQueue(index, index + 1) }, size = 36.dp, iconSize = 16.dp, enabled = index < playback.queue.lastIndex)
                        NeIconButton(NeIcon.Close, "Убрать из очереди", { state.removeQueue(index) }, size = 36.dp, iconSize = 16.dp, color = Ne.Dim)
                    } else NeIconButton(NeIcon.More, "Действия с треком", { state.contextTrack = track }, size = 40.dp, iconSize = 18.dp, color = Ne.Dim)
                })
        }
        if (playback.queue.isEmpty()) item { EmptyState(NeIcon.Queue, "Очередь свободна", "Включите альбом или добавьте треки через меню «В очередь».") }
    }
    if (confirm) ConfirmDialog("Очистить очередь?", "Воспроизведение остановится. Любимые треки и плейлисты останутся на месте.", "Очистить", { confirm = false }) { state.clearQueue(); confirm = false }
}
