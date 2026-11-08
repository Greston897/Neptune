package com.neptune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.Track
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*
import java.util.Calendar

/**
 * Home, rebuilt for density in 2.9.
 *
 * The old screen spent most of its first viewport on air: a 30sp title with 36dp of padding around
 * it, a 200dp full-bleed hero whose artwork was usually the fallback mark, and 24–32dp gaps between
 * every block. On a phone that pushed the actual music below the fold.
 *
 * Now every block is content-height with one shared 12dp rhythm, the hero is a compact row built
 * around a real cover, and the last panel is the import action — the app ships no audio of its own,
 * so "add your music" is a permanent, reachable control rather than a dead end.
 */
@Composable
fun HomeScreen(
    state: NeptuneState, onCollection: (String, String) -> Unit, onPlayer: () -> Unit,
    onProfile: () -> Unit, onSettings: () -> Unit, onAlbums: () -> Unit = {},
) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val actions = LocalLibraryActions.current
    val recent = remember(users.recent, library.tracks) { val map = library.byId; users.recent.mapNotNull(map::get) }
    val albums = remember(library.tracks) { LibraryLogic.albums(library.tracks) }
    // Count what actually resolves, not how many ids are stored: favourites saved against the demo
    // catalogue removed in 2.9 would otherwise inflate the tile above an empty collection.
    val likedCount = remember(users.liked, library.tracks) { library.tracks.count { it.id in users.liked } }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Доброй ночи"
    }
    val featured = recent.firstOrNull() ?: library.tracks.firstOrNull()
    val featuredQueue = if (recent.isNotEmpty()) recent else library.tracks

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen,
            top = NeSpace.xs + LocalTopInset.current, bottom = LocalBottomInset.current,
        ),
        // One vertical rhythm for the whole screen. Blocks no longer carry their own trailing
        // margins, which is what produced the uneven 20/24/32dp holes in the previous layout.
        verticalArrangement = Arrangement.spacedBy(NeSpace.md),
    ) {
        // Header: brand left, centred avatar, settings right — all on one 56dp baseline.
        item {
            Row(Modifier.fillMaxWidth().height(NeRow.header), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    NeptuneMark(Modifier.size(NeIconSize.lg))
                    Spacer(Modifier.width(NeSpace.sm))
                    NeText(
                        "neptune", size = NeType.section, lineHeight = NeType.sectionLine,
                        weight = FontWeight.ExtraBold, tracking = (-0.5).sp,
                    )
                }
                Box(
                    Modifier.size(NeTouch.standard).clip(CircleShape).clickable(onClick = onProfile),
                    contentAlignment = Alignment.Center,
                ) { ProfileAvatar(Modifier.size(36.dp).semanticsAvatar(), animated = true) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    NeIconButton(
                        NeIcon.Settings, "Настройки", onSettings, size = NeTouch.min,
                        iconSize = NeIconSize.lg, color = Ne.Dim,
                    )
                }
            }
        }
        // Greeting and title share one tight block: 24sp instead of 30sp, no 36dp of dead padding.
        item {
            Column {
                NeText(
                    "$greeting, ${settings.profileName}", size = NeType.caption, lineHeight = NeType.captionLine,
                    color = Ne.Dim, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NeText(
                        "Твоя музыка", Modifier.weight(1f), size = NeType.headline, lineHeight = NeType.headlineLine,
                        weight = FontWeight.ExtraBold, tracking = (-0.6).sp, maxLines = 1,
                    )
                    if (library.tracks.isNotEmpty()) {
                        NeText(
                            trackCount(library.tracks.size), size = NeType.caption, lineHeight = NeType.captionLine,
                            color = Ne.Faint, maxLines = 1,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NeSpace.sm)) {
                QuickCollection("Любимое", trackCount(likedCount), NeIcon.HeartFill, Modifier.weight(1f)) { onCollection("liked", "") }
                QuickCollection("Недавнее", trackCount(recent.size), NeIcon.Clock, Modifier.weight(1f)) { onCollection("recent", "") }
            }
        }
        if (featured != null) {
            item {
                ContinueCard(
                    featured, if (recent.isNotEmpty()) "ПРОДОЛЖИТЬ СЛУШАТЬ" else "ИЗ МЕДИАТЕКИ",
                    onPlay = { state.playTrack(featured, featuredQueue); onPlayer() },
                    onShuffle = { state.play(featuredQueue, shuffled = true); onPlayer() },
                )
            }
        } else {
            item {
                // An empty library is a designed state now, not a leftover: the app ships no audio.
                EmptyState(
                    NeIcon.Music, "Добавь свою музыку",
                    "Выбери один или несколько файлов — они появятся здесь и в медиатеке.",
                    action = "Добавить музыку", onAction = actions.import,
                )
            }
        }
        if (settings.showRecentPanel && recent.isNotEmpty()) {
            item { SectionHeader("Вернуться к музыке", action = "Все", onAction = { onCollection("recent", "") }) }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = NeSpace.xs)) {
                        val shown = recent.take(3)
                        shown.forEachIndexed { index, track ->
                            TrackRow(
                                track, playback.track?.id == track.id, playback.isPlaying,
                                { state.playTrack(track, recent) },
                                modifier = Modifier.padding(horizontal = NeRow.panelInset), onMore = { state.contextTrack = track },
                            )
                            if (index < shown.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = NeRow.panelInset), color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }
        }
        if (settings.showAlbumRow && albums.isNotEmpty()) {
            item { SectionHeader("Альбомы", action = "Медиатека", onAction = onAlbums, actionTag = "homeAlbumsAction") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NeSpace.md)) {
                    items(albums, key = { ListKeys.album(it.first.first, it.first.second) }) { (meta, tracks) ->
                        AlbumCard(
                            meta.second, meta.first, tracks.first(),
                            { onCollection("album", meta.first + "\u0000" + meta.second) },
                            Modifier.width(132.dp),
                        )
                    }
                }
            }
        }
        if (library.tracks.isNotEmpty()) {
            item {
                ImportPanel(onClick = actions.import)
            }
        }
    }
}

