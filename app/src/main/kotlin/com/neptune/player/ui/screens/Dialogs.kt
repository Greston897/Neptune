package com.neptune.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.PlaybackState
import com.neptune.player.ui.*

@Composable
fun NameDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ne.Elevated, title = { NeText(title, size = 20.sp, weight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = { OutlinedTextField(name, { name = it.take(40) }, label = { NeText(label, size = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent(), cursorColor = accent(), focusedTextColor = Ne.Text, unfocusedTextColor = Ne.Text)) },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { NeText("Сохранить", color = accent()) } },
        dismissButton = { TextButton(onClick = onDismiss) { NeText("Отмена", color = Ne.Dim) } })
}

@Composable
fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ne.Elevated,
        title = { NeText(title, size = 20.sp, weight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = { NeText(text, size = 13.sp, color = Ne.Dim, maxLines = 5, lineHeight = 20.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { NeText(action, color = Ne.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { NeText("Отмена", color = Ne.Dim) } })
}

@Composable
fun SleepDialog(playback: PlaybackState, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ne.Elevated,
        title = { NeText("Таймер сна", size = 20.sp, weight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = { Column { NeText("Музыка мягко остановится по таймеру.", size = 12.sp, color = Ne.Dim, maxLines = 2);
            listOf(0, 15, 30, 45, 60, 90).forEach { minutes ->
                SettingRow(if (minutes == 0) "Выключить таймер" else "$minutes минут",
                    if (minutes > 0 && playback.sleepRemainingMs in ((minutes - 15) * 60_000L + 1)..(minutes * 60_000L)) "Активен" else null,
                    onClick = { onConfirm(minutes) }, trailing = { if (minutes == 0 && playback.sleepRemainingMs == 0L) NeIconView(NeIcon.Check, 18.dp, accent()) })
            }
        } }, confirmButton = {})
}
