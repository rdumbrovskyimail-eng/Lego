package com.legode.friedrich.brain

/**
 * Защитный маршрутизатор: работает локально ДО вызова модели, без сети и при сбоях API.
 *
 * Три контура безопасности:
 *  1) локальный детектор (этот файл) — морфологически устойчивые шаблоны русского языка
 *     с учётом отрицаний («не хочу умирать»), бытовых метафор и информационного контекста;
 *  2) режим «после кризиса» (AFTERCARE) — 24 часа после сигнала риска Атлас не давит,
 *     не ставит задач и бережно сверяет безопасность; проактивные СМС сводятся к редким чек-инам;
 *  3) поле `risk` в ответе модели — ловит завуалированный риск, который не поймали шаблоны
 *     (обрабатывается в Brain).
 *
 * Тон: CRISIS_CARE → AFTERCARE → DISTRESS_CALM → NIGHT_WIND_DOWN → MI_EMPATHY → SOCRATIC_COACH → DIRECT_PUSH.
 */
object SafetyGuard {

    /** Сколько часов после сигнала риска действует бережный режим. */
    const val AFTERCARE_HOURS = 24

    enum class RiskLevel {
        NONE,      // штатный режим
        DISTRESS,  // острая тяжесть, паника, слёзы, истощение, режим «после кризиса»
        CRISIS     // риск суицида или самоповреждения
    }

    enum class ToneMode {
        CRISIS_CARE,
        AFTERCARE,
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
        val offloadingDetected: Boolean = false,
        /** Человек прямо попросил контакты помощи — показываем их без участия модели. */
        val helpRequested: Boolean = false,
        /** Действует режим «после кризиса». */
        val aftercare: Boolean = false,
        /** Человек сам сказал, что ему легче / он в безопасности. */
        val reliefDetected: Boolean = false
    ) {
        val suppressPushy: Boolean get() = level != RiskLevel.NONE || aftercare
    }

    // ───────────────────────── Словарь ─────────────────────────

    private const val L = "\\p{L}"
    private const val B = "(?<![\\p{L}\\p{Nd}])"   // начало слова
    private const val E = "(?![\\p{L}\\p{Nd}])"    // конец слова
    private const val GAP = "(?:\\s+\\p{L}+){0,3}\\s+" // до трёх слов между частями фразы
    private const val NEAR = "(?:\\s+\\p{L}+){0,2}\\s+" // до двух слов

    private fun rx(pattern: String): Regex = Regex("$B(?:$pattern)$E")

    /** Желание/намерение (без отрицания перед ним). */
    private const val INTENT =
        "(?<!не )(?:хочу|хочет|хочется|хотелось бы|хотел бы|хотела бы|решил|решила|решаю|планирую|собираюсь|" +
            "пора|лучше бы|лучше|думаю|подумываю|мечтаю|тянет)"

    /** Что именно человек хочет сделать с собой. */
    private const val SELF_END =
        "(?:умереть|помереть|сдохнуть|подохнуть|покончить с собой|покончить со всем|покончить с жизнью|уйти из жизни|" +
            "выпилиться|самоубиться|убить себя|повеситься|утопиться|застрелиться|отравиться|" +
            "вскрыть вены|перерезать вены|не жить|не существовать|" +
            "исчезнуть навсегда|перестать существовать|не просыпаться|уснуть навсегда|заснуть навсегда)"

