package com.legode.friedrich.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * База данных Атласа (версия 3):
 *  1) переписка — sessions, messages, pings;
 *  2) психологическое досье — profile, anchors, plans/plan_steps, traps, skill_progress, facts, journal, daily;
 *  3) когнитивный рост — sources (книги/URL/PDF), knowledge_cards (FSRS), decisions, predictions (Brier),
 *     focus_sessions (поток и дыхание), weekly_reviews (радар и синтез недели).
 */

// ───────────────────────────── Переписка ─────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Режим беседы — id из Skills. */
    val mode: String = "free",
    /** Аргумент режима: id протокола / id сценария / заголовок источника. */
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
    /** Что Атлас спросил последним: "A12" (якорь силы), "S7" (шаг плана) или "K5" (карточка). */
    val askedRef: String = "",
    val turnCount: Int = 0,
    /** Включён ли анти-оффлоадинг (сократический режим тренера) в этом чате. */
    val socraticMode: Boolean = false,
    /** Связанный источник знаний (0 — нет). */
    val sourceId: Long = 0,
    /** Связанное решение из Лаборатории решений (0 — нет). */
    val decisionId: Long = 0
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
    val answeredAt: Long = 0,
    /** Задержка отклика в миллисекундах (телеметрия JITAI). */
    val latencyMs: Long = 0,
    /** replied | quick | snoozed | ignored */
    val outcome: String = ""
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
    val formal: Boolean = false,
    /** Интеллектуальный ранг: наблюдатель | практик | аналитик | стратег. */
    val cognitiveRank: String = "наблюдатель",
    /** Целевая вероятность удержания памяти FSRS (по умолчанию 0.90). */
    val desiredRetention: Double = 0.90,
    /** Глобальный режим «не думай за меня» (сократический спарринг по умолчанию). */
    val socraticDefault: Boolean = false,
    /** Время последнего Воскресного синтеза (Executive Review). */
    val lastWeeklyReviewAt: Long = 0
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
    /** atlas | user | plan | source */
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
    val status: String = "new",
    /** Стабильность памяти FSRS (в днях). */
    val stability: Double = 0.0,
    /** Сложность памяти FSRS (1..10). */
    val difficulty: Double = 5.0
)

@Entity(tableName = "plans")
data class ActionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val sourceSessionId: Long = 0,
    /** W (WOOP): главное желание/результат плана. */
    val wish: String = "",
    /** O (WOOP): лучший конкретный образ исхода. */
    val outcome: String = "",
    /** O (WOOP): главное внутреннее препятствие. */
    val mainObstacle: String = ""
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
    val position: Int = 0,
    /** Главная помеха именно этого шага. */
    val obstacle: String = "",
    /** WOOP-правило: «Если [триггер/препятствие], то я [действие]». */
    val ifThenRule: String = ""
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
    /** reflection | milestone | note | emotion | critique */
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
    val xp: Int = 0,
    val cardsReviewed: Int = 0,
    val focusMinutes: Int = 0
)

// ───────────────────────────── Вторая память, Решения и Фокус (v3) ─────────────────────────────

/** Загруженный источник знаний: книга, PDF, веб-статья (URL), лекция или заметка. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** pdf | url | text | image | audio | topic */
    val kind: String = "text",
    /** URL статьи, путь к файлу вложенного источника или id темы. */
    val originRef: String = "",
    /** Плотная выжимка сути источника. */
    val summary: String = "",
    /** Выделенные ментальные модели (список строк или JSON). */
    val keyModels: String = "",
    /** Скрытые допущения, слабые места или предвзятость автора. */
    val authorBlindSpots: String = "",
    /** Очищенный исходный фрагмент текста для обсуждения в чате. */
    val rawExcerpt: String = "",
    val cardCount: Int = 0,
    val createdAt: Long,
    val lastStudiedAt: Long = 0
)

