package com.neptune.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neptune.player.model.AccentColor

/**
 * Personal accent picker.
 *
 * Twelve presets cannot express "my colour", so this exposes the full space three ways: a hue
 * slider, saturation/brightness sliders, and a hex field for a value copied from elsewhere. All
 * three edit one ARGB value, and every change is applied immediately — the whole app reads
 * `accent()`, so the preview is the app itself rather than a swatch that lies until you press OK.
 *
 * The value is passed through [AccentColor.ensureVisible]: hue and saturation are preserved exactly,
 * and only brightness is lifted when the pick would be unreadable on the fixed black backdrop.
 */
@Composable
fun CustomAccentPicker(argb: Int, onChange: (Int) -> Unit) {
    val hsv = remember(argb) { AccentColor.rgbToHsv(argb) }
    // The hex field keeps its own draft so a half-typed value ("#6F") is not rejected on every
    // keystroke; it commits only when it parses to a complete colour.
    var hexDraft by remember(argb) { mutableStateOf(AccentColor.formatHex(argb)) }
    val preview = Color(argb)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = NeRow.panelInset, vertical = NeSpace.md),
        verticalArrangement = Arrangement.spacedBy(NeSpace.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(preview)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .semantics { contentDescription = "Текущий акцентный цвет" },
            )
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText("Свой цвет", size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold)
                NeText(
                    AccentColor.formatHex(argb), size = NeType.caption, lineHeight = NeType.captionLine,
                    color = Ne.Dim, maxLines = 1,
                )
            }
        }
        ChannelSlider(
            "Оттенок", hsv[0], 0f..360f, "${hsv[0].toInt()}°",
            // The track shows the whole hue wheel at the current saturation/value, so the thumb's
            // position and the colour under it agree.
            trackBrush = Brush.horizontalGradient(
                (0..12).map { Color(AccentColor.hsvToArgb(it * 30f, maxOf(hsv[1], 0.35f), maxOf(hsv[2], AccentColor.MinValue))) },
            ),
            thumbColor = Color(AccentColor.hsvToArgb(hsv[0], hsv[1], hsv[2])),
        ) { onChange(AccentColor.hsvToArgb(it, hsv[1], hsv[2])) }
        ChannelSlider(
            "Насыщенность", hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()}%",
            trackBrush = Brush.horizontalGradient(
                listOf(Color(AccentColor.hsvToArgb(hsv[0], 0f, hsv[2])), Color(AccentColor.hsvToArgb(hsv[0], 1f, hsv[2]))),
            ),
            thumbColor = preview,
        ) { onChange(AccentColor.hsvToArgb(hsv[0], it, hsv[2])) }
        ChannelSlider(
            "Яркость", hsv[2], AccentColor.MinValue..1f, "${(hsv[2] * 100).toInt()}%",
            trackBrush = Brush.horizontalGradient(
                listOf(Color(AccentColor.hsvToArgb(hsv[0], hsv[1], AccentColor.MinValue)), Color(AccentColor.hsvToArgb(hsv[0], hsv[1], 1f))),
            ),
            thumbColor = preview,
        ) { onChange(AccentColor.hsvToArgb(hsv[0], hsv[1], it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeTextField(
                hexDraft,
                {
                    hexDraft = it.take(7)
                    AccentColor.parseHex(it)?.let(onChange)
                },
                "HEX", Modifier.weight(1f),
            )
            Spacer(Modifier.width(NeSpace.sm))
            NeButton("Применить", {
                AccentColor.parseHex(hexDraft)?.let(onChange)
            }, icon = NeIcon.Check)
        }
        NeText(
            "Яркость не опускается ниже ${(AccentColor.MinValue * 100).toInt()}%, чтобы цвет оставался читаемым на чёрном.",
            size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, maxLines = 2,
        )
    }
}

/** A labelled slider whose track is a colour gradient rather than a flat accent line. */
@Composable
private fun ChannelSlider(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    trackBrush: Brush, thumbColor: Color, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            NeText(title, Modifier.weight(1f).alignByBaseline(), size = NeType.label, lineHeight = NeType.labelLine, maxLines = 1)
            NeText(
                valueText, Modifier.alignByBaseline(), size = NeType.caption, lineHeight = NeType.captionLine,
                color = Ne.Dim, weight = FontWeight.SemiBold,
            )
        }
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(trackBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
                modifier = Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = title },
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    // The gradient box underneath is the track; Material's own track must stay out
                    // of the way or it would paint over the hue wheel.
                    activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}

/** Shape chooser shared by track artwork and the avatar, so both read the same way. */
@Composable
fun <T> ShapeChoice(title: String, options: List<T>, selected: Int, label: (T) -> String, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NeRow.panelInset, vertical = NeSpace.sm),
        verticalArrangement = Arrangement.spacedBy(NeSpace.sm),
    ) {
        NeText(title, size = NeType.label, lineHeight = NeType.labelLine, maxLines = 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NeSpace.sm)) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                Box(
                    Modifier.weight(1f).heightIn(min = NeTouch.min).clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accent().copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .clickable { onSelect(index) }
                        .padding(horizontal = NeSpace.sm, vertical = NeSpace.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    NeText(
                        label(option), size = NeType.caption, lineHeight = NeType.captionLine,
                        color = if (isSelected) accent() else Ne.Dim, maxLines = 1,
                        weight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
