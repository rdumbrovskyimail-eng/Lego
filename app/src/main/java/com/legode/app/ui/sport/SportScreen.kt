package com.legode.app.ui.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.legode.app.data.model.WorkoutEntry
import com.legode.app.data.model.WorkoutExercise
import com.legode.app.data.model.WorkoutSet
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SportScreen(
    workouts: List<WorkoutEntry>,
    selectedDate: String,
    onAddWorkout: (WorkoutEntry) -> Unit,
    onDeleteWorkout: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "На эту дату тренировок нет.\nНажмите +, чтобы зафиксировать нагрузку.",
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
                items(workouts, key = { it.id }) { workout ->
                    WorkoutCard(workout = workout, onDelete = { onDeleteWorkout(workout.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF10B981),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить тренировку")
        }
    }

    if (showDialog) {
        WorkoutLoggerDialog(
            selectedDate = selectedDate,
            onDismiss = { showDialog = false },
            onConfirm = { entry ->
                onAddWorkout(entry)
                showDialog = false
            }
        )
    }
}

@Composable
fun WorkoutCard(workout: WorkoutEntry, onDelete: () -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${workout.timeFormatted} • ${workout.sessionType}",
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Тоннаж: ${workout.totalTonnage.toInt()} кг",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF065F46))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFF64748B))
            }
        }

        Text(
            text = "Сон: ${workout.sleepQuality}/5 • Энергия: ${workout.energyLevel}",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        workout.exercises.forEach { ex ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(8.dp)
            ) {
                Text(text = ex.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                ex.sets.forEach { set ->
                    Text(
                        text = "Сет ${set.setNumber}: ${set.weight} кг × ${set.reps} повт.  [RPE ${set.rpe} | RIR ${set.rir}]",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (workout.notes.isNotBlank()) {
            Text(
                text = "Заметка: ${workout.notes}",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun WorkoutLoggerDialog(
    selectedDate: String,
    onDismiss: () -> Unit,
    onConfirm: (WorkoutEntry) -> Unit
) {
    var sessionType by remember { mutableStateOf("Силовая") }
    var sleepQuality by remember { mutableIntStateOf(4) }
    var energyLevel by remember { mutableStateOf("Норма") }
    var exName by remember { mutableStateOf("Приседания со штангой") }
    var weightText by remember { mutableStateOf("80") }
    var repsText by remember { mutableStateOf("10") }
    var rpeText by remember { mutableStateOf("8.0") }
    var rirText by remember { mutableStateOf("2") }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF090D16),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Регистрация тренировочной сессии",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Контроль перегрузки и шкала RPE/RIR",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = sessionType,
                            onValueChange = { sessionType = it },
                            label = { Text("Тип тренировки (Силовая/Кардио/Восстановление)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = exName,
                            onValueChange = { exName = it },
                            label = { Text("Упражнение") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it },
                                label = { Text("Вес (кг)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = repsText,
                                onValueChange = { repsText = it },
                                label = { Text("Повторы") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rpeText,
                                onValueChange = { rpeText = it },
                                label = { Text("RPE (1-10)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rirText,
                                onValueChange = { rirText = it },
                                label = { Text("RIR (запас)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Заметки по технике / самочувствию") },
                            modifier = Modifier.fillMaxWidth()
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
                            val w = weightText.toFloatOrNull() ?: 0f
                            val r = repsText.toIntOrNull() ?: 0
                            val rpe = rpeText.toFloatOrNull() ?: 8f
                            val rir = rirText.toIntOrNull() ?: 2

                            val sets = listOf(WorkoutSet(1, w, r, rpe, rir))
                            val exercises = listOf(WorkoutExercise(exName.trim().ifEmpty { "Основное упражнение" }, sets))
                            val tonnage = w * r

                            onConfirm(
                                WorkoutEntry(
                                    date = selectedDate,
                                    timeFormatted = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                    sessionType = sessionType.trim(),
                                    sleepQuality = sleepQuality,
                                    energyLevel = energyLevel,
                                    exercises = exercises,
                                    totalTonnage = tonnage,
                                    notes = notes.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}