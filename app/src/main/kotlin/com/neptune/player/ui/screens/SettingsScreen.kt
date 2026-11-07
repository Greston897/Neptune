package com.neptune.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.BuildConfig
import com.neptune.player.model.Accent
import com.neptune.player.model.AvatarShape
import com.neptune.player.model.Surface
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = NeSpace.xl)) {
        Row(Modifier.fillMaxWidth().heightIn(min = NeRow.header), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) NeIconButton(NeIcon.Back, "Назад", onBack)
            NeText(
                title, Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else NeSpace.sm, top = NeSpace.sm, bottom = NeSpace.sm),
                size = NeType.display, lineHeight = NeType.displayLine, weight = FontWeight.ExtraBold, tracking = (-0.8).sp, maxLines = 2,
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(NeSpace.xs))
            NeText(subtitle, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2)
        }
    }
}

@Composable
fun SettingsScreen(state: NeptuneState, onProfile: () -> Unit, onAppearance: () -> Unit, onPlayback: () -> Unit, onData: () -> Unit, onAbout: () -> Unit) {
    val settings = LocalSettings.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("Настройки", subtitle = "Всё под тебя. Без лишнего.") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(neRadius()))
                    .background(Color.White.copy(alpha = 0.04f)).clickable(onClick = onProfile).padding(vertical = NeSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(Modifier.size(64.dp), animated = true)
                Spacer(Modifier.height(NeSpace.md))
                NeText(settings.profileName, size = NeType.section, lineHeight = NeType.sectionLine, weight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(NeSpace.xs))
                NeText("Изменить профиль и аватар", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim)
            }
        }
        item { SettingsGroup("ПРИЛОЖЕНИЕ") {
            SettingRow("Внешний вид", "Цвета, форма, движение и размер текста", NeIcon.Palette, onClick = onAppearance)
            SettingRow("Воспроизведение", "Скорость, громкость и таймер сна", NeIcon.Wave, onClick = onPlayback)
            SettingRow("Музыка и данные", "Доступ к файлам, импорт и история", NeIcon.Folder, onClick = onData)
            SettingRow("О Neptune", "Версия ${BuildConfig.VERSION_NAME} · локальный плеер", NeIcon.Info, onClick = onAbout)
        } }
        item { NeText("Настройки сохраняются автоматически", Modifier.fillMaxWidth().padding(top = NeSpace.xxl), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, align = TextAlign.Center, maxLines = 2) }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = NeSpace.xxl)) {
        NeText(
            title, Modifier.padding(start = NeRow.panelInset, bottom = NeSpace.sm),
            size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, tracking = 1.2.sp, weight = FontWeight.Bold,
        )
        GlassPanel(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { content() } }
    }
}

@Composable
fun AppearanceScreen(state: NeptuneState, onBack: () -> Unit) {
    val s = LocalSettings.current
    var reset by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("Внешний вид", onBack, "Спокойная основа. Акцент — на твоей музыке.") }
        item { SettingsGroup("ЦВЕТ ОСНОВЫ") { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface.entries.forEach { surface ->
                val selected = s.surface == surface
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(Color(surface.hex))
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) accent() else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable { state.edit { it.copy(surfaceIndex = surface.ordinal) } }.padding(horizontal = NeSpace.md, vertical = NeSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(NeIconSize.xl).clip(CircleShape).background(Color(surface.hex)).border(1.dp, Ne.Faint, CircleShape))
                    Spacer(Modifier.width(NeSpace.md))
                    NeText(surface.title, Modifier.weight(1f), size = NeType.label, lineHeight = NeType.labelLine, color = if (selected) accent() else Ne.Text, maxLines = 1)
                    if (selected) { Spacer(Modifier.width(NeSpace.sm)); NeIconView(NeIcon.Check, NeIconSize.sm, accent()) }
                }
            }
        } } }
        item { SettingsGroup("АКЦЕНТ") { AccentPicker(s.accentIndex) { i -> state.edit { it.copy(accentIndex = i) } } } }
        item { SettingsGroup("LIQUID GLASS") {
            ToggleRow("Чёрное стекло", "Настоящее размытие фона на Android 12+, мягкая панель на старых версиях", s.glassEnabled) { v -> state.edit { it.copy(glassEnabled = v) } }
            SettingSlider("Размытие", s.glassBlur, 8f..56f, "${s.glassBlur.toInt()} dp", { v -> state.edit { it.copy(glassBlur = v) } })
            SettingSlider("Плотность стекла", s.glassTint, 0.35f..0.88f, "${(s.glassTint * 100).toInt()}%", { v -> state.edit { it.copy(glassTint = v) } })
            SettingSlider("Световой кант", s.glassRim, 0f..1f, "${(s.glassRim * 100).toInt()}%", { v -> state.edit { it.copy(glassRim = v) } })
            ToggleRow("Фоновое свечение", "Едва заметные блики на чёрном фоне", s.ambientGlow) { v -> state.edit { it.copy(ambientGlow = v) } }
            ToggleRow("Акцентное свечение", "Подкрашивать фон выбранным акцентом", s.accentGlow) { v -> state.edit { it.copy(accentGlow = v) } }
        } }
        item { SettingsGroup("ФОРМА И ТИПОГРАФИКА") {
            SettingSlider("Скругление панелей", s.radius, 12f..32f, "${s.radius.toInt()} dp", { v -> state.edit { it.copy(radius = v) } })
            SettingSlider("Размер текста", s.fontScale, 0.9f..1.2f, "${(s.fontScale * 100).toInt()}%", { v -> state.edit { it.copy(fontScale = v) } }, steps = 4)
            ToggleRow("Компактный список", "Больше треков на одном экране", s.compact) { v -> state.edit { it.copy(compact = v) } }
            ToggleRow("Подписи навигации", "Названия разделов внизу экрана", s.navLabels) { v -> state.edit { it.copy(navLabels = v) } }
        } }
        item { SettingsGroup("ДВИЖЕНИЕ И ОТКЛИК") {
            ToggleRow("Анимации интерфейса", "Плавные переходы и отклик кнопок", s.animations) { v -> state.edit { it.copy(animations = v) } }
            ToggleRow("Живые обложки и аватар", "Лёгкое движение только на главном экране и в плеере", s.artworkMotion) { v -> state.edit { it.copy(artworkMotion = v) } }
            ToggleRow("Тактильный отклик", "Лёгкая вибрация при нажатии", s.haptics) { v -> state.edit { it.copy(haptics = v) } }
            ToggleRow("Оставшееся время", "Показывать остаток вместо общей длительности", s.showRemaining) { v -> state.edit { it.copy(showRemaining = v) } }
        } }
        item { NeButton("Сбросить внешний вид", { reset = true }, Modifier.fillMaxWidth().padding(top = 24.dp), NeIcon.Reset, primary = false) }
    }
    if (reset) ConfirmDialog("Вернуть исходный вид?", "Профиль, музыка и плейлисты не изменятся.", "Сбросить", { reset = false }) { state.resetAppearance(); reset = false }
}

