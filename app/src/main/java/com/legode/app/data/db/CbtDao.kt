package com.legode.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.legode.app.data.model.CbtEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CbtDao {
    @Query("SELECT * FROM cbt_entries WHERE date = :date ORDER BY timestamp DESC")
    fun getEntriesByDate(date: String): Flow<List<CbtEntry>>

    @Query("SELECT * FROM cbt_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<CbtEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CbtEntry): Long

    @Query("DELETE FROM cbt_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT DISTINCT date FROM cbt_entries")
    fun getAllActiveDates(): Flow<List<String>>
}