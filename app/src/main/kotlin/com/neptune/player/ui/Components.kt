package com.neptune.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

/**
 * Renders a Lucide-backed VectorDrawable inside a locked square box.
 *
 * The glyph carries its own 24×24 grid and stroke weight, so it stays crisp and centred at any
 * size. `bold` selects the heavier twin used by the selected tab.
 */
@Composable
fun NeIconView(
    icon: NeIcon, size: Dp = NeIconSize.lg, color: Color = Ne.Text,
    modifier: Modifier = Modifier, bold: Boolean = false,
) {
    Image(
        painter = painterResource(if (bold) icon.boldRes else icon.res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size),
    )
}

/** Icon button with a guaranteed touch target; the glyph never exceeds 60% of the hit area. */
@Composable
fun NeIconButton(
    icon: NeIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, size: Dp = NeTouch.standard,
    iconSize: Dp = NeIconSize.md, color: Color = if (selected) accent() else Ne.Text,
    background: Color = Color.Transparent,
) {
    val haptic = LocalHaptics.current
    IconButton(
        onClick = { haptic(); onClick() }, enabled = enabled,
        modifier = modifier.size(size).clip(CircleShape).background(background).semantics { contentDescription = label },
    ) {
        NeIconView(icon, iconSize, if (enabled) color else Ne.Whisper)
    }
}

@Composable
fun NeButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: NeIcon? = null,
    primary: Boolean = true, enabled: Boolean = true,
) {
    val haptic = LocalHaptics.current
    Button(
        onClick = { haptic(); onClick() }, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = NeSpace.lg, vertical = NeSpace.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) accent() else Color.White.copy(alpha = 0.08f),
            contentColor = if (primary) Ne.Black else Ne.Text,
            disabledContainerColor = Color.White.copy(alpha = 0.05f), disabledContentColor = Ne.Whisper,
        ),
    ) {
        if (icon != null) {
            NeIconView(icon, NeIconSize.sm, if (primary && enabled) Ne.Black else Ne.Text)
            Spacer(Modifier.width(NeSpace.sm))
        }
        NeText(
            text, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.Bold,
            color = if (primary && enabled) Ne.Black else Ne.Text, maxLines = 1,
        )
    }
}

/** Filter chip: the selected state uses an accent-tinted fill plus a rim, not an inverted block. */
@Composable
fun NeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: NeIcon? = null) {
    val haptic = LocalHaptics.current
    val tint = accent()
    Row(
        modifier.heightIn(min = 36.dp).clip(CircleShape)
            .background(if (selected) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .clickable(role = Role.Button) { haptic(); onClick() }
            .padding(horizontal = NeSpace.lg, vertical = NeSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NeIconView(icon, NeIconSize.xs, if (selected) tint else Ne.Dim)
            Spacer(Modifier.width(6.dp))
        }
        NeText(
            text, size = NeType.caption, lineHeight = NeType.captionLine,
            weight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) tint else Ne.Dim, maxLines = 1,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        NeText(
            title, Modifier.weight(1f), size = NeType.section, lineHeight = NeType.sectionLine,
            weight = FontWeight.Bold, tracking = (-0.3).sp, maxLines = 1,
        )
        if (action != null && onAction != null) {
            NeText(
                action,
                Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = NeSpace.sm, vertical = 6.dp),
                size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, weight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassPanel(modifier.fillMaxWidth()) { content() }
}

/**
 * List row for a track. Title and subtitle get the flexible column, while thumb, duration and
 * overflow keep fixed widths — long titles ellipsize instead of pushing controls off-screen.
 */
@Composable
fun TrackRow(
    track: Track, current: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val compact = LocalSettings.current.compact
    val tint = accent()
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) NeRow.compact else NeRow.track)
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) tint.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = NeSpace.sm, end = NeSpace.xs, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackThumb(track, if (compact) NeRow.thumbCompact else NeRow.thumb, 10.dp)
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            NeText(
                track.title, size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.SemiBold,
                color = if (current) tint else Ne.Text, maxLines = 1,
            )
            Spacer(Modifier.height(NeSpace.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) {
                    EqualizerBars(playing, color = tint, maxHeight = 10.dp)
                    Spacer(Modifier.width(6.dp))
                }
                NeText(
                    subtitle ?: track.artist, size = NeType.caption, lineHeight = NeType.captionLine,
                    color = Ne.Dim, maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(NeSpace.sm))
            trailing()
        } else if (onMore == null) {
            NeText(
                formatTime(track.durationMs), Modifier.padding(horizontal = NeSpace.sm).widthIn(min = 36.dp),
                size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, align = TextAlign.End,
            )
        }
        if (onMore != null) {
            NeIconButton(
                NeIcon.More, "Действия с ${track.title}", onMore,
                size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim,
            )
        }
    }
}

@Composable
fun AlbumCard(album: String, artist: String, track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(NeSpace.xs)) {
        TrackArtwork(track, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        Spacer(Modifier.height(NeSpace.sm))
        NeText(album, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(NeSpace.xxs))
        NeText(artist, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
    }
}

@Composable
fun EmptyState(
    icon: NeIcon, title: String, text: String, modifier: Modifier = Modifier,
    action: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = NeSpace.xxl, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) { NeIconView(icon, NeIconSize.xl, Ne.Dim) }
        Spacer(Modifier.height(NeSpace.lg))
        NeText(title, size = NeType.title, lineHeight = NeType.titleLine, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(NeSpace.sm))
        NeText(text, size = NeType.label, lineHeight = NeType.bodyLine, color = Ne.Dim, maxLines = 4, align = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(NeSpace.xl))
            NeButton(action, onAction)
        }
    }
}

/**
 * Settings cell. The leading badge is a fixed 40dp square with a 20dp glyph, so every row in a
 * group shares one text start line regardless of which icon it uses.
 */
@Composable
fun SettingRow(
    title: String, subtitle: String? = null, icon: NeIcon? = null, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHaptics.current
    // `modifier` sits before the callbacks on purpose: `onClick` must stay last so the many
    // `SettingRow(...) { ... }` call sites keep binding their trailing lambda to the click.
    Row(
        modifier.fillMaxWidth().heightIn(min = NeRow.setting)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { haptic(); onClick() } else Modifier)
            .padding(horizontal = NeSpace.lg, vertical = NeSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(NeRow.leadingBadge).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { NeIconView(icon, NeIconSize.md, Ne.Dim) }
            Spacer(Modifier.width(NeSpace.md))
        }
        Column(Modifier.weight(1f)) {
            NeText(title, size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.SemiBold, maxLines = 2)
            if (subtitle != null) {
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(subtitle, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(NeSpace.md))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(NeSpace.sm))
            NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHaptics.current
    SettingRow(title, subtitle, trailing = {
        Switch(
            checked = checked, onCheckedChange = { haptic(); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ne.Black, checkedTrackColor = accent(), checkedBorderColor = accent(),
                uncheckedThumbColor = Ne.Dim, uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    })
}

@Composable
fun SettingSlider(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit, steps: Int = 0, onFinished: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeText(title, Modifier.weight(1f), size = NeType.label, lineHeight = NeType.labelLine, maxLines = 1)
            Spacer(Modifier.width(NeSpace.sm))
            NeText(valueText, size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.SemiBold)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            onValueChangeFinished = onFinished,
            modifier = Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = title },
            colors = SliderDefaults.colors(
                thumbColor = accent(), activeTrackColor = accent(), inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