@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    // Cells share the row width instead of a fixed 48dp, so twelve swatches never overflow the
    // panel on a 360dp screen; the hit area still stays at 44dp.
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NeSpace.sm, vertical = NeSpace.md),
        verticalArrangement = Arrangement.spacedBy(NeSpace.md),
    ) {
        Accent.entries.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEach { a ->
                    val isSelected = selected == a.ordinal
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(NeTouch.min).clip(CircleShape)
                                .background(if (isSelected) Color(a.hex).copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onSelect(a.ordinal) }
                                .semantics { contentDescription = a.title; this.selected = isSelected },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(if (isSelected) 30.dp else 26.dp).clip(CircleShape).background(Color(a.hex)),
                                contentAlignment = Alignment.Center,
                            ) { if (isSelected) NeIconView(NeIcon.Check, NeIconSize.xs, Ne.Black) }
                        }
                        Spacer(Modifier.height(NeSpace.xxs))
                        NeText(
                            a.title, size = NeType.micro, lineHeight = NeType.microLine,
                            color = if (isSelected) Ne.Text else Ne.Faint, maxLines = 1,
                            weight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(state: NeptuneState, onBack: () -> Unit) {
    val s = LocalSettings.current
    var name by remember { mutableStateOf(s.profileName) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("Твой профиль", onBack) }
        item { Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(Modifier.size(112.dp), animated = true)
            Spacer(Modifier.height(NeSpace.lg))
            NeText(s.profileName, size = NeType.title, lineHeight = NeType.titleLine, weight = FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
        } }
        item { SettingsGroup("ИМЯ") { Column(Modifier.padding(16.dp)) {
            androidx.compose.material3.OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = { NeText("Как к тебе обращаться", size = NeType.caption, lineHeight = NeType.captionLine) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = accent(), cursorColor = accent(), focusedTextColor = Ne.Text, unfocusedTextColor = Ne.Text))
            NeButton("Сохранить имя", { state.edit { it.copy(profileName = name) }; state.message("Имя сохранено") }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = name.isNotBlank() && name.trim() != s.profileName)
        } } }
        item { SettingsGroup("ФОРМА АВАТАРА") { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NeSpace.sm),
                verticalAlignment = Alignment.Top) {
                AvatarShape.entries.forEach { shape ->
                    val selected = s.avatarShape == shape
                    Column(
                        Modifier.weight(1f).heightIn(min = 104.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) accent().copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent() else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { state.edit { it.copy(avatarIndex = shape.ordinal) } }
                            .padding(vertical = NeSpace.md, horizontal = NeSpace.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProfileAvatar(Modifier.size(44.dp))
                        Spacer(Modifier.height(NeSpace.sm))
                        NeText(
                            shape.title, size = NeType.micro, lineHeight = NeType.microLine,
                            color = if (selected) accent() else Ne.Dim, maxLines = 1,
                            weight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            SettingSlider("Размер планеты", s.avatarSize, 0.8f..1.25f, "${(s.avatarSize * 100).toInt()}%", { v -> state.edit { it.copy(avatarSize = v) } })
        } } }
        item { SettingsGroup("ЦВЕТ АВАТАРА") { AccentPicker(s.avatarTone) { i -> state.edit { it.copy(avatarTone = i) } } } }
    }
}

@Composable
fun PlaybackSettingsScreen(state: NeptuneState, onBack: () -> Unit) {
    val s = LocalSettings.current
    val p by state.playback.collectAsStateWithLifecycle()
    var sleep by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> state.message(if (granted) "Уведомление плеера включено" else "Плеер работает и без уведомления") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("Воспроизведение", onBack, "Настоящий звук. Полный контроль.") }
        item { SettingsGroup("ЗВУК") {
            SettingSlider("Скорость", s.speed, 0.75f..1.5f, "×${"%.2f".format(s.speed)}", { v -> state.edit { it.copy(speed = v) } }, steps = 5)
            SettingSlider("Громкость плеера", p.volume, 0f..1f, "${(p.volume * 100).toInt()}%", state::setVolume)
            ToggleRow("Пропускать тишину", "Умное сокращение пауз внутри трека", s.skipSilence) { v -> state.edit { it.copy(skipSilence = v) } }
        } }
        item { SettingsGroup("КОМФОРТ") {
            SettingRow("Таймер сна", if (p.sleepRemainingMs > 0) "Остановится через ${formatTime(p.sleepRemainingMs)}" else "Выключен", NeIcon.Timer) { sleep = true }
            SettingRow("Повтор очереди", when (p.repeat) { 1 -> "Вся очередь"; 2 -> "Один трек"; else -> "Выключен" }, NeIcon.Repeat, onClick = state::cycleRepeat)
            ToggleRow("Перемешивание", "Случайный порядок воспроизведения", p.shuffle) { state.toggleShuffle() }
            if (Build.VERSION.SDK_INT >= 33) SettingRow("Уведомление плеера", "Управление с экрана блокировки и из шторки", NeIcon.Bell) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) state.message("Уведомления уже разрешены")
                else notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } }
        item { NeText("При входящем звонке музыка ставится на паузу. При отключении наушников воспроизведение останавливается.", Modifier.padding(horizontal = NeSpace.xs, vertical = NeSpace.xl), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 4) }
    }
    if (sleep) SleepDialog(p, { sleep = false }) { state.setSleepTimer(it); sleep = false }
}

