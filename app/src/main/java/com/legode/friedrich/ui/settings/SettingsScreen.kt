package com.legode.friedrich.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

    // Статусы разрешений перечитываем при каждом возвращении на экран.
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
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
    ) {
        TopBar(title = "Настройки", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // ───────── Gemini ─────────
            SectionTitle("Gemini API")
            Panel {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API-ключ") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Показать ключ", tint = Fx.TextDim
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        if (main.keyChecking) "Проверяю…" else "Сохранить и проверить",
                        onClick = { if (!main.keyChecking) main.saveAndTestKey(key) },
                        accent = true
                    )
                    Chip("Где взять ключ?", onClick = { openUrl(context, "https://aistudio.google.com/apikey") })
                }
                if (main.keyCheck.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(main.keyCheck, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        main.update { st -> st.copy(model = it.trim().ifEmpty { Settings.DEFAULT_MODEL }) }
                    },
                    label = { Text("Модель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (m in Settings.FALLBACK_MODELS.take(2)) {
                        Chip(m.removePrefix("gemini-"), onClick = {
                            model = m
                            main.update { st -> st.copy(model = m) }
                        }, selected = s.model == m)
                    }
                }
                val effective = main.brain.effectiveModel()
                if (effective != s.model) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Сейчас отвечает запасная модель $effective: «${s.model}» недоступна для этого ключа.",
                        color = Fx.Gold, style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Глубина размышлений модели", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Выше — точнее разбор ошибок, но медленнее и дороже. «Низк.» — хороший баланс.",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Segments(THINKING_LABELS, THINKING.indexOf(s.thinking).coerceAtLeast(0), { i ->
                    main.update { it.copy(thinking = THINKING[i]) }
                })
                Toggle(
                    "Весь справочник в каждом ответе",
                    "Friedrich сверяется со всеми таблицами A0–A1 (точнее, но ~25 тыс. токенов на запрос). Выключи для экономии — будут подгружаться только нужные темы.",
                    s.fullReference
                ) { v -> main.update { it.copy(fullReference = v) } }
                if (main.runtime.lastError.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Последняя ошибка API: " + main.runtime.lastError, color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ───────── Характер ─────────
            SectionTitle("Как общается Friedrich")
            Panel {
                Toggle(
                    "Доля русского — автоматически",
                    "Чем выше твой уровень, тем больше немецкого",
                    s.russianShare < 0
                ) { v -> main.update { it.copy(russianShare = if (v) -1 else 50) } }
                if (s.russianShare >= 0) {
                    Text("Русского ≈ ${s.russianShare}%", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    GoldSlider(s.russianShare / 100f, 0f..1f, 9) { v ->
                        main.update { it.copy(russianShare = (v * 10).roundToInt() * 10) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Исправление ошибок", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Segments(listOf("Мягко", "Обычно", "Строго"), s.strictness.coerceIn(0, 2), { i ->
                    main.update { it.copy(strictness = i) }
                })
                Toggle("Обращение на «Вы» (Sie)", "По умолчанию Friedrich на «ты»", profile?.formal == true) { v ->
                    main.saveProfile(name, goal, interests, about, v)
                }
                Toggle("Живые паузы", "Сообщения приходят по одному, как от человека", s.humanPacing) { v ->
                    main.update { it.copy(humanPacing = v) }
                }
            }

            // ───────── Профиль ─────────
            SectionTitle("О тебе")
            Panel {
                Field("Имя", name) { name = it }
                Field("Цель (зачем немецкий)", goal) { goal = it }
                Field("Интересы", interests) { interests = it }
                Field("Что ещё важно знать Фридриху", about, lines = 3) { about = it }
                Spacer(Modifier.height(8.dp))
                Chip("Сохранить", onClick = {
                    main.saveProfile(name, goal, interests, about, profile?.formal == true)
                }, accent = true)
            }

            // ───────── СМС ─────────
            SectionTitle("СМС от Фридриха")
            Panel {
                Toggle("Friedrich пишет первым", "Случайные задания в течение дня", s.pingsEnabled) { v ->
                    main.updatePings { it.copy(pingsEnabled = v) }
                }
                val lv = PingScheduler.level(s.pingIntensity)
                Text("Частота: ${lv.title}", color = Fx.Text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(lv.about, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
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
                    Chip("Напиши мне сейчас", onClick = { main.pingNow() }, accent = true)
                    if (main.runtime.snoozeUntil > Timex.now()) {
                        Chip("Снять паузу", onClick = {
                            main.cancelSnooze()
                            refresh++
                        })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Не беспокоить", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                Stepper("с", s.quietStartMin) { v -> main.updatePings { it.copy(quietStartMin = v) } }
                Stepper("до", s.quietEndMin) { v -> main.updatePings { it.copy(quietEndMin = v) } }
                Text(
                    if (s.quietStartMin == s.quietEndMin) "Тихих часов нет: Friedrich пишет круглосуточно"
                    else "В это время Friedrich молчит, а утром напишет первым",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Text("Максимум сообщений в день: ${s.dailyPingCap}", color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                GoldSlider(s.dailyPingCap.toFloat(), 5f..150f, 28) { v ->
                    main.updatePings { it.copy(dailyPingCap = (v / 5f).roundToInt() * 5) }
                }
                val target = sessions.firstOrNull { it.id == s.pingSessionId }
                SettingRow(
                    "Куда приходят смс",
                    (target?.title ?: "Главный чат") + " — изменить можно долгим нажатием на чат в списке"
                )
            }

            // ───────── Фон ─────────
            SectionTitle("Работа в фоне («всегда включён»)")
            Panel {
                Toggle(
                    "Постоянный сервис",
                    "Тихое уведомление «Friedrich на связи» не даёт системе выгрузить приложение",
                    s.keepAlive
                ) { v -> main.setKeepAlive(v) }
                StatusRow(
                    "Уведомления", notificationsOn,
                    if (notificationsOn) "разрешены" else "запрещены — Friedrich не сможет писать"
                ) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }
                StatusRow(
                    "Экономия батареи", batteryFree,
                    if (batteryFree) "отключена для Фридриха — отлично" else "включена — система может задерживать сообщения"
                ) { requestIgnoreBattery(context) }
                Text(
                    "На Xiaomi, Huawei, Honor, Oppo, Vivo, Samsung дополнительно включи для приложения «Автозапуск» и «Без ограничений» в настройках батареи — иначе прошивка может усыплять приложение.",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Chip("Открыть настройки приложения", onClick = { openAppSettings(context) })
            }

            // ───────── Звук и вид ─────────
            SectionTitle("Озвучка и текст")
            Panel {
                Toggle("Озвучка немецкого", "Долгое нажатие на сообщение → «Озвучить»", s.ttsEnabled) { v ->
                    main.update { it.copy(ttsEnabled = v) }
                }
                Text("Скорость речи: ×" + "%.1f".format(s.ttsRate), color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                GoldSlider(s.ttsRate, 0.5f..1.3f, 7) { v -> main.update { it.copy(ttsRate = (v * 10).roundToInt() / 10f) } }
                Text("Размер текста: ×" + "%.1f".format(s.fontScale), color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                GoldSlider(s.fontScale, 0.8f..1.4f, 5) { v -> main.update { it.copy(fontScale = (v * 10).roundToInt() / 10f) } }
            }

            // ───────── Данные ─────────
            SectionTitle("Данные")
            Panel {
                SettingRow("Экспорт всех данных", "Досье, словарь и переписка одним JSON-файлом", onClick = { main.exportAll(context) })
                SettingRow(
                    "Сбросить всё", "Удалить переписку, досье и настройки (ключ останется)",
                    onClick = { wipeConfirm = true }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Friedrich 1.0 · Gemini API · данные хранятся только на этом устройстве",
                color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (wipeConfirm) {
        ConfirmDialog(
            title = "Сбросить всё?",
            text = "Переписка, словарь, ошибки, прогресс и настройки будут удалены без возможности восстановления.",
            confirm = "Сбросить", danger = true,
            onConfirm = { main.wipeAll() },
            onDismiss = { wipeConfirm = false }
        )
    }
}

// ───────────────────────── Элементы ─────────────────────────

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Fx.OnGold, checkedTrackColor = Fx.Gold,
                uncheckedThumbColor = Fx.TextDim, uncheckedTrackColor = Fx.Ink, uncheckedBorderColor = Fx.Line
            )
        )
    }
}

@Composable
private fun GoldSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Fx.Gold, activeTrackColor = Fx.Gold, inactiveTrackColor = Fx.Line,
            activeTickColor = Fx.OnGold, inactiveTickColor = Fx.TextFaint
        )
    )
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        maxLines = if (lines == 1) 1 else 6,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    )
}

/** Выбор времени шагом 30 минут. */
@Composable
private fun Stepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fx.TextDim, modifier = Modifier.width(34.dp))
        StepButton("−") { onChange(((minutes - 30) % 1440 + 1440) % 1440) }
        Text(
            Timex.minutesToClock(minutes), color = Fx.Text, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        StepButton("+") { onChange((minutes + 30) % 1440) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Fx.Gold, style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Fx.CardHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatusRow(title: String, ok: Boolean, text: String, onFix: () -> Unit) {
    SettingRow((if (ok) "✅ " else "⚠️ ") + title, text, onClick = onFix) {
        if (!ok) Chip("Исправить", onClick = onFix, accent = true)
    }
}

// ───────────────────────── Системные переходы ─────────────────────────

fun isIgnoringBattery(context: Context): Boolean = try {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.isIgnoringBatteryOptimizations(context.packageName)
} catch (_: Throwable) {
    false
}

@SuppressLint("BatteryLife")
fun requestIgnoreBattery(context: Context) {
    try {
        context.startActivity(
            Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
        try {
            context.startActivity(
                Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            openAppSettings(context)
        }
    }
}

fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
        openAppSettings(context)
    }
}

fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}
