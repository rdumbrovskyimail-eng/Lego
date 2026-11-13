package com.legode.friedrich.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        PingEntity::class,
        ProfileEntity::class,
        VocabEntity::class,
        StudyListEntity::class,
        ListItemEntity::class,
        MistakeEntity::class,
        TopicProgressEntity::class,
        FactEntity::class,
        JournalEntity::class,
        DailyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun pingDao(): PingDao
    abstract fun profileDao(): ProfileDao
    abstract fun vocabDao(): VocabDao
    abstract fun listDao(): ListDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun topicDao(): TopicDao
    abstract fun factDao(): FactDao
    abstract fun journalDao(): JournalDao
    abstract fun dailyDao(): DailyDao
}
