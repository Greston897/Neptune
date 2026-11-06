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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.Track
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*
import java.util.Calendar

@Composable
fun HomeScreen(state: NeptuneState, onCollection: (String, String) -> Unit, onPlayer: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val recent = remember(users.recent, library.tracks) { val map = library.byId; users.recent.mapNotNull(map::get) }
    val albums = remember(library.tracks) { LibraryLogic.albums(library.tracks) }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Доброй ночи" }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(com.neptune.player.model.AvatarStyle.ORBIT, Ne.Text, Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp)); NeText("neptune", size = 17.sp, weight = FontWeight.ExtraBold, tracking = (-0.7).sp)
                }
                Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                    ProfileAvatar(Modifier.size(36.dp).semanticsAvatar(), animated = true)
                }
                NeIconButton(NeIcon.Settings, "Настройки", onSettings, modifier = Modifier.align(Alignment.CenterEnd), iconSize = 21.dp, color = Ne.Dim)
            }
        }
        item {
            Column(Modifier.padding(top = 22.dp, bottom = 24.dp)) {
                NeText("$greeting, ${settings.profileName}", size = 12.sp, color = Ne.Dim)
                Spacer(Modifier.height(8.dp))
                NeText("Твоя музыка.\nТвой ритм.", size = 36.sp, weight = FontWeight.ExtraBold, tracking = (-1.4).sp, maxLines = 2, lineHeight = 41.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCollection("Любимое", "${users.liked.size}", NeIcon.HeartFill, Modifier.weight(1f)) { onCollection("liked", "") }
                QuickCollection("Недавнее", "${recent.size}", NeIcon.Clock, Modifier.weight(1f)) { onCollection("recent", "") }
            }
        }
        item {
            val featured = recent.firstOrNull() ?: library.tracks.firstOrNull()
            if (featured != null) {
                Box(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 24.dp).height(204.dp).clip(RoundedCornerShape(neRadius())).background(Ne.Ink).clickable { state.playTrack(featured, if (recent.isNotEmpty()) recent else library.tracks); onPlayer() }) {
                    TrackArtwork(featured, Modifier.fillMaxSize(), 0.dp, animate = playback.isPlaying)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.86f), Color.Black.copy(alpha = 0.2f), Color.Transparent))))
                    Column(Modifier.align(Alignment.CenterStart).padding(22.dp).fillMaxWidth(0.65f)) {
                        NeText("В ТВОЕЙ ОРБИТЕ", size = 8.5.sp, color = Color.White.copy(alpha = 0.7f), tracking = 1.5.sp)
                        Spacer(Modifier.height(12.dp))
                        NeText(featured.title, size = 27.sp, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 2, lineHeight = 31.sp)
                        Spacer(Modifier.height(5.dp)); NeText(featured.artist, size = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.clip(CircleShape).background(Color.White).clickable { state.playTrack(featured, if (recent.isNotEmpty()) recent else library.tracks); onPlayer() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NeIconView(NeIcon.Play, 15.dp, Ne.Black); Spacer(Modifier.width(7.dp)); NeText("Слушать", size = 12.sp, weight = FontWeight.Bold, color = Ne.Black)
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Вернуться к музыке", action = "Все", onAction = { onCollection("recent", "") }) }
            items(recent.take(5), key = { "recent-${it.id}" }) { track ->
                TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, recent) }, onMore = { state.contextTrack = track })
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        item { SectionHeader("Альбомы для тебя", action = "Медиатека", onAction = { onCollection("albums", "") }) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(albums, key = { it.first.first + it.first.second }) { (meta, tracks) ->
                    AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.width(154.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(neRadius())).background(Ne.Ink).clickable { onCollection("demo", "") }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                NeIconView(NeIcon.Wave, 26.dp, accent()); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { NeText("Neptune Sessions", size = 14.sp, weight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); NeText("Шесть оригинальных аудиосессий", size = 11.sp, color = Ne.Dim) }
                NeIconView(NeIcon.ChevronRight, 17.dp, Ne.Faint)
            }
        }
    }
}

@Composable private fun Modifier.semanticsAvatar() = this.semantics { contentDescription = "Профиль и аватар" }

@Composable
private fun QuickCollection(title: String, count: String, icon: NeIcon, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(66.dp).clip(RoundedCornerShape(neRadius())).background(Ne.Ink).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        NeIconView(icon, 21.dp, accent()); Spacer(Modifier.width(11.dp))
        Column { NeText(title, size = 12.5.sp, weight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); NeText(com.neptune.player.model.trackCount(count.toInt()), size = 10.sp, color = Ne.Faint) }
    }
}