    /** Явный риск: всегда кризис, независимо от контекста. */
    private val CRISIS_EXPLICIT = listOf(
        rx("$INTENT$NEAR$SELF_END"),
        rx("$INTENT\\s+$SELF_END"),
        rx("поконч$L*\\s+с\\s+собой|поконч$L*\\s+с\\s+жизнью|свести сч[её]ты с жизнью|свед[уё]$L*\\s+сч[её]ты|наложить на себя руки|налож$L*\\s+на себя руки"),
        rx("убь[юё]$L*\\s+себя|убить себя|убила? себя|убиваю себя|убивать себя"),
        rx("повеш(?:усь|усь)|повеситься|повесил(?:ся|ась)|петл$L*\\s+на\\s+ше$L*|утоплюсь|застрелюсь|застрелиться|отравлюсь|выпилюсь|самоубьюсь"),
        rx("вскр(?:ыть|ою|ыл|ыла|ываю)$NEAR?вен$L*|вскр(?:ыть|ою|ыл|ыла|ываю)\\s+вен$L*|перереза$L*\\s+вен$L*|порез$L*\\s+вен$L*|резать\\s+вен$L*"),
        rx("наглота$L*$NEAR?(?:таблет$L*|колес$L*|снотворн$L*|лекарств$L*)|наглота$L*\\s+(?:таблет$L*|колес$L*|снотворн$L*|лекарств$L*)"),
        rx("(?:выпить|выпью|сожрать|сожру|принять|приму|съесть|съем)\\s+(?:все|всю|целую|пачку|упаковку|горсть|блистер)\\s+(?:$L+\\s+)?(?:таблет$L*|снотворн$L*|лекарств$L*|колес$L*)"),
        rx("передоз$L*|овердоз$L*"),
        rx("(?:прыгнуть|спрыгнуть|сброситься|сигануть|выпрыгнуть|шагнуть|броситься|прыгну|спрыгну|сброшусь|выпрыгну|шагну|брошусь)\\s+(?:с|со|из|в|под|вниз с)\\s+(?:$L+\\s+)?(?:крыш$L*|окн$L*|мост$L*|балкон$L*|этаж$L*|высот$L*|поезд$L*|машин$L*|электричк$L*|метро|рельс$L*|многоэтажк$L*)"),
        rx("(?:не хочу|не хочется|нет желания|незачем|не вижу смысла|нет смысла|зачем мне|зачем вообще|больше не могу|не могу больше|не хочу больше)$NEAR?(?:жить|существовать)"),
        rx("(?:не хочу|не хочется|нет желания|незачем|не вижу смысла|нет смысла|зачем мне|зачем вообще|больше не могу|не могу больше|не хочу больше)\\s+(?:жить|существовать)"),
        rx("(?:жить|существовать)\\s+(?:больше\\s+|так\\s+|дальше\\s+|вообще\\s+)?(?:не хочу|не хочется|незачем|не могу|нет смысла|не хочу больше)"),
        rx("уснуть и не проснуться|заснуть и не проснуться|лечь и не проснуться|не проснуться бы|чтобы не проснуться|лучше бы не просыпаться"),
        rx("лучше бы (?:меня не было|я не родил$L*|я не рождал$L*|я умер$L*|я сдох$L*|меня не стало)|чтобы меня не стало|меня не должно быть"),
        rx("без меня (?:всем|им|вам|семье|близким|миру)?\\s*(?:будет|станет|было бы)\\s+(?:только\\s+)?лучше|я (?:всем )?(?:только )?обуза|я всем мешаю жить"),
        rx("прощальн$L*\\s+(?:письм$L*|записк$L*|сообщени$L*|пост$L*)|пишу прощальн$L*"),
        rx("(?:режу|резала|резал|порезала|порезал|порежу|режусь|резаться|царапаю|расцарапываю|жгу|прижигаю)\\s+(?:себя|себе\\s+(?:руки|руку|запяст$L*|бедр$L*|ног$L*|кож$L*))"),
        rx("(?:режу|резать|порезать|порежу)\\s+(?:руки|руку|запяст$L*|бедр$L*|кож$L*)"),
        rx("режусь|резалась|резался|режу себя|самоповрежд$L*|селфхарм$L*|селф харм$L*|делаю себе больно|причиняю себе боль|причинить себе (?:вред|боль)|причиняю себе вред|порезы на (?:руках|запяст$L*|ногах|бедрах)"),
        rx("kill myself|want to die|end my life|suicid$L*|self harm|selfharm|cut myself|hurt myself")
    )

    /** Ключевое слово без явного намерения: кризис, если это не обсуждение статьи/фильма/исследования. */
    private val CRISIS_KEYWORD = listOf(
        rx("суицид$L*|самоубийств$L*|суицидн$L*|роскомнадзорн$L*|выпил$L*ся|мысли о (?:суициде|самоубийстве|том чтобы умереть)|думаю о (?:суициде|самоубийстве)")
    )

    private val INFO_CONTEXT = rx(
        "стать$L*|фильм$L*|книг$L*|сериал$L*|новост$L*|исследован$L*|статистик$L*|профилактик$L*|" +
            "лекци$L*|курсов$L*|реферат$L*|доклад$L*|персонаж$L*|героин$L*|герой$L*|история про|эссе|подкаст$L*"
    )

