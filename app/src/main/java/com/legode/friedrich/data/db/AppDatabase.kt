package com.legode.friedrich.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        DailyEntity::class,
        SourceEntity::class,
        KnowledgeCardEntity::class,
        DecisionEntity::class,
        PredictionEntity::class,
        FocusSessionEntity::class,
        WeeklyReviewEntity::class
    ],
    version = 3,
    exportSchema = true
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

    abstract fun sourceDao(): SourceDao
    abstract fun knowledgeCardDao(): KnowledgeCardDao
    abstract fun decisionDao(): DecisionDao
    abstract fun predictionDao(): PredictionDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao

    companion object {
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Расширение существующих таблиц (строго аддитивно, без потери данных)
                db.execSQL("ALTER TABLE sessions ADD COLUMN socraticMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN sourceId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN decisionId INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE pings ADD COLUMN latencyMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pings ADD COLUMN outcome TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE profile ADD COLUMN cognitiveRank TEXT NOT NULL DEFAULT 'наблюдатель'")
                db.execSQL("ALTER TABLE profile ADD COLUMN desiredRetention REAL NOT NULL DEFAULT 0.90")
                db.execSQL("ALTER TABLE profile ADD COLUMN socraticDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profile ADD COLUMN lastWeeklyReviewAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE anchors ADD COLUMN stability REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE anchors ADD COLUMN difficulty REAL NOT NULL DEFAULT 5.0")
                db.execSQL(
                    "UPDATE anchors SET stability = CASE WHEN intervalDays > 0 THEN intervalDays ELSE 0.0 END, " +
                        "difficulty = 5.0 WHERE stability = 0.0"
                )

                db.execSQL("ALTER TABLE plans ADD COLUMN wish TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plans ADD COLUMN outcome TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plans ADD COLUMN mainObstacle TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE plan_steps ADD COLUMN obstacle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_steps ADD COLUMN ifThenRule TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE daily ADD COLUMN cardsReviewed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily ADD COLUMN focusMinutes INTEGER NOT NULL DEFAULT 0")

                // 2. Новые таблицы когнитивного развития (v3)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        originRef TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        keyModels TEXT NOT NULL,
                        authorBlindSpots TEXT NOT NULL,
                        rawExcerpt TEXT NOT NULL,
                        cardCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastStudiedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceId INTEGER NOT NULL,
                        skillId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        front TEXT NOT NULL,
                        back TEXT NOT NULL,
                        hint TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastReviewedAt INTEGER NOT NULL,
                        dueAt INTEGER NOT NULL,
                        stability REAL NOT NULL,
                        difficulty REAL NOT NULL,
                        intervalDays REAL NOT NULL,
                        reps INTEGER NOT NULL,
                        lapses INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        suspended INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_cards_sourceId ON knowledge_cards (sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_cards_dueAt ON knowledge_cards (dueAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS decisions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        context TEXT NOT NULL,
                        alternatives TEXT NOT NULL,
                        chosenOption TEXT NOT NULL,
                        baseRateNote TEXT NOT NULL,
                        preMortem TEXT NOT NULL,
                        ifThenSafeguard TEXT NOT NULL,
                        confidencePct INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        reviewAt INTEGER NOT NULL,
                        outcomeNote TEXT NOT NULL,
                        processQuality INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        resolvedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS predictions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        decisionId INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        probabilityPct INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        rationale TEXT NOT NULL,
                        checkAt INTEGER NOT NULL,
                        resolved INTEGER NOT NULL,
                        outcome INTEGER NOT NULL,
                        brierScore REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        resolvedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_predictions_resolved ON predictions (resolved)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_predictions_checkAt ON predictions (checkAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        obstacle TEXT NOT NULL,
                        ifThenPlan TEXT NOT NULL,
                        plannedMinutes INTEGER NOT NULL,
                        actualMinutes INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        focusQuality INTEGER NOT NULL,
                        moodBefore INTEGER NOT NULL,
                        moodAfter INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_createdAt ON focus_sessions (createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekly_reviews (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        weekStartDay TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        nextWeekChallenge TEXT NOT NULL,
                        topTrigger TEXT NOT NULL,
                        bestWin TEXT NOT NULL,
                        scoresJson TEXT NOT NULL,
                        brierAvg REAL NOT NULL,
                        avgMood REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}