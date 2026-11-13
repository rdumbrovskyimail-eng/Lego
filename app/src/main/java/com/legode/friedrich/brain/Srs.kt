package com.legode.friedrich.brain

import kotlin.math.max
import kotlin.math.min

/**
 * Интервальные повторения (упрощённый SM-2), настроенные под «смс-режим»:
 * новое слово возвращается через 20 минут, затем 6 часов, сутки, три дня и дальше × ease.
 * Ошибка — слово возвращается через 10 минут и теряет часть «лёгкости».
 */
object Srs {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** Через сколько показать только что добавленное слово. */
    const val FIRST_DELAY = 20 * MINUTE

    data class State(
        val intervalDays: Double,
        val ease: Double,
        val reps: Int,
        val lapses: Int
    )

    data class Step(val state: State, val dueAt: Long, val status: String)

    fun onCorrect(s: State, now: Long): Step {
        val reps = s.reps + 1
        val interval = when (reps) {
            1 -> 0.25
            2 -> 1.0
            3 -> 3.0
            else -> max(3.0, s.intervalDays) * s.ease
        }
        val next = State(
            intervalDays = min(interval, 120.0),
            ease = min(2.8, s.ease + 0.05),
            reps = reps,
            lapses = s.lapses
        )
        return Step(next, now + (next.intervalDays * DAY).toLong(), statusOf(next))
    }

    fun onWrong(s: State, now: Long): Step {
        val next = State(
            intervalDays = 0.0,
            ease = max(1.3, s.ease - 0.2),
            reps = 0,
            lapses = s.lapses + 1
        )
        return Step(next, now + 10 * MINUTE, "learning")
    }

    fun statusOf(s: State): String = when {
        s.reps == 0 && s.lapses == 0 -> "new"
        s.intervalDays >= 14.0 -> "known"
        else -> "learning"
    }

    /** Изменение «освоенности» темы (0..100) по оценке модели d ∈ {-2,-1,1,2}. */
    fun masteryAfter(current: Int, d: Int): Int {
        val delta = when {
            d >= 2 -> max(4, (100 - current) / 6)
            d == 1 -> max(2, (100 - current) / 10)
            d == -1 -> -max(4, current / 8)
            d <= -2 -> -max(8, current / 4)
            else -> 0
        }
        return (current + delta).coerceIn(0, 100)
    }
}
