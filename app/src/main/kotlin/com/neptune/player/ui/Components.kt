package com.neptune.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime

data class LibraryActions(val permission: () -> Unit = {}, val import: () -> Unit = {}, val appSettings: () -> Unit = {})
val LocalLibraryActions = staticCompositionLocalOf { LibraryActions() }

@Composable
fun NeIconView(icon: NeIcon, size: Dp = 24.dp, color: Color = Ne.Text, modifier: Modifier = Modifier, weight: Float = 1.85f) {
    // Keep the 24×24 glyph square and optically centered even when a parent squeezes the canvas.
    Canvas(modifier.aspectRatio(1f, matchHeightConstraintsFirst = false).size(size)) {
        val k = this.size.minDimension / 24f
        val dx = (this.size.width - 24f * k) / 2f
        val dy = (this.size.height - 24f * k) / 2f
        translate(dx, dy) { scale(k, k, Offset.Zero) { drawNeIcon(icon, color, weight) } }
    }
}

@Composable
fun NeIconButton(icon: NeIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, size: Dp = 48.dp, iconSize: Dp = 22.dp,
    color: Color = if (selected) accent() else Ne.Text, background: Color = Color.Transparent) {
    val haptic = LocalHaptics.current
    IconButton(onClick = { haptic(); onClick() }, enabled = enabled,
        modifier = modifier.size(size).clip(CircleShape).background(background).semantics { contentDescription = label }) {
        NeIconView(icon, iconSize, if (enabled) color else Ne.Whisper)
    }
}

@Composable
fun NeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: NeIcon? = null,
    primary: Boolean = true, enabled: Boolean = true) {
    val haptic = LocalHaptics.current
    Button(onClick = { haptic(); onClick() }, modifier = modifier.heightIn(min = 50.dp), enabled = enabled,
        shape = RoundedCornerShape(neRadius()), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) accent() else Ne.Elevated,
            contentColor = if (primary) Ne.Black else Ne.Text, disabledContainerColor = Ne.Ink, disabledContentColor = Ne.Faint)) {
        if (icon != null) { NeIconView(icon, 19.dp, if (primary && enabled) Ne.Black else Ne.Text); Spacer(Modifier.width(9.dp)) }
        NeText(text, size = 14.sp, weight = FontWeight.Bold, color = if (primary && enabled) Ne.Black else Ne.Text)
    }
}

@Composable
fun NeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: NeIcon? = null) {
    val haptic = LocalHaptics.current
    Row(modifier.heightIn(min = 40.dp).clip(CircleShape)
        .background(if (selected) Ne.Text else Ne.Elevated)
        .clickable(role = Role.Button) { haptic(); onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { NeIconView(icon, 15.dp, if (selected) Ne.Black else Ne.Dim); Spacer(Modifier.width(6.dp)) }
        NeText(text, size = 12.sp, weight = FontWeight.Bold, color = if (selected) Ne.Black else Ne.Dim)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        NeText(title, modifier = Modifier.weight(1f), size = 20.sp, weight = FontWeight.ExtraBold, tracking = (-0.5).sp)
        if (action != null && onAction != null) NeText(action, Modifier.clip(CircleShape).clickable(onClick = onAction).padding(10.dp), size = 12.sp, color = Ne.Dim)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassPanel(modifier.fillMaxWidth()) { content() }
}

@Composable
fun TrackRow(track: Track, current: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val compact = LocalSettings.current.compact
    Row(modifier.fillMaxWidth().heightIn(min = if (compact) 60.dp else 72.dp)
        .clip(RoundedCornerShape(12.dp)).background(if (current) Ne.Ink else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TrackThumb(track, if (compact) 42.dp else 48.dp, 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NeText(track.title, size = 14.sp, weight = FontWeight.Bold, color = if (current) accent() else Ne.Text)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) { EqualizerBars(playing, color = accent(), maxHeight = 10.dp); Spacer(Modifier.width(6.dp)) }
                NeText(subtitle ?: track.artist, size = 11.5.sp, color = Ne.Dim, modifier = Modifier.weight(1f, fill = false))
            }
        }
        if (trailing != null) trailing() else if (onMore == null) NeText(formatTime(track.durationMs), Modifier.padding(horizontal = 8.dp), size = 11.sp, color = Ne.Faint)
        if (onMore != null) NeIconButton(NeIcon.More, "Действия с ${track.title}", onMore, iconSize = 18.dp, color = Ne.Dim)
    }
}

@Composable
fun AlbumCard(album: String, artist: String, track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp)) {
        TrackArtwork(track, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        Spacer(Modifier.height(10.dp))
        NeText(album, size = 13.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        NeText(artist, size = 11.sp, color = Ne.Dim)
    }
}
@Composable
fun EmptyState(icon: NeIcon, title: String, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Ne.Ink), contentAlignment = Alignment.Center) { NeIconView(icon, 26.dp, Ne.Dim) }
        Spacer(Modifier.height(18.dp))
        NeText(title, size = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        NeText(text, size = 13.sp, color = Ne.Dim, maxLines = 4, align = TextAlign.Center, lineHeight = 20.sp)
        if (action != null && onAction != null) { Spacer(Modifier.height(22.dp)); NeButton(action, onAction) }
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, icon: NeIcon? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val haptic = LocalHaptics.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button) { haptic(); onClick() } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) { NeIconView(icon, 18.dp, Ne.Dim) }; Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            NeText(title, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
            if (subtitle != null) { Spacer(Modifier.height(4.dp)); NeText(subtitle, size = 11.5.sp, color = Ne.Dim, maxLines = 3, lineHeight = 16.sp) }
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        else if (onClick != null) NeIconView(NeIcon.ChevronRight, 16.dp, Ne.Faint)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHaptics.current
    SettingRow(title, subtitle, trailing = {
        Switch(checked = checked, onCheckedChange = { haptic(); onChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Ne.Black, checkedTrackColor = accent(),
                uncheckedThumbColor = Ne.Dim, uncheckedTrackColor = Ne.Line, uncheckedBorderColor = Ne.Line))
    })
}

@Composable
fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit, steps: Int = 0, onFinished: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeText(title, Modifier.weight(1f), size = 13.sp)
            NeText(valueText, size = 12.sp, color = Ne.Dim)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps,
            onValueChangeFinished = onFinished, modifier = Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = title },
            colors = SliderDefaults.colors(thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Ne.Line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
    }
}

