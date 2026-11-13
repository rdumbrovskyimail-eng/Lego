package com.legode.friedrich.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * База Фридриха. Две половины:
 *  1) переписка — sessions, messages, pings;
 *  2) «досье» ученика — profile, vocab, lists/list_items, mistakes, topic_progress, facts, journal, daily.
 */

// ───────────────────────────── Переписка ─────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Режим беседы — id из Skills (free, lesson, drill, review, mistakes, roleplay, placement, exam, blitz). */
    val mode: String = "free",
    /** Аргумент режима: id темы / id сценария. */
    val modeArg: String = "",
    val emoji: String = "💬",
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /** Главный чат: сюда по умолчанию приходят «смс». Его нельзя удалить, только очистить. */
    val isHome: Boolean = false,
    val unread: Int = 0,
    val lastPreview: String = "",
    /** Сжатое содержание старой части беседы (экономит контекст модели). */
    val summary: String = "",
    val summarizedUpToId: Long = 0,
    val draft: String = "",
    /** Пользователь переименовал вручную — авто-заголовок больше не трогаем. */
    val titleLocked: Boolean = false,
    /** Активный список слов, который отрабатывается в этой беседе (0 — нет). */
    val listId: Long = 0,
    /** Что Friedrich спросил последним: "V12" (слово словаря) или "L7" (элемент списка). */
    val askedRef: String = "",
    val turnCount: Int = 0
)

@Entity(
    tableName = "messages",
    indices = [Index("sessionId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** user | friedrich | info */
    val role: String,
    val text: String,
    val createdAt: Long,
    /** ok | error */
    val status: String = "ok",
    /** chat | ping | info */
    val kind: String = "chat",
    /** JSON-массив вложений, см. Attachment. */
    val attachments: String = "",
    /** JSON-массив строк — кнопки быстрых ответов. */
    val quickReplies: String = "",
    /** JSON: память («+2 слова»), вид пинга, токены, текст ошибки. */
    val meta: String = "",
    val starred: Boolean = false
)

@Entity(tableName = "pings", indices = [Index("sessionId")])
data class PingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firedAt: Long,
    val kind: String,
    /** ai | offline */
    val source: String,
    val sessionId: Long,
    val messageId: Long,
    val answered: Boolean = false,
    val answeredAt: Long = 0
)

// ───────────────────────────── Досье ученика ─────────────────────────────

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    /** Оценка уровня: A0 | A1.1 | A1.2 | A1 */
    val level: String = "A0",
    val goal: String = "",
    val interests: String = "",
    /** Что ученик сам рассказал о себе в настройках. */
    val about: String = "",
    /** Текущий план Фридриха (из его «рефлексии»). */
    val plan: String = "",
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: String = "",
    val createdAt: Long = 0,
    val lastReflectionAt: Long = 0,
    val userMsgsAtReflection: Int = 0,
    /** Обращаться на «Вы» (Sie). */
    val formal: Boolean = false
)

@Entity(
    tableName = "vocab",
    indices = [Index(value = ["keyNorm"], unique = true), Index("dueAt")]
)
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Нормализованный ключ (нижний регистр, без артикля) — защита от дублей. */
    val keyNorm: String,
    /** Как показывать: существительные с артиклем — «der Tisch». */
    val de: String,
    val ru: String,
    val plural: String = "",
    val example: String = "",
    /** friedrich | user | list | core */
    val source: String = "friedrich",
    val createdAt: Long,
    val lastSeenAt: Long = 0,
    /** Когда слово должно вернуться на повторение. */
    val dueAt: Long = 0,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.3,
    val reps: Int = 0,
    val lapses: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    /** new | learning | known */
    val status: String = "new"
)

@Entity(tableName = "lists")
data class StudyListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val sourceSessionId: Long = 0
)

@Entity(tableName = "list_items", indices = [Index("listId")])
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val de: String,
    val ru: String = "",
    val correct: Int = 0,
    val wrong: Int = 0,
    /** Правильных ответов подряд; 2 и больше — элемент «освоен». */
    val streak: Int = 0,
    val lastAt: Long = 0,
    val position: Int = 0
)

@Entity(tableName = "mistakes", indices = [Index(value = ["keyNorm"], unique = true)])
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyNorm: String,
    val bad: String,
    val good: String,
    val topicId: String = "",
    val why: String = "",
    /** Сколько раз ученик повторил эту ошибку. */
    val times: Int = 1,
    val firstAt: Long,
    val lastAt: Long,
    val resolved: Boolean = false
)

@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey val topicId: String,
    /** 0..100 */
    val mastery: Int = 0,
    val exposures: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val lastAt: Long = 0
)

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    /** friedrich | user */
    val source: String = "friedrich"
)

@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** reflection | milestone | note */
    val kind: String = "reflection",
    val text: String
)

@Entity(tableName = "daily")
data class DailyEntity(
    @PrimaryKey val day: String,
    val userMsgs: Int = 0,
    val friedrichMsgs: Int = 0,
    val wordsAdded: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val pings: Int = 0,
    val pingsAnswered: Int = 0,
    val xp: Int = 0
)

// ───────────────────────────── Проекции запросов ─────────────────────────────

data class StatusCount(val status: String, val n: Int)

data class ListProgress(val listId: Long, val total: Int, val mastered: Int)
