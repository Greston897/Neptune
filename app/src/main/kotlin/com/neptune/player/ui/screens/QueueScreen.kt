package com.neptune.player.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun QueueScreen(state: NeptuneState, onBack: () -> Unit) {
    val playback by state.playback.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.sm, bottom = LocalBottomInset.current)) {
        item { Row(Modifier.fillMaxWidth().heightIn(min = NeRow.header), verticalAlignment = Alignment.CenterVertically) {
            NeIconButton(NeIcon.Back, "Назад", onBack)
            NeText("Очередь", Modifier.weight(1f).padding(start = NeSpace.sm), size = NeType.headline, lineHeight = NeType.headlineLine, weight = FontWeight.ExtraBold, maxLines = 1)
            NeIconButton(NeIcon.Edit, if (editing) "Завершить изменение порядка" else "Изменить порядок", { editing = !editing }, selected = editing, enabled = playback.queue.isNotEmpty())
            NeIconButton(NeIcon.Trash, "Очистить очередь", { confirm = true }, color = Ne.Dim, enabled = playback.queue.isNotEmpty())
        } }
        item { NeText(if (editing) "Стрелки меняют порядок. Нажмите на трек, чтобы включить его." else "${trackCount(playback.queue.size)} · следующий трек всегда под рукой", Modifier.padding(vertical = NeSpace.md), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2) }
        itemsIndexed(playback.queue, key = { index, track -> ListKeys.queueItem(track.id, index) }) { index, track ->
            TrackRow(track, index == playback.queueIndex, playback.isPlaying, { state.playbackJump(index) },
                subtitle = if (index == playback.queueIndex) "Сейчас играет · ${track.artist}" else track.artist,
                trailing = {
                    if (editing) Row {
                        NeIconButton(NeIcon.ArrowUp, "Выше", { state.moveQueue(index, index - 1) }, size = NeTouch.min, iconSize = NeIconSize.sm, enabled = index > 0)
                        NeIconButton(NeIcon.ArrowDown, "Ниже", { state.moveQueue(index, index + 1) }, size = NeTouch.min, iconSize = NeIconSize.sm, enabled = index < playback.queue.lastIndex)
                        NeIconButton(NeIcon.Close, "Убрать из очереди", { state.removeQueue(index) }, size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim)
                    } else NeIconButton(NeIcon.More, "Действия с треком", { state.contextTrack = track }, size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim)
                })
        }
        if (playback.queue.isEmpty()) item { EmptyState(NeIcon.Queue, "Очередь свободна", "Включите альбом или добавьте треки через меню «В очередь».") }
    }
    if (confirm) ConfirmDialog("Очистить очередь?", "Воспроизведение остановится. Любимые треки и плейлисты останутся на месте.", "Очистить", { confirm = false }) { state.clearQueue(); confirm = false }
}
