package com.neptune.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.ChartEntry
import com.neptune.player.model.RemoteTrack
import com.neptune.player.model.trackCount
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

/**
 * The charts, as the service publishes them.
 *
 * ## Why the menu is data
 *
 * Yandex decides which charts run (world, Russia, and a rotating set of genre and city ones). Hardcoding
 * "Мировой / Россия" chips would have been wrong within a month, so the chips *are* the menu the
 * response carries: title, request postfix, and which one is current. A chart that does not exist any
 * more simply stops being offered.
 *
 * ## Why positions are shown and shifts are not overstated
 *
 * The row's number is the position, and a shift is drawn only when it is non-zero — ▲ on the way up,
 * ▼ on the way down, nothing when it held. Listeners are formatted for reading (1,2 млн), never
 * spelled out to the last unit; nobody reads a chart to count people.
 */
@Composable
fun ChartScreen(state: NeptuneState, onBack: () -> Unit) {
    val streams by state.streams.collectAsStateWithLifecycle()
    val playback by state.playback.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val settings = LocalSettings.current

    // Opening the screen is the request. Nothing is fetched on app start: a chart is a network call
    // and the first screen should never wait for one.
    LaunchedEffect(Unit) { if (settings.sourceYandex) state.loadChart() }

    val page = streams.chart
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("chartList"),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen,
            top = NeSpace.md + LocalTopInset.current, bottom = LocalBottomInset.current,
        ),
    ) {
        item {
            ScreenTitle(
                page?.title ?: "Чарты", onBack,
                page?.description ?: "Что слушают прямо сейчас — по данным Яндекс Музыки.",
            )
        }

        if (!settings.sourceYandex) {
            item {
                EmptyState(
                    NeIcon.Globe, "Яндекс Музыка выключена",
                    "Чарты приходят из каталога. Включите Яндекс Музыку в настройках источников — медиатека при этом не пострадает.",
                    action = "Включить", onAction = { state.setSource(local = false, on = true) },
                )
            }
            return@LazyColumn
        }

        if (page == null && streams.chartLoading) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = NeSpace.xxl), verticalAlignment = Alignment.CenterVertically) {
                    NeIconView(NeIcon.Trending, NeIconSize.lg, accent())
                    Spacer(Modifier.width(NeSpace.md))
                    NeText("Загружаю чарт…", size = NeType.body, lineHeight = NeType.bodyLine, color = Ne.Dim, maxLines = 1)
                }
            }
        }

        streams.chartError?.let { message ->
            item {
                Panel(Modifier.padding(vertical = NeSpace.sm)) {
                    SettingRow(
                        "Чарт не загрузился", message, NeIcon.Globe,
                        trailing = { NeText("Повторить", size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.Bold) },
                        onClick = { state.loadChart(force = true) },
                    )
                }
            }
            item {
                NeText(
                    "Локальная музыка работает и без сети — чарты вернутся, когда связь появится.",
                    Modifier.padding(vertical = NeSpace.sm),
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 3,
                )
            }
        }

        if (page != null) {
            if (page.menu.items.size > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = NeSpace.lg),
                        horizontalArrangement = Arrangement.spacedBy(NeSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        page.menu.items.forEach { item ->
                            // `selected` from the service is the current chart; the postfix is what a
                            // tap asks for. Both come from the response, neither is guessed.
                            NeChip(
                                text = item.title,
                                selected = item.selected || item.postfix == streams.chartPostfix,
                                onClick = { state.loadChart(item.postfix) },
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = NeSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    NeText(trackCount(page.entries.size), Modifier.weight(1f), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 1)
                    NeButton("Слушать чарт", { state.playRemoteAll(page.entries.map { it.track }) }, icon = NeIcon.Play)
                }
            }
            items(page.entries, key = { ListKeys.track(it.track.uriId) }) { entry ->
                ChartRow(
                    entry = entry,
                    current = playback.track?.id == entry.track.uriId,
                    playing = playback.isPlaying,
                    liked = entry.track.uriId in users.liked,
                    onPlay = { state.playRemote(entry.track, page.entries.map { it.track }) },
                    onLike = { state.likeRemote(entry.track) },
                    onMore = { state.rememberRemote(listOf(entry.track)); state.contextRemote = entry.track },
                    progress = streams.downloading[entry.track.uriId],
                    saved = streams.isDownloaded(entry.track.uriId),
                    onDownload = { state.download(entry.track) },
                )
            }
            item {
                NeText(
                    "Данные и обложки предоставлены Яндекс Музыкой. Позиции и число слушателей обновляет сервис.",
                    Modifier.padding(top = NeSpace.xl),
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 3,
                )
            }
        }
    }
}

