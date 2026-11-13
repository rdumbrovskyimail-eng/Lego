package com.legode.friedrich.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Всё, что связано со временем: дни, «сколько прошло», немецкие и русские названия. */
object Timex {

    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val hmFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val weekdaysDe = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    val weekdaysRu = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    val monthsDe = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )
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

    fun hhmm(millis: Long): String = dateTime(millis).format(hmFmt)

    fun minuteOfDay(millis: Long): Int {
        val dt = dateTime(millis)
        return dt.hour * 60 + dt.minute
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
        val sec = ((millis - nowMillis) / 1000L)
        return when {
            sec <= 0 -> "вот-вот"
            sec < 60 -> "меньше минуты"
            sec < 3600 -> "≈ ${(sec + 30) / 60} мин"
            sec < 86400 -> "≈ ${sec / 3600} ч ${(sec % 3600) / 60} мин"
            else -> "≈ ${sec / 86400} дн"
        }
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Строка для промпта: «вторник (Dienstag), 10 ноября 2026 (10. November 2026), 17:40». */
    fun describeNow(nowMillis: Long = now()): String {
        val dt = dateTime(nowMillis)
        val wd = dt.dayOfWeek.value - 1
        val m = dt.monthValue - 1
        return "${weekdaysRu[wd]} (${weekdaysDe[wd]}), ${dt.dayOfMonth} ${monthsRuGen[m]} ${dt.year} " +
            "(${dt.dayOfMonth}. ${monthsDe[m]} ${dt.year}), ${dt.format(hmFmt)}"
    }

    fun partOfDay(nowMillis: Long = now()): String {
        return when (dateTime(nowMillis).hour) {
            in 5..10 -> "утро (der Morgen)"
            in 11..13 -> "полдень (der Mittag)"
            in 14..17 -> "день (der Nachmittag)"
            in 18..22 -> "вечер (der Abend)"
            else -> "ночь (die Nacht)"
        }
    }

    fun minutesToClock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
