@file:OptIn(ExperimentalMaterial3Api::class)

package com.legode.friedrich.ui.decisions

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Brain
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.DecisionEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.CalibrationChart
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.SectionTitle
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlin.math.roundToInt

@Composable
fun DecisionsScreen(main: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Fx.Ink)) {
        TopBar(title = "Решения", subtitle = "Pre-mortem · Базовые ставки · Brier Score") {
            IconButton(onClick = { main.open(Screen.Settings) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Fx.Text)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Fx.Panel, contentColor = Fx.Gold) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Решения") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Калибровка и Brier") })
        }
        if (tab == 0) DecisionsTab(main) else CalibrationTab(main)
    }
}

@Composable
private fun DecisionsTab(main: MainViewModel) {
    val decisions by main.decisions.collectAsStateWithLifecycle()
    var createOpen by remember { mutableStateOf(false) }
    var resolveFor by remember { mutableStateOf<DecisionEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Chip("＋ Новое важное решение (Pre-mortem)", onClick = { createOpen = true }, accent = true)
        }
        if (decisions.isEmpty()) {
            item {
                Panel {
                    Text("Лаборатория трезвых решений", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Фиксируй важные шаги ДО результата: базовую ставку, 3 причины провала (Pre-mortem) и WOOP-предохранитель. " +
                            "Это лечит искажение задним умом (hindsight bias) и качает Brier Score.",
                        color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        items(decisions, key = { it.id }) { d ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Pill("${d.confidencePct}%", Fx.Gold)
                    IconButton(onClick = { main.deleteDecision(d.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(16.dp))
                    }
                }
                if (d.chosenOption.isNotBlank()) {
                    Text("Выбор: ${d.chosenOption}", color = Fx.Gold, style = MaterialTheme.typography.bodyMedium)
                }
                if (d.baseRateNote.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Базовая ставка: ${d.baseRateNote}", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                if (d.preMortem.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Pre-mortem: ${d.preMortem}", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                if (d.ifThenSafeguard.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Предохранитель: ${d.ifThenSafeguard}", color = Fx.Blue, style = MaterialTheme.typography.bodySmall)
                }
                if (d.status == "reviewed") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Итог (качество процесса ${d.processQuality}/10): ${d.outcomeNote}",
                        color = Fx.Green, style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("⚖️ Спарринг в чате", onClick = { main.openDecisionChat(d) })
                        Chip("Закрыть и оценить ✓", onClick = { resolveFor = d }, accent = true)
                    }
                }
            }
        }
    }

    if (createOpen) CreateDecisionDialog(main, onDismiss = { createOpen = false })
    resolveFor?.let { d ->
        var outcome by remember { mutableStateOf("") }
        var proc by remember { mutableIntStateOf(7) }
        AlertDialog(
            onDismissRequest = { resolveFor = null },
            title = { Text("Разбор решения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Отдели качество процесса выбора в момент старта от слепой удачи исхода.", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(outcome, { outcome = it }, label = { Text("Что произошло на практике?") }, minLines = 2, maxLines = 4)
                    Text("Оценка процесса (не везения): $proc/10", color = Fx.Text)
                    Slider(proc.toFloat(), { proc = it.roundToInt() }, valueRange = 1f..10f, steps = 8)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        main.resolveDecision(d.id, outcome, proc)
                        resolveFor = null
                    },
                    enabled = outcome.isNotBlank()
                ) { Text("Завершить", color = Fx.Gold) }
            },
            dismissButton = { TextButton(onClick = { resolveFor = null }) { Text("Отмена", color = Fx.TextDim) } },
            containerColor = Fx.Card
        )
    }
}