/**
 * One chart position.
 *
 * The number is fixed-width so the column of positions reads as a column; the artwork is the same
 * token as everywhere else, and a track that is a 30-second preview says so here rather than stopping
 * the audio by surprise.
 */
@Composable
private fun ChartRow(
    entry: ChartEntry,
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
    val track: RemoteTrack = entry.track
    Row(
        Modifier.fillMaxWidth().heightIn(min = NeRow.track).clickable(onClickLabel = "Играть", onClick = onPlay).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            NeText(
                "${entry.position}", size = NeType.body, lineHeight = NeType.bodyLine,
                weight = FontWeight.ExtraBold, color = if (current) tint else Ne.Dim, maxLines = 1,
            )
        }
        Spacer(Modifier.width(NeSpace.sm))
        RemoteCover(track.artworkUri, track.id.hashCode(), Modifier.size(NeRow.thumbCompact), 12.dp)
        Spacer(Modifier.width(NeSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeText(
                    track.title, Modifier.weight(1f, fill = false), size = NeType.body, lineHeight = NeType.bodyLine,
                    weight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (current) tint else Ne.Text, maxLines = 1,
                )
                if (entry.shift != 0) {
                    Spacer(Modifier.width(NeSpace.xs))
                    NeIconView(if (entry.shift > 0) NeIcon.ArrowUp else NeIcon.ArrowDown, NeIconSize.xs,
                        if (entry.shift > 0) tint else Ne.Faint)
                }
                // A chart is where previews are most visible: the top of the chart is full of tracks the
                // service will not hand over whole, and a row that does not say so looks like a bug when
                // the song stops at half a minute.
                if (track.preview > 0) {
                    Spacer(Modifier.width(NeSpace.xs))
                    PreviewBadge(track.preview)
                }
            }
            Spacer(Modifier.height(NeSpace.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) { EqualizerBars(playing, color = tint, maxHeight = 10.dp); Spacer(Modifier.width(6.dp)) }
                NeText(
                    listOfNotNull(track.artists, listeners(entry.listeners)).joinToString(" · "),
                    size = NeType.caption, lineHeight = NeType.captionLine,
                    color = if (current) tint.copy(alpha = 0.62f) else Ne.Dim, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
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
            if (liked) NeIcon.HeartFill else NeIcon.Heart,
            if (liked) "Убрать из любимого" else "В любимое",
            onLike, size = NeTouch.min, iconSize = NeIconSize.sm, color = if (liked) tint else Ne.Dim,
        )
        NeIconButton(NeIcon.More, "Действия с ${track.title}", onMore, size = NeTouch.min, iconSize = NeIconSize.sm, color = Ne.Dim)
    }
}

/**
 * "1,2 млн слушателей" — a number a person can read at a glance.
 *
 * Ru plural rules, because "1,2 млн слушателей" and "2 слушателя" must not come out of one template.
 */
internal fun listeners(count: Int): String {
    if (count <= 0) return ""
    val text = when {
        count >= 1_000_000 -> "%.1f".format(count / 1_000_000.0).replace('.', ',') + " млн"
        count >= 1_000 -> "%.1f".format(count / 1_000.0).replace('.', ',') + " тыс."
        else -> count.toString()
    }
    val tens = count % 100
    val word = when {
        tens in 11..14 -> "слушателей"
        count % 10 == 1 -> "слушатель"
        count % 10 in 2..4 -> "слушателя"
        else -> "слушателей"
    }
    return "$text $word"
}

