package com.neptune.player.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.Features
import com.neptune.player.model.LyricsDoc
import com.neptune.player.model.PlanFeature
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

/** Longest text we accept from a file picker, so one stray binary cannot bloat the stored JSON. */
private const val MaxLyricsChars = 40_000

/**
 * Lyrics for the track that is actually playing, synchronised to the real playback position.
 *
 * Three behaviours make this usable for singing along rather than just reading:
 *
 *  1. **Karaoke follows time, not scroll.** The active line is a pure function of `positionMs`
 *     (`LyricsDoc.activeIndex`, a binary search), so it stays correct after a seek, a pause, a
 *     speed change or a scrub — no timer of its own to drift out of sync.
 *  2. **The line is scrolled, never driven.** A single `animateScrollToItem` fires when the active
 *     index *changes*; the user can scroll away freely and the list only follows on the next line.
 *     Auto-scrolling on every frame is what makes these screens feel sticky and unresponsive.
 *  3. **The reader still owns the screen.** Tapping any line seeks to it when the text carries
 *     timings, and nothing moves the list at all when it does not.
 *
 * The source of the text is printed in the header, because "tag file" and "I typed it" are
 * different promises and the user should know which one they are reading.
 */
@Composable
fun LyricsScreen(state: NeptuneState, onBack: () -> Unit) {
    val playback by state.playback.collectAsStateWithLifecycle()
    val docs by state.lyricsDocs.collectAsStateWithLifecycle()
    // Recomposed on every tier change so a save attempted on Free re-renders the dialog's promise.
    val tier by state.plan.collectAsStateWithLifecycle()
    // A track opened from the sheet takes precedence over playback; `null` means "follow the music".
    val target = state.lyricsTarget
    val track = target ?: playback.track
    val playing = target == null || target.id == playback.track?.id
    var editing by remember { mutableStateOf(false) }
    var draft by remember(track?.id) { mutableStateOf("") }

    // Reading the tag is I/O; it is kicked off once per track and published through the repository.
    LaunchedEffect(track?.id) {
        if (track != null) state.requestLyrics(track)
    }
    val doc: LyricsDoc? = track?.let { docs[it.id] ?: state.lyricsFor(it) }
    // `LyricsDoc.None` is a real answer from the repository ("scanned, nothing found"), so an empty
    // document means "no text", while `null` still means "scan has not finished". Without that
    // distinction the screen would keep promising that text is on its way.
    val scanned = doc != null
    val lyrics = doc?.takeUnless { it.isEmpty }
    val listState = rememberLazyListState()
    // Only a track that is actually playing can have an active line; another track's text is read.
    val active = if (playing) lyrics?.activeIndex(playback.positionMs) ?: -1 else -1

    // Follow the active line only when it changes. `active` is derived from the position, so this
    // effect re-launches four times a second — the guard keeps it from issuing a scroll each time.
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(index = (active - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.sm,
            bottom = LocalBottomInset.current + 72.dp,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = NeRow.header), verticalAlignment = Alignment.CenterVertically) {
                NeIconButton(NeIcon.ChevronDown, "Вернуться назад", onBack)
                Column(Modifier.weight(1f).padding(horizontal = NeSpace.sm)) {
                    NeText(
                        "ТЕКСТ ТРЕКА", size = NeType.micro, lineHeight = NeType.microLine,
                        color = Ne.Faint, tracking = 1.2.sp, weight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(NeSpace.xxs))
                    NeText(track?.title ?: "Ничего не играет", size = NeType.caption, lineHeight = NeType.captionLine, weight = FontWeight.Bold, maxLines = 1)
                }
                if (track != null) {
                    NeIconButton(NeIcon.Edit, "Свой текст", {
                        draft = lyrics?.raw?.takeIf { it.isNotBlank() } ?: ""
                        editing = true
                    }, color = Ne.Dim, iconSize = NeIconSize.md)
                }
            }
        }
        if (track == null) {
            item { EmptyState(NeIcon.Mic, "Пока тихо", "Включи трек — текст появится здесь сам.", action = "Назад", onAction = onBack) }
            return@LazyColumn
        }
        // Track card: cover + artist + where the text came from.
        if (!playing) {
            item {
                // Reading and singing are different promises: without playback there is nothing to
                // synchronise against, so the text is shown as text and the reason is printed.
                Row(Modifier.fillMaxWidth().padding(top = NeSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    NeIconView(NeIcon.Info, NeIconSize.xs, Ne.Dim)
                    Spacer(Modifier.width(NeSpace.sm))
                    NeText(
                        "Сейчас играет другой трек — текст показан без караоке.",
                        size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = NeSpace.md, bottom = NeSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackThumb(track, 56.dp, 16.dp)
                Spacer(Modifier.width(NeSpace.md))
                Column(Modifier.weight(1f)) {
                    NeText(track.title, size = NeType.section, lineHeight = NeType.sectionLine, weight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(NeSpace.xxs))
                    NeText(track.artist, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                }
                if (lyrics != null) {
                    NeChip(
                        if (lyrics.synced) "Караоке" else lyrics.source.title,
                        selected = lyrics.synced, onClick = {},
                        icon = if (lyrics.synced) NeIcon.Wave else NeIcon.Music,
                    )
                }
            }
        }
        when {
            !scanned -> {
                // The scan runs on IO; this is a designed waiting state, not a spinner over content.
                item {
                    NeText(
                        "Ищем текст в файле…", Modifier.fillMaxWidth().padding(vertical = NeSpace.xxl),
                        size = NeType.label, lineHeight = NeType.bodyLine, color = Ne.Faint, align = TextAlign.Center,
                    )
                }
            }
            lyrics == null -> {
                item {
                    EmptyState(
                        NeIcon.Mic, "Текста нет",
                        "В файле нет лирики. Можно вставить свой текст — он останется в приложении.",
                        action = "Добавить текст",
                        onAction = { draft = ""; editing = true },
                    )
                }
            }
            else -> {
                itemsIndexed(lyrics.lines, key = { index, line -> ListKeys.lyric(index, line.timeMs) }) { index, line ->
                    LyricRow(
                        text = line.text,
                        active = index == active,
                        synced = lyrics.synced,
                        timeMs = line.timeMs,
                        progress = if (index == active) lyrics.lineProgress(playback.positionMs) else 0f,
                        // Tapping a line seeks — but only inside the track that is really playing.
                        // Seeking the transport from another track's text would jump the song the
                        // user is listening to, which is never what the tap meant.
                        onClick = {
                            if (playing && lyrics.synced && line.timeMs >= 0 && playback.durationMs > 0) {
                                state.seek((line.timeMs.toFloat() / playback.durationMs).coerceIn(0f, 1f))
                            }
                        },
                        seekable = playing && lyrics.synced,
                    )
                }
                item {
                    Spacer(Modifier.height(NeSpace.xxl))
                    if (!lyrics.synced) {
                        NeText(
                            "В этом тексте нет таймингов — строки не подсвечиваются. Свой LRC с [mm:ss.xx] включит караоке.",
                            Modifier.fillMaxWidth().padding(horizontal = NeSpace.sm),
                            size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint,
                            align = TextAlign.Center, maxLines = 3,
                        )
                    }
                }
            }
        }
    }

    if (editing) LyricsEditorDialog(
        initial = draft,
        saveIncluded = Features.has(PlanFeature.LyricsSave, tier),
        onDismiss = { editing = false },
        onSave = { text ->
            editing = false
            state.saveLyrics(track, text.take(MaxLyricsChars))
        },
        onPickFile = {
            editing = false
            state.requestLyricsFile(track)
            LocalLibraryActions.current.lyricsFile()
        },
    )
}

/**
 * One lyric line.
 *
 * The active line grows, brightens and gains a thin accent progress bar underneath, which is the
 * smallest thing that communicates "this is the line, and you are 40% through it" without turning
 * the text into a moving gradient that would be unreadable on a phone at arm's length.
 */
@Composable
private fun LyricRow(
    text: String, active: Boolean, synced: Boolean, timeMs: Long, progress: Float,
    onClick: () -> Unit, seekable: Boolean = true,
) {
    val animations = LocalSettings.current.animations
    val top by animateDpAsState(if (active) NeSpace.sm else 6.dp, tween(if (animations) NeMotion.Quick else 0, easing = NeEase), label = "lyricTop")
    val bottom by animateDpAsState(if (active) NeSpace.sm else 6.dp, tween(if (animations) NeMotion.Quick else 0, easing = NeEase), label = "lyricBottom")
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = seekable && timeMs >= 0, onClick = onClick)
            .padding(horizontal = NeSpace.xs, vertical = top + bottom),
    ) {
        NeText(
            text,
            Modifier.fillMaxWidth(),
            size = if (active) NeType.section else NeType.body,
            lineHeight = if (active) NeType.sectionLine else NeType.bodyLine,
            weight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = when {
                active -> accent()
                synced -> Ne.Dim
                else -> Ne.Text
            },
            maxLines = 6,
        )
        if (active) {
            Spacer(Modifier.height(NeSpace.sm))
            Box(
                Modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
                    .background(accent().copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp)
                        .clip(CircleShape).background(accent()),
                )
            }
        }
    }
}

