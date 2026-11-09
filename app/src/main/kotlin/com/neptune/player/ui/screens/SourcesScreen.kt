package com.neptune.player.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
    // The confirmation page lives in a browser on purpose: the code is entered on Yandex's own site,
    // where the app cannot see the password even if it wanted to.
    fun openVerification(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { state.toast("Нет браузера — откройте адрес вручную", NeIcon.Info) }
    }
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
                                if (streams.account.present) "Отключить" else "Войти",
                                size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        },
                        // Signed in: the row is the way out. Signed out: it starts the flow instead of
                        // asking for a token nobody has — the code path is two taps and no typing.
                        onClick = if (streams.account.present) ({ state.setYandexToken(null) }) else ({ state.startYandexSignIn() }),
                    )
                    // The manual route stays available for a token the user already has (and for the
                    // day OAuth's confirmation page changes): it is smaller, but it is not hidden.
                    SettingRow(
                        "Ввести токен вручную",
                        "Если он у вас уже есть — сохранится на этом устройстве",
                        NeIcon.Edit,
                        onClick = { editingToken = true },
                    )
                }
            }
        }

        item {
            // The honest paragraph. It is here rather than in a legal screen nobody opens because the
            // app relies on endpoints that were never meant for third parties, and saying so is the
            // only way the failure mode ("sometimes the catalogue is unavailable") is not a surprise.
            NeText(
                "Поиск и прослушивание в Яндекс Музыке работают бесплатно: приложение обращается к сервису " +
                    "напрямую, без своей подписки и без оплаты внутри приложения. Свободный тариф самого " +
                    "сервиса отдаёт часть треков отрывком — тогда в строке написано «пробуем 30 сек», а " +
                    "когда отрывок заканчивается, плеер говорит это словами вместо тишины. " +
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

    if (streams.deviceCode != null || streams.deviceDone || streams.devicePolling ||
        (streams.deviceError != null && streams.account.token == null)
    ) {
        SignInDialog(
            streams,
            onOpen = { url -> openVerification(url) },
            onCancel = state::cancelYandexSignIn,
            onRetry = state::startYandexSignIn,
        )
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
 * The sign-in window: a code to type, and the same window once it worked.
 *
 * Nothing here is a spinner over the whole screen: the code stays readable until the token arrives, the
 * poll's progress is a sentence under it, and both ways out — "Открыть страницу" and "Отмена" — close
 * the flow for real instead of leaving a loop asking the service in the background.
 */
@Composable
private fun SignInDialog(
    streams: com.neptune.player.data.yandex.StreamState,
    onOpen: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val code = streams.deviceCode
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ne.Elevated,
        title = {
            NeText(
                if (streams.deviceDone || streams.account.verified) "Аккаунт подключён" else "Вход в Яндекс Музыку",
                size = NeType.title, lineHeight = NeType.titleLine,
                weight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2,
            )
        },
        text = {
            Column {
                if (streams.deviceDone || streams.account.verified) {
                    NeText(
                        "Теперь сервис отдаёт ваши плейлисты и максимальное качество, если Plus активен. " +
                            "Каталог работал и без этого — вход ничего не разблокировал в приложении.",
                        size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 5,
                    )
                    return@Column
                }
                if (code == null) {
                    // Either the request is still in flight or it failed. Both are one sentence here:
                    // the user only needs to know whether to wait or to tap "Повторить".
                    NeText(
                        if (streams.deviceError != null) streams.deviceError!! else "Готовлю код…",
                        size = NeType.body, lineHeight = NeType.bodyLine,
                        color = if (streams.deviceError != null) Ne.Text else Ne.Dim, maxLines = 4,
                    )
                    return@Column
                }
                NeText(
                    "1. Откройте страницу Яндекса — на этом телефоне или на любом другом.",
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2,
                )
                Spacer(Modifier.height(NeSpace.sm))
                NeText(
                    "2. Введите код",
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.xs))
                // The code is the one thing on this dialog that must be read character by character:
                // big, spaced and monospaced by weight rather than by a second font family.
                NeText(
                    code.userCode, Modifier.testTag("deviceCode"),
                    size = NeType.headline, lineHeight = NeType.headlineLine,
                    weight = androidx.compose.ui.text.font.FontWeight.ExtraBold, tracking = 3.sp, maxLines = 1,
                )
                Spacer(Modifier.height(NeSpace.sm))
                NeText(
                    when {
                        streams.deviceError != null -> streams.deviceError
                        streams.devicePolling -> "Проверяю подтверждение…"
                        else -> "Страница подтвердит вход сама — приложение только ждёт."
                    },
                    size = NeType.caption, lineHeight = NeType.captionLine,
                    color = if (streams.deviceError != null) accent() else Ne.Faint, maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code == null) onRetry() else onOpen(code.verificationUrl)
            }) {
                NeText(if (code == null) "Повторить" else "Открыть страницу", color = accent())
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { NeText(if (streams.deviceDone) "Готово" else "Отмена", color = Ne.Dim) } },
    )
}

/**
 * Paste-a-token dialog — the fallback, not the way in.
 *
 * The flow above is the one to use; this exists for a token the user already has (a backup, another
 * device, or the day the confirmation page changes shape). The line under the field says that the token
 * is the user's and stays on the device, because a credential prompt that does not say where the
 * credential goes is the one thing to avoid here.
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
