package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.SessionEntity

/**
 * Собирает запрос к модели: системную часть (личность + справочник) и пользовательскую часть —
 * время, досье ученика, режим беседы, активный список, переписку с вложениями и задачу хода.
 */
class ContextBuilder(
    private val repo: Repository,
    private val settings: SettingsStore,
    private val attachments: AttachmentStore
) {

    data class Built(
        val system: String,
        val parts: List<GeminiClient.Part>,
        /** Самое свежее сообщение ученика, попавшее в запрос (0 — если таких нет). */
        val lastUserId: Long
    )

    // ───────────────────────── Обычный ход в чате ─────────────────────────

    suspend fun chatTurn(session: SessionEntity, kickoff: Boolean, now: Long): Built {
        val s = settings.value
        val history = loadHistory(session)
        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(dossier(session, now, s))
        sb.append("\n# РЕЖИМ БЕСЕДЫ\n").append(Skills.directive(session.mode, session.modeArg)).append('\n')
        activeList(session)?.let { sb.append('\n').append(it) }

        val parts = ArrayList<GeminiClient.Part>()
        val lastUserId = appendTranscript(session, history, sb, parts, includeBinary = true)

        sb.append("\n# ЗАДАЧА\n")
        if (kickoff) {
            if (session.isHome && history.isEmpty()) {
                sb.append(
                    "Это твоё САМОЕ ПЕРВОЕ сообщение ученику — он только что установил приложение. В 2–3 коротких СМС: " +
                        "поздоровайся и представься (кто ты, что будешь писать ему сам в течение дня, спрашивать и поправлять, " +
                        "и что отвечать можно прямо из уведомления); скажи, что тебе можно прислать свои слова, фото учебника или голосовое; " +
                        "и задай первый простой вопрос для знакомства по-немецки с переводом (с учётом уровня из досье)."
                )
            } else {
                sb.append(Skills.opener(session.mode))
            }
        } else {
            sb.append("Ответь на новые сообщения ученика (всё, что он написал после твоей последней реплики).")
            if (session.askedRef.isNotEmpty()) {
                sb.append(" Оцени в result его ответ на «Последний заданный элемент» (если он на него отвечал).")
            }
        }
        if (!session.titleLocked && !session.isHome && session.turnCount in 1..3 &&
            session.title == Skills.defaultTitle(session.mode, session.modeArg) && session.mode == Skills.FREE
        ) {
            sb.append(" Добавь поле title — короткое название этой беседы (2–4 слова).")
        }
        sb.append(" Верни один JSON-объект.")
        parts.add(GeminiClient.Part.Text(sb.toString()))

        val system = if (s.fullReference) {
            Persona.system(Curriculum.fullReference, complete = true)
        } else {
            Persona.system(Curriculum.sheetsFor(relevantTopics(session, history)), complete = false)
        }
        return Built(system, parts, lastUserId)
    }

    // ───────────────────────── «СМС-пинг» ─────────────────────────

    suspend fun ping(session: SessionEntity, plan: PingPlan, now: Long): Built {
        val s = settings.value
        val history = loadHistory(session).takeLast(12)
        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(dossier(session, now, s))
        activeList(session)?.let { sb.append('\n').append(it) }
        val parts = ArrayList<GeminiClient.Part>()
        appendTranscript(session, history, sb, parts, includeBinary = false)
        sb.append("\n# ЗАДАЧА: СМС-ПИНГ\n")
        sb.append("Ты сам пишешь ученику — он сейчас не в чате и получит это как уведомление на телефон.\n")
        sb.append(plan.task).append('\n')
        sb.append(
            "Требования: 1 сообщение (максимум 2), до 4 строк; без дежурного приветствия (кроме утреннего пинга) — " +
                "сразу живая подводка и дело; ровно ОДИН вопрос или задание; если есть варианты ответа — дай кнопки quick; " +
                "не повторяй задания, которые уже есть в переписке выше; первая строка должна цеплять в уведомлении. " +
                "Верни один JSON-объект."
        )
        parts.add(GeminiClient.Part.Text(sb.toString()))

        val mastery = repo.masteryMap()
        val ids = LinkedHashSet<String>()
        ids.addAll(plan.topicIds)
        Curriculum.focus(mastery, 2).forEach { ids.add(it.id) }
        return Built(Persona.system(Curriculum.sheetsFor(ids), complete = false), parts, 0L)
    }

    // ───────────────────────── Рефлексия и сжатие ─────────────────────────

    suspend fun reflection(now: Long): Built {
        val s = settings.value
        val sb = StringBuilder()
        sb.append(header(now))
        sb.append(dossier(null, now, s))
        sb.append("\n# ПОСЛЕДНИЕ СООБЩЕНИЯ (все беседы)\n")
        for (m in repo.messageDao.recentAcross(50).reversed()) {
            val who = if (m.role == Repository.ROLE_USER) "Ученик" else "Friedrich"
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
                .append(Norm.oneLine(m.text, 320)).append('\n')
        }
        sb.append(
            "\n# ЗАДАЧА\nТы методист. Проанализируй, как идёт обучение, и обнови свои заметки.\n" +
                "note — 3–5 предложений: что у ученика получается, что стабильно не получается, какой у него стиль " +
                "(темп, мотивация, время активности), что сработало и что нет.\n" +
                "plan — 3–5 коротких пунктов через «;»: что именно делать в ближайшие дни (темы по id, типы заданий, что повторять).\n" +
                "facts — до 3 новых долговременных фактов об ученике, которых ещё нет в досье (или пустой список).\n" +
                "Верни JSON: {\"note\":\"…\",\"plan\":\"…\",\"facts\":[\"…\"]}"
        )
        return Built(Persona.analyst, listOf(GeminiClient.Part.Text(sb.toString())), 0L)
    }

    fun summary(previous: String, messages: List<MessageEntity>, now: Long): Built {
        val sb = StringBuilder()
        if (previous.isNotBlank()) sb.append("# ПРЕЖНЕЕ КРАТКОЕ СОДЕРЖАНИЕ\n").append(previous).append("\n\n")
        sb.append("# ФРАГМЕНТ ПЕРЕПИСКИ\n")
        for (m in messages) {
            val who = if (m.role == Repository.ROLE_USER) "Ученик" else "Friedrich"
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
                .append(Norm.oneLine(m.text, 500)).append('\n')
        }
        sb.append(
            "\n# ЗАДАЧА\nОбъедини прежнее краткое содержание и этот фрагмент в одно новое (до 900 знаков, по-русски): " +
                "о чём говорили, что объяснял Friedrich, что ученик усвоил и где ошибался, какие договорённости и " +
                "незакрытые вопросы остались. Только факты, без воды.\nВерни JSON: {\"summary\":\"…\"}"
        )
        return Built(Persona.analyst, listOf(GeminiClient.Part.Text(sb.toString())), 0L)
    }

    // ───────────────────────── Досье ─────────────────────────

    private fun header(now: Long): String =
        "# СЕЙЧАС\n${Timex.describeNow(now)} — ${Timex.partOfDay(now)}\n"

    private fun autoRussianShare(level: String): Int = when (level) {
        "A0" -> 70
        "A1.1" -> 55
        "A1.2" -> 40
        else -> 30
    }

    suspend fun dossier(session: SessionEntity?, now: Long, s: Settings): String {
        val p = repo.profile()
        val mastery = repo.masteryMap()
        val est = Curriculum.estimate(mastery)
        val progress = repo.topicDao.all()
        val touched = progress.filter { it.exposures > 0 }.map { it.topicId }.toSet()
        val sb = StringBuilder()

        sb.append("\n# ДОСЬЕ УЧЕНИКА\n")
        sb.append("Имя: ").append(p.name.ifBlank { "неизвестно (узнай при случае)" })
        sb.append(" | Уровень (оценка): ").append(est.label).append(" → цель A1")
        sb.append(" | Серия: ").append(p.streak).append(" дн").append(" | XP: ").append(p.xp).append('\n')
        if (p.goal.isNotBlank()) sb.append("Цель: ").append(p.goal).append('\n')
        if (p.interests.isNotBlank()) sb.append("Интересы: ").append(p.interests).append('\n')
        if (p.about.isNotBlank()) sb.append("О себе: ").append(p.about).append('\n')

        val ru = if (s.russianShare in 0..100) s.russianShare else autoRussianShare(est.label)
        val strict = when (s.strictness) {
            0 -> "мягко"
            2 -> "строго"
            else -> "обычно"
        }
        sb.append("Стиль: русского ≈ ").append(ru).append("%, немецкого ≈ ").append(100 - ru)
            .append("% · исправления: ").append(strict)
            .append(" · обращение: ").append(if (p.formal) "Sie (на «Вы»)" else "du").append('\n')

        val facts = repo.factDao.recent(14)
        if (facts.isNotEmpty()) {
            sb.append("Факты: ").append(facts.joinToString("; ") { it.text }).append('\n')
        }

        sb.append("Освоенность: A0 ").append(est.a0).append("% · A1.1 ").append(est.a11).append("% · A1.2 ")
            .append(est.a12).append("% (в целом ").append(est.overall).append("%)\n")
        val focus = Curriculum.focus(mastery, 3)
        if (focus.isNotEmpty()) {
            sb.append("В фокусе по программе: ")
                .append(focus.joinToString(", ") { "${it.id} — ${it.title} (${mastery[it.id] ?: 0}%)" }).append('\n')
        }
        val weak = Curriculum.weakest(mastery, touched, 3)
        if (weak.isNotEmpty()) {
            sb.append("Слабые места: ")
                .append(weak.joinToString(", ") { "${it.id} (${mastery[it.id] ?: 0}%)" }).append('\n')
        }

        val mistakes = repo.mistakeDao.top(6)
        if (mistakes.isNotEmpty()) {
            sb.append("Частые ошибки:\n")
            for (m in mistakes) {
                sb.append(" • «").append(m.bad).append("» → «").append(m.good).append("» ×").append(m.times)
                if (m.topicId.isNotEmpty()) sb.append(" [").append(m.topicId).append(']')
                sb.append('\n')
            }
        }

        val counts = repo.vocabDao.statusCounts().associate { it.status to it.n }
        val total = counts.values.sum()
        val dueCount = repo.vocabDao.dueCount(now)
        sb.append("Словарь: всего ").append(total)
            .append(" (новых ").append(counts["new"] ?: 0)
            .append(" · учит ").append(counts["learning"] ?: 0)
            .append(" · знает ").append(counts["known"] ?: 0)
            .append("). Ждут повторения: ").append(dueCount).append('\n')
        val due = repo.vocabDao.due(now, 8)
        if (due.isNotEmpty()) {
            sb.append("К повторению: ")
                .append(due.joinToString(" | ") { "V${it.id} ${it.de} — ${it.ru}" }).append('\n')
        }
        val dueIds = due.map { it.id }.toSet()
        val weakWords = repo.vocabDao.weakest(5).filter { it.id !in dueIds && it.wrong > it.correct }
        if (weakWords.isNotEmpty()) {
            sb.append("Трудные слова: ")
                .append(weakWords.joinToString(" | ") { "V${it.id} ${it.de} — ${it.ru}" }).append('\n')
        }
        val newest = repo.vocabDao.newest(8).filter { it.id !in dueIds }
        if (newest.isNotEmpty()) {
            sb.append("Недавние слова: ").append(newest.joinToString(", ") { it.de }).append('\n')
        }

        if (p.plan.isNotBlank()) sb.append("Твой план (из последнего анализа): ").append(p.plan).append('\n')
        repo.journalDao.latest()?.let { j ->
            sb.append("Твои заметки об ученике: ").append(Norm.oneLine(j.text, 600)).append('\n')
        }

        if (session != null && session.askedRef.isNotEmpty()) {
            describeRef(session.askedRef)?.let {
                sb.append("Последний заданный элемент: ").append(session.askedRef).append(' ').append(it).append('\n')
            }
        }

        sb.append("\n# УКАЗАТЕЛЬ ТЕМ (id = название, освоенность)\n").append(Curriculum.index(mastery)).append('\n')
        return sb.toString()
    }

    suspend fun describeRef(ref: String): String? {
        val id = ref.drop(1).toLongOrNull() ?: return null
        return when (ref.firstOrNull()) {
            'V' -> repo.vocabDao.get(id)?.let { "${it.de} — ${it.ru}" }
            'L' -> repo.listDao.getItem(id)?.let { "${it.de} — ${it.ru}" }
            else -> null
        }
    }

    private suspend fun activeList(session: SessionEntity): String? {
        if (session.listId == 0L) return null
        val list = repo.listDao.getList(session.listId) ?: return null
        val items = repo.listDao.items(list.id)
        if (items.isEmpty()) return null
        val mastered = items.count { it.streak >= 2 }
        val left = items.filter { it.streak < 2 }.sortedWith(compareByDescending<com.legode.friedrich.data.db.ListItemEntity> { it.wrong - it.correct }.thenBy { it.lastAt })
        val sb = StringBuilder()
        sb.append("# АКТИВНЫЙ СПИСОК\n«").append(list.title).append("» — освоено ").append(mastered).append(" из ")
            .append(items.size).append(".\n")
        if (left.isEmpty()) {
            sb.append("Все элементы освоены: поздравь, подведи итог и поставь list_done = true.\n")
        } else {
            sb.append("Осталось (ref элемент — перевод · ✓верно ✗ошибок):\n")
            for (item in left.take(25)) {
                sb.append(" L").append(item.id).append(' ').append(item.de)
                if (item.ru.isNotBlank()) sb.append(" — ").append(item.ru)
                sb.append(" · ✓").append(item.correct).append(" ✗").append(item.wrong).append('\n')
            }
            if (left.size > 25) sb.append(" … и ещё ").append(left.size - 25).append('\n')
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
        val lastFriedrich = history.indexOfLast { it.role == Repository.ROLE_FRIEDRICH }
        // Вложения целиком: все неотвеченные сообщения ученика + последнее более раннее сообщение с вложением.
        var carryIndex = -1
        if (includeBinary) {
            val from = (history.size - 8).coerceAtLeast(0)
            for (i in (lastFriedrich.coerceAtLeast(0)) downTo from) {
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
                isUser -> "Ученик"
                m.kind == "ping" -> "Friedrich (смс)"
                else -> "Friedrich"
            }
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
            sb.append(if (m.text.isBlank()) "(без текста)" else m.text).append('\n')

            val atts = Attachment.listFromJson(m.attachments)
            if (atts.isEmpty()) continue
            val pending = isUser && i > lastFriedrich
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
                    a.isImage -> "изображение"
                    a.isAudio -> "голосовое сообщение / аудио — прослушай"
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
        repo.mistakeDao.top(3).forEach { if (it.topicId.isNotEmpty()) ids.add(it.topicId) }
        if (session.mode == Skills.EXAM) ids.add("exam_a1")
        return ids.take(9)
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
