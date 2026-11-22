package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Движок интервальной памяти FSRS-5 (Free Spaced Repetition Scheduler, DSR-модель).
 *
 * Каждая единица памяти (якорь силы, ментальная модель, карточка из книги/статьи) описывается:
 *  • Difficulty (D ∈ [1..10]) — насколько тяжело даётся материал;
 *  • Stability (S, в днях) — за сколько дней вероятность вспомнить падает со 100% до 90%;
 *  • Retrievability (R ∈ [0..1]) — прогнозируемая вероятность вспомнить прямо сейчас.
 *
 * По сравнению с устаревшим SM-2 экономит 20–30% повторений и корректно учитывает опоздания.
 */
object Fsrs {

    enum class Rating(val code: Int, val label: String) {
        AGAIN(1, "Забыл"),
        HARD(2, "Трудно"),
        GOOD(3, "Вспомнил"),
        EASY(4, "Легко")
    }

    data class MemoryState(
        val stability: Double = 0.0,
        val difficulty: Double = 5.0,
        val reps: Int = 0,
        val lapses: Int = 0,
        val lastReviewedAt: Long = 0L
    ) {
        val isNew: Boolean get() = reps == 0 && lastReviewedAt == 0L && stability <= 0.0
    }

    data class ScheduleResult(
        val nextState: MemoryState,
        val intervalDays: Double,
        val dueAt: Long,
        val retrievabilityAtReview: Double,
        /** new | learning | review | core */
        val status: String
    )

    // Откалиброванные веса FSRS-5 (19 параметров, Open Spaced Repetition).
    private val W = doubleArrayOf(
        0.40255,  // w0: S0(Again)
        1.18385,  // w1: S0(Hard)
        3.173,    // w2: S0(Good)
        15.69105, // w3: S0(Easy)
        7.1949,   // w4: база начальной сложности
        0.5345,   // w5: экспонента начальной сложности
        1.4604,   // w6: шаг изменения сложности
        0.0046,   // w7: сила возврата к среднему (mean reversion)
        1.54575,  // w8: рост стабильности при успехе
        0.1192,   // w9: затухание роста с увеличением S
        1.01925,  // w10: бонус за повторение на границе забывания (spacing effect)
        1.9395,   // w11: база новой стабильности после провала
        0.11,     // w12: влияние сложности на сброс при провале
        0.29605,  // w13: сохранение части старой стабильности после провала
        2.2698,   // w14: влияние R на стабильность при провале
        0.2315,   // w15: множитель Hard
        2.9898,   // w16: множитель Easy
        0.51655,  // w17: масштаб краткосрочного повторения (< 1 дня)
        0.6621    // w18: сдвиг краткосрочного повторения
    )

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0 // гарантирует R(t = S, S) == 0.90
    private const val MIN_STABILITY = 0.05
    private const val MAX_STABILITY = 36500.0

