package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import kotlin.random.Random

/**
 * Мощный офлайн-движок СМС «на любой случай»: если нет интернета, API-ключа или сервер недоступен,
 * Атлас всё равно пишет глубокие мысли, КПТ-ловушки, дыхательные техники и вызовы прорыва из всей
 * локальной базы KnowledgeBase + личных якорей пользователя.
 */
object OfflineBank {

    data class Ping(
        val text: String,
        val quick: List<String>,
        val kind: String,
        val kbId: String = ""
    )

    private val NUDGES = listOf(
        "Молчание в тупике играет против тебя. Выйди на связь — хватит одного честного слова.",
        "Ты куда пропал? Когда человек проваливается в тишину, голова начинает крутить самый злой сценарий. Отзовись ⚡",
        "Я рядом и не отстану. Не нужно длинных речей — просто ткни, как ты сейчас по шкале дня.",
        "Застой любит темноту и тишину. Один короткий шаг на свет — и инерция сломана."
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
        "Стоп на 30 секунд:"
    )

    /**
     * Формирует глубокое офлайн-СМС из KnowledgeBase или личного сохранённого якоря пользователя.
     */
    fun make(
        kb: KnowledgeBase,
        rnd: Random,
        dueAnchorPhrase: String?,
        dueAnchorWhy: String?,
        states: Set<String>,
        unanswered: Int,
        excludeKbIds: Set<String>,
        now: Long = Timex.now()
    ): Ping {
        val phase = Timex.circadianPhase(now)
        val night = Timex.isNightRisk(now)
        val lead = if (unanswered >= 3 && !night && rnd.nextInt(100) < 55) NUDGES.random(rnd) + "\n\n" else ""

        // 1. Возврат личного сохранённого якоря силы, если пришло его время по SRS.
        if (!dueAnchorPhrase.isNullOrBlank() && !night && rnd.nextInt(100) < 55) {
            val whyLine = if (!dueAnchorWhy.isNullOrBlank()) "\n\n*Почему держит:* $dueAnchorWhy" else ""
            val text = "${lead}⚓ **Возврат твоего якоря силы:**\n«$dueAnchorPhrase»$whyLine\n\nПрименил это хоть раз за последние сутки или сегодня?"
            return Ping(
                text = text,
                quick = listOf("Да, держу строй ⚡", "Чуть просел, возвращаюсь", "Разберём сейчас"),
                kind = "anchor_due"
            )
        }

        // 2. Подбираем запись из крупной базы KnowledgeBase с учётом времени суток и состояния.
        val preferredTones = when {
            night -> setOf("calm")
            phase == Timex.CircadianPhase.MORNING_START -> setOf("push", "firm")
            phase == Timex.CircadianPhase.AFTERNOON_DIP -> setOf("push", "firm", "calm")
            else -> setOf("push", "firm", "deep")
        }
        val preferredKinds = when {
            night -> setOf("practice", "cbt", "philosophy")
            phase == Timex.CircadianPhase.MORNING_START -> setOf("breakthrough", "philosophy", "cbt")
            else -> setOf("cbt", "philosophy", "breakthrough", "practice")
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

        // 3. Аварийный встроенный резерв (если assets пусты).
        val emergencyText = if (night) {
            "${lead}${NIGHT_OPENERS.random(rnd)}\nСделай 4 цикла дыхания 4–7–8 и выгрузи всё, что тревожит, в заметку. До 10:00 утра любые мрачные выводы не имеют силы."
        } else {
            "${lead}${DAY_OPENERS.random(rnd)}\n**Правило 120 секунд:** не жди настроения, чтобы начать. Выбери 1 застрявшее дело ровно на 2 минуты по таймеру и отпишись, как сдвинул."
        }
        return Ping(
            text = emergencyText,
            quick = if (night) listOf("Сделал дыхание 🌙", "Всё ещё кручу мысли") else listOf("Сделал 2 мин ⚡", "В тупике, помоги"),
            kind = if (night) "calm_practice" else "breakthrough"
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
            "cbt" -> listOf("Попался в это 🎯", "Дай антидот сильнее", "У меня другая мысль")
            else -> listOf("Сильно, беру на вооружение", "Как применить к себе?", "Другую мысль")
        }

        return Ping(
            text = body,
            quick = quick,
            kind = e.kind,
            kbId = e.id
        )
    }
}