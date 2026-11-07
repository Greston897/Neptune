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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.BuildConfig
import com.neptune.player.model.Accent
import com.neptune.player.model.AvatarStyle
import com.neptune.player.model.Surface
import com.neptune.player.model.formatTime
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) NeIconButton(NeIcon.Back, "Назад", onBack)
            NeText(title, Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 8.dp), size = 28.sp, weight = FontWeight.ExtraBold, tracking = (-0.9).sp)
        }
        if (subtitle != null) { Spacer(Modifier.height(4.dp)); NeText(subtitle, size = 12.sp, color = Ne.Dim, maxLines = 2) }
    }
}

@Composable
fun SettingsScreen(state: NeptuneState, onProfile: () -> Unit, onAppearance: () -> Unit, onPlayback: () -> Unit, onData: () -> Unit, onAbout: () -> Unit) {
    val settings = LocalSettings.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("Настройки", subtitle = "Всё под тебя. Без лишнего.") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(neRadius())).background(Ne.Ink).clickable(onClick = onProfile).padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileAvatar(Modifier.size(64.dp), animated = true)
                Spacer(Modifier.height(12.dp)); NeText(settings.profileName, size = 17.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp)); NeText("Изменить профиль и аватар", size = 11.sp, color = Ne.Dim)
            }
        }
        item { SettingsGroup("ПРИЛОЖЕНИЕ") {
            SettingRow("Внешний вид", "Цвета, форма, движение и размер текста", NeIcon.Palette, onAppearance)
            SettingRow("Воспроизведение", "Скорость, громкость и таймер сна", NeIcon.Wave, onPlayback)
            SettingRow("Музыка и данные", "Доступ к файлам, импорт и история", NeIcon.Folder, onData)
            SettingRow("О Neptune", "Версия ${BuildConfig.VERSION_NAME} · локальный плеер", NeIcon.Info, onAbout)
        } }
        item { NeText("Настройки сохраняются автоматически", Modifier.fillMaxWidth().padding(top = 24.dp), size = 11.sp, color = Ne.Faint, align = TextAlign.Center) }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        NeText(title, Modifier.padding(start = 4.dp, bottom = 10.dp), size = 9.sp, color = Ne.Faint, tracking = 1.6.sp, weight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(neRadius())).background(Ne.Ink)) { content() }
    }
}

@Composable
fun AppearanceScreen(state: NeptuneState, onBack: () -> Unit) {
    val s = LocalSettings.current
    var reset by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("Внешний вид", onBack, "Спокойная основа. Акцент — на твоей музыке.") }
        item { SettingsGroup("ЦВЕТ ОСНОВЫ") { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface.entries.forEach { surface ->
                Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(surface.hex))
                    .border(1.dp, if (s.surface == surface) Ne.Dim else Ne.Line, RoundedCornerShape(12.dp))
                    .clickable { state.edit { it.copy(surfaceIndex = surface.ordinal) } }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(surface.hex)).border(1.dp, Ne.Faint, CircleShape))
                    Spacer(Modifier.width(12.dp)); NeText(surface.title, Modifier.weight(1f), size = 13.sp)
                    if (s.surface == surface) NeIconView(NeIcon.Check, 18.dp)
                }
            }
        } } }
        item { SettingsGroup("АКЦЕНТ") { AccentPicker(s.accentIndex) { i -> state.edit { it.copy(accentIndex = i) } } } }
        item { SettingsGroup("ФОРМА И ТИПОГРАФИКА") {
            SettingSlider("Скругление карточек", s.radius, 8f..28f, "${s.radius.toInt()} dp", { v -> state.edit { it.copy(radius = v) } })
            SettingSlider("Размер текста", s.fontScale, 0.9f..1.15f, "${(s.fontScale * 100).toInt()}%", { v -> state.edit { it.copy(fontScale = v) } }, steps = 4)
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Accent.entries.forEach { a ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { onSelect(a.ordinal) }.semantics { contentDescription = a.title }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(a.hex)).border(if (selected == a.ordinal) 2.dp else 0.dp, Ne.Text, CircleShape), contentAlignment = Alignment.Center) {
                        if (selected == a.ordinal) NeIconView(NeIcon.Check, 17.dp, Ne.Black, weight = 2.3f)
                    }
                }
                NeText(a.title, size = 9.sp, color = if (selected == a.ordinal) Ne.Text else Ne.Faint)
            }
        }
    }
}

