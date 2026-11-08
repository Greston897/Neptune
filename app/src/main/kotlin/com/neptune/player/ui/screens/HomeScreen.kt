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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*
import java.util.Calendar

@Composable
fun HomeScreen(
    state: NeptuneState, onCollection: (String, String) -> Unit, onPlayer: () -> Unit,
    onProfile: () -> Unit, onSettings: () -> Unit,
) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val actions = LocalLibraryActions.current
    val recent = remember(users.recent, library.tracks) { val map = library.byId; users.recent.mapNotNull(map::get) }
    val albums = remember(library.tracks) { LibraryLogic.albums(library.tracks) }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Доброй ночи"
    }
    val featured = recent.firstOrNull() ?: library.tracks.firstOrNull()
    val featuredQueue = if (recent.isNotEmpty()) recent else library.tracks

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.xs + LocalTopInset.current, bottom = LocalBottomInset.current,
        ),
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
        item {
            Column(Modifier.padding(top = NeSpace.lg, bottom = NeSpace.xl)) {
                NeText(
                    "$greeting, ${settings.profileName}", size = NeType.caption, lineHeight = NeType.captionLine,
                    color = Ne.Dim, maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                NeText(
                    "Твоя музыка", size = NeType.display, lineHeight = NeType.displayLine,
                    weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 1,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                verticalAlignment = Alignment.CenterVertically) {
                QuickCollection("Любимое", trackCount(users.liked.size), NeIcon.HeartFill, Modifier.weight(1f)) { onCollection("liked", "") }
                QuickCollection("Недавнее", trackCount(recent.size), NeIcon.Clock, Modifier.weight(1f)) { onCollection("recent", "") }
            }
        }
        if (featured != null) {
            item {
                // Minimum height, not a fixed one: at a large font scale the text column grows and
                // the card grows with it instead of clipping the "Слушать" button.
                GlassPanel(Modifier.fillMaxWidth().padding(top = NeSpace.md, bottom = NeSpace.xxl).heightIn(min = 200.dp)) {
                    TrackArtwork(featured, Modifier.matchParentSize(), 0.dp, animate = playback.isPlaying)
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.90f),
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.10f),
                                ),
                            ),
                        ),
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(NeSpace.xl).fillMaxWidth(0.78f)) {
                        NeText(
                            if (recent.isNotEmpty()) "ПРОДОЛЖИТЬ СЛУШАТЬ" else "ИЗ МЕДИАТЕКИ",
                            size = NeType.micro, lineHeight = NeType.microLine,
                            color = Color.White.copy(alpha = 0.66f), tracking = 1.2.sp, weight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(NeSpace.sm))
                        NeText(
                            featured.title, size = NeType.headline, lineHeight = NeType.headlineLine,
                            weight = FontWeight.ExtraBold, tracking = (-0.5).sp, maxLines = 2,
                        )
                        Spacer(Modifier.height(NeSpace.xs))
                        NeText(
                            featured.artist, size = NeType.caption, lineHeight = NeType.captionLine,
                            color = Color.White.copy(alpha = 0.70f), maxLines = 1,
                        )
                        Spacer(Modifier.height(NeSpace.lg))
                        Row(
                            Modifier.clip(CircleShape).background(Color.White)
                                .clickable { state.playTrack(featured, featuredQueue); onPlayer() }
                                .padding(horizontal = NeSpace.lg, vertical = NeSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NeIconView(NeIcon.Play, NeIconSize.xs, Ne.Black)
                            Spacer(Modifier.width(6.dp))
                            NeText(
                                "Слушать", size = NeType.caption, lineHeight = NeType.captionLine,
                                weight = FontWeight.Bold, color = Ne.Black,
                            )
                        }
                    }
                }
            }
        } else {
            item {
                EmptyState(
                    NeIcon.Music, "Добавь свою музыку",
                    "Разреши доступ к аудиофайлам или импортируй треки — и главная оживёт.",
                    action = "Добавить музыку", onAction = actions.import,
                )
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Вернуться к музыке", action = "Все", onAction = { onCollection("recent", "") }) }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = NeSpace.xs)) {
                        val shown = recent.take(4)
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
            item { Spacer(Modifier.height(NeSpace.xl)) }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Альбомы", action = "Медиатека", onAction = { onCollection("albums", "") }) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                    contentPadding = PaddingValues(vertical = NeSpace.sm),
                ) {
                    items(albums, key = { ListKeys.album(it.first.first, it.first.second) }) { (meta, tracks) ->
                        AlbumCard(
                            meta.second, meta.first, tracks.first(),
                            { onCollection("album", meta.first + "\u0000" + meta.second) },
                            Modifier.width(148.dp),
                        )
                    }
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth().padding(top = NeSpace.xl).clickable { onCollection("demo", "") }) {
                Row(Modifier.fillMaxWidth().padding(NeSpace.lg), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(NeRow.leadingIcon), contentAlignment = Alignment.Center) {
                        NeIconView(NeIcon.Wave, NeIconSize.lg, accent())
                    }
                    Spacer(Modifier.width(NeSpace.md))
                    Column(Modifier.weight(1f)) {
                        NeText("Демо-треки", size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.height(NeSpace.xxs))
                        NeText("Работают без разрешений", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                    }
                    Spacer(Modifier.width(NeSpace.sm))
                    NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
                }
            }
        }
    }
}

@Composable private fun Modifier.semanticsAvatar() = this.semantics { contentDescription = "Профиль и аватар" }

@Composable
private fun QuickCollection(title: String, count: String, icon: NeIcon, modifier: Modifier, onClick: () -> Unit) {
    GlassPanel(modifier.heightIn(min = 76.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeIconView(icon, NeIconSize.xl, accent())
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText(title, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(count, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
            }
        }
    }
}
