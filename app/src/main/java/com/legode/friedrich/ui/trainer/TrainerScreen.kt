@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.legode.friedrich.ui.trainer

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Fsrs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.data.db.KnowledgeCardEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.SectionTitle
import com.legode.friedrich.ui.common.Segments
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private sealed class ReviewDeckItem {
    abstract val key: String
    abstract val dueAt: Long
    abstract val memoryState: Fsrs.MemoryState
    abstract val frontText: String
    abstract val backText: String
    abstract val badge: String

    data class Card(val e: KnowledgeCardEntity) : ReviewDeckItem() {
        override val key: String get() = "c_${e.id}"
        override val dueAt: Long get() = e.dueAt
        override val memoryState: Fsrs.MemoryState
            get() = Fsrs.MemoryState(e.stability, e.difficulty, e.reps, e.lapses, e.lastReviewedAt)
        override val frontText: String get() = e.front
        override val backText: String get() = e.back
        override val badge: String
            get() = when (e.kind) {
                "transfer" -> "Перенос"
                "critique" -> "Критика"
                "feynman" -> "Фейнман"
                "debias" -> "Дебаисинг"
                else -> "Извлечение"
            }
    }

    data class Anchor(val e: AnchorEntity) : ReviewDeckItem() {
        override val key: String get() = "a_${e.id}"
        override val dueAt: Long get() = e.dueAt
        override val memoryState: Fsrs.MemoryState
            get() = if (e.stability > 0.0) Fsrs.MemoryState(e.stability, e.difficulty, e.reps, e.lapses, e.lastSeenAt)
            else Fsrs.fromLegacy(e.intervalDays, e.ease, e.reps, e.lapses, e.lastSeenAt)
        override val frontText: String
            get() = if (e.trigger.isNotBlank()) "Якорь при «${e.trigger}»: вспомни формулу и смысл"
            else "Вспомни смысл опоры: «${e.phrase}»"
        override val backText: String get() = "«${e.phrase}»\n\n${e.why}"
        override val badge: String get() = "⚓ Якорь силы"
    }
}

@Composable
fun TrainerScreen(main: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Fx.Ink)) {
        TopBar(title = "Тренажёр", subtitle = "память FSRS · фокус WOOP · соматика") {
            IconButton(onClick = { main.open(Screen.Settings) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Fx.Text)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Fx.Panel, contentColor = Fx.Gold) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Память FSRS") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Фокус WOOP") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Тело и Эмоции") })
        }
        when (tab) {
            0 -> FsrsMemoryTab(main)
            1 -> FocusWoopTab(main)
            else -> SomaticEmotionTab(main)
        }
    }
}

// ───────────────────────── Вкладка 1: Интервальная память FSRS-5 ─────────────────────────

