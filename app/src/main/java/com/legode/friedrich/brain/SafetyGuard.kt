package com.legode.friedrich.brain

/**
 * Тактический и защитный маршрутизатор (работает локально до вызова LLM и при сбоях API).
 *
 * Задачи:
 *  1) CRISIS — мгновенная деэскалация строго по новому вводу (без зацикливания на истории);
 *  2) DISTRESS — бережное заземление при панике, слезах и остром истощении;
 *  3) MI_EMPATHY — Мотивационное интервью (OARS) при амбивалентности и глухом сопротивлении;
 *  4) SOCRATIC_COACH — защита от когнитивного оффлоадинга (когда человек просит думать за него);
 *  5) NIGHT_WIND_DOWN / DIRECT_PUSH — адаптация давления к времени суток и режиму.
 */
object SafetyGuard {

    enum class RiskLevel {
        NONE,      // штатный режим
        DISTRESS,  // острая тяжесть, паника, слёзы, истощение
        CRISIS     // риск суицида или самоповреждения
    }

    enum class ToneMode {
        CRISIS_CARE,
        DISTRESS_CALM,
        NIGHT_WIND_DOWN,
        MI_EMPATHY,
        SOCRATIC_COACH,
        DIRECT_PUSH
    }

    data class Assessment(
        val level: RiskLevel,
        val reason: String = "",
        val states: Set<String> = emptySet(),
        val toneMode: ToneMode = ToneMode.DIRECT_PUSH,
        val resistanceDetected: Boolean = false,
        val changeTalkDetected: Boolean = false,
        val offloadingDetected: Boolean = false
    ) {
        val suppressPushy: Boolean get() = level != RiskLevel.NONE
    }

    private fun wordRegex(pattern: String): Regex =
        Regex("(?<![\\p{L}\\p{Nd}])(?:$pattern)(?![\\p{L}\\p{Nd}])")

    // Бытовые метафоры и отрицания, которые не должны вызывать ложное срабатывание кризисного протокола.
    private val METAPHOR_EXCLUSIONS = listOf(
        "убиться веником", "убить время", "убиваю время", "умираю со смеху",
        "умереть со смеху", "смертельно устал на работе", "смертельно хочется спать",
        "убью за это", "сам себя закопал в задачах", "мозг взрывается от работы",
        "я в безопасности", "я не хочу умирать", "умирать не собираюсь", "жить хочу",
        "не хочу умереть", "читал про суицид", "статья про суицид"
    )

    private val CRISIS_PATTERNS = listOf(
        wordRegex("(?:хочу|решил|планирую|собираюсь|пора|лучше)\\s+(?:умереть|сдохнуть|покончить|уйти из жизни|выпилиться)"),
        wordRegex("(?:покончить с собой|свести счеты с жизнью|наложить на себя руки|убить себя|резать себя|режу себя|порезать вены)"),
        wordRegex("(?:не хочу жить|больше не могу жить|зачем мне жить|нет смысла жить дальше|хочу исчезнуть навсегда)"),
        wordRegex("(?:суицид|самоубийств|таблеток наглотаться|спрыгнуть с)[\\p{L}\\p{Nd}]*"),
        wordRegex("(?:без меня всем будет лучше|я всем мешаю жить|прощальное письмо)")
    )

    private val DISTRESS_PATTERNS = listOf(
        wordRegex("(?:паническая атака|накрыла паника|задыхаюсь|трясет всего|сердце выпрыгивает|схожу с ума)"),
        wordRegex("(?:не вывожу|сил вообще нет|полное дно|жизнь разрушена|все кончено|ничего не осталось)"),
        wordRegex("(?:рыдаю|плачу без остановки|не могу встать с кровати уже|ненавижу себя|я ничтожество)"),
        wordRegex("(?:хочется провалиться|не хочу просыпаться|пустота внутри|мертвый внутри)")
    )

    // Сигналы защитного сопротивления (Sustain Talk по Miller & Rollnick), где прямой напор вызывает отторжение.
    private val RESISTANCE_PATTERNS = listOf(
        wordRegex("(?:бесполезно пробовать|все равно не получится|ничего не изменится|мне ничего не поможет)"),
        wordRegex("(?:отстань|хватит давить|я не готов|не хочу сейчас ничего менять|проще забить|оставь меня)"),
        wordRegex("(?:ты не понимаешь|легко говорить|мне слишком тяжело даже пробовать)")
    )

    // Сигналы внутренней мотивации (Change Talk), за которые Атласу стоит зацепиться и усилить.
    private val CHANGE_TALK_PATTERNS = listOf(
        wordRegex("(?:хочу попробовать|надоело так жить|хочу выбраться|готов сделать|может стоит|попробую сегодня)"),
        wordRegex("(?:мне важно|ради себя|не хочу так больше|пора менять|давай план|хочу научиться)")
    )

