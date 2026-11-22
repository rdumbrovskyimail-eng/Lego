package com.legode.friedrich.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * База данных Атласа. Две половины:
 *  1) переписка — sessions, messages, pings;
 *  2) психологическое досье — profile, anchors, plans/plan_steps, traps, skill_progress, facts, journal, daily.
 */

// ───────────────────────────── Переписка ─────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Режим беседы — id из Skills (free, breakthrough, sos, lesson, review, mistakes, roleplay, diagnostic, blitz, drill). */
    val mode: String = "free",
    /** Аргумент режима: id протокола / id сценария. */
    val modeArg: String = "",
    val emoji: String = "⚡",
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /** Главный чат: сюда по умолчанию приходят проактивные «смс». Его нельзя удалить, только очистить. */
    val isHome: Boolean = false,
    val unread: Int = 0,
    val lastPreview: String = "",
    /** Сжатое содержание старой части беседы (экономит контекст модели). */
    val summary: String = "",
    val summarizedUpToId: Long = 0,
    val draft: String = "",
    /** Пользователь переименовал вручную — авто-заголовок больше не трогаем. */
    val titleLocked: Boolean = false,
    /** Активный план прорыва, который отрабатывается в этой беседе (0 — нет). */
    val planId: Long = 0,
    /** Что Атлас спросил последним: "A12" (якорь силы) или "S7" (шаг плана). */
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
    /** user | atlas | info */
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
    /** JSON: настроение, эмоция, якоря, ловушки, токены, ошибка. */
    val meta: String = "",
    val starred: Boolean = false
)

@Entity(
    tableName = "pings",
    indices = [Index("sessionId"), Index("firedAt")]
)
data class PingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firedAt: Long,
    val kind: String,
    /** ai | offline */
    val source: String,
    val sessionId: Long,
    val messageId: Long,
    /** id записи из KnowledgeBase, чтобы не повторять одни и те же цитаты/практики. */
    val kbId: String = "",
    val answered: Boolean = false,
    val answeredAt: Long = 0
)

// ───────────────────────────── Психологическое досье ─────────────────────────────

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    /** Оценка устойчивости: истощение | спад | стабильность | сила */
    val stage: String = "спад",
    val goal: String = "",
    val interests: String = "",
    /** Что пользователь сам рассказал о своей ситуации и контексте жизни. */
    val about: String = "",
    /** Текущая тактика Атласа (из периодической рефлексии). */
    val tactic: String = "",
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: String = "",
    /** Последний зафиксированный балл состояния (0 = не оценено, 1..10). */
    val lastMood: Int = 0,
    val createdAt: Long = 0,
    val lastReflectionAt: Long = 0,
    val userMsgsAtReflection: Int = 0,
    /** Обращаться на «Вы». */
    val formal: Boolean = false
)

@Entity(
    tableName = "anchors",
    indices = [Index(value = ["keyNorm"], unique = true), Index("dueAt")]
)
data class AnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Нормализованный ключ фразы — защита от дублей. */
    val keyNorm: String,
    val phrase: String,
    val why: String,
    val source: String = "",
    val trigger: String = "",
    /** atlas | user | plan */
    val origin: String = "atlas",
    val createdAt: Long,
    val lastSeenAt: Long = 0,
    /** Когда якорь стоит вернуть на подкрепление. */
    val dueAt: Long = 0,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.3,
    val reps: Int = 0,
    val lapses: Int = 0,
    val reinforced: Int = 0,
    val relapses: Int = 0,
    /** new | active | core */
    val status: String = "new"
)

@Entity(tableName = "plans")
data class ActionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val sourceSessionId: Long = 0
)

@Entity(tableName = "plan_steps", indices = [Index("planId")])
data class PlanStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val step: String,
    val why: String = "",
    val doneCount: Int = 0,
    val skippedCount: Int = 0,
    /** 1 и более — шаг выполнен. */
    val streak: Int = 0,
    val lastAt: Long = 0,
    val position: Int = 0
)

@Entity(tableName = "traps", indices = [Index(value = ["keyNorm"], unique = true)])
data class TrapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyNorm: String,
    val thought: String,
    val antidote: String,
    val skillId: String = "",
    val why: String = "",
    /** Сколько раз всплывала эта ловушка ума. */
    val times: Int = 1,
    val firstAt: Long,
    val lastAt: Long,
    val resolved: Boolean = false
)

@Entity(tableName = "skill_progress")
data class SkillProgressEntity(
    @PrimaryKey val skillId: String,
    /** 0..100 */
    val mastery: Int = 0,
    val exposures: Int = 0,
    val wins: Int = 0,
    val slips: Int = 0,
    val lastAt: Long = 0
)

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    /** atlas | user */
    val source: String = "atlas"
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
    val atlasMsgs: Int = 0,
    val anchorsAdded: Int = 0,
    val stepsDone: Int = 0,
    val slips: Int = 0,
    val pings: Int = 0,
    val pingsAnswered: Int = 0,
    val moodSum: Int = 0,
    val moodCount: Int = 0,
    val xp: Int = 0
)

// ───────────────────────────── Проекции запросов ─────────────────────────────

data class StatusCount(val status: String, val n: Int)

data class PlanProgress(val planId: Long, val total: Int, val done: Int)