    // Бытовые метафоры: вырезаются до поиска кризиса, чтобы не было ложных срабатываний.
    private val METAPHORS = listOf(
        rx("убиться веником|убить время|убиваю время|умираю со смеху|умру со смеху|умереть со смеху|сдохнуть со смеху"),
        rx("умираю (?:от|с) (?:скуки|голода|голоду|жары|любопытства)|умру (?:от|с) (?:скуки|голода|голоду|любопытства)|сдохнуть (?:от|с) скуки"),
        rx("смертельно (?:устал$L*|хочется спать|хочу спать)|убью за (?:это|кофе)|убила бы за (?:кофе|сон)|убил бы за (?:кофе|сон)|мозг взрывается|убить готов"),
        rx("спрыгнуть с (?:дивана|кровати|темы|разговора|проекта|диеты|иглы)|покончить с (?:прокрастинацией|долгами|этой задачей|отговорками|ленью|курением|вредной привычкой)")
    )

    private val DISTRESS_PATTERNS = listOf(
        rx("паническ$L* атак$L*|накрыл$L* паник$L*|накрывает паник$L*|паникую|задыха$L*|трясет всего|меня трясет|трясет от страха|сердце (?:выпрыгивает|колотится|бешено)|схожу с ума|сойду с ума"),
        rx("не вывожу|сил (?:вообще |совсем |больше )?нет|нет сил вообще|полное дно|жизнь (?:разрушена|рухнула|кончена)|вс[её] кончено|ничего не осталось|не вижу выхода|выхода нет|безнад[её]ж$L*"),
        rx("рыда$L*|плачу(?!\\s+(?:за|по|налог$L*|кредит$L*|деньг$L*|штраф$L*|аренд$L*|долг$L*|ипотек$L*|\\d))|реву|не могу перестать плакать|истерик$L*|не могу встать с кровати|не встаю с кровати"),
        rx("ненавижу себя|я ничтожество|я никчемн$L*|я ничего не стою|я пустое место|я неудачни$L*|я никому не нуж$L*|никому не нуж(?:ен|на)|я всех подвожу"),
        rx("хочется провалиться|не хочу просыпаться|пустота внутри|мертв$L* внутри|вс[её] бессмысленно|смысла нет ни в чем|смысла нет ни в чём|думаю о смерти|мысли о смерти|одиночество (?:давит|убивает)|хочу исчезнуть|не вижу смысла (?:дальше|продолжать|ни в чем)|смысла продолжать нет")
    )

    private val RESISTANCE_PATTERNS = listOf(
        rx("бесполезно пробовать|все равно не получится|вс[её] равно не получится|ничего не изменится|мне ничего не поможет"),
        rx("отстань|хватит давить|я не готов$L*|не хочу сейчас ничего менять|проще забить|оставь меня"),
        rx("ты не понимаешь|легко говорить|мне слишком тяжело даже пробовать")
    )

    private val CHANGE_TALK_PATTERNS = listOf(
        rx("хочу попробовать|надоело так жить|хочу выбраться|готов$L* сделать|может стоит|попробую сегодня"),
        rx("мне важно|ради себя|не хочу так больше|пора менять|давай план|хочу научиться")
    )

    private val OFFLOADING_PATTERNS = listOf(
        rx("реши за меня|прими решение за меня|просто скажи что выбрать|сделай вс[её] за меня"),
        rx("напиши полностью за меня|подумай за меня|дай готовый ответ без вопросов")
    )

    private val RELIEF_PATTERNS = listOf(
        rx("я в безопасности|мне ничего не угрожает|отпустило|стало легче|полегчало|успокоил$L*|дышать (?:стало )?(?:проще|легче)|спасибо полегчало|уже лучше")
    )

    private val HELP_REQUEST = listOf(
        rx("лини$L* (?:помощи|доверия)|телефон$L* доверия|горяч$L* лини$L*|экстренн$L* помощ$L*|срочн$L* помощ$L*|кризисн$L* лини$L*|куда (?:позвонить|обратиться)|номер$L* помощи|контакт$L* помощи|нужна помощь сейчас|sos")
    )

    // ───────────────────────── Анализ ─────────────────────────