    // Запросы, где человек сбрасывает интеллектуальную работу на ИИ (Cognitive Offloading).
    private val OFFLOADING_PATTERNS = listOf(
        wordRegex("(?:реши за меня|прими решение за меня|просто скажи что выбрать|сделай все за меня)"),
        wordRegex("(?:напиши полностью за меня|подумай за меня|дай готовый ответ без вопросов)")
    )

    private val RELIEF_PATTERNS = listOf(
        wordRegex("(?:я в безопасности|отпустило|стало легче|успокоился|дышать проще|спасибо полегчало)")
    )

    /** Совместимый однострочный вызов (например, для одиночного сообщения или теста). */
    fun assess(rawText: String, inferredStates: Set<String> = emptySet()): Assessment =
        assessTurn(currentTurnText = rawText, recentHistoryText = "", inferredStates = inferredStates)

    /**
     * Полный анализ хода диалога:
     *  • CRISIS проверяется ТОЛЬКО по [currentTurnText] (новым необработанным сообщениям), чтобы кнопки
     *    быстрого ответа после кризисного экрана не зацикливали бота;
     *  • DISTRESS учитывает и недавний контекст, если текущий ответ нейтрально-короткий и не выражает облегчения.
     */
    fun assessTurn(
        currentTurnText: String,
        recentHistoryText: String = "",
        inferredStates: Set<String> = emptySet(),
        isNightRisk: Boolean = false,
        socraticPreferred: Boolean = false
    ): Assessment {
        val currentClean = stripMetaphors(Norm.looseKey(currentTurnText))
        val historyClean = stripMetaphors(Norm.looseKey(recentHistoryText))

        if (currentClean.isEmpty() && historyClean.isEmpty()) {
            val mode = if (isNightRisk) ToneMode.NIGHT_WIND_DOWN else if (socraticPreferred) ToneMode.SOCRATIC_COACH else ToneMode.DIRECT_PUSH
            return Assessment(RiskLevel.NONE, states = inferredStates, toneMode = mode)
        }

        // 1. Кризис — строго на актуальном сообщении пользователя.
        if (currentClean.isNotEmpty() && CRISIS_PATTERNS.any { it.containsMatchIn(currentClean) }) {
            return Assessment(
                level = RiskLevel.CRISIS,
                reason = "Обнаружены сигналы острого кризиса или риска самоповреждения.",
                states = inferredStates + "crisis",
                toneMode = ToneMode.CRISIS_CARE
            )
        }

        // 2. Острый дистресс / паника.
        val hasRelief = currentClean.isNotEmpty() && RELIEF_PATTERNS.any { it.containsMatchIn(currentClean) }
        val distressNow = currentClean.isNotEmpty() && DISTRESS_PATTERNS.any { it.containsMatchIn(currentClean) }
        val distressRecent = !hasRelief && currentClean.length < 40 &&
            historyClean.isNotEmpty() && DISTRESS_PATTERNS.any { it.containsMatchIn(historyClean) }

        if (distressNow || distressRecent) {
            return Assessment(
                level = RiskLevel.DISTRESS,
                reason = "Острый эмоциональный перегруз или глубокий спад.",
                states = inferredStates + "distress",
                toneMode = ToneMode.DISTRESS_CALM
            )
        }

        val resistance = currentClean.isNotEmpty() && RESISTANCE_PATTERNS.any { it.containsMatchIn(currentClean) }
        val changeTalk = currentClean.isNotEmpty() && CHANGE_TALK_PATTERNS.any { it.containsMatchIn(currentClean) }
        val offloading = currentClean.isNotEmpty() && OFFLOADING_PATTERNS.any { it.containsMatchIn(currentClean) }

        val tone = when {
            isNightRisk -> ToneMode.NIGHT_WIND_DOWN
            resistance && !changeTalk -> ToneMode.MI_EMPATHY
            socraticPreferred || offloading -> ToneMode.SOCRATIC_COACH
            else -> ToneMode.DIRECT_PUSH
        }

        val extraStates = LinkedHashSet(inferredStates).apply {
            if (resistance) add("resistance")
        }

        return Assessment(
            level = RiskLevel.NONE,
            states = extraStates,
            toneMode = tone,
            resistanceDetected = resistance,
            changeTalkDetected = changeTalk,
            offloadingDetected = offloading
        )
    }

    private fun stripMetaphors(clean: String): String {
        if (clean.isEmpty()) return ""
        return METAPHOR_EXCLUSIONS.fold(clean) { acc, meta ->
            acc.replace(Norm.looseKey(meta), " ")
        }.trim()
    }

