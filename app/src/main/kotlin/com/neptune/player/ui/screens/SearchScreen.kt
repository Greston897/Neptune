package com.neptune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.data.LibraryLogic
import com.neptune.player.model.RemoteAlbum

import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

/**
 * One field, two catalogues.
 *
 * The screen used to search only the phone's files. With the Yandex Music catalogue added, a second
 * search field would have been the easy answer and the wrong one: the user does not care *where* a
 * song is, they care that typing it gives it back. So there is one query, and the results are the
 * union — local matches first (they are instant and cannot fail), then whatever the catalogue found.
 *
 * Three honest states the old screen could not express:
 *
 *  * **offline / failed** — the local half still renders, and the remote half says what happened
 *    instead of pretending the song does not exist;
 *  * **a source is off** — the section header says which catalogue is being searched, so an empty
 *    result is never mysterious;
 *  * **the service corrected the spelling** — the hint is offered as a tap, not applied silently.
 */
@Composable
fun SearchScreen(state: NeptuneState, onCollection: (String, String) -> Unit, onRemoteAlbum: (RemoteAlbum) -> Unit = {}) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val streams by state.streams.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    var filter by remember { mutableIntStateOf(0) }
    val local = remember(state.search, library.tracks, settings.sourceLocal) { state.localResults() }
    val remote = remember(streams.results, settings.sourceYandex) { state.remoteResults() }
    val albums = remember(local) { LibraryLogic.albums(local) }
    val artists = remember(local) { LibraryLogic.artists(local) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("searchList"),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen,
            top = NeSpace.md + LocalTopInset.current, bottom = LocalBottomInset.current,
        ),
    ) {
        item { NeText("Поиск", Modifier.padding(vertical = NeSpace.sm), size = NeType.headline, lineHeight = NeType.headlineLine, weight = FontWeight.ExtraBold, tracking = (-0.6).sp) }
        item {
            GlassPanel(Modifier.fillMaxWidth().heightIn(min = NeRow.field)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = NeSpace.lg, vertical = NeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeIconView(NeIcon.Search, NeIconSize.lg, Ne.Dim)
                    Spacer(Modifier.width(NeSpace.md))
                    // The field's own text style must match NeText's line box, otherwise the typed
                    // text and the placeholder sit on different baselines inside the same row.
                    BasicTextField(
                        value = state.search,
                        onValueChange = { state.setSearch(it.take(100)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = neFieldTextStyle(),
                        cursorBrush = SolidColor(accent()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { state.submitSearch() }),
                        decorationBox = { field ->
                            Box(Modifier.heightIn(min = NeIconSize.lg), contentAlignment = Alignment.CenterStart) {
                                if (state.search.isEmpty()) {
                                    NeText(
                                        if (settings.sourceYandex) "Трек, исполнитель или альбом" else "Поиск по медиатеке",
                                        size = NeType.body, lineHeight = NeType.bodyLine, color = Ne.Faint,
                                    )
                                }
                                field()
                            }
                        },
                    )
                    if (state.search.isNotEmpty()) {
                        Spacer(Modifier.width(NeSpace.xs))
                        NeIconButton(
                            NeIcon.Close, "Очистить поиск", { state.setSearch("") },
                            size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim,
                        )
                    }
                }
            }
        }
        if (state.search.isBlank()) {
            if (users.searches.isNotEmpty()) {
                item { SectionHeader("Недавние запросы", action = "Очистить", onAction = state::clearSearches) }
                items(users.searches) { q ->
                    Row(Modifier.fillMaxWidth().heightIn(min = NeTouch.standard).clickable { state.setSearch(q) }.padding(vertical = NeSpace.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        NeIconView(NeIcon.Clock, NeIconSize.sm, Ne.Faint); Spacer(Modifier.width(NeSpace.md))
                        NeText(q, Modifier.weight(1f), size = NeType.label, lineHeight = NeType.labelLine, color = Ne.Dim, maxLines = 1)
                        Spacer(Modifier.width(NeSpace.sm)); NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
                    }
                }
            }
            if (settings.sourceLocal && library.tracks.isNotEmpty()) {
                item { SectionHeader("Альбомы", Modifier.padding(top = NeSpace.lg)) }
                items(LibraryLogic.albums(library.tracks).chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                        verticalAlignment = Alignment.Top) {
                        row.forEach { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (!settings.sourceYandex && !settings.sourceLocal) {
                item { EmptyState(NeIcon.Globe, "Источники выключены", "Включите медиатеку или Яндекс Музыку в настройках источников.") }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = NeSpace.lg),
                    horizontalArrangement = Arrangement.spacedBy(NeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("Всё", "Треки", "Альбомы", "Артисты").forEachIndexed { i, title ->
                        NeChip(title, filter == i, { filter = i })
                    }
                }
            }

            // Where the results come from, stated once, above both halves.
            item {
                NeText(
                    when {
                        settings.sourceLocal && settings.sourceYandex -> "МЕДИАТЕКА И ЯНДЕКС МУЗЫКА"
                        settings.sourceYandex -> "ЯНДЕКС МУЗЫКА"
                        else -> "ТОЛЬКО МЕДИАТЕКА"
                    },
                    Modifier.padding(bottom = NeSpace.sm),
                    size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, tracking = 1.2.sp,
                    weight = FontWeight.Bold, maxLines = 1,
                )
            }

            // ---- phone --------------------------------------------------------------
            if (settings.sourceLocal && local.isNotEmpty() && (filter == 0 || filter == 1)) {
                item { SectionHeader("На телефоне", trackCount(local.size)) }
                items(local, key = { ListKeys.track(it.id) }) { track ->
                    TrackRow(track, playback.track?.id == track.id, playback.isPlaying, { state.playTrack(track, local); state.submitSearch() }, onMore = { state.contextTrack = track })
                }
            }
            if (settings.sourceLocal && local.isNotEmpty() && (filter == 0 || filter == 2)) {
                items(albums.chunked(2), key = { ListKeys.album(it.first().first.first, it.first().first.second) }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(NeSpace.md),
                        verticalAlignment = Alignment.Top) {
                        row.forEach { (meta, tracks) -> AlbumCard(meta.second, meta.first, tracks.first(), { onCollection("album", "${meta.first}\u0000${meta.second}") }, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (settings.sourceLocal && local.isNotEmpty() && (filter == 0 || filter == 3)) {
                items(artists, key = { ListKeys.artist(it.first) }) { (artist, tracks) ->
                    Panel(Modifier.padding(vertical = NeSpace.xs)) { SettingRow(artist, trackCount(tracks.size), NeIcon.Artist, onClick = { onCollection("artist", artist) }) }
                }
            }

            // ---- catalogue ----------------------------------------------------------
            if (settings.sourceYandex) {
                if (streams.searchLoading && remote.isEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.xl), verticalAlignment = Alignment.CenterVertically) {
                            NeIconView(NeIcon.Globe, NeIconSize.sm, Ne.Faint)
                            Spacer(Modifier.width(NeSpace.sm))
                            NeText("Ищу в Яндекс Музыке…", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                        }
                    }
                }
                streams.searchError?.let { message ->
                    item {
                        Panel(Modifier.padding(vertical = NeSpace.sm)) {
                            SettingRow(
                                "Яндекс Музыка не ответила", message, NeIcon.Globe,
                                trailing = { NeText("Повторить", size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.Bold) },
                                onClick = { state.retrySearch() },
                            )
                        }
                    }
                }
                streams.corrected?.let { fixed ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = NeSpace.sm).clickable { state.setSearch(fixed) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NeText("Возможно: ", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1)
                            NeText(fixed, size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                if (remote.isNotEmpty() && (filter == 0 || filter == 1)) {
                    item {
                        SectionHeader(
                            "Яндекс Музыка", trackCount(remote.size),
                            action = "Слушать всё", onAction = { state.playRemoteAll(remote) },
                        )
                    }
                    items(remote, key = { ListKeys.track(it.uriId) }) { track ->
                        RemoteTrackRow(
                            track = track,
                            current = playback.track?.id == track.uriId,
                            playing = playback.isPlaying,
                            liked = track.uriId in users.liked,
                            onPlay = { state.playRemote(track, remote); state.submitSearch() },
                            onLike = { state.likeRemote(track) },
                            onMore = { state.rememberRemote(listOf(track)); state.contextRemote = track },
                            progress = streams.downloading[track.uriId],
                            saved = streams.isDownloaded(track.uriId),
                            onDownload = { state.download(track) },
                        )
                    }
                }
                if (remote.isNotEmpty() && (filter == 0 || filter == 2)) {
                    items(streams.results.albums, key = { ListKeys.remoteAlbum(it.id) }) { album ->
                        RemoteAlbumRow(album) { onRemoteAlbum(album) }
                    }
                }
                if (remote.isNotEmpty() && (filter == 0 || filter == 3)) {
                    items(streams.results.artists, key = { ListKeys.remoteArtist(it.id) }) { artist ->
                        Panel(Modifier.padding(vertical = NeSpace.xs)) {
                            SettingRow(artist.name, trackCount(artist.trackCount), NeIcon.Artist) { state.openRemoteArtist(artist.id) }
                        }
                    }
                }
                if (!streams.searchLoading && streams.searchError == null && streams.results.isEmpty && state.search.length >= 2) {
                    item { EmptyState(NeIcon.Search, "Ничего не найдено", "Ни в медиатеке, ни в Яндекс Музыке. Попробуйте другое название или имя исполнителя.", action = "Сбросить", onAction = { state.setSearch("") }) }
                }
            } else if (local.isEmpty()) {
                item { EmptyState(NeIcon.Search, "Ничего не найдено", "Поиск работает по медиатеке телефона — Яндекс Музыка выключена в источниках.", action = "Сбросить", onAction = { state.setSearch("") }) }
            }
        }
    }
}

/**
 * A streamed result.
 *
 * Differs from [TrackRow] in exactly two ways, and both matter: the heart saves it (a stream can be
 * loved without being owned), and the subtitle carries the album, because that is what tells two
 * identical titles by the same artist apart.
 */
@Composable
private fun RemoteTrackRow(
    track: RemoteTrack,
    current: Boolean,
    playing: Boolean,
    liked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    progress: Float? = null,
    saved: Boolean = false,
    onDownload: () -> Unit = {},
) {
    val tint = accent()
    Row(
        Modifier.fillMaxWidth().heightIn(min = NeRow.track).clickable(onClickLabel = "Играть", onClick = onPlay).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover art arrives as a URL; the brand mark stands in until it decodes (or forever, offline).
        RemoteCover(track.artworkUri, track.id.hashCode(), Modifier.size(NeRow.thumb), 14.dp)
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeText(
                    track.title, Modifier.weight(1f, fill = false), size = NeType.body, lineHeight = NeType.bodyLine,
                    weight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (current) tint else Ne.Text, maxLines = 1,
                )
                if (track.preview > 0) {
                    Spacer(Modifier.width(NeSpace.sm))
                    NeText("30 сек", size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, maxLines = 1)
                }
            }
            Spacer(Modifier.height(NeSpace.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) { EqualizerBars(playing, color = tint, maxHeight = 10.dp); Spacer(Modifier.width(6.dp)) }
                NeText(
                    track.artists, size = NeType.caption, lineHeight = NeType.captionLine,
                    color = if (current) tint.copy(alpha = 0.62f) else Ne.Dim, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // Offline state, then the heart, then the sheet. Three 44dp targets is already the most a
        // row can hold without pushing the title into an ellipsis on a 360dp screen.
        when {
            progress != null -> NeText(
                if (progress < 0) "…" else "${(progress * 100).toInt()}%",
                Modifier.widthIn(min = 40.dp).padding(end = NeSpace.xs),
                size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Dim, align = TextAlign.End,
            )
            saved -> NeIconButton(NeIcon.Check, "Сохранено для офлайна", onDownload, size = NeTouch.min, iconSize = NeIconSize.sm, color = tint)
            else -> NeIconButton(NeIcon.Download, "Скачать для офлайна", onDownload, size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim)
        }
        NeIconButton(
            if (liked) NeIcon.HeartFill else NeIcon.Heart, if (liked) "Убрать из любимого" else "В любимое",
            onLike, size = NeTouch.min, iconSize = NeIconSize.sm,
            color = if (liked) tint else Ne.Dim,
        )
        NeIconButton(NeIcon.More, "Действия с ${track.title}", onMore, size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim)
    }
}

/** A streamed album as a row rather than a tile: search results are a list, not a grid. */
@Composable
private fun RemoteAlbumRow(album: RemoteAlbum, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = NeRow.compact).clickable(onClick = onClick).padding(vertical = NeSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCover(album.artworkUri, album.id.hashCode(), Modifier.size(NeRow.thumbCompact), 12.dp)
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            NeText(album.title, size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(NeSpace.xxs))
            NeText(
                listOfNotNull(album.artist, album.year?.toString(), "${album.trackCount} трек(ов)").joinToString(" · "),
                size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1,
            )
        }
        NeIconView(NeIcon.ChevronRight, NeIconSize.xs, Ne.Whisper)
    }
}
