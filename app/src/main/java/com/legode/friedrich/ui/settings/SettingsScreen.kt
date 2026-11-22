package com.legode.friedrich.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.PingScheduler
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Settings
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.ConfirmDialog
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.SectionTitle
import com.legode.friedrich.ui.common.Segments
import com.legode.friedrich.ui.common.SettingRow
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlin.math.roundToInt

private val THINKING = listOf("auto", "minimal", "low", "medium", "high")
private val THINKING_LABELS = listOf("Авто", "Мин.", "Низк.", "Средн.", "Высок.")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(main: MainViewModel, onBack: () -> Unit) {
    val s by main.settingsFlow.collectAsStateWithLifecycle()
    val profile by main.profile.collectAsStateWithLifecycle()
    val sessions by main.sessions.collectAsStateWithLifecycle()
    val nextPing by main.nextPing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var key by remember { mutableStateOf(main.settings.apiKey()) }
    var keyVisible by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(s.model) }
    var deepModel by remember { mutableStateOf(s.deepModel) }
    var wipeConfirm by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(profile) {
        val p = profile
        if (p != null && !profileLoaded) {
            profileLoaded = true
            name = p.name
            goal = p.goal
            interests = p.interests
            about = p.about
        }
    }

    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val batteryFree = remember(refresh) { isIgnoringBattery(context) }
    val exactAlarmOk = remember(refresh) { canExactAlarms(context) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            main.importBackupUri(uri) { res ->
                res.onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    .onFailure { err -> Toast.makeText(context, err.message ?: "Ошибка импорта", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Fx.Ink)) {
        TopBar(title = "Настройки", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SectionTitle("Gemini API и Модели")
            Panel {
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("API-ключ") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Показать", tint = Fx.TextDim)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (main.keyChecking) "Проверяю…" else "Сохранить и проверить", onClick = {
                        if (!main.keyChecking) main.saveAndTestKey(key)
                    }, accent = true)
                    Chip("Получить ключ", onClick = { openUrl(context, "https://aistudio.google.com/apikey") })
                }
                if (main.keyCheck.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(main.keyCheck, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        main.update { st -> st.copy(model = it.trim().ifEmpty { Settings.DEFAULT_MODEL }) }
                    },
                    label = { Text("Быстрая модель (чат и СМС)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in Settings.FALLBACK_MODELS.take(3)) {
                        Chip(m.removePrefix("gemini-"), onClick = {
                            model = m
                            main.update { st -> st.copy(model = m) }
                        }, selected = s.model == m)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = deepModel,
                    onValueChange = {
                        deepModel = it
                        main.update { st -> st.copy(deepModel = it.trim().ifEmpty { Settings.DEFAULT_DEEP_MODEL }) }
                    },
                    label = { Text("Усиленная модель (книги, решения, неделя)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in Settings.DEEP_MODELS.take(3)) {
                        Chip(m.removePrefix("gemini-"), onClick = {
                            deepModel = m
                            main.update { st -> st.copy(deepModel = m) }
                        }, selected = s.deepModel == m)
                    }
                }
                val effective = main.brain.effectiveModel()
                if (effective != s.model) {
                    Spacer(Modifier.height(6.dp))
                    Text("Сейчас отвечает запасная модель: $effective", color = Fx.Gold, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text("Глубина рассуждений", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Segments(THINKING_LABELS, THINKING.indexOf(s.thinking).coerceAtLeast(0), { i ->
                    main.update { it.copy(thinking = THINKING[i]) }
                })
                Toggle("Потоковый вывод ответа (SSE)", "Текст появляется в реальном времени по мере генерации", s.streamingEnabled) { v ->
                    main.update { it.copy(streamingEnabled = v) }
                }
                Toggle("Веб-поиск и чтение URL", "Чтение присланных статей и проверка фактов/базовых ставок", s.webSearchEnabled) { v ->
                    main.update { it.copy(webSearchEnabled = v) }
                }
                Toggle("Весь атлас из 60 моделей в каждом запросе", "Точнее, но сильно повышает расход токенов и риск лимита 429", s.fullReference) { v ->
                    main.update { it.copy(fullReference = v) }
                }
            }

            SectionTitle("Интеллект, Характер и Память FSRS")
            Panel {
                Text("Режим давления", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Segments(listOf("Бережно", "Баланс", "Напористо ⚡"), s.pushiness.coerceIn(0, 2), { i ->
                    main.update { it.copy(pushiness = i) }
                })
                Toggle("Режим тренера по умолчанию", "Анти-оффлоадинг: сначала требует твои мысли и гипотезы", s.socraticByDefault) { v ->
                    main.update { it.copy(socraticByDefault = v) }
                }
                Toggle("Обращение на «Вы»", "По умолчанию Атлас общается на «ты»", profile?.formal == true) { v ->
                    main.saveProfile(name, goal, interests, about, v)
                }
                Toggle("Живые паузы между СМС", "Сообщения приходят по очереди", s.humanPacing) { v ->
                    main.update { it.copy(humanPacing = v) }
                }
                Toggle("Воскресный синтез недели", "Автоматический Executive Review вечером в воскресенье", s.weeklyReviewEnabled) { v ->
                    main.update { it.copy(weeklyReviewEnabled = v) }
                }
                Text("Целевое удержание памяти FSRS: ${(s.desiredRetention * 100).roundToInt()}%", color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
                GoldSlider(s.desiredRetention, 0.80f..0.96f, 7) { v ->
                    main.update { it.copy(desiredRetention = ((v * 50).roundToInt() / 50f).coerceIn(0.80f, 0.96f)) }
                }
            }

            SectionTitle("Приватность и Защита")
            Panel {
                Toggle("Биометрический замок", "Вход по отпечатку пальца или PIN устройства", s.biometricLock) { v ->
                    main.update { it.copy(biometricLock = v) }
                }
                Toggle("Защита экрана (FLAG_SECURE)", "Блокирует скриншоты и скрывает содержимое в списке недавних", s.screenshotBlock) { v ->
                    main.update { it.copy(screenshotBlock = v) }
                }
            }

            SectionTitle("Твой профиль")
            Panel {
                Field("Имя", name) { name = it }
                Field("Главная цель", goal) { goal = it }
                Field("Ценности и что заряжает", interests) { interests = it }
                Field("Контекст жизни и триггеры", about, lines = 3) { about = it }
                Spacer(Modifier.height(8.dp))
                Chip("Сохранить профиль", onClick = {
                    main.saveProfile(name, goal, interests, about, profile?.formal == true)
                    Toast.makeText(context, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                }, accent = true)
            }

            SectionTitle("СМС от Атласа (JITAI)")
            Panel {
                Toggle("Атлас пишет первым", "Утренние заряды, карточки FSRS, проверки прогнозов и WOOP", s.pingsEnabled) { v ->
                    main.updatePings { it.copy(pingsEnabled = v) }
                }
                val lv = PingScheduler.level(s.pingIntensity)
                Text("Частота: ${lv.title} (${lv.about})", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                GoldSlider(s.pingIntensity.toFloat(), 0f..4f, 3) { v ->
                    main.updatePings { it.copy(pingIntensity = v.roundToInt().coerceIn(0, 4)) }
                }
                Text(
                    when {
                        !s.pingsEnabled -> "Сообщения выключены"
                        main.runtime.snoozeUntil > Timex.now() -> "Отложено до " + Timex.hhmm(main.runtime.snoozeUntil)
                        nextPing > 0L -> "Следующее сообщение ≈ " + Timex.hhmm(nextPing)
                        else -> "Расписание составляется…"
                    },
                    color = Fx.Gold, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Напиши сейчас", onClick = { main.pingNow() }, accent = true)
                    if (main.runtime.snoozeUntil > Timex.now()) {
                        Chip("Снять паузу", onClick = {
                            main.cancelSnooze()
                            refresh++
                        })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Тихие часы", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Stepper("с", s.quietStartMin) { v -> main.updatePings { it.copy(quietStartMin = v) } }
                Stepper("до", s.quietEndMin) { v -> main.updatePings { it.copy(quietEndMin = v) } }
                Text("Максимум СМС в день: ${s.dailyPingCap}", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                GoldSlider(s.dailyPingCap.toFloat(), 5f..150f, 28) { v ->
                    main.updatePings { it.copy(dailyPingCap = (v / 5f).roundToInt() * 5) }
                }
            }

            SectionTitle("Работа в фоне и Диагностика")
            Panel {
                Toggle("Постоянный сервис", "Тихое уведомление «Атлас на связи»", s.keepAlive) { v -> main.setKeepAlive(v) }
                StatusRow("Уведомления", notificationsOn, if (notificationsOn) "разрешены" else "запрещены") {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else openNotificationSettings(context)
                }
                StatusRow("Экономия батареи", batteryFree, if (batteryFree) "отключена" else "включена") { requestIgnoreBattery(context) }
                val report = remember { main.kb.ensureLoaded(); main.kb.integrity }
                Spacer(Modifier.height(6.dp))
                Text(
                    "База знаний: ${report.totalEntries} записей (${report.loadedFiles} файлов) · ошибки JSON: ${report.invalidLines} · дубли: ${report.duplicateIds} · точные будильники: ${if (exactAlarmOk) "да" else "ограничены"}",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                if (main.runtime.lastError.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Последний сбой API: ${main.runtime.lastError}", color = Fx.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionTitle("Звук, Текст и Бэкап")
            Panel {
                Toggle("Голосовое чтение (TTS)", "Читает практики и карточки с паузами", s.ttsEnabled) { v -> main.update { it.copy(ttsEnabled = v) } }
                Text("Скорость речи: ×" + "%.1f".format(s.ttsRate), color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                GoldSlider(s.ttsRate, 0.5f..1.3f, 7) { v -> main.update { it.copy(ttsRate = (v * 10).roundToInt() / 10f) } }
                Text("Размер текста: ×" + "%.1f".format(s.fontScale), color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                GoldSlider(s.fontScale, 0.8f..1.4f, 5) { v -> main.update { it.copy(fontScale = (v * 10).roundToInt() / 10f) } }
                SettingRow("Экспорт полного бэкапа (JSON)", "Все чаты, досье, книги, карточки и решения", onClick = { main.exportAll(context) })
                SettingRow("Импорт / Восстановление из JSON", "Слияние данных из резервной копии", onClick = {
                    try {
                        importBackup.launch(arrayOf("application/json", "text/*"))
                    } catch (_: Throwable) {
                    }
                })
                SettingRow("Сбросить всё", "Удалить данные (API-ключ сохранится)", onClick = { wipeConfirm = true })
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (wipeConfirm) {
        ConfirmDialog(
            "Сбросить всё?",
            "Переписка, книги, карточки FSRS, решения и настройки будут удалены.",
            "Сбросить", { main.wipeAll() }, { wipeConfirm = false }, true
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Fx.OnGold, checkedTrackColor = Fx.Gold, uncheckedThumbColor = Fx.TextDim, uncheckedTrackColor = Fx.Ink, uncheckedBorderColor = Fx.Line)
        )
    }
}

@Composable
private fun GoldSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(thumbColor = Fx.Gold, activeTrackColor = Fx.Gold, inactiveTrackColor = Fx.Line)
    )
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 6, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
}

@Composable
private fun Stepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fx.TextDim, modifier = Modifier.width(34.dp))
        Text("−", color = Fx.Gold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Fx.CardHigh).clickable { onChange(((minutes - 30) % 1440 + 1440) % 1440) }.padding(horizontal = 16.dp, vertical = 4.dp))
        Text(Timex.minutesToClock(minutes), color = Fx.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text("+", color = Fx.Gold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Fx.CardHigh).clickable { onChange((minutes + 30) % 1440) }.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, text: String, onFix: () -> Unit) {
    SettingRow((if (ok) "✅ " else "⚠️ ") + title, text, onClick = onFix) { if (!ok) Chip("Исправить", onClick = onFix, accent = true) }
}

fun canExactAlarms(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
} catch (_: Throwable) {
    false
}

fun isIgnoringBattery(context: Context): Boolean = try {
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
} catch (_: Throwable) {
    false
}

@SuppressLint("BatteryLife")
fun requestIgnoreBattery(context: Context) {
    try {
        context.startActivity(Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        openAppSettings(context)
    }
}

fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        openAppSettings(context)
    }
}

fun openAppSettings(context: Context) {
    try {
        context.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}