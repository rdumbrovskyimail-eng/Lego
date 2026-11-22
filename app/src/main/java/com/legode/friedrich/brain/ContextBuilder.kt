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
 * Собирает контекст запросов к Gemini API:
 *  • соблюдает строгий порядок блоков ради Implicit Context Caching (статичный префикс вверху,
 *    динамическое время/задача в самом низу);
 *  • разделяет актуальный необработанный ввод и прошлую историю для SafetyGuard (фикс кризисной петли);
 *  • объединяет досье, RAG-подборку из KnowledgeBase, прикреплённые источники знаний,
 *    активные решения/прогнозы и WOOP-планы.
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
        val lastUserId: Long,
        val assessment: SafetyGuard.Assessment = SafetyGuard.Assessment(SafetyGuard.RiskLevel.NONE),
        val useSearch: Boolean = false,
        val useUrlContext: Boolean = false
    )

    // ───────────────────────── Обычный ход в чате ─────────────────────────

    suspend fun chatTurn(session: SessionEntity, kickoff: Boolean, now: Long): Built {
        val s = settings.value
        val history = loadHistory(session)
        val lastAtlasIdx = history.indexOfLast { it.role == Repository.ROLE_ATLAS }

        // Разделяем текущие новые сообщения (после последнего ответа Атласа) и предшествующий контекст.
        val pendingUserMsgs = if (kickoff) {
            emptyList()
        } else {
            history.drop((lastAtlasIdx + 1).coerceAtLeast(0)).filter { it.role == Repository.ROLE_USER }
        }
        val currentTurnText = pendingUserMsgs.joinToString("\n") { it.text }
        val priorUserText = history
            .take((lastAtlasIdx + 1).coerceAtLeast(0))
            .takeLast(6)
            .filter { it.role == Repository.ROLE_USER }
            .joinToString("\n") { it.text }

        val analysisText = currentTurnText.ifBlank {
            history.takeLast(3).filter { it.role == Repository.ROLE_USER }.joinToString("\n") { it.text }
        }
        val night = Timex.isNightRisk(now)
        val inferredStates = kb.inferStates(analysisText, now)

        val profile = repo.profile()
        val socraticPreferred = session.socraticMode ||
            s.socraticByDefault ||
            profile.socraticDefault ||
            Skills.mode(session.mode).defaultSocratic

        val assessment = SafetyGuard.assessTurn(
            currentTurnText = currentTurnText,
            recentHistoryText = priorUserText,
            inferredStates = inferredStates,
            isNightRisk = night,
            socraticPreferred = socraticPreferred
        )

        val preferredTones = tonesFor(assessment, s, night, session.mode)
        val preferredKinds = kindsFor(session.mode, assessment, night)
        val recentKb = repo.pingDao.recentKbIds(14).toHashSet()
        val ragEntries = kb.retrieve(
            KnowledgeBase.Query(
                userText = analysisText,
                states = assessment.states,
                preferredTones = preferredTones,
                preferredKinds = preferredKinds,
                excludeIds = recentKb,
                limit = 6,
                diversify = true
            )
        )

        // Статичный и полустатичный блок (для экономии токенов через Implicit Caching)
        val sb = StringBuilder()
        sb.append(dossier(session, now, s))
        val ragText = kb.formatForPrompt(ragEntries)
        if (ragText.isNotEmpty()) sb.append('\n').append(ragText)
        sb.append("\n# РЕЖИМ БЕСЕДЫ\n").append(Skills.directive(session.mode, session.modeArg)).append('\n')
        activePlan(session)?.let { sb.append('\n').append(it) }
        linkedSource(session)?.let { sb.append('\n').append(it) }
        linkedDecision(session)?.let { sb.append('\n').append(it) }

        val parts = ArrayList<GeminiClient.Part>()
        val lastUserId = appendTranscript(session, history, sb, parts, includeBinary = true)

        // Волатильная часть (время, тактика и задача) — строго в самом конце.
        sb.append('\n').append(header(now))
        sb.append(tacticalSection(assessment, s, night))
        sb.append("\n# ЗАДАЧА\n")
        if (kickoff) {
            if (session.isHome && history.isEmpty()) {
                sb.append(
                    "Это твоё САМОЕ ПЕРВОЕ сообщение пользователю — он только что установил приложение. В 2–3 живых СМС: " +
                        "представься как Атлас — личный психо-эмоциональный стратег и интеллектуальный спарринг-партнёр, который вытаскивает " +
                        "из тупиков и апатии к ясности, силе и действиям, а также помогает не сбрасывать мышление на автопилот; " +
                        "скажи, что будешь писать сам в течение дня (отвечать можно прямо из уведомления) и что сюда можно кидать голос, фото, PDF, EPUB и ссылки; " +
                        "задай первый прямой вопрос про главную точку напряжения или задачу прямо сейчас (с учётом данных досье)."
                )
            } else {
                sb.append(Skills.opener(session.mode))
            }
        } else {
            sb.append("Ответь на новые сообщения пользователя (всё, что он написал после твоей последней реплики).")
            if (session.askedRef.isNotEmpty()) {
                sb.append(" Оцени в result выполнение/осмысление по «Последний заданный шаг / якорь / карточка» (если ответ относится к нему).")
            }
        }
        if (!session.titleLocked && !session.isHome && session.turnCount in 1..3 &&
            session.title == Skills.defaultTitle(session.mode, session.modeArg) && session.mode == Skills.FREE
        ) {
            sb.append(" Добавь поле title — короткое ёмкое название этой беседы (2–4 слова).")
        }
        sb.append(" Верни один JSON-объект.")
        parts.add(GeminiClient.Part.Text(sb.toString()))

        val hasUrl = URL_REGEX.containsMatchIn(currentTurnText)
        val wantsFactCheck = s.webSearchEnabled && (
            hasUrl ||
                FACT_CHECK_REGEX.containsMatchIn(currentTurnText.lowercase())
            )

        val system = if (s.fullReference) {
            Persona.system(Curriculum.fullReference, complete = true)
        } else {
            Persona.system(Curriculum.sheetsFor(relevantTopics(session, history)), complete = false)
        }
        return Built(
            system = system,
            parts = parts,
            lastUserId = lastUserId,
            assessment = assessment,
            useSearch = wantsFactCheck,
            useUrlContext = s.webSearchEnabled && hasUrl
        )
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
            states = plan.states,
            toneMode = when {
                night -> SafetyGuard.ToneMode.NIGHT_WIND_DOWN
                plan.gentleOnly -> SafetyGuard.ToneMode.DISTRESS_CALM
                else -> SafetyGuard.ToneMode.DIRECT_PUSH
            }
        )

        val sb = StringBuilder()
        sb.append(dossier(session, now, s))
        val ragText = kb.formatForPrompt(ragEntries)
        if (ragText.isNotEmpty()) sb.append('\n').append(ragText)
        activePlan(session)?.let { sb.append('\n').append(it) }

        val parts = ArrayList<GeminiClient.Part>()
        appendTranscript(session, history, sb, parts, includeBinary = false)

        sb.append('\n').append(header(now))
        sb.append(tacticalSection(assessment, s, night))
        sb.append("\n# ЗАДАЧА: ПРОАКТИВНОЕ СМС-СООБЩЕНИЕ\n")
        sb.append("Ты сам пишешь пользователю — он сейчас не в чате и получит это в шторку уведомлений.\n")
        sb.append(plan.task).append('\n')
        sb.append(
            "Требования: 1 сообщение (максимум 2), 2–5 строк; первая строка сразу цепляет в уведомлении; " +
                "без дежурных фраз вроде «Как твои дела?»; опирайся на досье и базу знаний; " +
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

    // ───────────────────────── Новые модули: Экстрактор, Решения, Калибровка, Синтез ─────────────────────────

    suspend fun sourceExtraction(
        titleHint: String,
        kind: String,
        contentOrUrl: String,
        attachment: Attachment? = null,
        webEnabled: Boolean = true
    ): Built {
        val mastery = repo.masteryMap()
        val p = repo.profile()
        val parts = ArrayList<GeminiClient.Part>()
        val sb = StringBuilder()
        sb.append("# ПРОФИЛЬ ЧИТАТЕЛЯ\n")
        if (p.goal.isNotBlank()) sb.append("Главная цель: ").append(p.goal).append('\n')
        if (p.interests.isNotBlank()) sb.append("Интересы/опоры: ").append(p.interests).append('\n')
        sb.append("\n# УКАЗАТЕЛЬ НАВЫКОВ (для привязки карточек)\n").append(Curriculum.index(mastery)).append('\n')
        sb.append("\n# ИСТОЧНИК ($kind)\n")
        if (titleHint.isNotBlank()) sb.append("Заголовок/имя: ").append(titleHint).append('\n')

        var useUrl = false
        if (attachment != null) {
            if (attachment.isText) {
                val txt = attachments.readText(attachment)
                sb.append("Содержимое документа:\n<<<\n").append(txt.take(60_000)).append("\n>>>\n")
            } else {
                val b64 = attachments.readBase64(attachment)
                if (b64 != null) {
                    sb.append("Вложение (").append(attachment.label()).append(") приложено следующим блоком.\n")
                    parts.add(GeminiClient.Part.Text(sb.toString()))
                    sb.setLength(0)
                    parts.add(GeminiClient.Part.Blob(attachment.mime, b64))
                }
            }
        } else {
            useUrl = URL_REGEX.containsMatchIn(contentOrUrl)
            sb.append(if (useUrl) "Ссылка на материал: " else "Текст/тезисы:\n")
                .append(contentOrUrl.take(60_000)).append('\n')
        }

        sb.append(
            "\n# ЗАДАЧА\n" +
                "1) Дай концентрированную выжимку сути (summary).\n" +
                "2) Выдели 3–7 ментальных моделей или сильных принципов (key_models).\n" +
                "3) Вскрой 2–3 скрытых допущения, предвзятости или слабых места автора (blind_spots).\n" +
                "4) Сгенерируй 6–10 сильных карточек (cards) с разными типами: recall (объясни механизм), " +
                "transfer (примени к жизни/работе пользователя), critique (где это правило ломается), " +
                "feynman (объясни простыми словами), debias (какую ошибку мышления это лечит).\n" +
                "Верни строго JSON по схеме."
        )
        parts.add(GeminiClient.Part.Text(sb.toString()))

        return Built(
            system = Persona.extractor,
            parts = parts,
            lastUserId = 0L,
            useSearch = webEnabled && kind == "topic",
            useUrlContext = webEnabled && useUrl
        )
    }

    fun decisionAssist(
        title: String,
        context: String,
        alternatives: List<String>,
        chosenOption: String,
        confidencePct: Int,
        webSearch: Boolean
    ): Built {
        val sb = StringBuilder()
        sb.append("# РЕШЕНИЕ НА АУДИТ\n")
        sb.append("Суть выбора: ").append(title).append('\n')
        if (context.isNotBlank()) sb.append("Контекст и ограничения: ").append(context).append('\n')
        if (alternatives.isNotEmpty()) sb.append("Альтернативы: ").append(alternatives.joinToString(" | ")).append('\n')
        if (chosenOption.isNotBlank()) sb.append("Предварительный выбор: ").append(chosenOption)
            .append(" (уверенность ").append(confidencePct).append("%)\n")
        sb.append(
            "\n# ЗАДАЧА\n" +
                "Проведи холодный аудит решения:\n" +
                "• base_rate_note — взгляд снаружи (Outside View): как часто в похожих ситуациях всё проходит по плану и где скрыта ошибка планирования;\n" +
                "• pre_mortem — 3 конкретные причины, почему это решение может с треском провалиться через 6–12 месяцев;\n" +
                "• if_then_safeguard — чёткий WOOP-предохранитель «Если [сигнал риска], то я [конкретный стоп или шаг]»;\n" +
                "• suggested_pct — трезвая базовая вероятность успеха (1..99%).\nВерни строго JSON."
        )
        return Built(
            system = Persona.strategist,
            parts = listOf(GeminiClient.Part.Text(sb.toString())),
            lastUserId = 0L,
            useSearch = webSearch
        )
    }

    suspend fun weeklyReview(now: Long): Built {
        val s = settings.value
        val weekAgo = now - 7 * Srs.DAY
        val recentDaily = repo.dailyDao.recent(7).reversed()
        val traps = repo.trapDao.since(weekAgo)
        val focus = repo.focusSessionDao.since(weekAgo)
        val resolvedPreds = repo.predictionDao.allResolved()
        val calStats = Srs.computeCalibration(resolvedPreds.map { it.probabilityPct to it.outcome })
        val msgs = repo.messageDao.since(weekAgo).filter { it.role == Repository.ROLE_USER }.takeLast(35)

        val sb = StringBuilder()
        sb.append(dossier(null, now, s))
        sb.append("\n# ТЕЛЕМЕТРИЯ ЗА 7 ДНЕЙ\n")
        for (d in recentDaily) {
            val moodAvg = if (d.moodCount > 0) "%.1f/10".format(d.moodSum.toDouble() / d.moodCount) else "—"
            sb.append("• ").append(d.day)
                .append(": сообщ=").append(d.userMsgs)
                .append(", шагов=").append(d.stepsDone)
                .append(", срывов=").append(d.slips)
                .append(", карт=").append(d.cardsReviewed)
                .append(", фокус=").append(d.focusMinutes).append("м")
                .append(", состояние=").append(moodAvg).append('\n')
        }
        sb.append("Всего фокус-сессий: ").append(focus.size)
            .append(", минут фокуса: ").append(focus.sumOf { it.actualMinutes }).append('\n')
        if (calStats.totalResolved > 0) {
            sb.append("Калибровка прогнозов: Brier=").append("%.3f".format(calStats.brierScore))
                .append(" (").append(calStats.verdict).append(")\n")
        }
        if (traps.isNotEmpty()) {
            sb.append("Пойманные ловушки недели:\n")
            for (t in traps.take(8)) {
                sb.append(" • «").append(t.thought).append("» ×").append(t.times).append(" → ").append(t.antidote).append('\n')
            }
        }
        if (msgs.isNotEmpty()) {
            sb.append("\n# ФРАГМЕНТЫ РЕПЛИК ПОЛЬЗОВАТЕЛЯ ЗА НЕДЕЛЮ\n")
            for (m in msgs) {
                sb.append('[').append(Timex.dayOf(m.createdAt)).append(' ').append(Timex.hhmm(m.createdAt)).append("] ")
                    .append(Norm.oneLine(m.text, 220)).append('\n')
            }
        }
        sb.append('\n').append(header(now))
        sb.append(
            "\n# ЗАДАЧА: ВОСКРЕСНЫЙ EXECUTIVE REVIEW\n" +
                "Составь сильный, честный недельный отчёт о росте человека:\n" +
                "• summary — 5–7 конкретных предложений: что сдвинулось, где петли торможения, как соотносится режим тела/фокуса и настроение;\n" +
                "• top_trigger — главный триггер провалов или прокрастинации за неделю;\n" +
                "• best_win — самый ценный прорыв или верное решение;\n" +
                "• next_week_challenge — один конкретный вызов роста на следующие 7 дней;\n" +
                "• scores — 6 баллов 0..100 (resilience, critical, systems, calibration, learning, execution).\nВерни строго JSON."
        )
        return Built(Persona.analyst, listOf(GeminiClient.Part.Text(sb.toString())), 0L)
    }

    fun calibrationQuiz(topicHint: String, webSearch: Boolean): Built {
        val hint = topicHint.ifBlank { "история науки, технологии, статистика жизни, психология, экономика или базовые ставки" }
        val text = "Сформулируй ОДИН нетривиальный проверяемый бинарный факт-вопрос (ответ Да=1 или Нет=0) по теме: «$hint». " +
            "Вопрос должен тренировать калибровку уверенности и борьбу с иллюзией знаний (не очевидный для школьника, но имеющий точный факт-ответ). " +
            "Верни JSON по схеме."
        return Built(
            system = Persona.strategist,
            parts = listOf(GeminiClient.Part.Text(text)),
            lastUserId = 0L,
            useSearch = webSearch
        )
    }

    // ───────────────────────── Рефлексия и сжатие ─────────────────────────

    suspend fun reflection(now: Long): Built {
        val s = settings.value
        val sb = StringBuilder()
        sb.append(dossier(null, now, s))
        sb.append("\n# ПОСЛЕДНИЕ СООБЩЕНИЯ (все беседы)\n")
        for (m in repo.messageDao.recentAcross(50).reversed()) {
            val who = if (m.role == Repository.ROLE_USER) "Пользователь" else "Атлас"
            sb.append('[').append(stamp(m.createdAt, now)).append("] ").append(who).append(": ")
                .append(Norm.oneLine(m.text, 340)).append('\n')
        }
        sb.append('\n').append(header(now))
        sb.append(
            "\n# ЗАДАЧА\nТы когнитивно-психологический стратег. Проанализируй динамику и обнови тактические заметки.\n" +
                "note — 3–5 конкретных предложений: какие паттерны, когнитивные искажения и триггеры повторяются, " +
                "на какой тон и какие ментальные модели человек откликается лучше всего.\n" +
                "plan — 3–5 чётких тактических пунктов через «;»: какие навыки прокачивать (по id), на что давить.\n" +
                "facts — до 3 новых устойчивых фактов о жизни, триггерах или целях пользователя (или пустой список).\n" +
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
                "с чем человек пришёл, какие тупики и искажения разобрали, что сработало, какие решения и WOOP-шаги он взял. " +
                "Только факты и суть.\nВерни JSON: {\"summary\":\"…\"}"
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
        mode in setOf(Skills.TEACH, Skills.STEELMAN, Skills.PRODUCTIVE_FAIL, Skills.FALLACY_HUNT) ->
            setOf("firm", "deep", "push")
        mode == Skills.BREAKTHROUGH || s.pushiness >= 2 -> setOf("push", "firm", "deep")
        s.pushiness == 0 -> setOf("calm", "firm", "deep")
        else -> setOf("firm", "push", "calm")
    }

    private fun kindsFor(mode: String, assessment: SafetyGuard.Assessment, night: Boolean): Set<String> = when {
        assessment.level != SafetyGuard.RiskLevel.NONE || mode == Skills.SOS || night ->
            setOf("practice", "cbt", "philosophy")
        mode == Skills.TEACH -> setOf("learning", "model")
        mode in setOf(Skills.STEELMAN, Skills.FALLACY_HUNT) -> setOf("bias", "model", "cbt")
        mode == Skills.PRODUCTIVE_FAIL -> setOf("model", "bias", "learning")
        mode == Skills.BREAKTHROUGH || mode == Skills.DRILL -> setOf("breakthrough", "cbt", "model")
        else -> setOf("cbt", "philosophy", "practice", "breakthrough", "model", "bias", "learning")
    }

    suspend fun dossier(session: SessionEntity?, now: Long, s: Settings): String {
        val p = repo.profile()
        val mastery = repo.masteryMap()
        val est = Curriculum.estimate(mastery)
        val progress = repo.skillDao.all()
        val touched = progress.filter { it.exposures > 0 }.map { it.skillId }.toSet()
        val sb = StringBuilder()

        sb.append("# ДОСЬЕ ПОЛЬЗОВАТЕЛЯ\n")
        sb.append("Имя: ").append(p.name.ifBlank { "неизвестно (спроси при уместном моменте)" })
        sb.append(" | Устойчивость: ").append(est.label)
        sb.append(" | Ранг ума: ").append(p.cognitiveRank)
        if (p.lastMood > 0) sb.append(" | Состояние: ").append(p.lastMood).append("/10")
        sb.append(" | Серия: ").append(p.streak).append(" дн").append(" | Сила: ").append(p.xp).append(" XP\n")
        if (p.goal.isNotBlank()) sb.append("Главная цель / выход: ").append(p.goal).append('\n')
        if (p.interests.isNotBlank()) sb.append("Ценности и что заряжает: ").append(p.interests).append('\n')
        if (p.about.isNotBlank()) sb.append("Контекст жизни: ").append(p.about).append('\n')
        sb.append("Обращение: ").append(if (p.formal) "на «Вы»" else "на «ты»").append('\n')

        val recentDaily = repo.dailyDao.recent(7).filter { it.moodCount > 0 || it.focusMinutes > 0 || it.cardsReviewed > 0 }
        if (recentDaily.isNotEmpty()) {
            val line = recentDaily.take(5).reversed().joinToString(" → ") { d ->
                val moodStr = if (d.moodCount > 0) "${(d.moodSum.toFloat() / d.moodCount).roundToInt()}/10" else "—"
                "${d.day.takeLast(5)}:$moodStr"
            }
            val weekFocus = recentDaily.sumOf { it.focusMinutes }
            val weekCards = recentDaily.sumOf { it.cardsReviewed }
            sb.append("Динамика (7 дн): состояние ").append(line)
                .append(" | фокус ").append(weekFocus).append(" мин | карт FSRS ").append(weekCards).append('\n')
        }

        val resolvedPreds = repo.predictionDao.allResolved()
        if (resolvedPreds.isNotEmpty()) {
            val cal = Srs.computeCalibration(resolvedPreds.map { it.probabilityPct to it.outcome })
            sb.append("Калибровка прогнозов (n=").append(cal.totalResolved).append("): Brier=")
                .append("%.3f".format(cal.brierScore)).append(" — ").append(cal.verdict).append('\n')
        }

        val facts = repo.factDao.recent(14)
        if (facts.isNotEmpty()) {
            sb.append("Устойчивые факты и триггеры: ").append(facts.joinToString("; ") { it.text }).append('\n')
        }

        sb.append("Карта навыков: Стабилизация R0 ").append(est.r0).append("% · КПТ/ACT R1 ").append(est.r1)
            .append("% · Опора R2 ").append(est.r2).append("% · Прорыв R3 ").append(est.r3)
            .append("% · Интеллект M1–M3 ").append(est.intelOverall).append("%\n")

        val focus = Curriculum.focus(mastery, 3)
        if (focus.isNotEmpty()) {
            sb.append("Навыки в фокусе: ")
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
                sb.append(" • «").append(t.thought).append("» → «").append(t.antidote).append("» ×").append(t.times)
                if (t.skillId.isNotEmpty()) sb.append(" [").append(t.skillId).append(']')
                sb.append('\n')
            }
        }

        val counts = repo.anchorDao.statusCounts().associate { it.status to it.n }
        val total = counts.values.sum()
        val dueAnchors = repo.anchorDao.dueCount(now)
        val dueCards = repo.knowledgeCardDao.dueCount(now)
        sb.append("Банк памяти: якорей ").append(total)
            .append(" (пора подкрепить ").append(dueAnchors).append(") · карточек FSRS к повтору: ").append(dueCards).append('\n')

        val due = repo.anchorDao.due(now, 5)
        if (due.isNotEmpty()) {
            sb.append("Якоря для возврата: ")
                .append(due.joinToString(" | ") { "A${it.id} «${it.phrase}» (${it.trigger.ifBlank { it.source }})" }).append('\n')
        }
        val dueIds = due.map { it.id }.toSet()
        val newest = repo.anchorDao.newest(4).filter { it.id !in dueIds }
        if (newest.isNotEmpty()) {
            sb.append("Недавние якоря (не дублировать): ").append(newest.joinToString(" | ") { it.phrase }).append('\n')
        }

        if (p.tactic.isNotBlank()) sb.append("Твоя тактика: ").append(p.tactic).append('\n')
        repo.journalDao.latest()?.let { j ->
            sb.append("Заметка анализа: ").append(Norm.oneLine(j.text, 500)).append('\n')
        }

        if (session != null && session.askedRef.isNotEmpty()) {
            describeRef(session.askedRef)?.let {
                sb.append("Последний заданный шаг / якорь / карточка: ").append(session.askedRef).append(' ').append(it).append('\n')
            }
        }

        sb.append("\n# УКАЗАТЕЛЬ НАВЫКОВ (id = название, освоенность)\n").append(Curriculum.index(mastery)).append('\n')
        return sb.toString()
    }

    suspend fun describeRef(ref: String): String? {
        val id = ref.drop(1).toLongOrNull() ?: return null
        return when (ref.firstOrNull()) {
            'A' -> repo.anchorDao.get(id)?.let { "«${it.phrase}» — ${it.why}" }
            'S' -> repo.planDao.getStep(id)?.let { st ->
                buildString {
                    append(st.step)
                    if (st.why.isNotBlank()) append(" (").append(st.why).append(')')
                    if (st.ifThenRule.isNotBlank()) append(" [Правило: ").append(st.ifThenRule).append(']')
                }
            }

            'K' -> repo.knowledgeCardDao.get(id)?.let { "Вопрос: ${it.front} | Опора: ${Norm.oneLine(it.back, 160)}" }
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
        sb.append("# АКТИВНЫЙ WOOP-ПЛАН ПРОРЫВА\n«").append(plan.title).append("» — закрыто ").append(completed).append(" из ")
            .append(steps.size).append(".\n")
        if (plan.wish.isNotBlank()) sb.append("Цель (W): ").append(plan.wish).append('\n')
        if (plan.outcome.isNotBlank()) sb.append("Исход (O): ").append(plan.outcome).append('\n')
        if (plan.mainObstacle.isNotBlank()) sb.append("Главная помеха (O): ").append(plan.mainObstacle).append('\n')
        if (left.isEmpty()) {
            sb.append("Все шаги закрыты: зафиксируй победу человека и поставь plan_done = true.\n")
        } else {
            sb.append("Осталось выполнить (ref шаг · WOOP):\n")
            for (st in left.take(20)) {
                sb.append(" S").append(st.id).append(' ').append(st.step)
                if (st.why.isNotBlank()) sb.append(" — ").append(st.why)
                if (st.ifThenRule.isNotBlank()) sb.append(" [Если–То: ").append(st.ifThenRule).append(']')
                if (st.skippedCount > 0) sb.append(" · ✗слито:").append(st.skippedCount)
                sb.append('\n')
            }
            if (left.size > 20) sb.append(" … и ещё ").append(left.size - 20).append('\n')
        }
        return sb.toString()
    }

    private suspend fun linkedSource(session: SessionEntity): String? {
        if (session.sourceId == 0L) return null
        val src = repo.sourceDao.get(session.sourceId) ?: return null
        return buildString {
            append("# ИЗУЧАЕМЫЙ ИСТОЧНИК\n")
            append("Название: ").append(src.title).append(" (").append(src.kind).append(")\n")
            if (src.summary.isNotBlank()) append("Суть: ").append(src.summary).append('\n')
            if (src.keyModels.isNotBlank()) append("Модели: ").append(src.keyModels).append('\n')
            if (src.authorBlindSpots.isNotBlank()) append("Слабые места автора: ").append(src.authorBlindSpots).append('\n')
            if (src.rawExcerpt.isNotBlank()) append("Фрагмент текста:\n").append(src.rawExcerpt.take(3500)).append('\n')
        }
    }

    private suspend fun linkedDecision(session: SessionEntity): String? {
        if (session.decisionId == 0L) return null
        val d = repo.decisionDao.get(session.decisionId) ?: return null
        return buildString {
            append("# ОБСУЖДАЕМОЕ РЕШЕНИЕ\n")
            append("Выбор: ").append(d.title).append(" (уверенность ").append(d.confidencePct).append("%)\n")
            if (d.context.isNotBlank()) append("Контекст: ").append(d.context).append('\n')
            if (d.chosenOption.isNotBlank()) append("Вариант: ").append(d.chosenOption).append('\n')
            if (d.baseRateNote.isNotBlank()) append("Базовая ставка: ").append(d.baseRateNote).append('\n')
            if (d.preMortem.isNotBlank()) append("Pre-mortem: ").append(d.preMortem).append('\n')
            if (d.ifThenSafeguard.isNotBlank()) append("Предохранитель: ").append(d.ifThenSafeguard).append('\n')
        }
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
        Curriculum.focus(mastery, 2).forEach { ids.add(it.id) }
        Curriculum.focusMental(mastery, 2).forEach { ids.add(it.id) }
        repo.trapDao.top(2).forEach { if (it.skillId.isNotEmpty()) ids.add(it.skillId) }
        when (session.mode) {
            Skills.SOS -> {
                ids.add("breathing_reset")
                ids.add("grounding_54321")
            }
            Skills.BREAKTHROUGH -> {
                ids.add("action_activation")
                ids.add("strat_woop_mcii")
            }
            Skills.TEACH -> ids.add("learn_feynman")
            Skills.STEELMAN -> {
                ids.add("logic_steelman")
                ids.add("logic_paul_elder")
            }
            Skills.PRODUCTIVE_FAIL -> ids.add("learn_productive_fail")
            Skills.FALLACY_HUNT -> {
                ids.add("logic_causation")
                ids.add("learn_desirable_diff")
            }
        }
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
        val URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        val FACT_CHECK_REGEX = Regex("(проверь\\s+факт|найди\\s+в\\s+интернет|актуальн\\w*\\s+данн|статистик\\w*\\s+по|погугли|свежи\\w*\\s+новост)")
    }
}