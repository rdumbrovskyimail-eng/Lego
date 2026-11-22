package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.SessionEntity
import kotlin.math.roundToInt

/**
 * Собирает запрос к модели:
 *  • системный слой (личность Атласа + методологический атлас психологии/философии);
 *  • пользовательский контекст: время и циркадная фаза, тактический режим безопасности,
 *    психо-эмоциональное досье, RAG-подборка из крупной базы знаний, активный план и переписка.
 */
class ContextBuilder(
    private val repo: Repository,
    private val settings: SettingsStore,
    private val attachments: AttachmentStore,
    private val kb: KnowledgeBase
) {

    data class Built(
        val system: String,
        val parts: List<GeminiClient.Part>,
        /** Самое свежее сообщение пользователя, попавшее в запрос (0 — если таких нет). */
        val lastUserId: Long,
        /** Локальная оценка риска и состояний для безопасного управления ответом. */
        val assessment: SafetyGuard.Assessment = SafetyGuard.Assessment(SafetyGuard.RiskLevel.NONE)
    )

    // ───────────────────────── Обычный ход в чате ─────────────────────────

    suspend fun chatTurn(session: SessionEntity, kickoff: Boolean, now: Long): Built {
        val s = settings.value
        val history = loadHistory(session)
        val recentUserText = history
            .takeLast(4)
            .filter { it.role == Repository.ROLE_USER }
            .joinToString("\n") { it.text }

        val inferredStates = kb.inferStates(recentUserText, now)
        val assessment = SafetyGuard.assess(recentUserText, inferredStates)
        val night = Timex.isNightRisk(now)

        val preferredTones = tonesFor(assessment, s, night, session.mode)
        val preferredKinds = kindsFor(session.mode, assessment, night)
        val recentKb = repo.pingDao.recentKbIds(14).toHashSet()
        val ragEntries = kb.retrieve(
            KnowledgeBase.Query(
                userText = recentUserText,
                states = assessment.states,
                preferredTones = preferredTones,
                preferredKinds = preferredKinds,
                excludeIds = recentKb,
                limit = 6,
                diversify = true
            )
        )

        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(tacticalSection(assessment, s, night))
        sb.append(dossier(session, now, s))
        val ragText = kb.formatForPrompt(ragEntries)
        if (ragText.isNotEmpty()) sb.append('\n').append(ragText)
        sb.append("\n# РЕЖИМ БЕСЕДЫ\n").append(Skills.directive(session.mode, session.modeArg)).append('\n')
        activePlan(session)?.let { sb.append('\n').append(it) }

        val parts = ArrayList<GeminiClient.Part>()
        val lastUserId = appendTranscript(session, history, sb, parts, includeBinary = true)

        sb.append("\n# ЗАДАЧА\n")
        if (kickoff) {
            if (session.isHome && history.isEmpty()) {
                sb.append(
                    "Это твоё САМОЕ ПЕРВОЕ сообщение пользователю — он только что установил приложение. В 2–3 живых СМС: " +
                        "представься как Атлас — личный психо-эмоциональный стратег и мотивационный движок, который не кормит сладкой водой, " +
                        "а вытаскивает из тупиков, апатии и негатива к силе и действиям; скажи, что будешь писать сам в течение дня " +
                        "и что отвечать можно прямо из уведомления (текстом, голосом или скринами/заметками); " +
                        "и задай первый прямой, точный вопрос про его главную точку напряжения или тупик прямо сейчас (с учётом данных из досье)."
                )
            } else {
                sb.append(Skills.opener(session.mode))
            }
        } else {
            sb.append("Ответь на новые сообщения пользователя (всё, что он написал после твоей последней реплики).")
            if (session.askedRef.isNotEmpty()) {
                sb.append(" Оцени в result выполнение/осмысление по «Последний заданный шаг / якорь» (если ответ относится к нему).")
            }
        }
        if (!session.titleLocked && !session.isHome && session.turnCount in 1..3 &&
            session.title == Skills.defaultTitle(session.mode, session.modeArg) && session.mode == Skills.FREE
        ) {
            sb.append(" Добавь поле title — короткое ёмкое название этой беседы (2–4 слова).")
        }
        sb.append(" Верни один JSON-объект.")
        parts.add(GeminiClient.Part.Text(sb.toString()))

        val system = if (s.fullReference) {
            Persona.system(Curriculum.fullReference, complete = true)
        } else {
            Persona.system(Curriculum.sheetsFor(relevantTopics(session, history)), complete = false)
        }
        return Built(system, parts, lastUserId, assessment)
    }

    // ───────────────────────── Проактивный «СМС-пинг» ─────────────────────────

    suspend fun ping(session: SessionEntity, plan: PingPlan, now: Long): Built {
        val s = settings.value
        val history = loadHistory(session).takeLast(10)
        val night = Timex.isNightRisk(now)
        val recentKb = repo.pingDao.recentKbIds(18).toHashSet()

        val ragEntries = kb.retrieve(
            KnowledgeBase.Query(
                userText = plan.task,
                states = plan.states,
                preferredTones = plan.preferredTones,
                preferredKinds = plan.preferredKinds,
                excludeIds = recentKb,
                limit = 4,
                diversify = true
            )
        )
        val assessment = SafetyGuard.Assessment(
            level = if (plan.gentleOnly) SafetyGuard.RiskLevel.DISTRESS else SafetyGuard.RiskLevel.NONE,
            states = plan.states
        )

        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(tacticalSection(assessment, s, night))
        sb.append(dossier(session, now, s))
        val ragText = kb.formatForPrompt(ragEntries)
        if (ragText.isNotEmpty()) sb.append('\n').append(ragText)
        activePlan(session)?.let { sb.append('\n').append(it) }

        val parts = ArrayList<GeminiClient.Part>()
        appendTranscript(session, history, sb, parts, includeBinary = false)

        sb.append("\n# ЗАДАЧА: ПРОАКТИВНОЕ СМС-СООБЩЕНИЕ\n")
        sb.append("Ты сам пишешь пользователю — он сейчас не в чате и получит это в шторку уведомлений.\n")
        sb.append(plan.task).append('\n')
        sb.append(
            "Требования: 1 сообщение (максимум 2), 2–5 строк; первая строка должна сразу цеплять в уведомлении; " +
                "без дежурных фраз вроде «Как твои дела?»; опирайся на досье и базу знаний, но пиши живо от себя; " +
                "ровно ОДИН ясный вопрос или конкретный вызов; добавь 2–3 точные кнопки quick; " +
                "не повторяй формулировки, которые уже были в переписке выше. Верни один JSON-объект."
        )
        parts.add(GeminiClient.Part.Text(sb.toString()))

        val mastery = repo.masteryMap()
        val ids = LinkedHashSet<String>()
        ids.addAll(plan.topicIds)
        Curriculum.focus(mastery, 2).forEach { ids.add(it.id) }
        return Built(Persona.system(Curriculum.sheetsFor(ids), complete = false), parts, 0L, assessment)
    }

    // ───────────────────────── Рефлексия и сжатие ─────────────────────────

    suspend fun reflection(now: Long): Built {
        val s = settings.value
        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(dossier(null, now, s))
        sb.append("\n# ПОСЛЕДНИЕ СООБЩЕНИЯ (все беседы)\n")
        for (m in repo.messageDao.recentAcross(50).reversed()) {
            val who = if (m.role == Repository.ROLE_USER) "Пользователь" else "Атлас"
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
                .append(Norm.oneLine(m.text, 340)).append('\n')
        }
        sb.append(
            "\n# ЗАДАЧА\nТы психо-эмоциональный стратег. Проанализируй динамику и обнови тактические заметки.\n" +
                "note — 3–5 конкретных предложений: какие паттерны и триггеры повторяются, в какие моменты человек проваливается, " +
                "на какой тон (жёсткий нажим или спокойная опора) и какие техники откликается лучше всего.\n" +
                "plan — 3–5 чётких тактических пунктов через «;»: какие навыки прокачивать (по id), на что давить, какие якоря и ловушки держать в фокусе.\n" +
                "facts — до 3 новых устойчивых фактов о жизни, триггерах или целях пользователя, которых ещё нет в досье (или пустой список).\n" +
                "Верни JSON: {\"note\":\"…\",\"plan\":\"…\",\"facts\":[\"…\"]}"
        )
        return Built(Persona.analyst, listOf(GeminiClient.Part.Text(sb.toString())), 0L)
    }

    fun summary(previous: String, messages: List<MessageEntity>, now: Long): Built {
        val sb = StringBuilder()
        if (previous.isNotBlank()) sb.append("# ПРЕЖНЕЕ КРАТКОЕ СОДЕРЖАНИЕ\n").append(previous).append("\n\n")
        sb.append("# ФРАГМЕНТ ПЕРЕПИСКИ\n")
        for (m in messages) {
            val who = if (m.role == Repository.ROLE_USER) "Пользователь" else "Атлас"
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
                .append(Norm.oneLine(m.text, 500)).append('\n')
        }
        sb.append(
            "\n# ЗАДАЧА\nОбъедини прежнее краткое содержание и этот фрагмент в одну выжимку (до 1000 знаков, по-русски): " +
                "в каком состоянии человек пришёл, какие тупики и ловушки разобрали, что сработало, какие решения и обещания он дал, " +
                "какие незакрытые узлы остались. Только факты и суть.\nВерни JSON: {\"summary\":\"…\"}"
        )
        return Built(Persona.analyst, listOf(GeminiClient.Part.Text(sb.toString())), 0L)
    }

    // ───────────────────────── Секции контекста ─────────────────────────

    private fun header(now: Long): String =
        "# СЕЙЧАС\n${Timex.describeNow(now)} — ${Timex.dayContext(now)} · ${Timex.partOfDay(now)}\n"

    private fun tacticalSection(assessment: SafetyGuard.Assessment, s: Settings, night: Boolean): String {
        val pushLabel = when (s.pushiness) {
            0 -> "деликатная поддержка (минимум давления)"
            1 -> "сбалансированная опора и твёрдость"
            else -> "максимальная напористость и анти-тупиковый драйв (когда нет острого кризиса)"
        }
        return buildString {
            append("\n# ТАКТИЧЕСКИЙ РЕЖИМ\n")
            append("Настройка клиента: ").append(pushLabel).append('\n')
            append("Директива сейчас: ").append(SafetyGuard.tacticalDirective(assessment, night)).append('\n')
        }
    }

    private fun tonesFor(
        assessment: SafetyGuard.Assessment,
        s: Settings,
        night: Boolean,
        mode: String
    ): Set<String> = when {
        assessment.level != SafetyGuard.RiskLevel.NONE -> setOf("calm", "deep")
        mode == Skills.SOS || night -> setOf("calm", "deep")
        mode == Skills.BREAKTHROUGH || s.pushiness >= 2 -> setOf("push", "firm", "deep")
        s.pushiness == 0 -> setOf("calm", "firm", "deep")
        else -> setOf("firm", "push", "calm")
    }

    private fun kindsFor(mode: String, assessment: SafetyGuard.Assessment, night: Boolean): Set<String> = when {
        assessment.level != SafetyGuard.RiskLevel.NONE || mode == Skills.SOS || night ->
            setOf("practice", "cbt", "philosophy")
        mode == Skills.BREAKTHROUGH || mode == Skills.DRILL ->
            setOf("breakthrough", "cbt", "philosophy")
        else -> setOf("cbt", "philosophy", "practice", "breakthrough")
    }

    suspend fun dossier(session: SessionEntity?, now: Long, s: Settings): String {
        val p = repo.profile()
        val mastery = repo.masteryMap()
        val est = Curriculum.estimate(mastery)
        val progress = repo.skillDao.all()
        val touched = progress.filter { it.exposures > 0 }.map { it.skillId }.toSet()
        val sb = StringBuilder()

        sb.append("\n# ДОСЬЕ ПОЛЬЗОВАТЕЛЯ\n")
        sb.append("Имя: ").append(p.name.ifBlank { "неизвестно (спроси при уместном моменте)" })
        sb.append(" | Текущая устойчивость: ").append(est.label)
        if (p.lastMood > 0) sb.append(" | Последний балл состояния: ").append(p.lastMood).append("/10")
        sb.append(" | Серия: ").append(p.streak).append(" дн").append(" | Сила: ").append(p.xp).append(" XP\n")
        if (p.goal.isNotBlank()) sb.append("Главная цель / выход: ").append(p.goal).append('\n')
        if (p.interests.isNotBlank()) sb.append("Ценности и что заряжает: ").append(p.interests).append('\n')
        if (p.about.isNotBlank()) sb.append("Контекст жизни: ").append(p.about).append('\n')
        sb.append("Обращение: ").append(if (p.formal) "на «Вы»" else "на «ты»").append('\n')

        // Динамика состояния за последние 7 дней
        val recentDaily = repo.dailyDao.recent(7).filter { it.moodCount > 0 }
        if (recentDaily.isNotEmpty()) {
            val line = recentDaily.take(5).reversed().joinToString(" → ") { d ->
                val avg = (d.moodSum.toFloat() / d.moodCount).roundToInt()
                "${d.day.takeLast(5)}:$avg/10"
            }
            sb.append("Динамика состояния: ").append(line).append('\n')
        }

        val facts = repo.factDao.recent(14)
        if (facts.isNotEmpty()) {
            sb.append("Устойчивые факты и триггеры: ").append(facts.joinToString("; ") { it.text }).append('\n')
        }

        sb.append("Карта навыков: Стабилизация R0 ").append(est.r0).append("% · КПТ/ACT R1 ").append(est.r1)
            .append("% · Опора/Смыслы R2 ").append(est.r2).append("% · Прорыв R3 ").append(est.r3)
            .append("% (общая ").append(est.overall).append("%)\n")

        val focus = Curriculum.focus(mastery, 3)
        if (focus.isNotEmpty()) {
            sb.append("Ближайшие навыки в фокусе: ")
                .append(focus.joinToString(", ") { "${it.id} — ${it.title} (${mastery[it.id] ?: 0}%)" }).append('\n')
        }
        val weak = Curriculum.weakest(mastery, touched, 3)
        if (weak.isNotEmpty()) {
            sb.append("Уязвимые места: ")
                .append(weak.joinToString(", ") { "${it.id} (${mastery[it.id] ?: 0}%)" }).append('\n')
        }

        val traps = repo.trapDao.top(6)
        if (traps.isNotEmpty()) {
            sb.append("Повторяющиеся ловушки ума:\n")
            for (t in traps) {
                sb.append(" • Мысль «").append(t.thought).append("» → Антидот «").append(t.antidote).append("» ×").append(t.times)
                if (t.skillId.isNotEmpty()) sb.append(" [").append(t.skillId).append(']')
                sb.append('\n')
            }
        }

        val counts = repo.anchorDao.statusCounts().associate { it.status to it.n }
        val total = counts.values.sum()
        val dueCount = repo.anchorDao.dueCount(now)
        sb.append("Банк якорей силы: всего ").append(total)
            .append(" (новых ").append(counts["new"] ?: 0)
            .append(" · активных ").append(counts["active"] ?: 0)
            .append(" · вживлённых ").append(counts["core"] ?: 0)
            .append("). Пора подкрепить: ").append(dueCount).append('\n')

        val due = repo.anchorDao.due(now, 6)
        if (due.isNotEmpty()) {
            sb.append("Якоря для возврата: ")
                .append(due.joinToString(" | ") { "A${it.id} «${it.phrase}» (${it.trigger.ifBlank { it.source }})" }).append('\n')
        }
        val dueIds = due.map { it.id }.toSet()
        val newest = repo.anchorDao.newest(5).filter { it.id !in dueIds }
        if (newest.isNotEmpty()) {
            sb.append("Недавние якоря (не дублировать): ").append(newest.joinToString(" | ") { it.phrase }).append('\n')
        }

        if (p.tactic.isNotBlank()) sb.append("Твоя тактика (из последней рефлексии): ").append(p.tactic).append('\n')
        repo.journalDao.latest()?.let { j ->
            sb.append("Твои заметки: ").append(Norm.oneLine(j.text, 600)).append('\n')
        }

        if (session != null && session.askedRef.isNotEmpty()) {
            describeRef(session.askedRef)?.let {
                sb.append("Последний заданный шаг / якорь: ").append(session.askedRef).append(' ').append(it).append('\n')
            }
        }

        sb.append("\n# УКАЗАТЕЛЬ НАВЫКОВ (id = название, освоенность)\n").append(Curriculum.index(mastery)).append('\n')
        return sb.toString()
    }

    suspend fun describeRef(ref: String): String? {
        val id = ref.drop(1).toLongOrNull() ?: return null
        return when (ref.firstOrNull()) {
            'A' -> repo.anchorDao.get(id)?.let { "«${it.phrase}» — ${it.why}" }
            'S' -> repo.planDao.getStep(id)?.let { "${it.step}" + if (it.why.isNotBlank()) " (${it.why})" else "" }
            else -> null
        }
    }

    private suspend fun activePlan(session: SessionEntity): String? {
        if (session.planId == 0L) return null
        val plan = repo.planDao.getPlan(session.planId) ?: return null
        val steps = repo.planDao.steps(plan.id)
        if (steps.isEmpty()) return null
        val completed = steps.count { it.streak >= 1 }
        val left = steps.filter { it.streak < 1 }
        val sb = StringBuilder()
        sb.append("# АКТИВНЫЙ ПЛАН ПРОРЫВА\n«").append(plan.title).append("» — закрыто ").append(completed).append(" из ")
            .append(steps.size).append(".\n")
        if (left.isEmpty()) {
            sb.append("Все шаги закрыты: зафиксируй победу человека и поставь plan_done = true.\n")
        } else {
            sb.append("Осталось выполнить (ref шаг · попытки):\n")
            for (st in left.take(20)) {
                sb.append(" S").append(st.id).append(' ').append(st.step)
                if (st.why.isNotBlank()) sb.append(" — ").append(st.why)
                if (st.skippedCount > 0) sb.append(" · ✗слито:").append(st.skippedCount)
                sb.append('\n')
            }
            if (left.size > 20) sb.append(" … и ещё ").append(left.size - 20).append('\n')
        }
        return sb.toString()
    }

    // ───────────────────────── Переписка ─────────────────────────

    private suspend fun loadHistory(session: SessionEntity): List<MessageEntity> {
        val raw = repo.messageDao.recentDesc(session.id, 60)
            .filter { it.role != Repository.ROLE_INFO && it.id > session.summarizedUpToId }
            .reversed()
        var chars = 0
        val kept = ArrayList<MessageEntity>()
        for (m in raw.asReversed()) {
            chars += m.text.length + 40
            if (kept.size >= MAX_MESSAGES || (chars > MAX_CHARS && kept.size >= 6)) break
            kept.add(m)
        }
        kept.reverse()
        return kept
    }

    private suspend fun appendTranscript(
        session: SessionEntity,
        history: List<MessageEntity>,
        sb: StringBuilder,
        parts: MutableList<GeminiClient.Part>,
        includeBinary: Boolean
    ): Long {
        val now = Timex.now()
        sb.append("\n# ПЕРЕПИСКА\n")
        if (session.summary.isNotBlank()) {
            sb.append("Краткое содержание более ранней части: ").append(session.summary).append("\n\n")
        }
        if (history.isEmpty()) {
            sb.append("(пока пусто)\n")
            return 0L
        }
        val lastAtlas = history.indexOfLast { it.role == Repository.ROLE_ATLAS }
        var carryIndex = -1
        if (includeBinary) {
            val from = (history.size - 8).coerceAtLeast(0)
            for (i in (lastAtlas.coerceAtLeast(0)) downTo from) {
                if (history[i].role == Repository.ROLE_USER && history[i].attachments.isNotBlank()) {
                    carryIndex = i
                    break
                }
            }
        }
        var budget = MAX_INLINE_BASE64
        var lastUserId = 0L
        var attNo = 0

        for ((i, m) in history.withIndex()) {
            val isUser = m.role == Repository.ROLE_USER
            if (isUser) lastUserId = m.id
            val who = when {
                isUser -> "Пользователь"
                m.kind == "ping" -> "Атлас (смс)"
                else -> "Атлас"
            }
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
            sb.append(if (m.text.isBlank()) "(без текста)" else m.text).append('\n')

            val atts = Attachment.listFromJson(m.attachments)
            if (atts.isEmpty()) continue
            val pending = isUser && i > lastAtlas
            val full = includeBinary && (pending || i == carryIndex)
            for (a in atts) {
                attNo++
                if (!full) {
                    sb.append("  [вложение: ").append(a.label()).append("]\n")
                    continue
                }
                if (a.isText) {
                    val limit = if (pending) 24_000 else 6_000
                    val text = attachments.readText(a)
                    sb.append("  [вложение ").append(attNo).append(": файл «").append(a.name).append("», содержимое ниже]\n")
                    sb.append("<<<\n").append(if (text.length > limit) text.substring(0, limit) + "\n…(обрезано)" else text)
                        .append("\n>>>\n")
                    continue
                }
                val data = attachments.readBase64(a)
                if (data == null || data.length > budget) {
                    sb.append("  [вложение: ").append(a.label()).append(" — не удалось приложить]\n")
                    continue
                }
                budget -= data.length
                val what = when {
                    a.isImage -> "изображение / скриншот"
                    a.isAudio -> "голосовое сообщение — прослушай слова, темп, интонацию и усталость"
                    a.isPdf -> "PDF-документ «${a.name}»"
                    else -> "файл «${a.name}»"
                }
                sb.append("  [вложение ").append(attNo).append(": ").append(what).append(", идёт сразу следом]\n")
                parts.add(GeminiClient.Part.Text(sb.toString()))
                sb.setLength(0)
                parts.add(GeminiClient.Part.Blob(a.mime, data))
            }
        }
        return lastUserId
    }

    private suspend fun relevantTopics(session: SessionEntity, history: List<MessageEntity>): List<String> {
        val ids = LinkedHashSet<String>()
        if (session.mode == Skills.LESSON && Curriculum.topic(session.modeArg) != null) ids.add(session.modeArg)
        val recentText = history.takeLast(5).joinToString("\n") { it.text }
        Curriculum.match(recentText, 4).forEach { ids.add(it.id) }
        val mastery = repo.masteryMap()
        Curriculum.focus(mastery, 3).forEach { ids.add(it.id) }
        repo.trapDao.top(3).forEach { if (it.skillId.isNotEmpty()) ids.add(it.skillId) }
        if (session.mode == Skills.SOS) {
            ids.add("breathing_reset")
            ids.add("grounding_54321")
        }
        if (session.mode == Skills.BREAKTHROUGH) {
            ids.add("action_activation")
            ids.add("meaning_deadend")
        }
        return ids.take(8)
    }

    private fun stamp(at: Long, now: Long): String {
        val day = Timex.humanDay(at, now)
        return if (day == "Сегодня") Timex.hhmm(at) else day.lowercase() + " " + Timex.hhmm(at)
    }

    private companion object {
        const val MAX_MESSAGES = 30
        const val MAX_CHARS = 16_000
        const val MAX_INLINE_BASE64 = 14 * 1024 * 1024
    }
}