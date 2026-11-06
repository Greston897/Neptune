package com.neptune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.Accent
import com.neptune.player.ui.GlassAction
import com.neptune.player.ui.GlassSlider
import com.neptune.player.ui.LiquidGlass
import com.neptune.player.ui.Ne
import com.neptune.player.ui.NeIcon
import com.neptune.player.ui.NeIconView
import com.neptune.player.ui.NeText
import com.neptune.player.ui.PillToggle
import com.neptune.player.ui.rememberHaptics
import com.neptune.player.ui.squircle

@Composable
fun TuneScreen(state: NeptuneState, modifier: Modifier = Modifier) {
    val s = state.settings
    val accent = s.accent.color
    val haptic = rememberHaptics()

    LazyColumn(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NeText("Внешний вид", size = 30.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp)
                    Spacer(Modifier.height(4.dp))
                    NeText(
                        text = "Полная кастомизация стекла",
                        size = 11.5.sp,
                        weight = FontWeight.SemiBold,
                        color = Ne.Faint,
                        tracking = 0.1.sp,
                    )
                }
                GlassAction(
                    icon = NeIcon.Reset,
                    onClick = { haptic(); state.resetLook() },
                    size = 42.dp,
                    iconSize = 18.dp,
                )
            }
        }

        item {
            Section("Акцент") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Accent.entries.forEach { a ->
                        val selected = a == s.accent
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.85f), a.color),
                                    )
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = Color.White.copy(alpha = if (selected) 0.95f else 0.18f),
                                    shape = CircleShape,
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic()
                                    state.edit { it.copy(accentIndex = a.ordinal) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                NeIconView(NeIcon.Check, size = 14.dp, color = Color(0xFF07070B), weight = 2.6f)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                NeText(
                    text = s.accent.title,
                    size = 11.sp,
                    weight = FontWeight.SemiBold,
                    color = Ne.Faint,
                    tracking = 0.3.sp,
                )
            }
        }

        item {
            Section("Стекло") {
                SliderRow(
                    label = "Размытие",
                    valueText = "${s.blur.toInt()} dp",
                    value = s.blur,
                    range = 0f..44f,
                    accent = accent,
                ) { v -> state.edit { it.copy(blur = v) } }
                SliderRow(
                    label = "Плотность",
                    valueText = "${(s.tint * 100).toInt()} %",
                    value = s.tint,
                    range = 0f..0.42f,
                    accent = accent,
                ) { v -> state.edit { it.copy(tint = v) } }
                SliderRow(
                    label = "Радиус",
                    valueText = "${s.radius.toInt()} dp",
                    value = s.radius,
                    range = 8f..36f,
                    accent = accent,
                ) { v -> state.edit { it.copy(radius = v) } }
            }
        }

        item {
            Section("Сцена") {
                SliderRow(
                    label = "Насыщенность фона",
                    valueText = "${(s.bg * 100).toInt()} %",
                    value = s.bg,
                    range = 0f..1.6f,
                    accent = accent,
                ) { v -> state.edit { it.copy(bg = v) } }
                SliderRow(
                    label = "Скорость волн",
                    valueText = "×${"%.1f".format(s.speed)}",
                    value = s.speed,
                    range = 0.25f..2.5f,
                    accent = accent,
                ) { v -> state.edit { it.copy(speed = v) } }
            }
        }

        item {
            Section("Переключатели") {
                ToggleRow("Анимации", "Живые волны и движение", s.animations) { v ->
                    state.edit { it.copy(animations = v) }
                }
                ToggleRow("Свечение", "Ореол вокруг обложки", s.glow) { v ->
                    state.edit { it.copy(glow = v) }
                }
                ToggleRow("Зерно", "Плёночная текстура стекла", s.grain) { v ->
                    state.edit { it.copy(grain = v) }
                }
                ToggleRow("Плавающий навбар", "Отступ снизу", s.floatingNav) { v ->
                    state.edit { it.copy(floatingNav = v) }
                }
                ToggleRow("Вибрация", "Тактильный отклик", s.haptics) { v ->
                    state.edit { it.copy(haptics = v) }
                }
                ToggleRow("Мини-плеер", "Плашка над навбаром", s.miniPlayer) { v ->
                    state.edit { it.copy(miniPlayer = v) }
                }
                ToggleRow("Время трека", "Отсчёт и остаток", s.showTimes) { v ->
                    state.edit { it.copy(showTimes = v) }
                }
                ToggleRow("Кольца обложки", "Графические круги", s.rings) { v ->
                    state.edit { it.copy(rings = v) }
                }
                ToggleRow("Подписи в навбаре", "Текст под иконками", s.labels) { v ->
                    state.edit { it.copy(labels = v) }
                }
            }
        }

        item {
            LiquidGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { haptic(); state.resetLook() },
                shape = CircleShape,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NeText(
                        "Сбросить настройки вида",
                        size = 12.5.sp,
                        weight = FontWeight.Bold,
                        color = Ne.Dim,
                        tracking = 0.2.sp,
                    )
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NeText(
                    "NEPTUNE 1.0 · UI-ДЕМО",
                    size = 9.sp,
                    weight = FontWeight.Bold,
                    color = Ne.Whisper,
                    tracking = 2.4.sp,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(3.dp))
                NeText(
                    "Локальные треки — в следующем обновлении",
                    size = 10.5.sp,
                    weight = FontWeight.Medium,
                    color = Ne.Whisper,
                    tracking = 0.1.sp,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    LiquidGlass(Modifier.fillMaxWidth(), shape = squircle(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            NeText(
                title.uppercase(),
                size = 9.sp,
                weight = FontWeight.ExtraBold,
                color = Ne.Faint,
                tracking = 2.4.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeText(label, size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            NeText(
                valueText,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = Ne.Faint,
                tracking = 0.3.sp,
                align = TextAlign.End,
            )
        }
        GlassSlider(
            value = fraction,
            onValueChange = { onChange(range.start + it * span) },
            accent = accent,
            trackHeight = 6.dp,
            thumb = 13.dp,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            NeText(label, size = 13.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            NeText(hint, size = 10.5.sp, weight = FontWeight.Medium, color = Ne.Whisper, tracking = 0.sp)
        }
        Spacer(Modifier.width(10.dp))
        PillToggle(checked = checked, onChange = onChange)
    }
}