@Composable
fun ProfileScreen(state: NeptuneState, onBack: () -> Unit) {
    val s = LocalSettings.current
    var name by remember { mutableStateOf(s.profileName) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("Твой профиль", onBack) }
        item { Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(Modifier.size(72.dp), animated = true)
            Spacer(Modifier.height(14.dp)); NeText(s.profileName, size = 20.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp)); NeText("Маленький символ. Большая любовь к музыке.", size = 11.sp, color = Ne.Dim)
        } }
        item { SettingsGroup("ИМЯ") { Column(Modifier.padding(16.dp)) {
            androidx.compose.material3.OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = { NeText("Как к тебе обращаться", size = 11.sp) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = accent(), cursorColor = accent(), focusedTextColor = Ne.Text, unfocusedTextColor = Ne.Text))
            NeButton("Сохранить имя", { state.edit { it.copy(profileName = name) }; state.message("Имя сохранено") }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = name.isNotBlank() && name.trim() != s.profileName)
        } } }
        item { SettingsGroup("ВЕКТОРНЫЙ АВАТАР") { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarStyle.entries.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    Column(Modifier.weight(1f).height(106.dp).clip(RoundedCornerShape(12.dp)).background(if (s.avatar == style) Ne.Line else Ne.Elevated)
                        .clickable { state.edit { it.copy(avatarIndex = style.ordinal) } }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(style, Color(Accent.of(s.avatarTone).hex), Modifier.size(48.dp), animated = s.avatar == style)
                        Spacer(Modifier.height(10.dp)); NeText(style.title, size = 11.sp, color = if (s.avatar == style) Ne.Text else Ne.Dim)
                    }
                }
            } }
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
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("Воспроизведение", onBack, "Настоящий звук. Полный контроль.") }
        item { SettingsGroup("ЗВУК") {
            SettingSlider("Скорость", s.speed, 0.75f..1.5f, "×${"%.2f".format(s.speed)}", { v -> state.edit { it.copy(speed = v) } }, steps = 5)
            SettingSlider("Громкость плеера", p.volume, 0f..1f, "${(p.volume * 100).toInt()}%", state::setVolume)
            ToggleRow("Пропускать тишину", "Умное сокращение пауз внутри трека", s.skipSilence) { v -> state.edit { it.copy(skipSilence = v) } }
        } }
        item { SettingsGroup("КОМФОРТ") {
            SettingRow("Таймер сна", if (p.sleepRemainingMs > 0) "Остановится через ${formatTime(p.sleepRemainingMs)}" else "Выключен", NeIcon.Timer) { sleep = true }
            SettingRow("Повтор очереди", when (p.repeat) { 1 -> "Вся очередь"; 2 -> "Один трек"; else -> "Выключен" }, NeIcon.Repeat, state::cycleRepeat)
            ToggleRow("Перемешивание", "Случайный порядок воспроизведения", p.shuffle) { state.toggleShuffle() }
            if (Build.VERSION.SDK_INT >= 33) SettingRow("Уведомление плеера", "Управление с экрана блокировки и из шторки", NeIcon.Bell) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) state.message("Уведомления уже разрешены")
                else notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } }
        item { NeText("При входящем звонке музыка ставится на паузу. При отключении наушников воспроизведение останавливается.", Modifier.padding(start = 4.dp, end = 4.dp, top = 22.dp), size = 11.sp, color = Ne.Faint, maxLines = 4, lineHeight = 17.sp) }
    }
    if (sleep) SleepDialog(p, { sleep = false }) { state.setSleepTimer(it); sleep = false }
}

@Composable
fun DataScreen(state: NeptuneState, onBack: () -> Unit) {
    val library by state.library.collectAsStateWithLifecycle()
    val users by state.users.collectAsStateWithLifecycle()
    val actions = LocalLibraryActions.current
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("Музыка и данные", onBack, "Твоя коллекция остаётся на твоём устройстве.") }
        item { SettingsGroup("ИСТОЧНИКИ") {
            SettingRow("Музыка на устройстве", if (library.permission) "${library.localCount} локальных треков · доступ разрешён" else "Нет доступа к аудиофайлам", NeIcon.Device, if (library.permission) actions.appSettings else actions.permission)
            SettingRow("Импорт аудиофайлов", "Выбрать музыку через системный проводник", NeIcon.Folder, actions.import)
            SettingRow("Обновить медиатеку", if (library.loading) "Сканирование…" else "Найти новые треки", NeIcon.Reset, state::refreshLibrary)
        } }
        item { SettingsGroup("ИСТОРИЯ") {
            SettingRow("Недавно прослушанные", "${users.recent.size} треков в истории", NeIcon.Clock)
            SettingRow("Очистить историю", "Не затрагивает любимое и плейлисты", NeIcon.Trash) { clear = true }
        } }
        item { NeText("Neptune Sessions — шесть оригинальных синтезированных аудиосессий. Они доступны без разрешений. Обложки локальных треков читаются из системной медиатеки.", Modifier.padding(top = 22.dp), size = 11.sp, color = Ne.Faint, maxLines = 5, lineHeight = 17.sp) }
    }
    if (clear) ConfirmDialog("Очистить историю?", "Любимые треки и плейлисты сохранятся.", "Очистить", { clear = false }) { state.clearHistory(); clear = false }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { ScreenTitle("О Neptune", onBack) }
        item { Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(AvatarStyle.ORBIT, Ne.Text, Modifier.size(72.dp), animated = true)
            Spacer(Modifier.height(18.dp)); NeText("neptune", size = 32.sp, weight = FontWeight.ExtraBold, tracking = (-1).sp)
            Spacer(Modifier.height(6.dp)); NeText("Музыка. Ничего лишнего.", size = 13.sp, color = Ne.Dim)
        } }
        item { SettingsGroup("ПОД КАПОТОМ") {
            SettingRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", NeIcon.Info)
            SettingRow("Аудиодвижок", "Media3 / ExoPlayer · фоновая медиасессия", NeIcon.Wave)
            SettingRow("Интерфейс", "Kotlin · Jetpack Compose · Material 3", NeIcon.Palette)
            SettingRow("Приватность", "Без аккаунта, рекламы и аналитики", NeIcon.Device)
        } }
    }
}
