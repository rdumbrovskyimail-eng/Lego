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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.RadarScores
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.brain.Topic
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.int
import com.legode.friedrich.core.parseObjectOrNull
import com.legode.friedrich.data.db.ActionPlanEntity
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.ActivityHeatmap
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.ConfirmDialog
import com.legode.friedrich.ui.common.InputDialog
import com.legode.friedrich.ui.common.MarkdownText
import com.legode.friedrich.ui.common.Meter
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.RadarChart
import com.legode.friedrich.ui.common.SectionTitle
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlin.math.roundToInt

private val TABS = listOf("Обзор", "Неделя", "Якоря", "Ловушки", "Навыки", "WOOP-планы", "Избранное", "Память")
private val PAD = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

@Composable
fun DossierScreen(main: MainViewModel, onBack: (() -> Unit)? = null) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Fx.Ink)) {
        TopBar(title = "Досье", subtitle = "радар роста · якоря · ловушки · синтез", onBack = onBack) {
            if (onBack == null) {
                IconButton(onClick = { main.open(Screen.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Fx.Text)
                }
            }
        }
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Fx.Panel,
            contentColor = Fx.Gold,
            edgePadding = 8.dp,
            divider = {}
        ) {
            TABS.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, color = if (tab == i) Fx.Gold else Fx.TextDim) })
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> OverviewTab(main)
                1 -> WeeklyTab(main)
                2 -> AnchorsTab(main)
                3 -> TrapsTab(main)
                4 -> SkillsTab(main)
                5 -> PlansTab(main)
                6 -> StarredTab(main)
                else -> MemoryTab(main)
            }
        }
    }
}

