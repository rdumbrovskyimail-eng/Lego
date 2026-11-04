package com.legode.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.legode.app.data.model.CbtEntry
import com.legode.app.data.model.WorkoutEntry

@Database(entities = [CbtEntry::class, WorkoutEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cbtDao(): CbtDao
    abstract fun sportDao(): SportDao
}