@Composable
fun DataScreen(state: NeptuneState, onBack: () -> Unit, onRecent: () -> Unit = {}) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val actions = LocalLibraryActions.current
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("Музыка и данные", onBack, "Твоя коллекция остаётся на твоём устройстве.") }
        item { SettingsGroup("ИСТОЧНИКИ") {
            SettingRow("Музыка на устройстве", if (library.permission) "${library.localCount} локальных треков · доступ разрешён" else "Нет доступа к аудиофайлам", NeIcon.Device, onClick = if (library.permission) actions.appSettings else actions.permission)
            SettingRow("Импорт аудиофайлов", "Выбрать музыку через системный проводник", NeIcon.Folder, onClick = actions.import)
            SettingRow("Обновить медиатеку", if (library.loading) "Сканирование…" else "Найти новые треки", NeIcon.Reset, onClick = state::refreshLibrary)
        } }
        item { SettingsGroup("ИСТОРИЯ") {
            SettingRow("Недавно прослушанные", "${users.recent.size} треков в истории", NeIcon.Clock, onClick = onRecent)
            SettingRow("Очистить историю", "Не затрагивает любимое и плейлисты", NeIcon.Trash) { clear = true }
        } }
        item { NeText("Демо-треки доступны без разрешений. Обложки локальных треков читаются из системной медиатеки.", Modifier.padding(top = NeSpace.xl), size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint, maxLines = 4) }
    }
    if (clear) ConfirmDialog("Очистить историю?", "Любимые треки и плейлисты сохранятся.", "Очистить", { clear = false }) { state.clearHistory(); clear = false }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.md, bottom = LocalBottomInset.current)) {
        item { ScreenTitle("О Neptune", onBack) }
        item { Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NeptuneMark(Modifier.size(72.dp), animated = true)
            Spacer(Modifier.height(NeSpace.lg))
            NeText("neptune", size = NeType.display, lineHeight = NeType.displayLine, weight = FontWeight.ExtraBold, tracking = (-0.8).sp)
            Spacer(Modifier.height(NeSpace.xs))
            NeText("Версия ${BuildConfig.VERSION_NAME}", size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim)
        } }
        item { SettingsGroup("ПОД КАПОТОМ") {
            SettingRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", NeIcon.Info)
            SettingRow("Аудиодвижок", "Media3 / ExoPlayer · фоновая медиасессия", NeIcon.Wave)
            SettingRow("Интерфейс", "Kotlin · Jetpack Compose · Material 3", NeIcon.Palette)
            SettingRow("Приватность", "Без аккаунта, рекламы и аналитики", NeIcon.Device)
        } }
    }
}