@Composable
private fun OverviewTab(main: MainViewModel) {
    val profile by main.profile.collectAsStateWithLifecycle()
    val skills by main.skills.collectAsStateWithLifecycle()
    val anchors by main.anchors.collectAsStateWithLifecycle()
    val cards by main.knowledgeCards.collectAsStateWithLifecycle()
    val daily by main.daily.collectAsStateWithLifecycle()
    val traps by main.traps.collectAsStateWithLifecycle()
    val predictions by main.predictions.collectAsStateWithLifecycle()
    val grid by main.activityGrid.collectAsStateWithLifecycle()
    val pings by main.pingCount.collectAsStateWithLifecycle()
    val answered by main.pingAnswered.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mastery = remember(skills) { skills.associate { it.skillId to it.mastery } }
    val est = remember(mastery) { Curriculum.estimate(mastery) }
    val brierAvg = remember(predictions) {
        val res = predictions.filter { it.resolved }
        if (res.isEmpty()) -1.0 else Srs.computeCalibration(res.map { it.probabilityPct to it.outcome }).brierScore
    }
    val radar = remember(mastery, brierAvg) { Curriculum.radarScores(mastery, brierAvg) }
    val now = Timex.now()
    val today = daily.firstOrNull { it.day == Timex.today() }
    val focus = remember(mastery) { Curriculum.focus(mastery, 3) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Fx.Gold),
                        contentAlignment = Alignment.Center
                    ) {
                        val badge = if ((profile?.lastMood ?: 0) > 0) "${profile?.lastMood}/10" else est.label.take(4)
                        Text(badge, color = Fx.OnGold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.name?.takeIf { it.isNotBlank() } ?: "Профиль Атласа", color = Fx.Text, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Ранг: ${profile?.cognitiveRank ?: "наблюдатель"} · опора: ${est.label}",
                            color = Fx.Gold, style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "🔥 ${profile?.streak ?: 0} дн (рекорд ${profile?.bestStreak ?: 0}) · ⚡ ${profile?.xp ?: 0} XP",
                            color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                RadarChart(radar)
                Spacer(Modifier.height(6.dp))
                LevelLine("R0 · Стабилизация", est.r0)
                LevelLine("R1 · Взлом ловушек", est.r1)
                LevelLine("R2 · Смысл и опора", est.r2)
                LevelLine("R3 · Прорыв и воля", est.r3)
                LevelLine("M1–M3 · Интеллект", est.intelOverall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Якорей", anchors.size.toString(), Modifier.weight(1f))
                StatBox("Карт FSRS", cards.size.toString(), Modifier.weight(1f))
                StatBox("Повтор", (anchors.count { it.dueAt <= now } + cards.count { it.dueAt <= now }).toString(), Modifier.weight(1f))
                StatBox("Ловушек", traps.count { !it.resolved }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Panel {
                Text("Сегодня и 14 дней", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Сообщений: ${today?.userMsgs ?: 0} · шагов: ${today?.stepsDone ?: 0} · карт: ${today?.cardsReviewed ?: 0} · фокус: ${today?.focusMinutes ?: 0}м",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                )
                Text("СМС отклик: $answered из $pings", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                val days = remember(daily) { daily.sortedBy { it.day }.takeLast(14) }
                val peak = (days.maxOfOrNull { it.userMsgs } ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(54.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (d in days) {
                        val avgMood = if (d.moodCount > 0) d.moodSum.toFloat() / d.moodCount else 0f
                        val h = if (avgMood > 0f) (avgMood / 10f).coerceIn(0.12f, 1f) else (d.userMsgs.toFloat() / peak).coerceIn(0.06f, 1f)
                        val barColor = when {
                            avgMood >= 7f -> Fx.Green
                            avgMood >= 4f -> Fx.Gold
                            avgMood > 0f -> Fx.Red
                            d.userMsgs > 0 -> Fx.Blue
                            else -> Fx.Line
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(4.dp)).background(barColor))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Тепловая карта активности (14 дней)", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                ActivityHeatmap(grid)
            }
        }
        item {
            Panel {
                Text("Ближайшие навыки в фокусе", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                for (t in focus) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { main.newSession(Skills.LESSON, t.id) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                            Meter(mastery[t.id] ?: 0, Modifier.fillMaxWidth(), height = 4.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Pill("практика ▶", Fx.Gold)
                    }
                }
            }
        }
        item {
            Panel {
                Text("Тактика Атласа", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    profile?.tactic?.takeIf { it.isNotBlank() } ?: "Тактический план обновится автоматически после серии диалогов.",
                    color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (main.reflecting) "Анализирую…" else "🧠 Анализ тактики", onClick = {
                        main.reflectNow { ok ->
                            Toast.makeText(context, if (ok) "Тактика обновлена" else "Нужны ключ, сеть и сообщения", Toast.LENGTH_LONG).show()
                        }
                    }, accent = true)
                    Chip("🧭 Аудит", onClick = { main.newSession(Skills.DIAGNOSTIC) })
                }
            }
        }
    }
}

@Composable
private fun WeeklyTab(main: MainViewModel) {
    val reviews by main.weeklyReviews.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Chip(if (main.generatingWeekly) "Собираю синтез…" else "📊 Сгенерировать синтез недели", onClick = {
                main.generateWeeklyReviewNow { ok ->
                    Toast.makeText(context, if (ok) "Воскресный отчёт готов!" else "Нужны ключ и интернет", Toast.LENGTH_LONG).show()
                }
            }, accent = true)
        }
        if (reviews.isEmpty()) {
            item {
                Panel {
                    Text("Воскресный Executive Review", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Каждое воскресенье вечером Атлас подводит итоги недели: корреляции сна/фокуса/состояния, главные ловушки ума, Brier Score и вызов роста на следующую неделю.",
                        color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        items(reviews, key = { it.id }) { r ->
            val sc = parseObjectOrNull(r.scoresJson)
            val radar = if (sc != null) RadarScores(
                resilience = sc.int("resilience"),
                critical = sc.int("critical"),
                systems = sc.int("systems"),
                calibration = sc.int("calibration"),
                learning = sc.int("learning"),
                execution = sc.int("execution")
            ) else null

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Неделя с ${r.weekStartDay}", color = Fx.Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (r.avgMood > 0) Pill("фон %.1f/10".format(r.avgMood))
                    IconButton(onClick = { main.deleteWeeklyReview(r.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(16.dp))
                    }
                }
                if (radar != null) RadarChart(radar)
                Text(r.summary, color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
                if (r.bestWin.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("🏆 Победа: ${r.bestWin}", color = Fx.Green, style = MaterialTheme.typography.bodySmall)
                }
                if (r.topTrigger.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("⚠️ Триггер недели: ${r.topTrigger}", color = Fx.Red, style = MaterialTheme.typography.bodySmall)
                }
                if (r.nextWeekChallenge.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("🎯 Вызов следующей недели: ${r.nextWeekChallenge}", color = Fx.Gold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LevelLine(label: String, percent: Int) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(146.dp))
        Meter(percent, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("$percent%", color = Fx.Text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Fx.Card).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Fx.Gold, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Fx.TextDim, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnchorsTab(main: MainViewModel) {
    val anchors by main.anchors.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<AnchorEntity?>(null) }
    val now = Timex.now()

    val shown = remember(anchors, filter, query) {
        val q = query.trim().lowercase()
        anchors.filter { a ->
            val byFilter = when (filter) {
                1 -> a.dueAt <= now
                2 -> a.status == "new"
                3 -> a.status == "active"
                4 -> a.status == "core"
                else -> true
            }
            byFilter && (q.isEmpty() || a.phrase.lowercase().contains(q) || a.why.lowercase().contains(q) || a.trigger.lowercase().contains(q))
        }
    }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("⚓ Закрепить в чате", onClick = { main.newSession(Skills.REVIEW) }, accent = true)
                Chip("＋ Якорь", onClick = { addOpen = true })
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Поиск по якорям", color = Fx.TextFaint) },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("Все ${anchors.size}", "Пора закрепить", "Новые", "Активные", "Вживлённые")
                items(labels.size) { i -> Chip(labels[i], onClick = { filter = i }, selected = filter == i) }
            }
        }
        items(shown, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fx.Card).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot = when (a.status) {
                    "core" -> Fx.Green
                    "active" -> Fx.Gold
                    else -> Fx.Blue
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.phrase, color = Fx.Text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(a.why, color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
                    val dueText = if (a.dueAt <= now) "пора напомнить" else "через " + Timex.until(a.dueAt, now).removePrefix("≈ ")
                    Text("✓${a.reinforced} ↻${a.relapses} · $dueText", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { if (settings.ttsEnabled) main.speaker.speak("${a.phrase}. ${a.why}", settings.ttsRate) }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучить", tint = Fx.TextDim, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { deleteFor = a }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (addOpen) AddAnchorDialog(onAdd = { p, w, tr -> main.addAnchor(p, w, "Личный якорь", tr) }, onDismiss = { addOpen = false })
    deleteFor?.let { a ->
        ConfirmDialog("Удалить якорь?", "«${a.phrase}» исчезнет из повторений.", "Удалить", { main.deleteAnchor(a.id) }, { deleteFor = null }, true)
    }
}

@Composable
private fun AddAnchorDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый якорь силы") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phrase, { phrase = it }, label = { Text("Опорная фраза / модель") }, minLines = 2, maxLines = 4)
                OutlinedTextField(why, { why = it }, label = { Text("Почему она тебя держит") }, minLines = 2, maxLines = 4)
                OutlinedTextField(trigger, { trigger = it }, label = { Text("Триггер (тревога, апатия…)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(phrase.trim(), why.trim(), trigger.trim())
                onDismiss()
            }, enabled = phrase.isNotBlank() && why.isNotBlank()) { Text("Сохранить", color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

@Composable
private fun TrapsTab(main: MainViewModel) {
    val traps by main.traps.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Chip("🪤 Охота на ловушки с Атласом", onClick = { main.newSession(Skills.MISTAKES) }, accent = true) }
        items(traps, key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fx.Card).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.thought, color = if (t.resolved) Fx.TextFaint else Fx.Red, style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough)
                    Text(t.antidote, color = if (t.resolved) Fx.TextDim else Fx.Green, style = MaterialTheme.typography.bodyLarge)
                    if (t.why.isNotBlank()) Text(t.why, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("×${t.times}", Fx.Gold)
                        Curriculum.topic(t.skillId)?.let { Pill(it.title) }
                    }
                }
                IconButton(onClick = { main.resolveTrap(t.id, !t.resolved) }) {
                    Icon(Icons.Filled.Check, contentDescription = "Обезврежена", tint = if (t.resolved) Fx.Green else Fx.TextFaint, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { main.deleteTrap(t.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SkillsTab(main: MainViewModel) {
    val skills by main.skills.collectAsStateWithLifecycle()
    val mastery = remember(skills) { skills.associate { it.skillId to it.mastery } }
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fx.Card).clickable { sheetFor = t }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(5.dp))
                        Meter(value, Modifier.fillMaxWidth(), height = 4.dp, color = when {
                            value >= 70 -> Fx.Green
                            value >= 30 -> Fx.Gold
                            else -> Fx.Blue
                        })
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("$value%", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
                    Pill(t.stage)
                }
            }
        }
    }

    sheetFor?.let { t ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Fx.Card) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.title, color = Fx.Text, style = MaterialTheme.typography.titleLarge)
                Text("${t.school} · ${t.stage} · освоено ${mastery[t.id] ?: 0}%", color = Fx.TextDim)
                Chip("🧠 Отработать с Атласом", onClick = {
                    sheetFor = null
                    main.newSession(Skills.LESSON, t.id)
                }, accent = true)
                Chip("📖 Открыть протокол и таблицу", onClick = {
                    sheetFor = null
                    main.open(Screen.Reference(t.id))
                })
                Chip("✅ Уверенно применяю (90%)", onClick = {
                    main.setMastery(t.id, 90)
                    sheetFor = null
                })
                Chip("↺ Сбросить прогресс", onClick = {
                    main.setMastery(t.id, 0)
                    sheetFor = null
                })
            }
        }
    }
}

@Composable
private fun PlansTab(main: MainViewModel) {
    val plans by main.plans.collectAsStateWithLifecycle()
    val progress by main.planProgress.collectAsStateWithLifecycle()
    val byId = remember(progress) { progress.associateBy { it.planId } }
    var createOpen by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<ActionPlanEntity?>(null) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("＋ Новый WOOP-план", onClick = { createOpen = true }, accent = true) }
        items(plans, key = { it.id }) { pl ->
            val p = byId[pl.id]
            val total = p?.total ?: 0
            val done = p?.done ?: 0
            Panel {
                Text(pl.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                if (pl.wish.isNotBlank()) Text("Цель (W): ${pl.wish}", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                if (pl.mainObstacle.isNotBlank()) Text("Помеха (O): ${pl.mainObstacle}", color = Fx.Amber, style = MaterialTheme.typography.bodySmall)
                Text("закрыто $done из $total · ${Timex.humanDay(pl.createdAt).lowercase()}", color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Meter(if (total == 0) 0 else done * 100 / total, Modifier.fillMaxWidth(), color = if (done >= total && total > 0) Fx.Green else Fx.Gold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("▶ В работу", onClick = { main.startPlanDrill(pl) }, accent = true)
                    Chip("↺ Сначала", onClick = { main.resetPlan(pl.id) })
                    Chip("Удалить", onClick = { deleteFor = pl })
                }
            }
        }
    }

    if (createOpen) {
        var title by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("Новый WOOP-план") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it }, label = { Text("Шаги с новой строки") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    main.createPlanFromText(title.trim(), text)
                    createOpen = false
                }, enabled = text.isNotBlank()) { Text("Создать", color = Fx.Gold) }
            },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text("Отмена", color = Fx.TextDim) } },
            containerColor = Fx.Card
        )
    }
    deleteFor?.let { pl ->
        ConfirmDialog("Удалить план «${pl.title}»?", "Шаги плана будут удалены.", "Удалить", { main.deletePlan(pl.id) }, { deleteFor = null }, true)
    }
}

@Composable
private fun StarredTab(main: MainViewModel) {
    val starred by main.starredMessages.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (starred.isEmpty()) {
            item {
                Text(
                    "Избранных сообщений пока нет. Нажми и удерживай любой сильный ответ в чате → «В избранное».",
                    color = Fx.TextDim, modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(starred, key = { it.id }) { m ->
            Panel(onClick = { main.openChat(m.sessionId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Fx.Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${Timex.humanDay(m.createdAt)} ${Timex.hhmm(m.createdAt)}", color = Fx.Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { main.unstarMessage(m.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать", tint = Fx.TextFaint, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                MarkdownText(text = Norm.truncateWords(m.text, 500), color = Fx.Text, style = MaterialTheme.typography.bodyMedium, boldColor = Fx.Gold)
            }
        }
    }
}

@Composable
private fun MemoryTab(main: MainViewModel) {
    val facts by main.facts.collectAsStateWithLifecycle()
    val journal by main.journal.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            SectionTitle("Факты, опоры и триггеры")
            Chip("＋ Добавить факт о себе", onClick = { addOpen = true }, accent = true)
        }
        items(facts, key = { "f" + it.id }) { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fx.Card).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(f.text, color = Fx.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { main.deleteFact(f.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Забыть", tint = Fx.TextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { SectionTitle("Журнал наблюдений и эмоций") }
        items(journal, key = { "j" + it.id }) { j ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badge = when (j.kind) {
                        "milestone" -> "🏔️ "
                        "emotion" -> "🎭 "
                        else -> "🧠 "
                    }
                    Text(badge + Timex.humanDay(j.createdAt) + ", " + Timex.hhmm(j.createdAt), color = Fx.Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("удалить", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { main.deleteJournal(j.id) })
                }
                Spacer(Modifier.height(6.dp))
                Text(j.text, color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (addOpen) {
        InputDialog("Факт или триггер", confirm = "Запомнить", onConfirm = { main.addFact(it) }, onDismiss = { addOpen = false })
    }
}