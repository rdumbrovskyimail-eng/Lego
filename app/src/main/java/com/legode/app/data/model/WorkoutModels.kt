package com.legode.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class WorkoutSet(
    val setNumber: Int,
    val weight: Float,
    val reps: Int,
    val rpe: Float, // Rate of Perceived Exertion (1.0..10.0)
    val rir: Int    // Reps In Reserve (0..5)
)

data class WorkoutExercise(
    val name: String,
    val sets: List<WorkoutSet>
)

@Entity(tableName = "workout_entries")
data class WorkoutEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // Формат: YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val sessionType: String, // Силовая, Кардио, Функциональная, Восстановление
    val sleepQuality: Int,   // 1..5
    val energyLevel: String, // Высокая, Норма, Низкая
    val exercises: List<WorkoutExercise>,
    val totalTonnage: Float,
    val notes: String
)