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

    @Query("UPDATE sessions SET listId = :listId, askedRef = '' WHERE id = :id")
    suspend fun setList(id: Long, listId: Long): Int

    @Query("UPDATE sessions SET askedRef = :ref WHERE id = :id")
    suspend fun setAsked(id: Long, ref: String): Int

    @Query("UPDATE sessions SET summary = :summary, summarizedUpToId = :upTo WHERE id = :id")
    suspend fun setSummary(id: Long, summary: String, upTo: Long): Int

    @Query("UPDATE sessions SET turnCount = turnCount + 1 WHERE id = :id")
    suspend fun bumpTurn(id: Long): Int

    @Query("UPDATE sessions SET listId = 0, askedRef = '' WHERE listId = :listId")
    suspend fun detachList(listId: Long): Int

    @Query("UPDATE sessions SET mode = :mode, modeArg = :modeArg, emoji = :emoji WHERE id = :id")
    suspend fun setMode(id: Long, mode: String, modeArg: String, emoji: String): Int

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

    @Query("UPDATE pings SET answered = 1, answeredAt = :at WHERE sessionId = :sessionId AND answered = 0")
    suspend fun markAnswered(sessionId: Long, at: Long): Int

    @Query("SELECT COUNT(*) FROM pings WHERE answered = 0 AND firedAt > :since")
    suspend fun unansweredSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM pings WHERE firedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT kind FROM pings ORDER BY id DESC LIMIT :limit")
    suspend fun recentKinds(limit: Int): List<String>

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
interface VocabDao {
    @Query("SELECT * FROM vocab ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<VocabEntity>>

    @Query("SELECT * FROM vocab WHERE keyNorm = :key LIMIT 1")
    suspend fun byKey(key: String): VocabEntity?

    @Query("SELECT * FROM vocab WHERE id = :id")
    suspend fun get(id: Long): VocabEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: VocabEntity): Long

    @Update
    suspend fun update(e: VocabEntity): Int

    @Query("DELETE FROM vocab WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM vocab WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<VocabEntity>

    @Query("SELECT COUNT(*) FROM vocab WHERE dueAt <= :now")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM vocab WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM vocab ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun newest(limit: Int): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE wrong > 0 ORDER BY (wrong - correct) DESC, lastSeenAt DESC LIMIT :limit")
    suspend fun weakest(limit: Int): List<VocabEntity>

    @Query("SELECT status, COUNT(*) AS n FROM vocab GROUP BY status")
    suspend fun statusCounts(): List<StatusCount>

    @Query("SELECT status, COUNT(*) AS n FROM vocab GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>

    @Query("SELECT COUNT(*) FROM vocab")
    suspend fun count(): Int

    @Query("SELECT keyNorm FROM vocab")
    suspend fun allKeys(): List<String>
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY createdAt DESC, id DESC")
    fun observeLists(): Flow<List<StudyListEntity>>

    @Query("SELECT * FROM lists ORDER BY createdAt DESC, id DESC")
    suspend fun lists(): List<StudyListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: Long): StudyListEntity?

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observeList(id: Long): Flow<StudyListEntity?>

    @Insert
    suspend fun insertList(e: StudyListEntity): Long

    @Query("UPDATE lists SET title = :title WHERE id = :id")
    suspend fun renameList(id: Long, title: String): Int

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: Long): Int

    @Insert
    suspend fun insertItems(items: List<ListItemEntity>): List<Long>

    @Query("SELECT * FROM list_items WHERE listId = :listId ORDER BY position ASC, id ASC")
    suspend fun items(listId: Long): List<ListItemEntity>

    @Query("SELECT * FROM list_items WHERE listId = :listId ORDER BY position ASC, id ASC")
    fun observeItems(listId: Long): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE id = :id")
    suspend fun getItem(id: Long): ListItemEntity?

    @Update
    suspend fun updateItem(e: ListItemEntity): Int

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteItems(listId: Long): Int

    @Query("DELETE FROM list_items WHERE id = :id")
    suspend fun deleteItem(id: Long): Int

    @Query("UPDATE list_items SET correct = 0, wrong = 0, streak = 0, lastAt = 0 WHERE listId = :listId")
    suspend fun resetItems(listId: Long): Int

    @Query(
        "SELECT listId, COUNT(*) AS total, SUM(CASE WHEN streak >= 2 THEN 1 ELSE 0 END) AS mastered " +
            "FROM list_items GROUP BY listId"
    )
    fun observeProgress(): Flow<List<ListProgress>>

    @Query(
        "SELECT listId, COUNT(*) AS total, SUM(CASE WHEN streak >= 2 THEN 1 ELSE 0 END) AS mastered " +
            "FROM list_items WHERE listId = :listId GROUP BY listId"
    )
    suspend fun progress(listId: Long): ListProgress?
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes ORDER BY resolved ASC, lastAt DESC")
    fun observeAll(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE resolved = 0 ORDER BY times DESC, lastAt DESC LIMIT :limit")
    suspend fun top(limit: Int): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE keyNorm = :key LIMIT 1")
    suspend fun byKey(key: String): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: MistakeEntity): Long

    @Update
    suspend fun update(e: MistakeEntity): Int

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT COUNT(*) FROM mistakes WHERE resolved = 0")
    suspend fun activeCount(): Int

    @Query("UPDATE mistakes SET resolved = :resolved WHERE id = :id")
    suspend fun setResolved(id: Long, resolved: Boolean): Int
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topic_progress")
    fun observeAll(): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress")
    suspend fun all(): List<TopicProgressEntity>

    @Query("SELECT * FROM topic_progress WHERE topicId = :id")
    suspend fun get(id: String): TopicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: TopicProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TopicProgressEntity>): List<Long>
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
}
