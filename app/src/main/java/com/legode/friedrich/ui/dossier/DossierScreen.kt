@file:OptIn(ExperimentalMaterial3Api::class)

package com.legode.friedrich.ui.dossier

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.brain.Topic
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.StudyListEntity
import com.legode.friedrich.data.db.VocabEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.ConfirmDialog
import com.legode.friedrich.ui.common.InputDialog
import com.legode.friedrich.ui.common.Meter
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.SectionTitle
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx

private val TABS = listOf("Обзор", "Слова", "Ошибки", "Темы", "Списки", "Память")

/** Досье: всё, что Friedrich знает об ученике. Здесь это можно посмотреть и поправить. */
@Composable
fun DossierScreen(main: MainViewModel, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
    ) {
        TopBar(title = "Досье", subtitle = "что Friedrich знает о тебе", onBack = onBack)
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Fx.Panel,
            contentColor = Fx.Gold,
            edgePadding = 8.dp,
            divider = {}
        ) {
            TABS.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, color = if (tab == i) Fx.Gold else Fx.TextDim) }
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            when (tab) {
                0 -> OverviewTab(main)
                1 -> WordsTab(main)
                2 -> MistakesTab(main)
                3 -> TopicsTab(main)
                4 -> ListsTab(main)
                else -> MemoryTab(main)
            }
        }
    }
}

private val PAD = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

// ───────────────────────── Обзор ─────────────────────────

@Composable
private fun OverviewTab(main: MainViewModel) {
    val profile by main.profile.collectAsStateWithLifecycle()
    val topics by main.topics.collectAsStateWithLifecycle()
    val vocab by main.vocab.collectAsStateWithLifecycle()
    val daily by main.daily.collectAsStateWithLifecycle()
    val mistakes by main.mistakes.collectAsStateWithLifecycle()
    val pings by main.pingCount.collectAsStateWithLifecycle()
    val answered by main.pingAnswered.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mastery = remember(topics) { topics.associate { it.topicId to it.mastery } }
    val est = remember(mastery) { Curriculum.estimate(mastery) }
    val now = Timex.now()
    val today = daily.firstOrNull { it.day == Timex.today() }
    val focus = remember(mastery) { Curriculum.focus(mastery, 3) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Fx.Gold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(est.label, color = Fx.OnGold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.name?.takeIf { it.isNotBlank() } ?: "Ученик",
                            color = Fx.Text, style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "🔥 серия ${profile?.streak ?: 0} дн (рекорд ${profile?.bestStreak ?: 0}) · ⭐ ${profile?.xp ?: 0} XP",
                            color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LevelLine("A0 — база", est.a0)
                LevelLine("A1.1 — первые шаги", est.a11)
                LevelLine("A1.2 — уверенный A1", est.a12)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Слов", vocab.size.toString(), Modifier.weight(1f))
                StatBox("Знаю", vocab.count { it.status == "known" }.toString(), Modifier.weight(1f))
                StatBox("Повторить", vocab.count { it.dueAt <= now }.toString(), Modifier.weight(1f))
                StatBox("Ошибок", mistakes.count { !it.resolved }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Panel {
                Text("Сегодня", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сообщений: ${today?.userMsgs ?: 0} · верно: ${today?.correct ?: 0} · ошибок: ${today?.wrong ?: 0} · новых слов: ${today?.wordsAdded ?: 0}",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "СМС от Фридриха: ${today?.pings ?: 0}, отвечено: ${today?.pingsAnswered ?: 0} · всего за всё время: $answered из $pings",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text("Активность за 14 дней", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                val days = remember(daily) { daily.sortedBy { it.day }.takeLast(14) }
                val peak = (days.maxOfOrNull { it.userMsgs } ?: 0).coerceAtLeast(1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (days.isEmpty()) {
                        Text("Пока пусто — напиши Фридриху 🙂", color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall)
                    }
                    for (d in days) {
                        val h = (d.userMsgs.toFloat() / peak).coerceIn(0.04f, 1f)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(h)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (d.userMsgs > 0) Fx.Gold else Fx.Line)
                        )
                    }
                }
            }
        }
        item {
            Panel {
                Text("Дальше по программе", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (focus.isEmpty()) {
                    Text("Вся программа A0–A1 освоена. Glückwunsch! 🎓", color = Fx.Green)
                }
                for (t in focus) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { main.newSession(Skills.LESSON, t.id) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                            Meter(mastery[t.id] ?: 0, Modifier.fillMaxWidth(), height = 4.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Pill("урок ▶", Fx.Gold)
                    }
                }
            }
        }
        item {
            Panel {
                Text("План Фридриха", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    profile?.plan?.takeIf { it.isNotBlank() }
                        ?: "Плана пока нет. Friedrich составит его сам после нескольких десятков сообщений — или прямо сейчас по кнопке.",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        if (main.reflecting) "Думаю…" else "🧠 Проанализируй меня",
                        onClick = {
                            main.reflectNow { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) "Готово: заметки и план обновлены" else "Не получилось. Нужны ключ, сеть и хотя бы пара сообщений.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        accent = true
                    )
                    Chip("🧪 Тест уровня", onClick = { main.newSession(Skills.PLACEMENT) })
                }
            }
        }
    }
}

@Composable
private fun LevelLine(label: String, percent: Int) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
        Meter(percent, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("$percent%", color = Fx.Text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Fx.Card)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Fx.Gold, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Fx.TextDim, style = MaterialTheme.typography.labelSmall)
    }
}

