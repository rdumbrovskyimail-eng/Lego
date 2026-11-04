package com.legode.app.ui.cbt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.legode.app.data.model.CbtEntry
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val AVAILABLE_DISTORTIONS = listOf(
    "Катастрофизация",
    "Черно-белое мышление",
    "Чтение мыслей",
    "Долженствование",
    "Обесценивание позитива",
    "Сверхобобщение",
    "Эмоциональное обоснование",
    "Персонализация",
    "Навешивание ярлыков",
    "Туннельное зрение"
)

@Composable
fun CbtScreen(
    entries: List<CbtEntry>,
    selectedDate: String,
    onAddEntry: (CbtEntry) -> Unit,
    onDeleteEntry: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет КПТ-записей на эту дату.\nНажмите +, чтобы разобрать мысль.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    CbtCard(entry = entry, onDelete = { onDeleteEntry(entry.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFA855F7),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить сессию КПТ")
        }
    }

    if (showDialog) {
        CbtProtocolDialog(
            selectedDate = selectedDate,
            onDismiss = { showDialog = false },
            onConfirm = { entry ->
                onAddEntry(entry)
                showDialog = false
            }
        )
    }
}

@Composable
fun CbtCard(entry: CbtEntry, onDelete: () -> Unit) {
    val beliefDelta = entry.beliefBefore - entry.beliefAfter
    val intensityDelta = entry.intensityBefore - entry.intensityAfter

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entry.timeFormatted} • ${entry.emotion}",
                color = Color(0xFFA855F7),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFF64748B))
            }
        }

        Text(
            text = "Ситуация: ${entry.situation}",
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "Автоматическая мысль:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "«${entry.automaticThought}»",
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Text(
                    text = "Убежденность: ${entry.beliefBefore}% ➔ ${entry.beliefAfter}% (Δ -$beliefDelta%)",
                    color = Color(0xFFA855F7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (entry.distortions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.distortions.take(3).forEach { dist ->
                    Text(
                        text = dist,
                        fontSize = 10.sp,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF581C87).copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF064E3B).copy(alpha = 0.3f))
                .border(1.dp, Color(0xFF059669).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "Адаптивный ответ:",
                    color = Color(0xFF34D399),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.adaptiveResponse,
                    color = Color(0xFFECFDF5),
                    fontSize = 13.sp
                )
                Text(
                    text = "Снижение интенсивности эмоции: ${entry.intensityBefore}% ➔ ${entry.intensityAfter}% (Спад: -$intensityDelta%)",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CbtProtocolDialog(
    selectedDate: String,
    onDismiss: () -> Unit,
    onConfirm: (CbtEntry) -> Unit
) {
    var situation by remember { mutableStateOf("") }
    var thought by remember { mutableStateOf("") }
    var beliefBefore by remember { mutableFloatStateOf(80f) }
    var emotion by remember { mutableStateOf("Тревога") }
    var intensityBefore by remember { mutableFloatStateOf(85f) }
    var somatic by remember { mutableStateOf("") }
    var selectedDistortions by remember { mutableStateOf(setOf<String>()) }
    var evidenceFor by remember { mutableStateOf("") }
    var evidenceAgainst by remember { mutableStateOf("") }
    var adaptive by remember { mutableStateOf("") }
    var beliefAfter by remember { mutableFloatStateOf(30f) }
    var intensityAfter by remember { mutableFloatStateOf(25f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF090D16),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Протокол реструктуризации КПТ",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Стандарт когнитивной модели А. Бека",
                    color = Color(0xFFA855F7),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = situation,
                            onValueChange = { situation = it },
                            label = { Text("1. Ситуация (факты, без эмоций)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = thought,
                            onValueChange = { thought = it },
                            label = { Text("2. Автоматическая мысль") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "Вера в мысль ДО анализа: ${beliefBefore.toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Slider(
                            value = beliefBefore,
                            onValueChange = { beliefBefore = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFFA855F7))
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = emotion,
                            onValueChange = { emotion = it },
                            label = { Text("3. Эмоция (Тревога, Стыд, Гнев...)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "Интенсивность эмоции ДО: ${intensityBefore.toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Slider(
                            value = intensityBefore,
                            onValueChange = { intensityBefore = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFEF4444), activeTrackColor = Color(0xFFEF4444))
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = somatic,
                            onValueChange = { somatic = it },
                            label = { Text("Телесная реакция (ком в горле, пульс)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "4. Когнитивные искажения (выберите ошибки):",
                            fontSize = 12.sp,
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AVAILABLE_DISTORTIONS.forEach { dist ->
                                val isSelected = selectedDistortions.contains(dist)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDistortions = if (isSelected) {
                                            selectedDistortions - dist
                                        } else {
                                            selectedDistortions + dist
                                        }
                                    },
                                    label = { Text(dist, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = evidenceFor,
                            onValueChange = { evidenceFor = it },
                            label = { Text("5a. Факты «ЗА» истинность мысли") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = evidenceAgainst,
                            onValueChange = { evidenceAgainst = it },
                            label = { Text("5b. Факты «ПРОТИВ» (контраргументы)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = adaptive,
                            onValueChange = { adaptive = it },
                            label = { Text("6. Адаптивный рациональный ответ") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = "Вера в старую мысль ПОСЛЕ разбора: ${beliefAfter.toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Slider(
                            value = beliefAfter,
                            onValueChange = { beliefAfter = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFFA855F7))
                        )
                    }
                    item {
                        Text(
                            text = "Интенсивность эмоции ПОСЛЕ разбора: ${intensityAfter.toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                        Slider(
                            value = intensityAfter,
                            onValueChange = { intensityAfter = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена", color = Color(0xFF94A3B8)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (situation.isNotBlank() && thought.isNotBlank()) {
                                onConfirm(
                                    CbtEntry(
                                        date = selectedDate,
                                        timeFormatted = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                        situation = situation.trim(),
                                        automaticThought = thought.trim(),
                                        beliefBefore = beliefBefore.toInt(),
                                        emotion = emotion.trim(),
                                        intensityBefore = intensityBefore.toInt(),
                                        somaticReaction = somatic.trim(),
                                        distortions = selectedDistortions.toList(),
                                        evidenceFor = evidenceFor.trim(),
                                        evidenceAgainst = evidenceAgainst.trim(),
                                        adaptiveResponse = adaptive.trim().ifEmpty { "Рациональный баланс найден." },
                                        beliefAfter = beliefAfter.toInt(),
                                        intensityAfter = intensityAfter.toInt()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
                    ) {
                        Text("Сохранить протокол")
                    }
                }
            }
        }
    }
}