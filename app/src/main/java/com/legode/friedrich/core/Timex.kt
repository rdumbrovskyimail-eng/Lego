package com.legode.friedrich.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Всё, что связано со временем: дни, календарные окна, циркадные фазы и интервалы памяти. */
object Timex {

    enum class CircadianPhase {
        MORNING_START,   // 05..10 — запуск, опора, фокус дня
        MIDDAY_FLOW,     // 11..13 — продуктивное окно
        AFTERNOON_DIP,   // 14..17 — спад энергии, риск прокрастинации
        EVENING_UNWIND,  // 18..21 — подведение итогов, разгрузка головы
        LATE_RISK,       // 22..23 — риск руминации, беречь сон
        DEEP_NIGHT       // 00..04 — уязвимое время, только бережная стабилизация
    }

    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val hmFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val weekdaysRu = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    val weekdaysShortRu = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val monthsRuGen = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun now(): Long = System.currentTimeMillis()

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    fun today(): String = LocalDate.now(zone()).format(dayFmt)

    fun dayOf(millis: Long): String = dateTime(millis).toLocalDate().format(dayFmt)

    fun yesterdayOf(day: String): String = try {
        LocalDate.parse(day, dayFmt).minusDays(1).format(dayFmt)
    } catch (_: Exception) {
        ""
    }

    fun startOfDay(millis: Long = now()): Long =
        dateTime(millis).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()