@Composable
private fun CreateDecisionDialog(main: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var ctx by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf("") }
    var baseRate by remember { mutableStateOf("") }
    var preMortem by remember { mutableStateOf("") }
    var safeguard by remember { mutableStateOf("") }
    var conf by remember { mutableIntStateOf(65) }
    var reviewDays by remember { mutableIntStateOf(14) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое решение (Pre-mortem)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Суть выбора") }, singleLine = true)
                OutlinedTextField(ctx, { ctx = it }, label = { Text("Контекст и ставки") }, minLines = 2, maxLines = 4)
                OutlinedTextField(alt, { alt = it }, label = { Text("Альтернативы (через ;)") }, singleLine = true)
                OutlinedTextField(chosen, { chosen = it }, label = { Text("Какой вариант выбираешь?") }, singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (main.assistingDecision) "Анализ…" else "🧠 Аудит Атласа", onClick = {
                        if (title.isNotBlank()) {
                            main.assistDecision(title, ctx, alt.split(';'), chosen, conf) { res ->
                                res.onSuccess { n ->
                                    baseRate = n.baseRateNote
                                    preMortem = n.preMortem
                                    safeguard = n.ifThenSafeguard
                                    conf = n.suggestedPct
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, accent = true)
                    if (main.assistingDecision) CircularProgressIndicator(Modifier.size(18.dp), color = Fx.Gold, strokeWidth = 2.dp)
                }

                OutlinedTextField(baseRate, { baseRate = it }, label = { Text("Базовая ставка (Outside View)") }, minLines = 2, maxLines = 4)
                OutlinedTextField(preMortem, { preMortem = it }, label = { Text("Pre-mortem (почему провалится)") }, minLines = 2, maxLines = 5)
                OutlinedTextField(safeguard, { safeguard = it }, label = { Text("Если–То предохранитель") }, minLines = 2, maxLines = 3)
                Text("Уверенность в успехе: $conf%", color = Fx.Text)
                Slider(conf.toFloat(), { conf = it.roundToInt() }, valueRange = 5f..95f)
                Text("Проверить исход через: $reviewDays дн", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                Slider(reviewDays.toFloat(), { reviewDays = it.roundToInt() }, valueRange = 3f..90f)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    main.createDecision(
                        title, ctx, alt.split(';'), chosen, baseRate, preMortem, safeguard, conf, reviewDays
                    )
                    onDismiss()
                },
                enabled = title.isNotBlank()
            ) { Text("Зафиксировать", color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

@Composable
private fun CalibrationTab(main: MainViewModel) {
    val predictions by main.predictions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolved = remember(predictions) { predictions.filter { it.resolved } }
    val stats = remember(resolved) { Srs.computeCalibration(resolved.map { it.probabilityPct to it.outcome }) }

    var newPredOpen by remember { mutableStateOf(false) }
    var activeQuiz by remember { mutableStateOf<Brain.CalibrationQuiz?>(null) }
    var quizPct by remember { mutableIntStateOf(65) }
    var quizResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Brier Score", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (stats.brierScore < 0) "—" else "%.3f".format(stats.brierScore),
                            color = Fx.Gold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black
                        )
                    }
                    Pill("${stats.totalResolved} проверок")
                }
                Spacer(Modifier.height(4.dp))
                Text(stats.verdict, color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                CalibrationChart(stats)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (main.generatingQuiz) "Генерирую…" else "🎯 Калибровочная дуэль", onClick = {
                    quizResult = null
                    main.generateCalibrationQuiz("") { res ->
                        res.onSuccess { q ->
                            activeQuiz = q
                            quizPct = 60
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Нужен ключ Gemini", Toast.LENGTH_LONG).show()
                        }
                    }
                }, accent = true)
                Chip("＋ Личный прогноз", onClick = { newPredOpen = true })
            }
        }

        activeQuiz?.let { q ->
            item {
                Panel {
                    Pill("Тренировка уверенности", Fx.Blue)
                    Spacer(Modifier.height(6.dp))
                    Text(q.question, color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (quizResult == null) {
                        Text("Твоя вероятность, что это ПРАВДА: $quizPct%", color = Fx.Gold, fontWeight = FontWeight.Bold)
                        Slider(quizPct.toFloat(), { quizPct = it.roundToInt() }, valueRange = 1f..99f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Fx.Gold)
                                .clickable {
                                    main.recordQuizResult(q.question, quizPct, q.outcome, q.explanation)
                                    val brier = Srs.brierFor(quizPct, q.outcome)
                                    quizResult = "Факт: ${if (q.outcome == 1) "ПРАВДА ✓" else "ЛОЖЬ ✗"} · Твой Brier = ${"%.3f".format(brier)}\n\n${q.explanation}"
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Зафиксировать ставку $quizPct%", color = Fx.OnGold, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(quizResult.orEmpty(), color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val openPreds = predictions.filter { !it.resolved }
        if (openPreds.isNotEmpty()) {
            item { SectionTitle("Открытые прогнозы (${openPreds.size})") }
            items(openPreds, key = { it.id }) { p ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.question, color = Fx.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Pill("${p.probabilityPct}%", Fx.Gold)
                        IconButton(onClick = { main.deletePrediction(p.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Fx.TextFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("Проверка: ${Timex.formatShortDate(p.checkAt)}", color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Сбылось ✓", onClick = { main.resolvePrediction(p.id, 1) }, accent = true)
                        Chip("Не сбылось ✗", onClick = { main.resolvePrediction(p.id, 0) })
                    }
                }
            }
        }
    }

    if (newPredOpen) {
        var q by remember { mutableStateOf("") }
        var pct by remember { mutableIntStateOf(65) }
        var days by remember { mutableIntStateOf(14) }
        AlertDialog(
            onDismissRequest = { newPredOpen = false },
            title = { Text("Новый вероятностный прогноз") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(q, { q = it }, label = { Text("Чёткий вопрос «Сбудется / Не сбудется»") }, minLines = 2, maxLines = 4)
                    Text("Вероятность: $pct%", color = Fx.Gold)
                    Slider(pct.toFloat(), { pct = it.roundToInt() }, valueRange = 1f..99f)
                    Text("Срок проверки: $days дн", color = Fx.TextDim)
                    Slider(days.toFloat(), { days = it.roundToInt() }, valueRange = 1f..180f)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        main.createPrediction(q, pct, days)
                        newPredOpen = false
                    },
                    enabled = q.isNotBlank()
                ) { Text("Записать", color = Fx.Gold) }
            },
            dismissButton = { TextButton(onClick = { newPredOpen = false }) { Text("Отмена", color = Fx.TextDim) } },
            containerColor = Fx.Card
        )
    }
}