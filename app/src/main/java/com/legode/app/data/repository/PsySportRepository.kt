package com.legode.app.data.repository

import com.legode.app.data.db.CbtDao
import com.legode.app.data.db.SportDao
import com.legode.app.data.model.CbtEntry
import com.legode.app.data.model.WorkoutEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PsySportRepository @Inject constructor(
    private val cbtDao: CbtDao,
    private val sportDao: SportDao
) {
    fun getCbtByDate(date: String): Flow<List<CbtEntry>> = cbtDao.getEntriesByDate(date)
    fun getAllCbt(): Flow<List<CbtEntry>> = cbtDao.getAllEntries()
    suspend fun saveCbt(entry: CbtEntry): Long = cbtDao.insert(entry)
    suspend fun deleteCbt(id: Long) { cbtDao.deleteById(id) }
    fun getCbtActiveDates(): Flow<List<String>> = cbtDao.getAllActiveDates()

    fun getWorkoutsByDate(date: String): Flow<List<WorkoutEntry>> = sportDao.getWorkoutsByDate(date)
    fun getAllWorkouts(): Flow<List<WorkoutEntry>> = sportDao.getAllWorkouts()
    suspend fun saveWorkout(entry: WorkoutEntry): Long = sportDao.insert(entry)
    suspend fun deleteWorkout(id: Long) { sportDao.deleteById(id) }
    fun getSportActiveDates(): Flow<List<String>> = sportDao.getAllActiveDates()
}