@Composable private fun Modifier.semanticsAvatar() = this.semantics { contentDescription = "Профиль и аватар" }

/**
 * The hero, as a row.
 *
 * A full-bleed 200dp banner needs a real photograph to justify itself; with generated fallback art it
 * was a large empty rectangle. A cover + two lines + the play action carries the same information in
 * roughly half the height, and the cover finally respects the round-art preference.
 */
@Composable
private fun ContinueCard(track: Track, caption: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val tint = accent()
    GlassPanel(Modifier.fillMaxWidth().heightIn(min = 116.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(NeSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumb(track, 72.dp, 20.dp)
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText(
                    caption, size = NeType.micro, lineHeight = NeType.microLine,
                    color = Ne.Faint, tracking = 1.1.sp, weight = FontWeight.Bold, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(
                    track.title, size = NeType.section, lineHeight = NeType.sectionLine,
                    weight = FontWeight.Bold, tracking = (-0.3).sp, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(
                    track.artist, size = NeType.caption, lineHeight = NeType.captionLine,
                    color = Ne.Dim, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NeSpace.sm)) {
                    Row(
                        Modifier.clip(CircleShape).background(tint).clickable(onClick = onPlay)
                            .heightIn(min = NeTouch.min).padding(horizontal = NeSpace.lg, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeIconView(NeIcon.Play, NeIconSize.xs, Ne.Black)
                        Spacer(Modifier.width(6.dp))
                        NeText(
                            "Слушать", size = NeType.caption, lineHeight = NeType.captionLine,
                            weight = FontWeight.Bold, color = Ne.Black,
                        )
                    }
                    NeIconButton(
                        NeIcon.Shuffle, "Слушать вперемешку", onShuffle,
                        size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim,
                    )
                }
            }
        }
    }
}

/** Permanent entry point for the document picker: one file or a whole selection at once. */
@Composable
private fun ImportPanel(onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(NeRow.leadingIcon), contentAlignment = Alignment.Center) {
                NeIconView(NeIcon.Plus, NeIconSize.lg, accent())
            }
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText("Импорт треков", size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(NeSpace.xxs))
                NeText("Один файл или сразу несколько", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
            }
            NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
        }
    }
}

@Composable
private fun QuickCollection(title: String, count: String, icon: NeIcon, modifier: Modifier, onClick: () -> Unit) {
    GlassPanel(modifier.heightIn(min = 64.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NeSpace.md, vertical = NeSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeIconView(icon, NeIconSize.lg, accent())
            Spacer(Modifier.width(NeSpace.sm))
            Column(Modifier.weight(1f)) {
                NeText(title, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
                NeText(count, size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Dim, maxLines = 1)
            }
        }
    }
}
