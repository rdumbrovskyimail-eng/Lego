package com.legode.friedrich.brain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Когнитивно-математические метрики памяти и калибровки:
 *  • константы времени и совместимый мост к FSRS-5 для якорей и карточек знаний;
 *  • расчёт прогресса навыков устойчивости и интеллектуальных моделей (0..100);
 *  • расчёт Brier Score и бинов кривой калибровки вероятностей (по Филипу Тетлоку).
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

    data class CalibrationBin(
        val label: String,
        val rangeStartPct: Int,
        val rangeEndPct: Int,
        val count: Int,
        val expectedProb: Double,
        val observedFreq: Double
    )

    data class CalibrationStats(
        val totalResolved: Int,
        val brierScore: Double,
        val meanBias: Double,
        val highConfBias: Double,
        val verdict: String,
        val bins: List<CalibrationBin>
    )

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

    /** Маппинг результата из диалога ("done" | "partial" | "skipped") в оценку FSRS. */
    fun ratingFromDialogResult(result: String): Fsrs.Rating = when (result) {
        "done" -> Fsrs.Rating.GOOD
        "partial" -> Fsrs.Rating.HARD
        "skipped" -> Fsrs.Rating.AGAIN
        else -> Fsrs.Rating.GOOD
    }

    /** Вычисляет Brier Score для одного бинарного прогноза (вероятность 1..99%, исход 0 или 1). */
    fun brierFor(probabilityPct: Int, outcome: Int): Double {
        val p = probabilityPct.coerceIn(1, 99) / 100.0
        val o = if (outcome > 0) 1.0 else 0.0
        return (p - o).pow(2)
    }

    /**
     * Полная статистика калибровки по завершённым прогнозам `(probabilityPct, outcome 0|1)`.
     * Brier Score: 0.0 = идеал, 0.25 = монетка 50/50, <0.15 = сильный прогнозист.
     */
    fun computeCalibration(samples: List<Pair<Int, Int>>): CalibrationStats {
        if (samples.isEmpty()) {
            return CalibrationStats(
                totalResolved = 0,
                brierScore = -1.0,
                meanBias = 0.0,
                highConfBias = 0.0,
                verdict = "Пока нет завершённых прогнозов",
                bins = emptyList()
            )
        }

        var brierSum = 0.0
        var biasSum = 0.0
        var highBiasSum = 0.0
        var highCount = 0

        for ((pct, out) in samples) {
            val p = pct.coerceIn(1, 99) / 100.0
            val o = if (out > 0) 1.0 else 0.0
            brierSum += (p - o).pow(2)
            biasSum += (p - o)
            if (pct >= 70) {
                highBiasSum += (p - o)
                highCount++
            }
        }

        val n = samples.size
        val brier = brierSum / n
        val meanBias = biasSum / n
        val highBias = if (highCount > 0) highBiasSum / highCount else meanBias

        val ranges = listOf(
            Triple("0–20%", 0, 20),
            Triple("21–40%", 21, 40),
            Triple("41–60%", 41, 60),
            Triple("61–80%", 61, 80),
            Triple("81–100%", 81, 100)
        )
        val bins = ranges.map { (label, lo, hi) ->
            val sub = samples.filter { it.first.coerceIn(0, 100) in lo..hi }
            if (sub.isEmpty()) {
                CalibrationBin(label, lo, hi, 0, (lo + hi) / 200.0, 0.0)
            } else {
                val exp = sub.map { it.first / 100.0 }.average()
                val obs = sub.map { if (it.second > 0) 1.0 else 0.0 }.average()
                CalibrationBin(label, lo, hi, sub.size, exp, obs)
            }
        }

        val verdict = when {
            n < 5 -> "Слишком мало проверок ($n) — нужно хотя бы 5 для честной калибровки"
            highCount >= 3 && highBias > 0.14 -> "Сверхуверенность: при заявленных 70%+ сбывается реже ожидаемого"
            meanBias < -0.14 -> "Излишний пессимизм / недооценка уверенности"
            brier <= 0.12 && abs(meanBias) <= 0.08 -> "Сильная калибровка уровня суперпрогнозиста 🎯"
            brier <= 0.20 -> "Хорошая рабочая калибровка"
            else -> "Шум в оценках — чаще сверяйся с базовой ставкой (Outside View)"
        }

        return CalibrationStats(
            totalResolved = n,
            brierScore = brier,
            meanBias = meanBias,
            highConfBias = highBias,
            verdict = verdict,
            bins = bins
        )
    }

    /** Изменение освоенности психологического/когнитивного навыка (0..100) по оценке d ∈ {-2,-1,1,2}. */
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