    /** Мгновенный ответ при CRISIS (работает и офлайн, и если Gemini заблокировал ответ). */
    fun crisisResponse(userName: String): List<String> {
        val who = if (userName.isBlank()) "" else "$userName, "
        return listOf(
            "${who}стоп. Я с тобой и слышу, насколько тебе сейчас невыносимо тяжело. В таком состоянии не нужно ничего доказывать, решать глобальные задачи или «собираться с силами» прямо сейчас.",
            "Сейчас единственная задача — удержаться в безопасности ближайшие 10 минут. Пожалуйста, не оставайся с этим в одиночестве: напиши или позвони человеку, которому доверяешь, или специалисту прямо сейчас:\n" +
                "- **112** — единый номер экстренной помощи (ЕС / Германия / РФ)\n" +
                "- **0800 111 0 111** или **0800 111 0 222** — круглосуточная линия доверия в Германии (бесплатно, анонимно)\n" +
                "- **8-800-333-44-34** — бесплатная кризисная психологическая линия (РФ)\n" +
                "- Если ты в другой стране — **findahelpline.com** (подбор бесплатной горячей линии по стране)",
            "Если прямо сейчас рядом никого нет — просто останься здесь, в чате. Сделай один медленный выдох, назови 3 предмета, которые видишь вокруг себя, и напиши мне, в безопасности ли ты физически прямо сейчас."
        )
    }

    fun crisisQuickReplies(): List<String> = listOf(
        "Я в безопасности, просто очень больно",
        "Нужно дыхание и заземление",
        "Хочу выговориться"
    )

    /** Запасной ответ, если Gemini API вернул SAFETY/BLOCKED на тяжёлое эмоциональное сообщение. */
    fun blockedFallback(level: RiskLevel, userName: String): List<String> {
        if (level == RiskLevel.CRISIS) return crisisResponse(userName)
        val who = if (userName.isBlank()) "" else "$userName, "
        return listOf(
            "${who}я вижу, что внутри сейчас очень тяжело и штормит. Давай не будем торопиться и ломать себя.",
            "Сделай сейчас одну простую вещь для тела: опусти плечи, сделай медленный вдох на 4 счёта и долгий выдох на 6. Потом напиши мне одной строкой, что именно давит сильнее всего прямо в эту минуту."
        )
    }

    /** Директива для системного/пользовательского контекста, точно настраивающая стратегию Атласа. */
    fun tacticalDirective(assessment: Assessment, isNightRisk: Boolean): String {
        val base = when (assessment.toneMode) {
            ToneMode.CRISIS_CARE ->
                "КРИТИЧЕСКИЙ ПРОТОКОЛ БЕЗОПАСНОСТИ: запрещён жёсткий напор, критика, обесценивание и философия о борьбе. " +
                    "Говори предельно тепло, спокойно и заземляюще. Поддержи контакт, предложи обратиться к близким или на кризисную линию (112 / 0800 111 0 111 / 8-800-333-44-34) и удержи фокус на безопасности."

            ToneMode.DISTRESS_CALM ->
                "РЕЖИМ ТВЁРДОЙ ОПОРЫ: человек сейчас в острой боли, панике или истощении. Отмени жёсткий прессинг. " +
                    "Сначала признай тяжесть без жалости и без токсичного позитива, дай 1 конкретную технику стабилизации тела/мысли из базы знаний и предложи микро-шаг на 60 секунд."

            ToneMode.NIGHT_WIND_DOWN ->
                "НОЧНОЙ ТАКТИЧЕСКИЙ РЕЖИМ: сейчас позднее время. Не накручивай и не требуй масштабных подвигов. " +
                    "Останови ночную прокрутку мыслей (руминацию), переведи фокус на выгрузку тревог до утра, снятие напряжения и сон."

            ToneMode.MI_EMPATHY ->
                "РЕЖИМ МОТИВАЦИОННОГО ИНТЕРВЬЮ (OARS): у человека сильное сопротивление и амбивалентность — НЕ спорь в лоб и НЕ читай мораль (это усилит протест). " +
                    "Сначала отзеркаль его сомнение и усталость, уважай свободу выбора, а затем задай один открытый вопрос, чтобы он сам назвал, что ему важно или почему так жить дальше не хочется."

            ToneMode.SOCRATIC_COACH ->
                "СОКРАТИЧЕСКИЙ РЕЖИМ (АНТИ-ОФФЛОАДИНГ): не решай задачу за человека и не отдавай сразу готовую простыню выводов. " +
                    "Сохрани ему полезное когнитивное усилие: попроси сформулировать собственную гипотезу, 2 альтернативы или скрытое допущение — и опирайся на стандарты Пола–Элдер (ясность, факты, логика, альтернативный взгляд)."

            ToneMode.DIRECT_PUSH -> if (isNightRisk) {
                "НОЧНОЙ РЕЖИМ: бережный фокус на выгрузку мыслей и покой."
            } else {
                "ШТАТНЫЙ НАПОРИСТЫЙ РЕЖИМ: будь честным, собранным, энергичным союзником. Не сюсюкай и не корми дежурными утешениями. " +
                    "Вскрывай когнитивные искажения и отговорки, опирайся на базу знаний и дожимай до одного конкретного WOOP-шага («Если [помеха], то я [действие]»)."
            }
        }
        return if (assessment.changeTalkDetected && assessment.level == RiskLevel.NONE) {
            "$base Пользователь уже проявил импульс к действию (Change Talk) — обязательно закрепи его собственную причину и переведи в конкретный ближайший шаг."
        } else {
            base
        }
    }
}