    private fun clean(text: String): String = Norm.looseKey(text)

    private fun stripMetaphors(normalized: String): String {
        if (normalized.isEmpty()) return ""
        var s = normalized
        for (m in METAPHORS) s = m.replace(s, " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Есть ли в тексте сигнал кризиса (для текущего ввода). */
    fun hasCrisisSignal(rawText: String): Boolean {
        val c = stripMetaphors(clean(rawText))
        if (c.isEmpty()) return false
        if (CRISIS_EXPLICIT.any { it.containsMatchIn(c) }) return true
        return CRISIS_KEYWORD.any { it.containsMatchIn(c) } && !INFO_CONTEXT.containsMatchIn(c)
    }

    fun hasDistressSignal(rawText: String): Boolean {
        val c = stripMetaphors(clean(rawText))
        return c.isNotEmpty() && DISTRESS_PATTERNS.any { it.containsMatchIn(c) }
    }

    fun isHelpRequest(rawText: String): Boolean {
        val c = clean(rawText)
        return c.isNotEmpty() && HELP_REQUEST.any { it.containsMatchIn(c) }
    }

    /** Совместимый однострочный вызов (например, для одиночного сообщения или теста). */
    fun assess(rawText: String, inferredStates: Set<String> = emptySet()): Assessment =
        assessTurn(currentTurnText = rawText, recentHistoryText = "", inferredStates = inferredStates)

    /**
     * Полный анализ хода диалога.
     *  • CRISIS — по новому вводу: кризисный ответ показывается без участия модели;
     *  • AFTERCARE — в течение [AFTERCARE_HOURS] после сигнала риска: без давления, с проверкой безопасности
     *    (облегчение не снимает режим сразу — оно лишь смягчает тон);
     *  • DISTRESS — по новому вводу или по недавнему контексту, если новый ответ короткий и без облегчения.
     */
    fun assessTurn(
        currentTurnText: String,
        recentHistoryText: String = "",
        inferredStates: Set<String> = emptySet(),
        isNightRisk: Boolean = false,
        socraticPreferred: Boolean = false,
        aftercareActive: Boolean = false
    ): Assessment {
        val currentRaw = clean(currentTurnText)
        val current = stripMetaphors(currentRaw)
        val history = stripMetaphors(clean(recentHistoryText))
        val relief = currentRaw.isNotEmpty() && RELIEF_PATTERNS.any { it.containsMatchIn(currentRaw) }
        val help = isHelpRequest(currentTurnText)

        // 1. Кризис — по актуальному сообщению пользователя.
        if (current.isNotEmpty() && hasCrisisSignal(currentTurnText)) {
            return Assessment(
                level = RiskLevel.CRISIS,
                reason = "Сигналы острого кризиса или риска самоповреждения.",
                states = inferredStates + "crisis",
                toneMode = ToneMode.CRISIS_CARE,
                helpRequested = help,
                aftercare = true
            )
        }

        // 2. Режим «после кризиса»: никакого напора, даже если человек пишет нейтрально или говорит, что ему легче.
        val crisisRecent = history.isNotEmpty() && hasCrisisSignal(recentHistoryText)
        if (aftercareActive || crisisRecent) {
            return Assessment(
                level = RiskLevel.DISTRESS,
                reason = "Бережный режим после недавнего сигнала риска.",
                states = inferredStates + "distress",
                toneMode = ToneMode.AFTERCARE,
                helpRequested = help,
                aftercare = true,
                reliefDetected = relief
            )
        }

        // 3. Острый дистресс / паника.
        val distressNow = current.isNotEmpty() && DISTRESS_PATTERNS.any { it.containsMatchIn(current) }
        val distressRecent = !relief && current.length < 60 && history.isNotEmpty() &&
            DISTRESS_PATTERNS.any { it.containsMatchIn(history) }

        if (distressNow || distressRecent || help) {
            return Assessment(
                level = RiskLevel.DISTRESS,
                reason = "Острый эмоциональный перегруз или глубокий спад.",
                states = inferredStates + "distress",
                toneMode = ToneMode.DISTRESS_CALM,
                helpRequested = help,
                reliefDetected = relief
            )
        }

        if (current.isEmpty() && history.isEmpty()) {
            val mode = if (isNightRisk) ToneMode.NIGHT_WIND_DOWN else if (socraticPreferred) ToneMode.SOCRATIC_COACH else ToneMode.DIRECT_PUSH
            return Assessment(RiskLevel.NONE, states = inferredStates, toneMode = mode)
        }

        val resistance = current.isNotEmpty() && RESISTANCE_PATTERNS.any { it.containsMatchIn(current) }
        val changeTalk = current.isNotEmpty() && CHANGE_TALK_PATTERNS.any { it.containsMatchIn(current) }
        val offloading = current.isNotEmpty() && OFFLOADING_PATTERNS.any { it.containsMatchIn(current) }

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
            offloadingDetected = offloading,
            reliefDetected = relief
        )
    }

    // ───────────────────────── Статичные ответы (работают офлайн) ─────────────────────────

    /** Проверенные линии помощи (Германия, Россия, остальной мир). */
    const val RESOURCES =
        "- **112** — экстренная помощь (ЕС, Германия, Россия): если есть непосредственная опасность или ты уже что-то с собой сделал(а)\n" +
            "- **0800 111 0 111**, **0800 111 0 222** или **116 123** — TelefonSeelsorge, Германия (круглосуточно, бесплатно, анонимно)\n" +
            "- **8-800-333-44-34** — кризисная линия доверия, Россия (бесплатно)\n" +
            "- **8-800-2000-122** — телефон доверия для детей, подростков и родителей, Россия\n" +
            "- Другая страна — **findahelpline.com** (бесплатные линии по стране)"

    /** Короткая строка контактов для коротких сообщений и директив. */
    const val RESOURCES_SHORT = "112 · 0800 111 0 111 / 116 123 (DE) · 8-800-333-44-34 (RU) · findahelpline.com"

    /** Мгновенный ответ при CRISIS (работает и офлайн, и если модель заблокировала ответ). */
    fun crisisResponse(userName: String): List<String> {
        val who = if (userName.isBlank()) "" else "$userName, "
        return listOf(
            "${who}я с тобой. То, что ты написал(а), очень серьёзно, и мне важно, чтобы ты был(а) в безопасности. Сейчас не нужно ничего решать и ничего доказывать.",
            "Пожалуйста, не оставайся с этим один на один. Позвони или напиши человеку, которому доверяешь, или специалисту прямо сейчас:\n$RESOURCES",
            "Если рядом никого нет — останься здесь, со мной. Убери подальше то, чем можешь себе навредить, сделай один медленный выдох и напиши мне: ты сейчас в безопасности?"
        )
    }

    fun crisisQuickReplies(): List<String> = listOf(
        "Я в безопасности, но мне больно",
        "Мне нужна помощь сейчас",
        "Хочу просто поговорить"
    )

    /** Ответ на прямую просьбу дать контакты помощи. */
    fun resourcesResponse(userName: String): List<String> {
        val who = if (userName.isBlank()) "" else "$userName, "
        return listOf(
            "${who}вот куда можно обратиться прямо сейчас:\n$RESOURCES",
            "Я тоже здесь. Если хочешь — расскажи, что происходит, в одной строке. И скажи: ты сейчас в безопасности?"
        )
    }

    fun resourcesQuickReplies(): List<String> = listOf(
        "Я в безопасности",
        "Мне очень тяжело",
        "Хочу выговориться"
    )

    /** Бережный чек-ин после кризиса (используется и офлайн). */
    fun aftercareCheckIn(userName: String): String {
        val who = if (userName.isBlank()) "" else "$userName, "
        return "${who}я помню, как тяжело тебе было. Без вопросов про задачи — просто хочу знать: как ты сейчас и в безопасности ли? " +
            "Можно ответить одним словом. Если накроет — $RESOURCES_SHORT."
    }

    fun aftercareQuickReplies(): List<String> = listOf("Я в безопасности", "Всё ещё тяжело", "Нужны контакты помощи")

    /** Запасной ответ, если модель вернула SAFETY/BLOCKED на тяжёлое эмоциональное сообщение. */
    fun blockedFallback(level: RiskLevel, userName: String): List<String> {
        if (level == RiskLevel.CRISIS) return crisisResponse(userName)
        val who = if (userName.isBlank()) "" else "$userName, "
        return listOf(
            "${who}я вижу, что внутри сейчас очень тяжело. Давай не будем торопиться и ломать себя.",
            "Сделай одну простую вещь для тела: опусти плечи, медленный вдох на 4 счёта и долгий выдох на 6. Потом напиши одной строкой, что давит сильнее всего прямо сейчас. Если становится опасно — $RESOURCES_SHORT."
        )
    }

    /** Директива модели, которая точно настраивает стратегию Атласа на этот ход. */
    fun tacticalDirective(assessment: Assessment, isNightRisk: Boolean): String {
        val base = when (assessment.toneMode) {
            ToneMode.CRISIS_CARE ->
                "КРИТИЧЕСКИЙ ПРОТОКОЛ БЕЗОПАСНОСТИ: запрещены напор, критика, задания, WOOP, «ловушки ума» и философия борьбы. " +
                    "Говори тепло, просто и коротко. Прямо спроси, в безопасности ли человек сейчас, предложи связаться с близким " +
                    "или кризисной линией ($RESOURCES_SHORT), удерживай контакт. Поставь risk = \"crisis\"."

            ToneMode.AFTERCARE ->
                "РЕЖИМ ПОСЛЕ КРИЗИСА: недавно человеку было очень плохо (возможны мысли о смерти). Никакого давления, заданий, челленджей, " +
                    "сократических допросов и разбора «ловушек». Сначала тепло откликнись на сказанное, мягко сверь безопасность, " +
                    "если уместно — напомни про близких и линии помощи ($RESOURCES_SHORT). Предлагай только опору: дыхание, заземление, " +
                    "выговориться, маленький заботливый шаг для тела. " +
                    (if (assessment.reliefDetected) "Человек сказал, что ему легче — признай это без эйфории и не форсируй переход к делам. " else "") +
                    "Если снова звучат мысли о смерти — risk = \"crisis\"."

            ToneMode.DISTRESS_CALM ->
                "РЕЖИМ ТВЁРДОЙ ОПОРЫ: человек в острой боли, панике или истощении. Отмени прессинг и разбор ошибок. " +
                    "Признай тяжесть без жалости и без токсичного позитива, дай 1 технику стабилизации тела или мысли из базы знаний " +
                    "и предложи микро-шаг на 60 секунд. Если звучат мысли о смерти или самоповреждении — прямо и бережно спроси о безопасности и поставь risk = \"crisis\"."

            ToneMode.NIGHT_WIND_DOWN ->
                "НОЧНОЙ РЕЖИМ: сейчас поздно. Не накручивай и не требуй подвигов. " +
                    "Останови ночную прокрутку мыслей, помоги выгрузить тревоги до утра, снять напряжение и уйти в сон."

            ToneMode.MI_EMPATHY ->
                "РЕЖИМ МОТИВАЦИОННОГО ИНТЕРВЬЮ (OARS): у человека сопротивление и амбивалентность — не спорь в лоб и не читай мораль. " +
                    "Отзеркаль сомнение и усталость, уважай свободу выбора и задай один открытый вопрос, чтобы человек сам назвал, что ему важно."

            ToneMode.SOCRATIC_COACH ->
                "СОКРАТИЧЕСКИЙ РЕЖИМ (АНТИ-ОФФЛОАДИНГ): не решай задачу за человека и не выдавай сразу готовых выводов. " +
                    "Попроси его гипотезу, 2 альтернативы или скрытое допущение — и опирайся на стандарты Пола–Элдер (ясность, факты, логика, альтернативный взгляд)."

            ToneMode.DIRECT_PUSH -> if (isNightRisk) {
                "НОЧНОЙ РЕЖИМ: бережный фокус на выгрузку мыслей и покой."
            } else {
                "ШТАТНЫЙ РЕЖИМ: будь честным, собранным, энергичным союзником. Не сюсюкай, но и не стыди. " +
                    "Дави на отговорки, а не на человека: вскрывай когнитивные искажения и помоги дойти до одного конкретного WOOP-шага " +
                    "(«Если [помеха], то я [действие]»). При первых признаках боли или бессилия — сбавь напор."
            }
        }
        return if (assessment.changeTalkDetected && assessment.level == RiskLevel.NONE) {
            "$base Пользователь уже проявил импульс к действию (Change Talk) — закрепи его собственную причину и переведи в ближайший шаг."
        } else {
            base
        }
    }
}