    /** Вероятность вспомнить сейчас (0.0 .. 1.0). */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        if (elapsedDays <= 0.0) return 1.0
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY).coerceIn(0.0, 1.0)
    }

    fun retrievabilityNow(state: MemoryState, now: Long): Double {
        if (state.isNew || state.lastReviewedAt <= 0L) return 0.0
        val elapsedDays = ((now - state.lastReviewedAt).coerceAtLeast(0L)) / Timex.DAY_MS_DOUBLE
        return retrievability(elapsedDays, state.stability)
    }

    /** Интервал в днях, за который R упадёт до [desiredRetention] (по умолчанию 90%). */
    fun intervalDays(stability: Double, desiredRetention: Double = 0.90, maxDays: Double = 365.0): Double {
        val dr = desiredRetention.coerceIn(0.70, 0.98)
        val raw = (stability.coerceAtLeast(MIN_STABILITY) / FACTOR) * (dr.pow(1.0 / DECAY) - 1.0)
        return raw.coerceIn(MIN_STABILITY, maxDays)
    }

    /** Основная функция пересчёта состояния памяти после ответа пользователя. */
    fun review(
        current: MemoryState,
        rating: Rating,
        now: Long,
        desiredRetention: Double = 0.90,
        maxIntervalDays: Double = 365.0,
        isSameDayFloorMinutes: Int = 15
    ): ScheduleResult {
        if (current.isNew) {
            val s0 = initStability(rating)
            val d0 = initDifficulty(rating)
            val interval = when (rating) {
                Rating.AGAIN -> isSameDayFloorMinutes / 1440.0
                Rating.HARD -> maxOf(0.5, intervalDays(s0, desiredRetention, maxIntervalDays))
                else -> intervalDays(s0, desiredRetention, maxIntervalDays).coerceAtLeast(1.0)
            }
            val next = MemoryState(
                stability = s0,
                difficulty = d0,
                reps = 1,
                lapses = if (rating == Rating.AGAIN) 1 else 0,
                lastReviewedAt = now
            )
            return ScheduleResult(
                nextState = next,
                intervalDays = interval,
                dueAt = now + (interval * Srs.DAY).toLong(),
                retrievabilityAtReview = 1.0,
                status = statusOf(next, rating)
            )
        }

        val elapsedDays = if (current.lastReviewedAt > 0L) {
            ((now - current.lastReviewedAt).coerceAtLeast(0L)) / Srs.DAY.toDouble()
        } else {
            1.0
        }
        val r = retrievability(elapsedDays, current.stability)
        val nextD = nextDifficulty(current.difficulty, rating)

        val nextS = if (elapsedDays < 1.0) {
            shortTermStability(current.stability, rating)
        } else if (rating == Rating.AGAIN) {
            nextForgetStability(current.difficulty, current.stability, r)
        } else {
            nextRecallStability(current.difficulty, current.stability, r, rating)
        }

        val interval = if (rating == Rating.AGAIN) {
            (isSameDayFloorMinutes / 1440.0).coerceAtLeast(MIN_STABILITY / 4.0)
        } else if (elapsedDays < 1.0 && rating == Rating.HARD) {
            maxOf(0.25, intervalDays(nextS, desiredRetention, maxIntervalDays))
        } else {
            intervalDays(nextS, desiredRetention, maxIntervalDays).coerceAtLeast(1.0)
        }

        val next = MemoryState(
            stability = nextS,
            difficulty = nextD,
            reps = current.reps + 1,
            lapses = current.lapses + if (rating == Rating.AGAIN) 1 else 0,
            lastReviewedAt = now
        )
        return ScheduleResult(
            nextState = next,
            intervalDays = interval,
            dueAt = now + (interval * Srs.DAY).toLong(),
            retrievabilityAtReview = r,
            status = statusOf(next, rating)
        )
    }

    /** Предпросмотр будущих интервалов для 4 кнопок оценки в UI. */
    fun previewIntervals(
        state: MemoryState,
        now: Long,
        desiredRetention: Double = 0.90
    ): Map<Rating, String> = Rating.entries.associateWith { r ->
        val res = review(state, r, now, desiredRetention)
        Timex.formatIntervalDays(res.intervalDays)
    }

    /** Конвертация старого состояния из SM-2 (для бесшовной миграции существующих якорей). */
    fun fromLegacy(intervalDays: Double, ease: Double, reps: Int, lapses: Int, lastSeenAt: Long): MemoryState {
        if (reps == 0 && lapses == 0 && intervalDays <= 0.0) return MemoryState(lastReviewedAt = lastSeenAt)
        val estS = intervalDays.coerceIn(0.4, 365.0)
        val estD = (11.0 - (ease - 1.3) * (8.0 / 1.5)).coerceIn(1.0, 10.0)
        return MemoryState(stability = estS, difficulty = estD, reps = reps, lapses = lapses, lastReviewedAt = lastSeenAt)
    }

    private fun initStability(rating: Rating): Double =
        W[rating.code - 1].coerceIn(MIN_STABILITY, MAX_STABILITY)

    private fun initDifficulty(rating: Rating): Double =
        (W[4] - exp(W[5] * (rating.code - 1)) + 1.0).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val d0Easy = W[4] - exp(W[5] * 3.0) + 1.0
        val delta = -W[6] * (rating.code - 3)
        val dPrime = d + delta * ((10.0 - d) / 9.0)
        return (W[7] * d0Easy + (1.0 - W[7]) * dPrime).coerceIn(1.0, 10.0)
    }

    private fun shortTermStability(s: Double, rating: Rating): Double {
        val next = s * exp(W[17] * (rating.code - 3 + W[18]))
        return when (rating) {
            Rating.AGAIN -> minOf(next, s * 0.75)
            Rating.HARD -> minOf(next, s * 1.15)
            else -> next
        }.coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) W[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) W[16] else 1.0
        val growth = exp(W[8]) * (11.0 - d) * s.pow(-W[9]) * (exp(W[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus + 1.0
        return (s * growth).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val next = W[11] * d.pow(-W[12]) * ((s + 1.0).pow(W[13]) - 1.0) * exp(W[14] * (1.0 - r))
        return minOf(next, s * 0.75).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun statusOf(state: MemoryState, lastRating: Rating): String = when {
        lastRating == Rating.AGAIN || state.stability < 1.0 -> "learning"
        state.stability >= 14.0 -> "core"
        else -> "review"
    }

    private val Timex.DAY_MS_DOUBLE: Double get() = 86_400_000.0
}