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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun HomeScreen(state: NeptuneState, onCollection: (String, String) -> Unit, onPlayer: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val actions = LocalLibraryActions.current
    val recent = remember(users.recent, library.tracks) { val map = library.byId; users.recent.mapNotNull(map::get) }
    val albums = remember(library.tracks) { LibraryLogic.albums(library.tracks) }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Доброй ночи" }
    val featured = recent.firstOrNull() ?: library.tracks.firstOrNull()
    val featuredQueue = if (recent.isNotEmpty()) recent else library.tracks

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = LocalBottomInset.current)) {
        item {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                    NeptuneMark(Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    NeText("neptune", size = 18.sp, weight = FontWeight.ExtraBold, tracking = (-0.7).sp)
                }
                Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                    ProfileAvatar(Modifier.size(40.dp).semanticsAvatar(), animated = true)
                }
                NeIconButton(NeIcon.Settings, "Настройки", onSettings, modifier = Modifier.align(Alignment.CenterEnd), iconSize = 21.dp, color = Ne.Dim, background = Color.White.copy(alpha = 0.06f))
            }
        }
        item {
            Column(Modifier.padding(top = 18.dp, bottom = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent()))
                    Spacer(Modifier.width(8.dp))
                    NeText("$greeting, ${settings.profileName}", size = 12.sp, color = Ne.Dim)
                }
                Spacer(Modifier.height(10.dp))
                NeText("Твоя музыка.\nТвой ритм.", size = 36.sp, weight = FontWeight.ExtraBold, tracking = (-1.4).sp, maxLines = 2, lineHeight = 41.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCollection("Любимое", trackCount(users.liked.size), NeIcon.HeartFill, Modifier.weight(1f)) { onCollection("liked", "") }
                QuickCollection("Недавнее", trackCount(recent.size), NeIcon.Clock, Modifier.weight(1f)) { onCollection("recent", "") }
            }
        }
        if (featured != null) {
            item {
                GlassPanel(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).height(224.dp)) {
                    TrackArtwork(featured, Modifier.fillMaxSize(), 0.dp, animate = playback.isPlaying)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.08f)))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                    Column(Modifier.align(Alignment.CenterStart).padding(22.dp).fillMaxWidth(0.70f)) {
                        NeText("В ТВОЕЙ ОРБИТЕ", size = 9.sp, color = Color.White.copy(alpha = 0.72f), tracking = 1.6.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        NeText(featured.title, size = 28.sp, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 2, lineHeight = 32.sp)
                        Spacer(Modifier.height(6.dp))
                        NeText(featured.artist, size = 12.sp, color = Color.White.copy(alpha = 0.72f))
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.clip(CircleShape).background(Color.White)
                            .clickable { state.playTrack(featured, featuredQueue); onPlayer() }
                            .padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            NeIconView(NeIcon.Play, 15.dp, Ne.Black)
                            Spacer(Modifier.width(8.dp))
                            NeText("Слушать", size = 12.sp, weight = FontWeight.Bold, color = Ne.Black)
                        }
                    }
                }
            }
        } else {
            item { EmptyState(NeIcon.Music, "Добавь свою музыку", "Разреши доступ к аудиофайлам или импортируй треки — и главная оживёт.", action = "Добавить музыку", onAction = actions.import) }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Вернуться к музыке", action = "Все", onAction = { onCollection("recent", "") }) }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        recent.take(4).forEachIndexed { index, track ->
                            TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, recent) },
                                modifier = Modifier.padding(horizontal = 6.dp), onMore = { state.contextTrack = track })
                            if (index < recent.take(4).lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Альбомы для тебя", action = "Медиатека", onAction = { onCollection("albums", "") }) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(albums, key = { it.first.first + it.first.second }) { (meta, tracks) ->
                        AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.width(156.dp))
                    }
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth().padding(top = 20.dp).clickable { onCollection("demo", "") }) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        NeIconView(NeIcon.Wave, 22.dp, accent())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        NeText("Neptune Sessions", size = 14.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        NeText("Шесть оригинальных аудиосессий", size = 11.sp, color = Ne.Dim)
                    }
                    NeIconView(NeIcon.ChevronRight, 17.dp, Ne.Faint)
                }
            }
        }
    }
}

@Composable private fun Modifier.semanticsAvatar() = this.semantics { contentDescription = "Профиль и аватар" }

@Composable
private fun QuickCollection(title: String, count: String, icon: NeIcon, modifier: Modifier, onClick: () -> Unit) {
    GlassPanel(modifier.height(86.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(accent().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                NeIconView(icon, 19.dp, accent())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NeText(title, size = 13.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                NeText(count, size = 10.5.sp, color = Ne.Faint)
            }
            NeIconView(NeIcon.ChevronRight, 15.dp, Ne.Whisper)
        }
    }
}
