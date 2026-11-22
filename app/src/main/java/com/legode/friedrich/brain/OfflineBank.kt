package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import kotlin.random.Random

/**
 * Офлайн-движок проактивных СМС: когда нет интернета или API-ключа, Атлас работает
 * на всей локальной базе KnowledgeBase, личных якорях силы, карточках FSRS и прогнозах.
 */
object OfflineBank {

    data class Ping(
        val text: String,
        val quick: List<String>,
        val kind: String,
        val kbId: String = ""
    )

    private val NUDGES = listOf(
        "Я на связи, без спешки. Хватит одного слова — как ты сейчас?",
        "Давно тебя не слышно. Ничего не нужно объяснять — просто отметь, как ты по шкале от 1 до 10.",
        "Я рядом, когда будешь готов(а). Можно ответить одной кнопкой.",
        "Если сейчас не до разговоров — это нормально. Я здесь, когда понадобится."
    )

    private val MORNING_OPENERS = listOf(
        "Доброе утро ⚡ Сборка дня до того, как затянет суета:",
        "Новый день, чистый бланк. Поймай фокус прямо сейчас:",
        "Старт без раскачки. Мысль на утро:"
    )

    private val NIGHT_OPENERS = listOf(
        "Ночной режим 🌙 Снижаем обороты и не верим ночной тревоге:",
        "Уже поздно. Выключаем внутренний суд до утра:"
    )

    private val DAY_OPENERS = listOf(
        "Атлас на связи ⚡",
        "Короткий чек-ин дня:",
        "Проверка курса:",
        "Точка опоры на сейчас:",
        "Пит-стоп для ума:"
    )

    fun make(
        kb: KnowledgeBase,
        rnd: Random,
        dueAnchorPhrase: String?,
        dueAnchorWhy: String?,
        states: Set<String>,
        unanswered: Int,
        excludeKbIds: Set<String>,
        now: Long = Timex.now(),
        dueCardFront: String? = null,
        duePredQuestion: String? = null,
        duePredPct: Int = 50
    ): Ping {
        val phase = Timex.circadianPhase(now)
        val night = Timex.isNightRisk(now)
        val lead = if (unanswered >= 3 && !night && rnd.nextInt(100) < 55) NUDGES.random(rnd) + "\n\n" else ""

        if (!duePredQuestion.isNullOrBlank() && !night && rnd.nextInt(100) < 35) {
            return Ping(
                text = "${lead}🎯 **Проверка прогноза (Калибровка):**\n«$duePredQuestion»\nТвоя оценка была **$duePredPct%**. Наступил срок — сбылось или нет? Зафиксируй исход в Решениях.",
                quick = listOf("Сбылось ✓", "Не сбылось ✗", "Перенести срок"),
                kind = "prediction_check"
            )
        }

        if (!dueCardFront.isNullOrBlank() && !night && rnd.nextInt(100) < 40) {
            return Ping(
                text = "${lead}🧬 **Активное извлечение (FSRS):**\n$dueCardFront\n\nСначала вытащи ответ из головы своими словами — потом проверь в Тренажёре.",
                quick = listOf("Вспомнил уверенно ✓", "Частично", "Забыл — разберём"),
                kind = "card_due"
            )
        }

        if (!dueAnchorPhrase.isNullOrBlank() && !night && rnd.nextInt(100) < 50) {
            val whyLine = if (!dueAnchorWhy.isNullOrBlank()) "\n\n*Почему держит:* $dueAnchorWhy" else ""
            return Ping(
                text = "${lead}⚓ **Возврат твоего якоря силы:**\n«$dueAnchorPhrase»$whyLine\n\nПрименил это хоть раз за последние сутки или сегодня?",
                quick = listOf("Да, держу строй ⚡", "Чуть просел, возвращаюсь", "Разберём сейчас"),
                kind = "anchor_due"
            )
        }

        val preferredTones = when {
            night -> setOf("calm")
            phase == Timex.CircadianPhase.MORNING_START -> setOf("push", "firm")
            phase == Timex.CircadianPhase.AFTERNOON_DIP -> setOf("push", "firm", "calm")
            else -> setOf("push", "firm", "deep")
        }
        val preferredKinds = when {
            night -> setOf("practice", "cbt", "philosophy")
            phase == Timex.CircadianPhase.MORNING_START -> setOf("breakthrough", "philosophy", "model")
            else -> setOf("cbt", "philosophy", "breakthrough", "practice", "model", "bias", "learning")
        }
        val targetStates = LinkedHashSet(states).apply {
            if (night) add("night")
            if (isEmpty()) add("deadend")
        }

        val entry = kb.pickForPing(
            rnd = rnd,
            states = targetStates,
            preferredTones = preferredTones,
            preferredKinds = preferredKinds,
            excludeIds = excludeKbIds
        )

        if (entry != null) {
            return formatEntry(entry, lead, phase, night, rnd)
        }

        val emergencyText = if (night) {
            "${lead}${NIGHT_OPENERS.random(rnd)}\nСделай 4 цикла дыхания 4–7–8 и выгрузи всё, что тревожит, в заметку. До 10:00 утра ночные приговоры не имеют силы."
        } else {
            "${lead}${DAY_OPENERS.random(rnd)}\n**Правило 120 секунд:** не жди настроения, чтобы начать. Выбери 1 застрявшее дело ровно на 2 минуты по таймеру и отпишись."
        }
        return Ping(
            text = emergencyText,
            quick = if (night) listOf("Сделал дыхание 🌙", "Всё ещё кручу мысли") else listOf("Сделал 2 мин ⚡", "В тупике, помоги"),
            kind = if (night) "calm_reset" else "breakthrough"
        )
    }

    private fun formatEntry(
        e: KbEntry,
        lead: String,
        phase: Timex.CircadianPhase,
        night: Boolean,
        rnd: Random
    ): Ping {
        val header = when {
            night -> NIGHT_OPENERS.random(rnd)
            phase == Timex.CircadianPhase.MORNING_START -> MORNING_OPENERS.random(rnd)
            else -> DAY_OPENERS.random(rnd)
        }
        val badge = when (e.kind) {
            "cbt" -> "🔍 Ловушка ума"
            "philosophy" -> "🏛️ Опора"
            "practice" -> "🌊 Стабилизация"
            "model" -> "🕸️ Ментальная модель"
            "bias" -> "⚖️ Дебаисинг"
            "learning" -> "🧬 Ультраобучение"
            else -> "⚡ Рывок"
        }
        val sourceTail = if (e.source.isNotBlank()) " — *${e.source}*" else ""

        val body = buildString {
            append(lead).append(header).append('\n')
            append("**").append(badge).append(": ").append(e.title).append("**\n")
            append("«").append(e.core).append("»").append(sourceTail)
            if (e.analysis.isNotBlank()) append("\n\n").append(e.analysis)
            if (e.action.isNotBlank()) append("\n\n🎯 **Ход:** ").append(e.action)
        }

        val quick = when (e.kind) {
            "practice" -> listOf("Сделал, чуть легче 🌊", "Всё равно накрывает", "Ещё технику")
            "breakthrough" -> listOf("Пошёл делать ⚡", "Сделал! 🔥", "Дай шаг проще")
            "cbt", "bias" -> listOf("Попался в это 🎯", "Дай антидот сильнее", "Проверь мою мысль")
            "model", "learning" -> listOf("Применю к задаче 🧠", "Разберём на примере", "Другую модель")
            else -> listOf("Сильно, беру на вооружение", "Как применить к себе?", "Другую мысль")
        }

        return Ping(text = body, quick = quick, kind = e.kind, kbId = e.id)
    }
}