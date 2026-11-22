package com.legode.friedrich.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY isHome DESC, pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY isHome DESC, pinned DESC, updatedAt DESC")
    suspend fun all(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE isHome = 1 ORDER BY id ASC LIMIT 1")
    suspend fun home(): SessionEntity?

    @Insert
    suspend fun insert(e: SessionEntity): Long

    @Update
    suspend fun update(e: SessionEntity): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE sessions SET unread = 0 WHERE id = :id")
    suspend fun clearUnread(id: Long): Int

    @Query("UPDATE sessions SET unread = unread + :by WHERE id = :id")
    suspend fun addUnread(id: Long, by: Int): Int

    @Query("UPDATE sessions SET draft = :draft WHERE id = :id")
    suspend fun setDraft(id: Long, draft: String): Int

    @Query("UPDATE sessions SET updatedAt = :at, lastPreview = :preview WHERE id = :id")
    suspend fun touch(id: Long, at: Long, preview: String): Int

    @Query("UPDATE sessions SET title = :title, titleLocked = :locked WHERE id = :id")
    suspend fun rename(id: Long, title: String, locked: Boolean): Int

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean): Int

    @Query("UPDATE sessions SET planId = :planId, askedRef = '' WHERE id = :id")
    suspend fun setPlan(id: Long, planId: Long): Int

    @Query("UPDATE sessions SET askedRef = :ref WHERE id = :id")
    suspend fun setAsked(id: Long, ref: String): Int

    @Query("UPDATE sessions SET summary = :summary, summarizedUpToId = :upTo WHERE id = :id")
    suspend fun setSummary(id: Long, summary: String, upTo: Long): Int

    @Query("UPDATE sessions SET turnCount = turnCount + 1 WHERE id = :id")
    suspend fun bumpTurn(id: Long): Int

    @Query("UPDATE sessions SET planId = 0, askedRef = '' WHERE planId = :planId")
    suspend fun detachPlan(planId: Long): Int

    @Query("UPDATE sessions SET mode = :mode, modeArg = :modeArg, emoji = :emoji WHERE id = :id")
    suspend fun setMode(id: Long, mode: String, modeArg: String, emoji: String): Int

    @Query("UPDATE sessions SET socraticMode = :enabled WHERE id = :id")
    suspend fun setSocratic(id: Long, enabled: Boolean): Int

    @Query("SELECT COALESCE(SUM(unread), 0) FROM sessions")
    fun totalUnread(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observe(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun all(sessionId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentDesc(sessionId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun last(sessionId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Insert
    suspend fun insert(e: MessageEntity): Long

    @Update
    suspend fun update(e: MessageEntity): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND role != 'info'")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND role != 'info' AND id > :afterId")
    suspend fun countAfter(sessionId: Long, afterId: Long): Int

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND role != 'info' AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun after(sessionId: Long, afterId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT MAX(createdAt) FROM messages WHERE role = 'user'")
    suspend fun lastUserAt(): Long?

    @Query("SELECT MAX(createdAt) FROM messages WHERE role = 'user'")
    fun observeLastUserAt(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'user'")
    suspend fun userCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'user'")
    fun observeUserCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE role != 'info' ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentAcross(limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE role != 'info' AND createdAt >= :since ORDER BY createdAt ASC")
    suspend fun since(since: Long): List<MessageEntity>

    @Query("UPDATE messages SET status = :status, meta = :meta WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, meta: String): Int

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStar(id: Long, starred: Boolean): Int

    @Query("UPDATE messages SET quickReplies = '' WHERE sessionId = :sessionId AND quickReplies != ''")
    suspend fun clearQuickReplies(sessionId: Long): Int

    @Query("SELECT attachments FROM messages WHERE sessionId = :sessionId AND attachments != ''")
    suspend fun attachmentsOf(sessionId: Long): List<String>

    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY createdAt DESC")
    fun observeStarred(): Flow<List<MessageEntity>>
}

@Dao
interface PingDao {
    @Insert
    suspend fun insert(e: PingEntity): Long

    @Query(
        "UPDATE pings SET answered = 1, answeredAt = :at, " +
            "latencyMs = CASE WHEN (:at - firedAt) > 0 THEN (:at - firedAt) ELSE 0 END, " +
            "outcome = :outcome " +
            "WHERE sessionId = :sessionId AND answered = 0"
    )
    suspend fun markAnswered(sessionId: Long, at: Long, outcome: String = "replied"): Int

    @Query("UPDATE pings SET outcome = 'snoozed' WHERE answered = 0 AND outcome = ''")
    suspend fun markUnansweredSnoozed(): Int

    @Query("SELECT COUNT(*) FROM pings WHERE answered = 0 AND firedAt > :since")
    suspend fun unansweredSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM pings WHERE firedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT kind FROM pings ORDER BY id DESC LIMIT :limit")
    suspend fun recentKinds(limit: Int): List<String>

    @Query("SELECT kbId FROM pings WHERE kbId != '' ORDER BY id DESC LIMIT :limit")
    suspend fun recentKbIds(limit: Int): List<String>

    @Query("SELECT * FROM pings WHERE firedAt >= :since ORDER BY firedAt DESC")
    suspend fun since(since: Long): List<PingEntity>

    @Query("SELECT COUNT(*) FROM pings")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pings WHERE answered = 1")
    fun observeAnswered(): Flow<Int>

    @Query("DELETE FROM pings WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long): Int
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ProfileEntity): Long
}

@Dao
interface AnchorDao {
    @Query("SELECT * FROM anchors ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AnchorEntity>>

    @Query("SELECT * FROM anchors WHERE keyNorm = :key LIMIT 1")
    suspend fun byKey(key: String): AnchorEntity?

    @Query("SELECT * FROM anchors WHERE id = :id")
    suspend fun get(id: Long): AnchorEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: AnchorEntity): Long

    @Update
    suspend fun update(e: AnchorEntity): Int

    @Query("DELETE FROM anchors WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM anchors WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<AnchorEntity>

    @Query("SELECT COUNT(*) FROM anchors WHERE dueAt <= :now")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM anchors WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM anchors ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun newest(limit: Int): List<AnchorEntity>

    @Query("SELECT status, COUNT(*) AS n FROM anchors GROUP BY status")
    suspend fun statusCounts(): List<StatusCount>

    @Query("SELECT status, COUNT(*) AS n FROM anchors GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>

    @Query("SELECT COUNT(*) FROM anchors")
    suspend fun count(): Int

    @Query("SELECT keyNorm FROM anchors")
    suspend fun allKeys(): List<String>
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt DESC, id DESC")
    fun observePlans(): Flow<List<ActionPlanEntity>>

    @Query("SELECT * FROM plans ORDER BY createdAt DESC, id DESC")
    suspend fun plans(): List<ActionPlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getPlan(id: Long): ActionPlanEntity?

    @Query("SELECT * FROM plans WHERE id = :id")
    fun observePlan(id: Long): Flow<ActionPlanEntity?>

    @Insert
    suspend fun insertPlan(e: ActionPlanEntity): Long

    @Query("UPDATE plans SET title = :title WHERE id = :id")
    suspend fun renamePlan(id: Long, title: String): Int

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: Long): Int

    @Insert
    suspend fun insertSteps(steps: List<PlanStepEntity>): List<Long>

    @Query("SELECT * FROM plan_steps WHERE planId = :planId ORDER BY position ASC, id ASC")
    suspend fun steps(planId: Long): List<PlanStepEntity>

    @Query("SELECT * FROM plan_steps WHERE planId = :planId ORDER BY position ASC, id ASC")
    fun observeSteps(planId: Long): Flow<List<PlanStepEntity>>

    @Query("SELECT * FROM plan_steps WHERE id = :id")
    suspend fun getStep(id: Long): PlanStepEntity?

    @Update
    suspend fun updateStep(e: PlanStepEntity): Int

    @Query("DELETE FROM plan_steps WHERE planId = :planId")
    suspend fun deleteSteps(planId: Long): Int

    @Query("DELETE FROM plan_steps WHERE id = :id")
    suspend fun deleteStep(id: Long): Int

    @Query("UPDATE plan_steps SET doneCount = 0, skippedCount = 0, streak = 0, lastAt = 0 WHERE planId = :planId")
    suspend fun resetSteps(planId: Long): Int

    @Query(
        "SELECT planId, COUNT(*) AS total, SUM(CASE WHEN streak >= 1 THEN 1 ELSE 0 END) AS done " +
            "FROM plan_steps GROUP BY planId"
    )
    fun observeProgress(): Flow<List<PlanProgress>>

    @Query(
        "SELECT planId, COUNT(*) AS total, SUM(CASE WHEN streak >= 1 THEN 1 ELSE 0 END) AS done " +
            "FROM plan_steps WHERE planId = :planId GROUP BY planId"
    )
    suspend fun progress(planId: Long): PlanProgress?
}

@Dao
interface TrapDao {
    @Query("SELECT * FROM traps ORDER BY resolved ASC, lastAt DESC")
    fun observeAll(): Flow<List<TrapEntity>>

    @Query("SELECT * FROM traps WHERE resolved = 0 ORDER BY times DESC, lastAt DESC LIMIT :limit")
    suspend fun top(limit: Int): List<TrapEntity>

    @Query("SELECT * FROM traps WHERE lastAt >= :since ORDER BY times DESC")
    suspend fun since(since: Long): List<TrapEntity>

    @Query("SELECT * FROM traps WHERE keyNorm = :key LIMIT 1")
    suspend fun byKey(key: String): TrapEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: TrapEntity): Long

    @Update
    suspend fun update(e: TrapEntity): Int

    @Query("DELETE FROM traps WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT COUNT(*) FROM traps WHERE resolved = 0")
    suspend fun activeCount(): Int

    @Query("UPDATE traps SET resolved = :resolved WHERE id = :id")
    suspend fun setResolved(id: Long, resolved: Boolean): Int
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skill_progress")
    fun observeAll(): Flow<List<SkillProgressEntity>>

    @Query("SELECT * FROM skill_progress")
    suspend fun all(): List<SkillProgressEntity>

    @Query("SELECT * FROM skill_progress WHERE skillId = :id")
    suspend fun get(id: String): SkillProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SkillProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SkillProgressEntity>): List<Long>
}

@Dao
interface FactDao {
    @Query("SELECT * FROM facts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<FactEntity>

    @Insert
    suspend fun insert(e: FactEntity): Long

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(): JournalEntity?

    @Insert
    suspend fun insert(e: JournalEntity): Long

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily WHERE day = :day")
    suspend fun get(day: String): DailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: DailyEntity): Long

    @Query("SELECT * FROM daily ORDER BY day DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyEntity>>

    @Query("SELECT * FROM daily ORDER BY day DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DailyEntity>
}

// ───────────────────────────── Новые DAO (v3) ─────────────────────────────

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Insert
    suspend fun insert(e: SourceEntity): Long

    @Update
    suspend fun update(e: SourceEntity): Int

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface KnowledgeCardDao {
    @Query("SELECT * FROM knowledge_cards WHERE suspended = 0 ORDER BY dueAt ASC, id DESC")
    fun observeAllActive(): Flow<List<KnowledgeCardEntity>>

    @Query("SELECT * FROM knowledge_cards WHERE sourceId = :sourceId ORDER BY id ASC")
    fun observeBySource(sourceId: Long): Flow<List<KnowledgeCardEntity>>

    @Query("SELECT * FROM knowledge_cards WHERE suspended = 0 AND dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<KnowledgeCardEntity>

    @Query("SELECT COUNT(*) FROM knowledge_cards WHERE suspended = 0 AND dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM knowledge_cards WHERE suspended = 0 AND dueAt <= :now")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT * FROM knowledge_cards WHERE suspended = 0 ORDER BY lastReviewedAt ASC, id DESC LIMIT :limit")
    suspend fun recentOrPracticePool(limit: Int): List<KnowledgeCardEntity>

    @Query("SELECT * FROM knowledge_cards WHERE id = :id")
    suspend fun get(id: Long): KnowledgeCardEntity?

    @Insert
    suspend fun insert(e: KnowledgeCardEntity): Long

    @Insert
    suspend fun insertAll(items: List<KnowledgeCardEntity>): List<Long>

    @Update
    suspend fun update(e: KnowledgeCardEntity): Int

    @Query("DELETE FROM knowledge_cards WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM knowledge_cards WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long): Int
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decisions ORDER BY CASE status WHEN 'open' THEN 0 WHEN 'reviewed' THEN 1 ELSE 2 END, createdAt DESC")
    fun observeAll(): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE id = :id")
    suspend fun get(id: Long): DecisionEntity?

    @Query("SELECT * FROM decisions WHERE status = 'open' AND reviewAt > 0 AND reviewAt <= :now ORDER BY reviewAt ASC LIMIT :limit")
    suspend fun dueForReview(now: Long, limit: Int): List<DecisionEntity>

    @Insert
    suspend fun insert(e: DecisionEntity): Long

    @Update
    suspend fun update(e: DecisionEntity): Int

    @Query("DELETE FROM decisions WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions ORDER BY resolved ASC, checkAt ASC, id DESC")
    fun observeAll(): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE resolved = 1 ORDER BY resolvedAt DESC")
    fun observeResolved(): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE resolved = 1 ORDER BY resolvedAt DESC")
    suspend fun allResolved(): List<PredictionEntity>

    @Query("SELECT * FROM predictions WHERE resolved = 0 AND checkAt <= :now ORDER BY checkAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<PredictionEntity>

    @Query("SELECT COUNT(*) FROM predictions WHERE resolved = 0 AND checkAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM predictions WHERE id = :id")
    suspend fun get(id: Long): PredictionEntity?

    @Insert
    suspend fun insert(e: PredictionEntity): Long

    @Update
    suspend fun update(e: PredictionEntity): Int

    @Query("DELETE FROM predictions WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE createdAt >= :since ORDER BY createdAt DESC")
    suspend fun since(since: Long): List<FocusSessionEntity>

    @Insert
    suspend fun insert(e: FocusSessionEntity): Long

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface WeeklyReviewDao {
    @Query("SELECT * FROM weekly_reviews ORDER BY createdAt DESC LIMIT 20")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>

    @Query("SELECT * FROM weekly_reviews ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): WeeklyReviewEntity?

    @Insert
    suspend fun insert(e: WeeklyReviewEntity): Long

    @Query("DELETE FROM weekly_reviews WHERE id = :id")
    suspend fun delete(id: Long): Int
}