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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 24.dp), corner = 28.dp, haze = null) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                NeText("Таймер сна", size = 20.sp, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                NeText("Музыка мягко остановится по таймеру.", size = 12.sp, color = Ne.Dim, maxLines = 2)
                Spacer(Modifier.height(14.dp))
                listOf(0, 15, 30, 45, 60, 90).forEach { minutes ->
                    val active = if (minutes == 0) playback.sleepRemainingMs == 0L
                        else playback.sleepRemainingMs in ((minutes - 15) * 60_000L + 1)..(minutes * 60_000L)
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (active) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                        .clickable { onConfirm(minutes); onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        NeText(if (minutes == 0) "Выключить таймер" else "$minutes минут", Modifier.weight(1f), size = 14.sp)
                        if (active) NeIconView(NeIcon.Check, 18.dp, accent())
                    }
                }
                Spacer(Modifier.height(14.dp))
                NeButton("Закрыть", onDismiss, Modifier.fillMaxWidth(), primary = false)
            }
        }
    }
}