    /** Начало текущей календарной недели (понедельник 00:00). */
    fun startOfWeek(millis: Long = now()): Long =
        dateTime(millis).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone())
            .toInstant()
            .toEpochMilli()

    /** Метка времени через [days] дней в указанный час (для дедлайнов решений и прогнозов). */
    fun daysFromNow(days: Int, hour: Int = 12, nowMillis: Long = now()): Long {
        val targetDate = dateTime(nowMillis).toLocalDate().plusDays(days.toLong().coerceAtLeast(0L))
        return targetDate.atTime(hour.coerceIn(0, 23), 0).atZone(zone()).toInstant().toEpochMilli()
    }

    /** Последние [count] дней в формате yyyy-MM-dd от самого раннего к сегодняшнему. */
    fun recentDays(count: Int, nowMillis: Long = now()): List<String> {
        if (count <= 0) return emptyList()
        val end = dateTime(nowMillis).toLocalDate()
        return ((count - 1) downTo 0).map { offset -> end.minusDays(offset.toLong()).format(dayFmt) }
    }

    fun hhmm(millis: Long): String = dateTime(millis).format(hmFmt)

    fun minuteOfDay(millis: Long): Int {
        val dt = dateTime(millis)
        return dt.hour * 60 + dt.minute
    }

    /** 0 = понедельник .. 6 = воскресенье. */
    fun dayOfWeekIndex(millis: Long = now()): Int = (dateTime(millis).dayOfWeek.value - 1).coerceIn(0, 6)

    /** Час суток 0..23. */
    fun hourOfDay(millis: Long = now()): Int = dateTime(millis).hour

    /** Короткая дата: «22 ноя» (или «22.11.2025» для другого года). */
    fun formatShortDate(millis: Long, nowMillis: Long = now()): String {
        if (millis <= 0L) return "—"
        val d = dateTime(millis).toLocalDate()
        val cur = dateTime(nowMillis).toLocalDate()
        return if (d.year == cur.year) {
            "${d.dayOfMonth} ${monthsRuGen[d.monthValue - 1].take(3)}"
        } else {
            "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
        }
    }

    /** «Сегодня», «Вчера», «12 ноября», «3 марта 2025». */
    fun humanDay(millis: Long, nowMillis: Long = now()): String {
        val d = dateTime(millis).toLocalDate()
        val today = dateTime(nowMillis).toLocalDate()
        val diff = ChronoUnit.DAYS.between(d, today)
        return when {
            diff == 0L -> "Сегодня"
            diff == 1L -> "Вчера"
            d.year == today.year -> "${d.dayOfMonth} ${monthsRuGen[d.monthValue - 1]}"
            else -> "${d.dayOfMonth} ${monthsRuGen[d.monthValue - 1]} ${d.year}"
        }
    }

    /** Для списка чатов: время сегодня, иначе «вчера» / дата. */
    fun shortStamp(millis: Long, nowMillis: Long = now()): String {
        if (millis <= 0L) return ""
        val d = dateTime(millis).toLocalDate()
        val today = dateTime(nowMillis).toLocalDate()
        val diff = ChronoUnit.DAYS.between(d, today)
        return when {
            diff == 0L -> hhmm(millis)
            diff == 1L -> "вчера"
            diff < 7L -> weekdaysRu[d.dayOfWeek.value - 1].take(2)
            else -> "%02d.%02d".format(d.dayOfMonth, d.monthValue)
        }
    }

    fun ago(millis: Long, nowMillis: Long = now()): String {
        if (millis <= 0L) return "никогда"
        val sec = ((nowMillis - millis) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 60 -> "только что"
            sec < 3600 -> "${sec / 60} мин назад"
            sec < 86400 -> "${sec / 3600} ч назад"
            else -> "${sec / 86400} дн назад"
        }
    }

    fun until(millis: Long, nowMillis: Long = now()): String {
        val sec = (millis - nowMillis) / 1000L
        return when {
            sec <= 0 -> "вот-вот"
            sec < 60 -> "меньше минуты"
            sec < 3600 -> "≈ ${(sec + 30) / 60} мин"
            sec < 86400 -> "≈ ${sec / 3600} ч ${(sec % 3600) / 60} мин"
            else -> "≈ ${sec / 86400} дн"
        }
    }

    /** Формат интервала в днях (может быть дробным) для кнопок FSRS. */
    fun formatIntervalDays(days: Double): String = when {
        days < (1.0 / 24.0) -> "${(days * 1440.0).roundToInt().coerceAtLeast(1)} мин"
        days < 1.0 -> "${(days * 24.0).roundToInt().coerceAtLeast(1)} ч"
        days < 30.0 -> "${days.roundToInt().coerceAtLeast(1)} дн"
        days < 365.0 -> "${(days / 30.0).roundToInt().coerceAtLeast(1)} мес"
        else -> "%.1f г".format(days / 365.0)
    }

    fun isDueSoon(dueAt: Long, windowHours: Int = 24, nowMillis: Long = now()): Boolean =
        dueAt > 0L && dueAt <= nowMillis + windowHours * 3_600_000L

    fun duration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Строка для промпта: «воскресенье, 22 ноября 2026, 16:10». */
    fun describeNow(nowMillis: Long = now()): String {
        val dt = dateTime(nowMillis)
        val wd = dt.dayOfWeek.value - 1
        val m = dt.monthValue - 1
        return "${weekdaysRu[wd]}, ${dt.dayOfMonth} ${monthsRuGen[m]} ${dt.year}, ${dt.format(hmFmt)}"
    }

    fun circadianPhase(nowMillis: Long = now()): CircadianPhase = when (dateTime(nowMillis).hour) {
        in 5..10 -> CircadianPhase.MORNING_START
        in 11..13 -> CircadianPhase.MIDDAY_FLOW
        in 14..17 -> CircadianPhase.AFTERNOON_DIP
        in 18..21 -> CircadianPhase.EVENING_UNWIND
        in 22..23 -> CircadianPhase.LATE_RISK
        else -> CircadianPhase.DEEP_NIGHT
    }

    fun isNightRisk(nowMillis: Long = now()): Boolean {
        val p = circadianPhase(nowMillis)
        return p == CircadianPhase.LATE_RISK || p == CircadianPhase.DEEP_NIGHT
    }

    fun isWeekend(nowMillis: Long = now()): Boolean =
        dateTime(nowMillis).dayOfWeek == DayOfWeek.SATURDAY || dateTime(nowMillis).dayOfWeek == DayOfWeek.SUNDAY

    /** Эмоциональный и циркадный контекст времени суток. */
    fun partOfDay(nowMillis: Long = now()): String = when (circadianPhase(nowMillis)) {
        CircadianPhase.MORNING_START -> "утро (старт дня, сборка опоры и первого шага)"
        CircadianPhase.MIDDAY_FLOW -> "первая половина дня (рабочий ритм и ясность)"
        CircadianPhase.AFTERNOON_DIP -> "день (возможен спад энергии и прокрастинация)"
        CircadianPhase.EVENING_UNWIND -> "вечер (время подвести итог и разгрузить голову)"
        CircadianPhase.LATE_RISK -> "поздний вечер (пора беречь сон и останавливать прокрутку мыслей)"
        CircadianPhase.DEEP_NIGHT -> "глубокая ночь (уязвимое время: усталость усиливает тревогу и безнадёжность)"
    }

    /** Дополнительный контекст дня недели для промпта. */
    fun dayContext(nowMillis: Long = now()): String {
        val dt = dateTime(nowMillis)
        return when (dt.dayOfWeek) {
            DayOfWeek.MONDAY -> if (dt.hour < 14) "понедельник, старт недели (часто включается сопротивление и тревога масштаба)" else "понедельник"
            DayOfWeek.FRIDAY -> if (dt.hour >= 16) "вечер пятницы (накопленная усталость недели)" else "пятница"
            DayOfWeek.SATURDAY -> "суббота (выходной день — риск либо выгорания, либо ощущения пустоты)"
            DayOfWeek.SUNDAY -> if (dt.hour >= 17) "воскресный вечер (часто накатывает тревога перед понедельником)" else "воскресенье"
            else -> weekdaysRu[dt.dayOfWeek.value - 1]
        }
    }

    fun minutesToClock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }
}