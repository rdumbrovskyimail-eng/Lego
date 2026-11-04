package com.legode.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.legode.app.data.model.WorkoutEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SportDao {
    @Query("SELECT * FROM workout_entries WHERE date = :date ORDER BY timestamp DESC")
    fun getWorkoutsByDate(date: String): Flow<List<WorkoutEntry>>

    @Query("SELECT * FROM workout_entries ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WorkoutEntry): Long

    @Query("DELETE FROM workout_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT date FROM workout_entries")
    fun getAllActiveDates(): Flow<List<String>>
}