/**
 * Editor for a user-supplied text.
 *
 * Kept as a full-width dialog rather than a route: it must not lose the draft when the user steps
 * back to check the song, and it needs the keyboard inset, which a dialog handles correctly.
 */
@Composable
private fun LyricsEditorDialog(
    initial: String, saveIncluded: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit, onPickFile: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ne.Elevated,
        title = { NeText("Свой текст", size = NeType.title, lineHeight = NeType.titleLine, weight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                NeText(
                    "Вставьте текст или LRC с таймингами [mm:ss.xx] — строки станут караоке.",
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 3,
                )
                Spacer(Modifier.height(NeSpace.md))
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(MaxLyricsChars) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
                        .padding(NeSpace.md)
                        .semantics { contentDescription = "Текст трека" },
                    textStyle = neFieldTextStyle(),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent()),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                NeText("Вставьте текст…", size = NeType.body, lineHeight = NeType.bodyLine, color = Ne.Faint)
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(NeSpace.sm))
                NeText(
                    if (saveIncluded) "Текст сохранится в приложении и подставится к этому треку."
                    else "Сохранение своего текста входит в Neptune Plus. Караоке по тегу файла — бесплатно.",
                    size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, maxLines = 2,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                NeText("Сохранить", color = accent())
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = onPickFile) { NeText("Из файла", color = Ne.Dim) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { NeText("Отмена", color = Ne.Dim) }
            }
        },
    )
}