// ───────────────────────── Слова ─────────────────────────

@Composable
private fun WordsTab(main: MainViewModel) {
    val vocab by main.vocab.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<VocabEntity?>(null) }
    val now = Timex.now()

    val shown = remember(vocab, filter, query) {
        val q = query.trim().lowercase()
        vocab.filter { v ->
            val byFilter = when (filter) {
                1 -> v.dueAt <= now
                2 -> v.status == "new"
                3 -> v.status == "learning"
                4 -> v.status == "known"
                else -> true
            }
            byFilter && (q.isEmpty() || v.de.lowercase().contains(q) || v.ru.lowercase().contains(q))
        }
    }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🃏 Повторить с Фридрихом", onClick = { main.newSession(Skills.REVIEW) }, accent = true)
                Chip("＋ Слово", onClick = { addOpen = true })
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Поиск по словарю", color = Fx.TextFaint) },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("Все ${vocab.size}", "Пора повторить", "Новые", "Учу", "Знаю")
                items(labels.size) { i -> Chip(labels[i], onClick = { filter = i }, selected = filter == i) }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (vocab.isEmpty()) "Словарь пуст. Friedrich сам добавляет сюда слова из ваших разговоров — или добавь своё."
                    else "Ничего не найдено",
                    color = Fx.TextDim, modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(shown, key = { it.id }) { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Fx.Card)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot = when (v.status) {
                    "known" -> Fx.Green
                    "learning" -> Fx.Gold
                    else -> Fx.Blue
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        v.de + if (v.plural.isNotBlank()) "  ·  " + v.plural else "",
                        color = Fx.Text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium
                    )
                    Text(v.ru, color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
                    if (v.example.isNotBlank()) {
                        Text(v.example, color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall)
                    }
                    val dueText = if (v.dueAt <= now) "пора повторить" else "повтор через " + Timex.until(v.dueAt, now).removePrefix("≈ ")
                    Text(
                        "✓${v.correct} ✗${v.wrong} · $dueText",
                        color = if (v.dueAt <= now) Fx.Blue else Fx.TextFaint, style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = { if (settings.ttsEnabled) main.speaker.speak(v.de, settings.ttsRate) }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Произнести", tint = Fx.TextDim, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { deleteFor = v }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (addOpen) AddWordDialog(onAdd = { de, ru -> main.addWord(de, ru) }, onDismiss = { addOpen = false })
    deleteFor?.let { v ->
        ConfirmDialog(
            title = "Удалить «${v.de}»?", text = "Слово исчезнет из словаря и повторений.",
            confirm = "Удалить", danger = true,
            onConfirm = { main.deleteWord(v.id) }, onDismiss = { deleteFor = null }
        )
    }
}

@Composable
private fun AddWordDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var de by remember { mutableStateOf("") }
    var ru by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое слово") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(de, { de = it }, label = { Text("По-немецки (с артиклем)") }, singleLine = true)
                OutlinedTextField(ru, { ru = it }, label = { Text("Перевод") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(de.trim(), ru.trim())
                    onDismiss()
                },
                enabled = de.isNotBlank() && ru.isNotBlank()
            ) { Text("Добавить", color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

// ───────────────────────── Ошибки ─────────────────────────

@Composable
private fun MistakesTab(main: MainViewModel) {
    val mistakes by main.mistakes.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Chip("🩹 Работа над ошибками с Фридрихом", onClick = { main.newSession(Skills.MISTAKES) }, accent = true)
        }
        if (mistakes.isEmpty()) {
            item {
                Text(
                    "Ошибок пока нет. Когда ты ошибаешься, Friedrich записывает это сюда и потом возвращается к правилу.",
                    color = Fx.TextDim, modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(mistakes, key = { it.id }) { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Fx.Card)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.bad, color = if (m.resolved) Fx.TextFaint else Fx.Red,
                        style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough
                    )
                    Text(m.good, color = if (m.resolved) Fx.TextDim else Fx.Green, style = MaterialTheme.typography.bodyLarge)
                    if (m.why.isNotBlank()) Text(m.why, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("×${m.times}", Fx.Gold)
                        Curriculum.topic(m.topicId)?.let { Pill(it.title) }
                        if (m.resolved) Pill("закрыта", Fx.Green)
                    }
                }
                IconButton(onClick = { main.resolveMistake(m.id, !m.resolved) }) {
                    Icon(
                        Icons.Filled.Check, contentDescription = "Закрыть",
                        tint = if (m.resolved) Fx.Green else Fx.TextFaint, modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { main.deleteMistake(m.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ───────────────────────── Темы ─────────────────────────

@Composable
private fun TopicsTab(main: MainViewModel) {
    val topics by main.topics.collectAsStateWithLifecycle()
    val mastery = remember(topics) { topics.associate { it.topicId to it.mastery } }
    var sheetFor by remember { mutableStateOf<Topic?>(null) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (block in Curriculum.blocks) {
            item(key = "b" + block.id) {
                val list = Curriculum.inBlock(block.id)
                val avg = if (list.isEmpty()) 0 else list.sumOf { mastery[it.id] ?: 0 } / list.size
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.emoji + "  " + block.title, color = Fx.Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$avg%", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium)
                }
            }
            items(Curriculum.inBlock(block.id), key = { it.id }) { t ->
                val value = mastery[t.id] ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Fx.Card)
                        .clickable { sheetFor = t }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(5.dp))
                        Meter(
                            value, Modifier.fillMaxWidth(), height = 4.dp,
                            color = when {
                                value >= 70 -> Fx.Green
                                value >= 30 -> Fx.Gold
                                else -> Fx.Blue
                            }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("$value%", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
                    Pill(t.level)
                }
            }
        }
    }

    sheetFor?.let { t ->
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Fx.Card
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.title, color = Fx.Text, style = MaterialTheme.typography.titleLarge)
                Text(t.de + " · " + t.level + " · освоено ${mastery[t.id] ?: 0}%", color = Fx.TextDim)
                Chip("📚 Урок по теме", onClick = {
                    sheetFor = null
                    main.newSession(Skills.LESSON, t.id)
                }, accent = true)
                Chip("📖 Открыть шпаргалку", onClick = {
                    sheetFor = null
                    main.open(Screen.Reference(t.id))
                })
                Chip("✅ Я это уже знаю (90%)", onClick = {
                    main.setMastery(t.id, 90)
                    sheetFor = null
                })
                Chip("↺ Сбросить прогресс темы", onClick = {
                    main.setMastery(t.id, 0)
                    sheetFor = null
                })
            }
        }
    }
}

// ───────────────────────── Списки ─────────────────────────

@Composable
private fun ListsTab(main: MainViewModel) {
    val lists by main.lists.collectAsStateWithLifecycle()
    val progress by main.listProgress.collectAsStateWithLifecycle()
    val byId = remember(progress) { progress.associateBy { it.listId } }
    var createOpen by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<StudyListEntity?>(null) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Chip("＋ Новый список", onClick = { createOpen = true }, accent = true)
        }
        if (lists.isEmpty()) {
            item {
                Text(
                    "Списков пока нет. Пришли Фридриху в чат слова или предложения (текстом, файлом или фото) и скажи «давай отработаем» — он сам создаст список. Или добавь его здесь.",
                    color = Fx.TextDim, modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(lists, key = { it.id }) { l ->
            val p = byId[l.id]
            val total = p?.total ?: 0
            val done = p?.mastered ?: 0
            Panel {
                Text(l.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Text(
                    "освоено $done из $total · создан " + Timex.humanDay(l.createdAt).lowercase(),
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Meter(if (total == 0) 0 else done * 100 / total, Modifier.fillMaxWidth(), color = if (done >= total && total > 0) Fx.Green else Fx.Gold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("▶ Отработать", onClick = { main.startDrill(l) }, accent = true)
                    Chip("↺ Сначала", onClick = { main.resetList(l.id) })
                    Chip("Удалить", onClick = { deleteFor = l })
                }
            }
        }
    }

    if (createOpen) CreateListDialog(onCreate = { title, text -> main.createListFromText(title, text) }, onDismiss = { createOpen = false })
    deleteFor?.let { l ->
        ConfirmDialog(
            title = "Удалить список «${l.title}»?", text = "Слова из него останутся в словаре.",
            confirm = "Удалить", danger = true,
            onConfirm = { main.deleteList(l.id) }, onDismiss = { deleteFor = null }
        )
    }
}

@Composable
private fun CreateListDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый список") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Каждая строка: немецкий — перевод") },
                    minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(title.trim(), text)
                    onDismiss()
                },
                enabled = text.isNotBlank()
            ) { Text("Создать", color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

// ───────────────────────── Память ─────────────────────────

@Composable
private fun MemoryTab(main: MainViewModel) {
    val facts by main.facts.collectAsStateWithLifecycle()
    val journal by main.journal.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            SectionTitle("Факты о тебе")
            Chip("＋ Рассказать Фридриху о себе", onClick = { addOpen = true }, accent = true)
        }
        if (facts.isEmpty()) {
            item {
                Text(
                    "Friedrich запоминает то, что узнаёт из разговора: город, работу, семью, цели — и использует это в заданиях.",
                    color = Fx.TextDim, modifier = Modifier.padding(12.dp)
                )
            }
        }
        items(facts, key = { "f" + it.id }) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Fx.Card)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(f.text, color = Fx.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { main.deleteFact(f.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Забыть", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { SectionTitle("Заметки Фридриха") }
        if (journal.isEmpty()) {
            item {
                Text(
                    "Раз в несколько десятков сообщений Friedrich анализирует, как идёт учёба, и записывает наблюдения сюда.",
                    color = Fx.TextDim, modifier = Modifier.padding(12.dp)
                )
            }
        }
        items(journal, key = { "j" + it.id }) { j ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (j.kind == "milestone") "🏅 " else "🧠 ") + Timex.humanDay(j.createdAt) + ", " + Timex.hhmm(j.createdAt),
                        color = Fx.Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "удалить", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { main.deleteJournal(j.id) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(j.text, color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (addOpen) {
        InputDialog(
            title = "Факт о себе",
            hint = "Например: «Работаю водителем», «Переезжаю в Мюнхен в мае», «Люблю футбол».",
            confirm = "Запомнить",
            onConfirm = { main.addFact(it) },
            onDismiss = { addOpen = false }
        )
    }
}