@Composable
private fun FsrsMemoryTab(main: MainViewModel) {
    val cards by main.knowledgeCards.collectAsStateWithLifecycle()
    val anchors by main.anchors.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(Timex.now()) }
    var practiceAhead by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }

    val deck = remember(cards, anchors, now, practiceAhead) {
        val combined = cards.map { ReviewDeckItem.Card(it) } + anchors.map { ReviewDeckItem.Anchor(it) }
        val due = combined.filter { it.dueAt <= now }.sortedBy { it.dueAt }
        if (due.isNotEmpty() || !practiceAhead) due else combined.sortedBy { it.dueAt }.take(10)
    }

    val current = deck.firstOrNull()
    LaunchedEffect(current?.key) { revealed = false }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("К повтору: ${deck.size}", if (deck.isNotEmpty()) Fx.Gold else Fx.Green)
                Pill("Всего в памяти: ${cards.size + anchors.size}")
                Spacer(Modifier.weight(1f))
                Chip("＋ Карточка", onClick = { addOpen = true }, accent = true)
            }
        }

        if (current == null) {
            item {
                Panel {
                    Text("На сейчас вся очередь разобрана ✅", color = Fx.Green, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "FSRS вернёт карточки ровно тогда, когда вероятность забывания приблизится к ${((1f - settings.desiredRetention) * 100).roundToInt()}%. " +
                            "Новые карточки можно сгенерировать из любой книги, статьи или PDF во вкладке «База».",
                        color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                    )
                    if (cards.isNotEmpty() || anchors.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Повторить вне графика", onClick = { practiceAhead = true }, accent = true)
                            Chip("Импортировать материал", onClick = { main.open(Screen.Reference(null)) })
                        }
                    }
                }
            }
        } else {
            item(key = current.key) {
                val retention = settings.desiredRetention.toDouble()
                val previews = remember(current.key, now, retention) {
                    Fsrs.previewIntervals(current.memoryState, now, retention)
                }
                val rPct = if (current.memoryState.isNew) 100
                else (Fsrs.retrievabilityNow(current.memoryState, now) * 100).roundToInt()

                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(current.badge, Fx.Gold)
                        Spacer(Modifier.width(6.dp))
                        if (!current.memoryState.isNew) Pill("след R ≈ $rPct%")
                        Spacer(Modifier.weight(1f))
                        if (current is ReviewDeckItem.Card && current.e.skillId.isNotBlank()) {
                            Curriculum.topic(current.e.skillId)?.let { Pill(it.title.take(18)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(current.frontText, color = Fx.Text, style = MaterialTheme.typography.titleMedium)

                    if (current is ReviewDeckItem.Card && current.e.hint.isNotBlank() && !revealed) {
                        Spacer(Modifier.height(8.dp))
                        Text("Подсказка: ${current.e.hint}", color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(14.dp))
                    if (!revealed) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Fx.Gold)
                                .clickable { revealed = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Показать разбор (сначала вспомни сам)", color = Fx.OnGold, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Fx.Ink)
                                .padding(12.dp)
                        ) {
                            Text(current.backText, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Оцени честное усилие извлечения:", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val btns = listOf(
                                Triple(Fsrs.Rating.AGAIN, Fx.Red, previews[Fsrs.Rating.AGAIN].orEmpty()),
                                Triple(Fsrs.Rating.HARD, Fx.Amber, previews[Fsrs.Rating.HARD].orEmpty()),
                                Triple(Fsrs.Rating.GOOD, Fx.Green, previews[Fsrs.Rating.GOOD].orEmpty()),
                                Triple(Fsrs.Rating.EASY, Fx.Blue, previews[Fsrs.Rating.EASY].orEmpty())
                            )
                            for ((rating, col, iv) in btns) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Fx.Ink)
                                        .border(1.dp, col.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (current) {
                                                is ReviewDeckItem.Card -> main.reviewCard(current.e.id, rating)
                                                is ReviewDeckItem.Anchor -> main.reviewAnchor(current.e.id, rating)
                                            }
                                            now = Timex.now()
                                            revealed = false
                                        }
                                        .padding(vertical = 9.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(rating.label, color = col, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(iv, color = Fx.TextDim, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cards.isNotEmpty()) {
            item { SectionTitle("Последние карточки (${cards.size})") }
            items(cards.take(15), key = { "list_c_${it.id}" }) { c ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.front, color = Fx.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val dueStr = if (c.dueAt <= now) "пора сейчас" else Timex.until(c.dueAt, now)
                            Text("${c.status} · повтор: $dueStr", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {
                            if (settings.ttsEnabled) main.speaker.speak("${c.front}. ${c.back}", settings.ttsRate)
                        }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучить", tint = Fx.TextDim)
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddCardDialog(
            onSave = { front, back, kind ->
                main.addManualCard(front, back, kind)
                Toast.makeText(context, "Карточка добавлена в FSRS", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { addOpen = false }
        )
    }
}

@Composable
private fun AddCardDialog(onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var kindIdx by remember { mutableIntStateOf(0) }
    val kinds = listOf("recall" to "Вопрос", "transfer" to "Перенос", "debias" to "Ловушка")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая карточка памяти") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segments(kinds.map { it.second }, kindIdx, { kindIdx = it })
                OutlinedTextField(front, { front = it }, label = { Text("Вопрос / задача на вспоминание") }, minLines = 2, maxLines = 4)
                OutlinedTextField(back, { back = it }, label = { Text("Суть / ответ / критерий") }, minLines = 3, maxLines = 6)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(front.trim(), back.trim(), kinds[kindIdx].first)
                    onDismiss()
                },
                enabled = front.isNotBlank() && back.isNotBlank()
            ) { Text("Создать", color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

// ───────────────────────── Вкладка 2: Фокус-таймер WOOP ─────────────────────────

@Composable
private fun FocusWoopTab(main: MainViewModel) {
    val sessions by main.focusSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val presets = listOf(
        Triple("deep_90", "Ультрадианный блок", 90),
        Triple("sprint_25", "Боевой спринт", 25),
        Triple("micro_2", "Анти-старт (2 мин)", 2)
    )
    var presetIdx by remember { mutableIntStateOf(1) }
    var goal by remember { mutableStateOf("") }
    var obstacle by remember { mutableStateOf("") }
    var ifThen by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var totalSec by remember { mutableIntStateOf(25 * 60) }
    var remainSec by remember { mutableIntStateOf(25 * 60) }
    var postReviewOpen by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (remainSec > 0 && running) {
            delay(1000L)
            remainSec--
        }
        if (remainSec == 0 && running) {
            running = false
            postReviewOpen = true
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                if (!running) {
                    Text("Режим глубокого внимания", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Segments(presets.map { it.second.substringBefore('(').trim() }, presetIdx, { i ->
                        presetIdx = i
                        totalSec = presets[i].third * 60
                        remainSec = totalSec
                    })
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        goal, { goal = it }, label = { Text("Одна конкретная задача блока") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        obstacle, { obstacle = it }, label = { Text("Главная помеха (WOOP): что попытается отвлечь?") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        ifThen, { ifThen = it }, label = { Text("Правило «Если отвлекусь, то я…»") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Fx.Gold)
                            .clickable {
                                totalSec = presets[presetIdx].third * 60
                                remainSec = totalSec
                                running = true
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Запустить ${presets[presetIdx].third} мин ⚡", color = Fx.OnGold, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val mm = remainSec / 60
                    val ss = remainSec % 60
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Pill(presets[presetIdx].second, Fx.Gold)
                        Spacer(Modifier.height(10.dp))
                        Text("%02d:%02d".format(mm, ss), color = Fx.Gold, fontSize = 52.sp, fontWeight = FontWeight.Black)
                        if (goal.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(goal, color = Fx.Text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                        if (ifThen.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Предохранитель: $ifThen", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip("Завершить и оценить ✓", onClick = {
                                running = false
                                postReviewOpen = true
                            }, accent = true)
                            Chip("Сброс", onClick = {
                                running = false
                                remainSec = totalSec
                            })
                        }
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item { SectionTitle("Журнал сессий (${sessions.size})") }
            items(sessions.take(12), key = { it.id }) { fs ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fs.goal.ifBlank { fs.kind }, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${fs.actualMinutes} мин · поток ${fs.focusQuality}/10 · ${Timex.humanDay(fs.createdAt)} ${Timex.hhmm(fs.createdAt)}",
                                color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (fs.completed) Pill("✓ закрыто", Fx.Green)
                    }
                }
            }
        }
    }

    if (postReviewOpen) {
        var quality by remember { mutableIntStateOf(7) }
        var moodAfter by remember { mutableIntStateOf(7) }
        var note by remember { mutableStateOf("") }
        val elapsedMin = ((totalSec - remainSec) / 60).coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { postReviewOpen = false },
            title = { Text("Итог фокус-блока ($elapsedMin мин)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Глубина фокуса: $quality/10", color = Fx.Text)
                    Slider(quality.toFloat(), { quality = it.roundToInt() }, valueRange = 1f..10f, steps = 8)
                    Text("Состояние сейчас: $moodAfter/10", color = Fx.Text)
                    Slider(moodAfter.toFloat(), { moodAfter = it.roundToInt() }, valueRange = 1f..10f, steps = 8)
                    OutlinedTextField(note, { note = it }, label = { Text("Что реально сдвинул?") }, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    main.logFocusSession(
                        kind = presets[presetIdx].first,
                        goal = goal.ifBlank { presets[presetIdx].second },
                        obstacle = obstacle,
                        ifThenPlan = ifThen,
                        plannedMin = presets[presetIdx].third,
                        actualMin = elapsedMin,
                        completed = remainSec <= 5,
                        focusQuality = quality,
                        moodBefore = 0,
                        moodAfter = moodAfter,
                        note = note
                    )
                    postReviewOpen = false
                    remainSec = totalSec
                    Toast.makeText(context, "Сессия записана в досье ⚡", Toast.LENGTH_SHORT).show()
                }) { Text("Записать", color = Fx.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { postReviewOpen = false }) { Text("Закрыть", color = Fx.TextDim) }
            },
            containerColor = Fx.Card
        )
    }
}

// ───────────────────────── Вкладка 3: Дыхательный пейсер + Компас эмоций ─────────────────────────

private data class BreathPhase(val label: String, val sec: Int, val targetScale: Float)

private data class BreathProtocol(
    val id: String,
    val title: String,
    val about: String,
    val phases: List<BreathPhase>
)

private val BREATH_PROTOCOLS = listOf(
    BreathProtocol(
        "breath_phys", "Физиологический вздох", "Быстрый сброс паники и пульса за 4 цикла",
        listOf(BreathPhase("Вдох носом", 3, 0.85f), BreathPhase("До-вдох", 1, 1.0f), BreathPhase("Долгий выдох", 6, 0.35f))
    ),
    BreathProtocol(
        "breath_478", "Сон / тормоз 4–7–8", "Снижение ночной тревоги перед сном",
        listOf(BreathPhase("Вдох", 4, 1.0f), BreathPhase("Задержка", 7, 1.0f), BreathPhase("Плавный выдох", 8, 0.35f))
    ),
    BreathProtocol(
        "breath_box", "Квадрат 4–4–4–4", "Выравнивание ясности ума в стрессе",
        listOf(BreathPhase("Вдох", 4, 1.0f), BreathPhase("Пауза", 4, 1.0f), BreathPhase("Выдох", 4, 0.35f), BreathPhase("Пауза", 4, 0.35f))
    ),
    BreathProtocol(
        "breath_coherent", "Резонанс 5–5", "Вагусный баланс (6 вдохов/мин)",
        listOf(BreathPhase("Ровный вдох", 5, 1.0f), BreathPhase("Мягкий выдох", 5, 0.35f))
    )
)

private data class EmotionCell(val label: String, val score: Int, val color: Color)

private val EMOTION_GRID = listOf(
    EmotionCell("Ярость", 3, Fx.Red),
    EmotionCell("Паника", 2, Fx.Red),
    EmotionCell("Тревога", 4, Fx.Amber),
    EmotionCell("Фрустрация", 4, Fx.Amber),
    EmotionCell("Апатия", 3, Fx.Blue),
    EmotionCell("Истощение", 2, Fx.Blue),
    EmotionCell("Стыд / Вина", 3, Fx.Purple),
    EmotionCell("Одиночество", 4, Fx.Purple),
    EmotionCell("Скука", 5, Fx.TextDim),
    EmotionCell("Ровность", 6, Fx.Cyan),
    EmotionCell("Любопытство", 8, Fx.Green),
    EmotionCell("Ясность / Поток", 9, Fx.Gold)
)

@Composable
private fun SomaticEmotionTab(main: MainViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var protoIdx by remember { mutableIntStateOf(0) }
    var breathing by remember { mutableStateOf(false) }
    var phaseIdx by remember { mutableIntStateOf(0) }
    var secLeft by remember { mutableIntStateOf(3) }
    var cyclesDone by remember { mutableIntStateOf(0) }

    val proto = BREATH_PROTOCOLS[protoIdx]
    val curPhase = proto.phases.getOrElse(phaseIdx) { proto.phases.first() }
    val targetScale = if (breathing) curPhase.targetScale else 0.55f
    val animScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = (curPhase.sec.coerceAtLeast(1) * 900)),
        label = "breath_ring"
    )

    LaunchedEffect(breathing, protoIdx) {
        if (!breathing) return@LaunchedEffect
        phaseIdx = 0
        cyclesDone = 0
        while (breathing) {
            val ph = BREATH_PROTOCOLS[protoIdx].phases[phaseIdx]
            secLeft = ph.sec
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            for (s in ph.sec downTo 1) {
                secLeft = s
                delay(1000L)
                if (!breathing) break
            }
            if (!breathing) break
            phaseIdx = (phaseIdx + 1) % BREATH_PROTOCOLS[protoIdx].phases.size
            if (phaseIdx == 0) cyclesDone++
        }
    }

    var selectedEmotion by remember { mutableStateOf<EmotionCell?>(null) }
    var emotionNote by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Panel {
            Text("Дыхательный пейсер", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
            Text(proto.about, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BREATH_PROTOCOLS.forEachIndexed { idx, p ->
                    Chip(p.title, onClick = {
                        breathing = false
                        protoIdx = idx
                    }, selected = protoIdx == idx)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(175.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(165.dp)) {
                    val maxR = size.minDimension / 2f
                    drawCircle(color = Fx.Line, radius = maxR)
                    drawCircle(color = Fx.Gold.copy(alpha = 0.24f), radius = maxR * animScale)
                    drawCircle(color = Fx.Gold, radius = maxR * animScale * 0.65f)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (breathing) curPhase.label else "Готов",
                        color = Fx.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    if (breathing) {
                        Text("$secLeft с", color = Fx.OnGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("круг ${cyclesDone + 1}", color = Fx.TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (breathing) Fx.CardHigh else Fx.Gold)
                    .clickable {
                        if (breathing) {
                            breathing = false
                            if (cyclesDone >= 1) {
                                main.logFocusSession(
                                    kind = proto.id, goal = proto.title, obstacle = "", ifThenPlan = "",
                                    plannedMin = 2, actualMin = (cyclesDone.coerceAtLeast(1) / 3).coerceAtLeast(1),
                                    completed = true, focusQuality = 7, moodBefore = 0, moodAfter = 0,
                                    note = "Пройдено $cyclesDone циклов дыхания"
                                )
                                Toast.makeText(context, "Практика сохранена в досье 🌊", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            breathing = true
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (breathing) "Остановить и записать ($cyclesDone циклов)" else "Старт (с виброоткликом фаз)",
                    color = if (breathing) Fx.Text else Fx.OnGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Panel {
            Text("Компас эмоций (Гранулярность)", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
            Text(
                "Точное название чувства снижает реактивность миндалины. Выбери, что звучит внутри прямо сейчас:",
                color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (em in EMOTION_GRID) {
                    val on = selectedEmotion == em
                    Text(
                        "${em.label} (${em.score})",
                        color = if (on) Fx.OnGold else Fx.Text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Fx.Gold else Fx.Ink)
                            .border(1.dp, em.color.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .clickable { selectedEmotion = em }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            selectedEmotion?.let { em ->
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    emotionNote, { emotionNote = it },
                    label = { Text("Что стало триггером (необязательно)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Зафиксировать «${em.label}»", onClick = {
                        main.logEmotionCheck(em.label, em.score, emotionNote)
                        emotionNote = ""
                        selectedEmotion = null
                        Toast.makeText(context, "Состояние записано", Toast.LENGTH_SHORT).show()
                    }, accent = true)
                    Chip("Разобрать в чате", onClick = {
                        main.logEmotionCheck(em.label, em.score, emotionNote)
                        main.openHomeChat()
                    })
                }
            }
        }
    }
}