/** Карточка активного извлечения (Active Recall / Elaborative Interrogation / Debiasing) на базе FSRS-5. */
@Entity(
    tableName = "knowledge_cards",
    indices = [Index("sourceId"), Index("dueAt")]
)
data class KnowledgeCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 — самостоятельная карточка/модель, >0 — из конкретного SourceEntity. */
    val sourceId: Long = 0,
    /** Привязанный протокол/модель из Curriculum (может быть пустым). */
    val skillId: String = "",
    /** recall | transfer | critique | feynman | debias */
    val kind: String = "recall",
    /** Вопрос, кейс на перенос или провокация (лицевая сторона). */
    val front: String,
    /** Эталонный разбор, критерии истины или контр-пример (обратная сторона). */
    val back: String,
    val hint: String = "",
    val tags: String = "",
    val createdAt: Long,
    val lastReviewedAt: Long = 0,
    val dueAt: Long = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val intervalDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** new | learning | review | core */
    val status: String = "new",
    val suspended: Boolean = false
)

/** Зафиксированное решение до получения результата (защита от hindsight bias + Pre-mortem). */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val context: String = "",
    /** JSON-массив альтернатив. */
    val alternatives: String = "",
    val chosenOption: String = "",
    /** Взгляд со стороны / базовая ставка (Reference Class). */
    val baseRateNote: String = "",
    /** Pre-mortem: 3 главные причины, почему решение провалится через год. */
    val preMortem: String = "",
    /** WOOP-предохранитель: «Если пойдёт не так X → я сделаю Y». */
    val ifThenSafeguard: String = "",
    val confidencePct: Int = 50,
    /** open | reviewed | archived */
    val status: String = "open",
    val reviewAt: Long = 0,
    val outcomeNote: String = "",
    /** Качество самого процесса решения (1..10), отдельно от везения исхода. */
    val processQuality: Int = 0,
    val createdAt: Long,
    val resolvedAt: Long = 0
)

/** Вероятностный прогноз для подсчёта Brier Score и калибровки уверенности. */
@Entity(
    tableName = "predictions",
    indices = [Index("resolved"), Index("checkAt")]
)
data class PredictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long = 0,
    val question: String,
    /** 1..99% */
    val probabilityPct: Int,
    /** personal | career | calibration_quiz | world */
    val category: String = "personal",
    val rationale: String = "",
    val checkAt: Long,
    val resolved: Boolean = false,
    /** -1 = открыт, 0 = не сбылось, 1 = сбылось */
    val outcome: Int = -1,
    /** -1.0 = не посчитан, иначе (p - o)^2 ∈ [0..1] */
    val brierScore: Double = -1.0,
    val createdAt: Long,
    val resolvedAt: Long = 0
)

/** Сессия глубокого фокуса или соматического дыхательного пейсера. */
@Entity(tableName = "focus_sessions", indices = [Index("createdAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** deep_90 | sprint_25 | micro_2 | breath_phys | breath_478 | breath_box | breath_coherent */
    val kind: String,
    val goal: String = "",
    val obstacle: String = "",
    val ifThenPlan: String = "",
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean = false,
    val focusQuality: Int = 0,
    val moodBefore: Int = 0,
    val moodAfter: Int = 0,
    val note: String = "",
    val createdAt: Long
)

/** Еженедельный интеллектуально-психологический синтез (Executive Review). */
@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDay: String,
    val summary: String,
    val nextWeekChallenge: String = "",
    val topTrigger: String = "",
    val bestWin: String = "",
    /** JSON: {"critical":65,"calibration":70,"systems":55,"resilience":72,"learning":60,"execution":68} */
    val scoresJson: String = "",
    val brierAvg: Double = -1.0,
    val avgMood: Double = 0.0,
    val createdAt: Long
)

// ───────────────────────────── Проекции запросов ─────────────────────────────

data class StatusCount(val status: String, val n: Int)

data class PlanProgress(val planId: Long, val total: Int, val done: Int)