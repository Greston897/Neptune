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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neptune.player.model.Track
import com.neptune.player.model.formatTime
import androidx.compose.foundation.layout.fillMaxSize
import coil.compose.AsyncImage

/**
 * Host-provided pickers, injected once at the top of the tree.
 *
 * `avatar` and `lyricsFile` live here rather than in each screen because only the activity can own
 * an `ActivityResultLauncher`, and a launcher registered inside a screen that leaves the composition
 * mid-pick would drop its result.
 */
data class LibraryActions(
    val permission: () -> Unit = {},
    val import: () -> Unit = {},
    val appSettings: () -> Unit = {},
    val avatar: () -> Unit = {},
    val lyricsFile: () -> Unit = {},
)
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
    // Material3's IconButton centres its content inside a 40dp inner box and then pads the ripple
    // out to 48dp. When a caller asks for a 44dp button those two boxes disagree and the glyph ends
    // up a pixel or two off centre. A plain Box centres the glyph in exactly the requested square.
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { haptic(); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
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

/**
 * A section caption with an optional trailing action.
 *
 * [subtitle] is a count ("20 треков") rather than an action word, because a header that carries both
 * a number and a "play all" button reads as two competing affordances; the chart and the search
 * results put the button below the caption instead.
 */
@Composable
fun SectionHeader(
    title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null,
    actionTag: String? = null, subtitle: String? = null, onTitleClick: (() -> Unit)? = null,
) {
    if (subtitle != null) {
        Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            NeText(
                title, Modifier.weight(1f).alignByBaseline(), size = NeType.section, lineHeight = NeType.sectionLine,
                weight = FontWeight.Bold, tracking = (-0.3).sp, maxLines = 1,
            )
            NeText(
                subtitle,
                Modifier.alignByBaseline()
                    .then(if (onTitleClick != null) Modifier.clip(CircleShape).clickable(onClick = onTitleClick) else Modifier)
                    .padding(start = NeSpace.sm, top = 6.dp, bottom = 6.dp),
                size = NeType.caption, lineHeight = NeType.captionLine,
                color = if (onTitleClick != null) accent() else Ne.Faint, maxLines = 1,
                weight = if (onTitleClick != null) FontWeight.Bold else FontWeight.Normal,
            )
        }
        return
    }
    // Two trailing slots in one header would compete for the same edge, so a count and an action
    // never arrive together. Both are nullable and every caller passes one or the other.
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        NeText(
            title, Modifier.weight(1f).alignByBaseline(), size = NeType.section, lineHeight = NeType.sectionLine,
            weight = FontWeight.Bold, tracking = (-0.3).sp, maxLines = 1,
        )
        if (action != null && onAction != null) {
            NeText(
                action,
                Modifier.alignByBaseline().then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
                    .clip(CircleShape).clickable(onClick = onAction)
                    .padding(start = NeSpace.sm, top = 6.dp, bottom = 6.dp),
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
 * List row for a track.
 *
 * Title and subtitle get the flexible column, while thumb, duration and overflow keep fixed widths —
 * long titles ellipsize instead of pushing controls off-screen.
 *
 * **Selection is colour, not a plate.** The current track used to sit on a 10%-accent rounded
 * rectangle that stopped short of the panel's edges, so a row inside a glass panel showed a *third*
 * radius between the title and the panel curve and read as a compressed, misaligned block. The tinted
 * fill is gone: the artwork keeps its own shape, the title turns accent-coloured, the equalizer marks
 * playback, and the row keeps exactly the panel's rhythm. That reads as accent highlighting without
 * introducing another surface.
 */
@Composable
fun TrackRow(
    track: Track, current: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val compact = settings.compact
    val tint = accent()
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) NeRow.compact else NeRow.track)
            // The ripple is clipped to the row, but nothing is painted behind it: selection is a
            // colour change on the text, never a plate. See the note above.
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One scale factor for every list in the app, so the preference reads the same on Home,
        // Library, Search and inside collections.
        TrackThumb(track, (if (compact) NeRow.thumbCompact else NeRow.thumb) * settings.thumbScale, 14.dp)
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeText(
                    track.title, Modifier.weight(1f, fill = false), size = NeType.body, lineHeight = NeType.bodyLine,
                    weight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (current) tint else Ne.Text, maxLines = 1,
                )
                // A streamed track carries no file: the row says where it comes from, so "убрать из
                // приложения" and "удалить с устройства" are never offered for it and the user is not
                // surprised when it needs a network.
                if (track.source == Track.Source.STREAM) {
                    Spacer(Modifier.width(NeSpace.sm))
                    StreamBadge()
                }
                if (track.previewMs > 0) {
                    Spacer(Modifier.width(NeSpace.xs))
                    NeText("30 сек", size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, maxLines = 1)
                }
            }
            Spacer(Modifier.height(NeSpace.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) {
                    EqualizerBars(playing, color = tint, maxHeight = 10.dp)
                    Spacer(Modifier.width(6.dp))
                }
                NeText(
                    subtitle ?: track.artist, size = NeType.caption, lineHeight = NeType.captionLine,
                    color = if (current) tint.copy(alpha = 0.62f) else Ne.Dim, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(NeSpace.sm))
            trailing()
        } else if (onMore == null) {
            NeText(
                formatTime(track.durationMs), Modifier.padding(start = NeSpace.sm).widthIn(min = 36.dp),
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

/**
 * The one mark that says "this plays from the catalogue, not from your phone".
 *
 * Deliberately quiet: a small icon rather than a coloured chip, because a search result mixing files
 * and streams should not look like two different products.
 */
@Composable
fun StreamBadge(modifier: Modifier = Modifier) {
    NeIconView(NeIcon.Globe, NeIconSize.xs, Ne.Faint, modifier = modifier)
}

/**
 * A cover for something that has no local file behind it — a streamed track, album or artist.
 *
 * Takes a URL instead of a Track because there may be no track yet: three of the call sites are
 * identicons for an album row, and one of them runs before anything has been fetched. The brand mark
 * is drawn underneath, so a missing cover, a dead link and a slow load all degrade to the same shape
 * the rest of the app uses instead of an empty rectangle.
 */
@Composable
fun RemoteCover(coverUri: String?, seed: Int, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(artShape(corner))) {
        BrandCover(seed = seed, animated = false)
        if (coverUri != null) {
            AsyncImage(
                model = coverUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun AlbumCard(album: String, artist: String, track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
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
        modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            NeIconView(icon, NeIconSize.xxl, Ne.Dim)
        }
        Spacer(Modifier.height(NeSpace.md))
        NeText(title, size = NeType.title, lineHeight = NeType.titleLine, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(NeSpace.sm))
        NeText(text, size = NeType.label, lineHeight = NeType.bodyLine, color = Ne.Dim, maxLines = 4, align = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(NeSpace.lg))
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
            .padding(horizontal = NeRow.panelInset, vertical = NeSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // Fixed square keeps every label in the group on one start line; no grey plate behind it.
            Box(Modifier.size(NeRow.leadingIcon), contentAlignment = Alignment.Center) {
                NeIconView(icon, NeIconSize.lg, Ne.Dim)
            }
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

/** Shared text style for editable fields so typed text matches [NeText] on the same grid. */
@Composable
fun neFieldTextStyle(): TextStyle = TextStyle(
    fontFamily = Manrope, fontSize = NeType.body, lineHeight = NeType.bodyLine,
    fontWeight = FontWeight.Medium, color = Ne.Text, letterSpacing = (-0.15).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * Outlined input used by the profile name and the playlist dialogs.
 *
 * Material3's default text style keeps `includeFontPadding` and an unspecified line height, so the
 * typed value sat higher than the floating label and higher than neighbouring rows. Passing the
 * shared style puts the value, the label and the rest of the UI on one grid.
 */
@Composable
fun NeTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        singleLine = true, textStyle = neFieldTextStyle(),
        label = { NeText(label, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent(), cursorColor = accent(),
            focusedTextColor = Ne.Text, unfocusedTextColor = Ne.Text,
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            focusedLabelColor = accent(), unfocusedLabelColor = Ne.Dim,
        ),
    )
}

@Composable
fun SettingSlider(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onChange: (Float) -> Unit, steps: Int = 0, onFinished: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = NeRow.panelInset, vertical = NeSpace.sm)) {
        // Label and value are different sizes: centre-aligning their boxes leaves the baselines
        // visibly offset, so they are aligned on the shared text baseline instead.
        Row(Modifier.fillMaxWidth()) {
            NeText(title, Modifier.weight(1f).alignByBaseline(), size = NeType.label, lineHeight = NeType.labelLine, maxLines = 1)
            Spacer(Modifier.width(NeSpace.sm))
            NeText(
                valueText, Modifier.alignByBaseline(), size = NeType.caption, lineHeight = NeType.captionLine,
                color = accent(), weight = FontWeight.SemiBold,
            )
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
