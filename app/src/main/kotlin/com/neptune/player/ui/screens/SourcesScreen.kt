package com.neptune.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

/**
 * Where the music comes from.
 *
 * 4.0 has two catalogues and they behave differently, so the screen states the difference instead of
 * offering two anonymous switches:
 *
 *  * **Медиатека телефона** — files the app can already play, works offline, no account, nothing
 *    leaves the device;
 *  * **Яндекс Музыка** — search, charts and streaming. Works anonymously, which is why the free tier
 *    is genuinely free here: no card, no trial, no in-app purchase. A token is optional and only
 *    changes what the *service* gives back (the account's own charts and higher bitrates).
 *
 * Turning the last source off is refused, and the refusal is a sentence in the pill rather than a
 * switch that silently springs back.
 */
@Composable
fun SourcesScreen(state: NeptuneState, onBack: () -> Unit, onCharts: () -> Unit = {}) {
    val streams by state.streams.collectAsStateWithLifecycle()
    val library by state.library.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    var tokenField by remember { mutableStateOf("") }
    var editingToken by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("sourcesList"),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen,
            top = NeSpace.md + LocalTopInset.current, bottom = LocalBottomInset.current,
        ),
    ) {
        item { ScreenTitle("Источники", onBack, "Две библиотеки в одном поиске.") }

        item {
            SettingsGroup("ГДЕ ИСКАТЬ") {
                ToggleRow(
                    "Медиатека телефона",
                    if (library.tracks.isEmpty()) "Файлов пока нет — можно добавить в любой момент"
                    else "${library.tracks.size} трек(ов) на устройстве · работает без сети",
                    settings.sourceLocal,
                ) { state.setSource(local = true, on = it) }
                ToggleRow(
                    "Яндекс Музыка",
                    if (settings.sourceYandex) "Поиск, чарты и прослушивание · бесплатно, без карты"
                    else "Каталог отключён — поиск идёт только по телефону",
                    settings.sourceYandex,
                ) { state.setSource(local = false, on = it) }
            }
        }

        item {
            SettingsGroup("ЧАРТЫ") {
                SettingRow(
                    "Чарты Яндекс Музыки",
                    if (streams.chart != null) "${streams.chart!!.title} · ${streams.chart!!.entries.size} позиций"
                    else "Позиции и число слушателей — от сервиса",
                    NeIcon.Trending, onClick = onCharts,
                )
            }
        }

        if (settings.sourceYandex) {
            item {
                SettingsGroup("АККАУНТ ЯНДЕКС МУЗЫКИ") {
                    SettingRow(
                        streams.account.label,
                        when {
                            streams.accountBusy -> "Проверяю токен…"
                            !streams.account.present -> "Необязательно: без него тоже играет — на бесплатном тарифе"
                            streams.account.plus -> "Plus активен · максимальное качество"
                            streams.account.verified -> "Без Plus · свободный тариф сервиса"
                            else -> streams.account.error ?: "Токен не проверен"
                        },
                        NeIcon.Login,
                        trailing = {
                            NeText(
                                if (streams.account.present) "Изменить" else "Подключить",
                                size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        },
                    ) { editingToken = true }
                }
            }
        }

        item {
            // The honest paragraph. It is here rather than in a legal screen nobody opens because the
            // app relies on endpoints that were never meant for third parties, and saying so is the
            // only way the failure mode ("sometimes the catalogue is unavailable") is not a surprise.
            NeText(
                "Поиск и прослушивание в Яндекс Музыке работают бесплатно: приложение обращается к сервису " +
                    "напрямую, без своей подписки и без оплаты внутри приложения. Часть треков сервис " +
                    "отдаёт только по своей подписке Plus — тогда плеер скажет об этом прямо. " +
                    "Каталог использует непубличные адреса сервиса: если они изменятся, каталог " +
                    "перестанет работать, а медиатека телефона — нет.",
                Modifier.padding(top = NeSpace.xl),
                size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 8,
            )
        }

        item {
            SettingRow(
                "Отключить аккаунт",
                if (streams.account.present) "Токен удалится с этого устройства" else "Аккаунт не подключён",
                NeIcon.Close,
                onClick = if (streams.account.present) ({ state.setYandexToken(null) }) else null,
            )
        }
    }

    if (editingToken) TokenDialog(
        initial = streams.account.token ?: "",
        onDismiss = { editingToken = false },
    ) { value ->
        editingToken = false
        state.setYandexToken(value)
    }
}

/**
 * Paste-a-token dialog.
 *
 * A token, not a login form: the app has no OAuth client of its own and must not pretend otherwise.
 * The line under the field says that the token is the user's and stays on the device, because a
 * credential prompt that does not say where the credential goes is the one thing to avoid here.
 */
@Composable
private fun TokenDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ne.Elevated,
        title = {
            NeText(
                if (initial.isBlank()) "Подключить аккаунт" else "Новый токен",
                size = NeType.title, lineHeight = NeType.titleLine,
                weight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2,
            )
        },
        text = {
            Column {
                NeTextField(value, { value = it.take(200) }, "OAuth-токен Яндекс Музыки")
                Spacer(Modifier.height(NeSpace.md))
                NeText(
                    "Токен выдаётся вашим аккаунтом Яндекса и хранится только на этом устройстве. " +
                        "Без него приложение работает на анонимном доступе.",
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { NeText("Сохранить", color = accent()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { NeText("Отмена", color = Ne.Dim) } },
    )
}
