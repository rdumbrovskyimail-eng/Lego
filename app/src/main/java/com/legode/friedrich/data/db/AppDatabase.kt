package com.legode.friedrich.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        PingEntity::class,
        ProfileEntity::class,
        AnchorEntity::class,
        ActionPlanEntity::class,
        PlanStepEntity::class,
        TrapEntity::class,
        SkillProgressEntity::class,
        FactEntity::class,
        JournalEntity::class,
        DailyEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun pingDao(): PingDao
    abstract fun profileDao(): ProfileDao
    abstract fun anchorDao(): AnchorDao
    abstract fun planDao(): PlanDao
    abstract fun trapDao(): TrapDao
    abstract fun skillDao(): SkillDao
    abstract fun factDao(): FactDao
    abstract fun journalDao(): JournalDao
    abstract fun dailyDao(): DailyDao
}