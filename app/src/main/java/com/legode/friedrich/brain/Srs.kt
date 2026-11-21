package com.legode.friedrich.brain
import kotlin.math.max
import kotlin.math.min

/**
 * Интервальное подкрепление для психо-эмоциональной устойчивости:
 *  • личные «якоря силы» (цитаты, трезвые рефреймы, опорные фразы) возвращаются по расписанию:
 *    через 35 мин → 12 ч → 2 дн → 5 дн → 12 дн…, чтобы в трудный час человек не скатился назад;
 *  • при откате или срыве («skipped») якорь переводится в усиленное повторение через 45 мин;
 *  • расчёт прогресса навыков устойчивости (0..100).
 */
object Srs {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** Через сколько впервые вернуть только что сохранённый якорь. */
    const val FIRST_DELAY = 35 * MINUTE

    data class State(
        val intervalDays: Double,
        val ease: Double,
        val reps: Int,
        val lapses: Int
    )

    data class Step(val state: State, val dueAt: Long, val status: String)

    fun onReinforced(s: State, now: Long): Step {
        val reps = s.reps + 1
        val interval = when (reps) {
            1 -> 0.5
            2 -> 2.0
            3 -> 5.0
            else -> max(4.0, s.intervalDays) * s.ease
        }
        val next = State(
            intervalDays = min(interval, 90.0),
            ease = min(2.8, s.ease + 0.06),
            reps = reps,
            lapses = s.lapses
        )
        return Step(next, now + (next.intervalDays * DAY).toLong(), statusOf(next))
    }

    fun onRelapse(s: State, now: Long): Step {
        val next = State(
            intervalDays = 0.0,
            ease = max(1.3, s.ease - 0.2),
            reps = 0,
            lapses = s.lapses + 1
        )
        return Step(next, now + 45 * MINUTE, "active")
    }

    fun statusOf(s: State): String = when {
        s.reps == 0 && s.lapses == 0 -> "new"
        s.intervalDays >= 10.0 -> "core"
        else -> "active"
    }

    /** Изменение освоенности психологического навыка (0..100) по оценке модели d ∈ {-2,-1,1,2}. */
    fun masteryAfter(current: Int, d: Int): Int {
        val delta = when {
            d >= 2 -> max(5, (100 - current) / 6)
            d == 1 -> max(2, (100 - current) / 10)
            d == -1 -> -max(3, current / 10)
            d <= -2 -> -max(6, current / 5)
            else -> 0
        }
        return (current + delta).coerceIn(0, 100)
    }
}
