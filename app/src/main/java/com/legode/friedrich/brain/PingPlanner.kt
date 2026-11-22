package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.db.SessionEntity
import kotlin.random.Random

/** План проактивного JITAI-вмешательства от Атласа. */
data class PingPlan(
    val kind: String,
    val task: String,
    val askedRef: String = "",
    val topicIds: List<String> = emptyList(),
    val states: Set<String> = emptySet(),
    val preferredTones: Set<String> = emptySet(),
    val preferredKinds: Set<String> = emptySet(),
    val gentleOnly: Boolean = false
)

/** Расписание «смс» с JITAI-адаптацией к восприимчивости пользователя. */
object PingScheduler {

    data class Level(val title: String, val minMinutes: Int, val maxMinutes: Int, val about: String)

    val INTENSITY: List<Level> = listOf(
        Level("Редко", 150, 300, "каждые 2,5–5 часов"),
        Level("Спокойно", 60, 120, "каждые 1–2 часа"),
        Level("Бодро", 30, 65, "каждые 30–65 минут"),
        Level("Напористо ⚡", 15, 38, "каждые 15–38 минут"),
        Level("Плотный режим 🔥", 6, 18, "каждые 6–18 минут")
    )

    fun level(index: Int): Level = INTENSITY[index.coerceIn(0, INTENSITY.lastIndex)]

    fun inQuiet(minuteOfDay: Int, s: Settings): Boolean {
        val a = s.quietStartMin
        val b = s.quietEndMin
        if (a == b) return false
        return if (a < b) minuteOfDay in a until b else (minuteOfDay >= a || minuteOfDay < b)
    }

    fun endOfQuiet(at: Long, s: Settings): Long {
        val dt = Timex.dateTime(at)
        val minute = dt.hour * 60 + dt.minute
        val endH = s.quietEndMin / 60
        val endM = s.quietEndMin % 60
        var end = dt.withHour(endH).withMinute(endM).withSecond(0).withNano(0)
        if (minute >= s.quietEndMin) end = end.plusDays(1)
        return end.atZone(Timex.zone()).toInstant().toEpochMilli()
    }

    fun next(now: Long, s: Settings, unanswered: Int, rnd: Random): Long {
        val lv = level(s.pingIntensity)
        val base = rnd.nextInt(lv.minMinutes, lv.maxMinutes + 1)
        val factor = when {
            unanswered >= 8 -> 2.4
            unanswered >= 4 -> 1.45
            else -> 1.0
        }
        val jitterSec = rnd.nextInt(0, 45)
        var at = now + (base * factor * 60_000L).toLong() + jitterSec * 1000L
        if (inQuiet(Timex.minuteOfDay(at), s)) {
            at = endOfQuiet(at, s) + rnd.nextInt(1, 20) * 60_000L
        }
        return at
    }
}

/**
 * JITAI-планировщик (Just-In-Time Adaptive Interventions):
 * учитывает 3-дневную траекторию состояния, телеметрию отклика (когда человек реально отвечает),
 * падение FSRS Retrievability карточек и якорей, сроки прогнозов Brier Score и активные WOOP-планы.
 */
class PingPlanner(private val repo: Repository, private val runtime: RuntimeState) {

    suspend fun plan(
        session: SessionEntity,
        s: Settings,
        now: Long,
        rnd: Random,
        forcedKind: String?,
        aftercare: Boolean = false
    ): PingPlan {
        if (aftercare) {
            return build(SAFE_CHECKIN, session, repo.masteryMap(), repo.profile().lastMood, now, rnd, 0, 0, Timex.isNightRisk(now), false)
        }
        val raw = planRaw(session, s, now, rnd, forcedKind)
        // Низкое состояние: никаких «дожимов» — любое СМС только бережное.
        return if (isLowTrajectory() && !raw.gentleOnly) {
            raw.copy(gentleOnly = true, preferredTones = setOf("calm", "deep"), preferredKinds = raw.preferredKinds + "practice")
        } else {
            raw
        }
    }

    private suspend fun isLowTrajectory(): Boolean {
        val recentDaily = repo.dailyDao.recent(3).filter { it.moodCount > 0 }
        val avg = if (recentDaily.isEmpty()) 0.0
        else recentDaily.sumOf { it.moodSum }.toDouble() / recentDaily.sumOf { it.moodCount }.coerceAtLeast(1)
        return (avg in 0.1..4.2) || (repo.profile().lastMood in 1..4)
    }

    private suspend fun planRaw(session: SessionEntity, s: Settings, now: Long, rnd: Random, forcedKind: String?): PingPlan {
        val today = Timex.today()
        val hour = Timex.dateTime(now).hour
        val night = Timex.isNightRisk(now)
        val mastery = repo.masteryMap()
        val profile = repo.profile()
        val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
        val lastUserAt = repo.messageDao.lastUserAt() ?: 0L
        val silentDays = if (lastUserAt == 0L) 0 else ((now - lastUserAt) / Srs.DAY).toInt()

        // 3-дневная траектория настроения для раннего перехвата сползания.
        val recentDaily = repo.dailyDao.recent(3).filter { it.moodCount > 0 }
        val avgMood3d = if (recentDaily.isEmpty()) 0.0
        else recentDaily.sumOf { it.moodSum }.toDouble() / recentDaily.sumOf { it.moodCount }.coerceAtLeast(1)
        val lowTrajectory = (avgMood3d in 0.1..4.2) || (profile.lastMood in 1..4)

        // Проверка восприимчивости за последние 14 дней: в текущем окне времени человек обычно отвечает?
        val bucketPings = repo.pingDao.since(now - 14 * Srs.DAY).filter { p ->
            val ph = Timex.hourOfDay(p.firedAt)
            when (hour) {
                in 6..11 -> ph in 6..11
                in 12..17 -> ph in 12..17
                else -> ph >= 18 || ph < 6
            }
        }
        val lowReceptivityNow = bucketPings.size >= 4 &&
            bucketPings.count { it.answered }.toDouble() / bucketPings.size < 0.2

        if (forcedKind != null) {
            return build(forcedKind, session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, night, lowReceptivityNow)
        }

        if (night) {
            return build("night_calm", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, true, lowReceptivityNow)
        }

        if (runtime.morningDay != today && hour in 5..11) {
            runtime.morningDay = today
            return build("morning", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false, lowReceptivityNow)
        }

        if (silentDays >= 2 && rnd.nextInt(100) < 65) {
            return build("comeback", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false, lowReceptivityNow)
        }

        val quietStartHour = s.quietStartMin / 60
        val eveningFrom = if (s.quietStartMin == s.quietEndMin) 21 else ((quietStartHour + 24 - 2) % 24)
        if (runtime.eveningDay != today && hour >= 19 && hour >= eveningFrom && repo.today(now).userMsgs > 0) {
            runtime.eveningDay = today
            return build("evening", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false, lowReceptivityNow)
        }

        if (unanswered >= 3 && !lowTrajectory && rnd.nextInt(100) < 40) {
            return build("nudge", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false, lowReceptivityNow)
        }

        val weights = LinkedHashMap<String, Int>()
        val dueAnchors = repo.anchorDao.dueCount(now)
        val dueCards = repo.knowledgeCardDao.dueCount(now)
        val duePreds = repo.predictionDao.due(now, 3).size
        val dueDecisions = repo.decisionDao.dueForReview(now, 3).size

        if (dueAnchors > 0) weights["anchor_due"] = 18 + minOf(dueAnchors, 8) * 2
        if (dueCards > 0 && !lowTrajectory) weights["card_due"] = 22 + minOf(dueCards, 8) * 2
        if ((duePreds > 0 || dueDecisions > 0) && !lowTrajectory) weights["prediction_check"] = 24
        // При низком состоянии — никаких «дожимов», дуэлей и охоты на ловушки: только опора.
        if (session.planId != 0L && !lowTrajectory) weights["plan_push"] = if (lowReceptivityNow) 18 else 34
        if (repo.trapDao.activeCount() > 0 && !lowTrajectory) weights["trap_hunt"] = 15
        if (lowTrajectory) weights["mood_rescue"] = 32
        if (!lowTrajectory) weights["breakthrough"] = 24
        weights["philosophy"] = 16
        weights["mental_model"] = if (lowTrajectory) 8 else 22
        if (!lowTrajectory) weights["debias_duel"] = if (lowReceptivityNow) 6 else 18
        if (!lowTrajectory) weights["cbt_check"] = 16
        weights["calm_reset"] = if (lowTrajectory) 18 else 6
        weights["halt_check"] = if (Timex.circadianPhase(now) == Timex.CircadianPhase.AFTERNOON_DIP) 14 else 5

        val recent = repo.pingDao.recentKinds(2)
        for (k in recent) weights[k]?.let { weights[k] = maxOf(1, it / 5) }

        var roll = rnd.nextInt(weights.values.sum().coerceAtLeast(1))
        var chosen = if (lowTrajectory) "calm_reset" else "breakthrough"
        for ((k, w) in weights) {
            if (roll < w) {
                chosen = k
                break
            }
            roll -= w
        }
        return build(chosen, session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false, lowReceptivityNow)
    }

    private suspend fun build(
        kind: String,
        session: SessionEntity,
        mastery: Map<String, Int>,
        lastMood: Int,
        now: Long,
        rnd: Random,
        unanswered: Int,
        silentDays: Int,
        night: Boolean,
        lowReceptivity: Boolean
    ): PingPlan {
        val focus = Curriculum.focus(mastery, 4)
        val focusIntel = Curriculum.focusMental(mastery, 4)
        val touched = repo.skillDao.all().filter { it.exposures > 0 }.map { it.skillId }.toSet()
        val weak = Curriculum.weakest(mastery, touched, 3)

        fun pickSkill(preferMental: Boolean = false): Topic {
            val pool = ArrayList<Topic>()
            if (preferMental) pool.addAll(focusIntel) else pool.addAll(focus)
            pool.addAll(weak)
            if (pool.isEmpty()) pool.addAll(Curriculum.topics)
            return pool[rnd.nextInt(pool.size)]
        }

        val brevityHint = if (lowReceptivity) {
            " ВАЖНО: в это время суток пользователь редко отвечает на длинные тексты — сделай СМС сверхкоротким (2–3 строки) с максимально лёгкой кнопкой быстрого ответа."
        } else ""

        when (kind) {
            SAFE_CHECKIN -> return PingPlan(
                kind = kind,
                task = "БЕРЕЖНЫЙ ЧЕК-ИН ПОСЛЕ ТЯЖЁЛОГО РАЗГОВОРА. Недавно человеку было очень плохо (возможны мысли о смерти). " +
                    "Одним коротким тёплым сообщением (2–3 строки) спроси, как он сейчас и в безопасности ли. " +
                    "Без заданий, напора, философии и разбора ошибок. Напомни, что можно написать сюда или позвонить: " +
                    "${SafetyGuard.RESOURCES_SHORT}. Кнопки quick: «Я в безопасности», «Всё ещё тяжело», «Нужны контакты помощи».",
                topicIds = listOf("breathing_reset", "grounding_54321"),
                states = setOf("anxiety", "depression"),
                preferredTones = setOf("calm"),
                preferredKinds = setOf("practice"),
                gentleOnly = true
            )

            "morning" -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = kind,
                    task = "УТРЕННИЙ ЗАРЯД. Задай твёрдый вектор дня (свет, вода, спина, старт), подвяжи 1 сильную мысль/модель из базы и попроси сформулировать ОДИН WOOP-якорь дня («Что сделаю, если потянет слиться?»).$brevityHint",
                    topicIds = listOf("halt_foundation", "strat_woop_mcii", sk.id),
                    states = setOf("apathy", "procrastination", "deadend"),
                    preferredTones = setOf("push", "firm"),
                    preferredKinds = setOf("breakthrough", "philosophy", "model")
                )
            }

            "evening" -> {
                val d = repo.today(now)
                return PingPlan(
                    kind = kind,
                    task = "ВЕЧЕРНЯЯ РАЗГРУЗКА И ИТОГ. Сегодня: сообщений ${d.userMsgs}, шагов закрыто ${d.stepsDone}, карточек FSRS ${d.cardsReviewed}, фокуса ${d.focusMinutes} мин, срывов ${d.slips}. Помоги увидеть хотя бы 1–2 твёрдых факта дня, выгрузить тревоги из головы и спокойно закрыть день.$brevityHint",
                    topicIds = listOf("night_shutdown", "learn_ultradian"),
                    states = setOf("night", "rumination", "guilt"),
                    preferredTones = setOf("calm", "deep"),
                    preferredKinds = setOf("practice", "philosophy"),
                    gentleOnly = true
                )
            }

            "night_calm" -> return PingPlan(
                kind = kind,
                task = "НОЧНОЕ УСПОКОЕНИЕ. Сейчас ночь. Запрещён прессинг. Напомни правило ночного мозга, дай быструю технику выгрузки мыслей или дыхания 4–7–8 с паузами.",
                topicIds = listOf("night_shutdown", "breathing_reset"),
                states = setOf("night", "rumination", "anxiety"),
                preferredTones = setOf("calm"),
                preferredKinds = setOf("practice", "cbt"),
                gentleOnly = true
            )

            "comeback" -> return PingPlan(
                kind = kind,
                task = "ВОЗВРАЩЕНИЕ ИЗ ТИШИНЫ. Человек не выходил на связь $silentDays дн. Напиши прямо, по-товарищески, без стыда и морализаторства: спроси, как он от 1 до 10, и предложи одну кнопку входа.",
                topicIds = listOf("meaning_loneliness", "action_activation"),
                states = setOf("loneliness", "depression", "apathy"),
                preferredTones = setOf("calm", "firm"),
                preferredKinds = setOf("breakthrough", "cbt")
            )

            "nudge" -> return PingPlan(
                kind = kind,
                task = "МЯГКИЙ ЧЕК-ИН. Человек давно не отвечает — без упрёков и без счёта пропущенных сообщений. Коротко, тепло и с живым характером спроси, что сейчас ближе: усталость, перегруз или избегание одного дела. Дай 3 лёгкие кнопки.$brevityHint",
                topicIds = listOf("action_120sec"),
                states = setOf("procrastination", "apathy", "deadend"),
                preferredTones = setOf("firm", "calm"),
                preferredKinds = setOf("breakthrough", "cbt")
            )

            "anchor_due" -> {
                val due = repo.anchorDao.due(now, 6)
                if (due.isEmpty()) return build("breakthrough", session, mastery, lastMood, now, rnd, unanswered, silentDays, night, lowReceptivity)
                val a = due[rnd.nextInt(due.size)]
                return PingPlan(
                    kind = kind,
                    task = "ПОДКРЕПЛЕНИЕ ЯКОРЯ (FSRS). Верни личный якорь A${a.id}: «${a.phrase}» (${a.why}). Проверь: сработала ли эта опора на деле или где мозг пытается её обойти? В asked поставь \"A${a.id}\".$brevityHint",
                    askedRef = "A${a.id}",
                    states = setOf("deadend", "anxiety", "depression"),
                    preferredTones = setOf("firm", "deep")
                )
            }

            "card_due" -> {
                val due = repo.knowledgeCardDao.due(now, 6)
                if (due.isEmpty()) return build("mental_model", session, mastery, lastMood, now, rnd, unanswered, silentDays, night, lowReceptivity)
                val c = due[rnd.nextInt(due.size)]
                return PingPlan(
                    kind = kind,
                    task = "АКТИВНОЕ ИЗВЛЕЧЕНИЕ ИЗ ВТОРОЙ ПАМЯТИ (FSRS). Задай пользователю вопрос карточки K${c.id} БЕЗ спойлера ответа (не пиши back целиком, пусть сначала вспомнит или применит сам!): «${c.front}»" +
                        (if (c.hint.isNotBlank()) " (лёгкий намёк при надобности: ${c.hint})" else "") +
                        ". В asked обязательно поставь \"K${c.id}\".$brevityHint",
                    askedRef = "K${c.id}",
                    topicIds = if (c.skillId.isNotEmpty()) listOf(c.skillId) else listOf("learn_retrieval"),
                    states = setOf("learning", "logic"),
                    preferredTones = setOf("firm", "deep"),
                    preferredKinds = setOf("learning", "model")
                )
            }

            "prediction_check" -> {
                val preds = repo.predictionDao.due(now, 3)
                val decs = repo.decisionDao.dueForReview(now, 3)
                if (preds.isEmpty() && decs.isEmpty()) {
                    return build("mental_model", session, mastery, lastMood, now, rnd, unanswered, silentDays, night, lowReceptivity)
                }
                val text = if (preds.isNotEmpty()) {
                    val p = preds.first()
                    "ПРОВЕРКА ПРОГНОЗА (Brier Score). Подошёл срок проверить твой прошлый прогноз: «${p.question}» (ты ставил ${p.probabilityPct}%). Спроси прямо: сбылось или нет, и что реально определило исход (отделяя процесс от удачи)?"
                } else {
                    val d = decs.first()
                    "РЕВИЗИЯ РЕШЕНИЯ. Пора оглянуться на решение «${d.title}» (выбор: ${d.chosenOption}, уверенность ${d.confidencePct}%, Pre-mortem: ${d.preMortem}). Спроси, что сбылось на практике и оправдались ли предохранители."
                }
                return PingPlan(
                    kind = kind,
                    task = "$text$brevityHint",
                    topicIds = listOf("dec_calibration", "dec_premortem"),
                    states = setOf("decision", "logic"),
                    preferredTones = setOf("deep", "firm"),
                    preferredKinds = setOf("model", "bias")
                )
            }

            "plan_push" -> {
                val steps = repo.planDao.steps(session.planId).filter { it.streak < 1 }
                if (steps.isEmpty()) return build("breakthrough", session, mastery, lastMood, now, rnd, unanswered, silentDays, night, lowReceptivity)
                val st = steps.first()
                val woopHint = if (st.ifThenRule.isNotBlank()) " Напомни его же правило: «${st.ifThenRule}»." else ""
                return PingPlan(
                    kind = kind,
                    task = "ДОЖИМ WOOP-ШАГА. Напомни про активный шаг S${st.id}: «${st.step}»" +
                        (if (st.why.isNotBlank()) " (${st.why})" else "") + woopHint +
                        (if (st.skippedCount > 0) " Это уже откладывалось ${st.skippedCount} раз(а) — урежь порог входа до 2 минут прямо сейчас." else " Спроси статус или запусти спринт.") +
                        " В asked обязательно поставь \"S${st.id}\".$brevityHint",
                    askedRef = "S${st.id}",
                    topicIds = listOf("action_120sec", "strat_woop_mcii"),
                    states = setOf("procrastination", "deadend", "apathy"),
                    preferredTones = setOf("push"),
                    preferredKinds = setOf("breakthrough")
                )
            }

            "trap_hunt" -> {
                val traps = repo.trapDao.top(5)
                if (traps.isEmpty()) return build("cbt_check", session, mastery, lastMood, now, rnd, unanswered, silentDays, night, lowReceptivity)
                val t = traps[rnd.nextInt(traps.size)]
                return PingPlan(
                    kind = kind,
                    task = "ОХОТА НА ЛОВУШКУ УМА. Пользователь уже ${t.times} раз(а) проваливался в мысль «${t.thought}». Подкинь живую проверку или вопрос на перенос, чтобы поймать эту ловушку на горячем и закрепить антидот («${t.antidote}»).$brevityHint",
                    topicIds = if (t.skillId.isNotEmpty()) listOf(t.skillId) else listOf("cbt_distortions"),
                    states = setOf("guilt", "depression", "anxiety", "failure"),
                    preferredTones = setOf("firm", "push"),
                    preferredKinds = setOf("cbt", "bias")
                )
            }

            "mood_rescue" -> return PingPlan(
                kind = kind,
                task = "ВЫТЯГИВАНИЕ ИЗ СПАДА. Последнее состояние пользователя низкое ($lastMood/10). Без дешёвого оптимизма: дай 1 точную мысль + 1 микро-шаг для тела или среды (не тяжелее 2 минут), чтобы остановить сползание.$brevityHint",
                topicIds = listOf("halt_foundation", "action_activation", "grounding_54321"),
                states = setOf("depression", "burnout", "apathy", "deadend"),
                preferredTones = setOf("firm", "calm"),
                preferredKinds = setOf("practice", "cbt", "breakthrough"),
                gentleOnly = lastMood in 1..2
            )

            "mental_model" -> {
                val sk = pickSkill(preferMental = true)
                return PingPlan(
                    kind = kind,
                    task = "ТРЕНИРОВКА ИНТЕЛЛЕКТУАЛЬНОЙ МОДЕЛИ. Возьми ментальную модель из базы знаний (первые принципы, 2-й порядок, петли Медоуз, Байес, Гудхарт, асимметрия Талеба, стимулы Мангера). Не просто перескажи — задай 1 острый прикладной вопрос: где именно в жизни/работе пользователя эта модель скрыто работает прямо сейчас? Отметь навык в skills.$brevityHint",
                    topicIds = listOf(sk.id, "sys_feedback_loops", "dec_base_rate"),
                    states = setOf("decision", "systems", "logic"),
                    preferredTones = setOf("deep", "firm"),
                    preferredKinds = setOf("model", "learning")
                )
            }

            "debias_duel" -> {
                val sk = pickSkill(preferMental = true)
                return PingPlan(
                    kind = kind,
                    task = "ДЕБАИСИНГ-ДУЭЛЬ. Брось пользователю короткую реалистичную когнитивную задачу или провокацию на ошибку мышления (ошибка выжившего, базовая ставка, ложная дихотомия, Consider-the-Opposite, Post Hoc) — пусть он сам найдёт подвох, без подсказки в лоб!$brevityHint",
                    topicIds = listOf("logic_steelman", "logic_causation", sk.id),
                    states = setOf("logic", "decision"),
                    preferredTones = setOf("push", "firm"),
                    preferredKinds = setOf("bias", "model")
                )
            }

            "philosophy" -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = kind,
                    task = "ФИЛОСОФСКИЙ УДАР В ЦЕЛЬ. Возьми сильную философскую мысль из базы знаний, раскрой механику в 2 строках и задай 1 острый вопрос на осмысление.$brevityHint",
                    topicIds = listOf(sk.id, "stoic_control", "meaning_why"),
                    states = setOf("deadend", "depression", "failure", "anxiety"),
                    preferredTones = setOf("deep", "firm"),
                    preferredKinds = setOf("philosophy")
                )
            }

            "cbt_check" -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = kind,
                    task = "КПТ-ПРОВЕРКА РЕАЛЬНОСТИ. Подними когнитивное искажение или самообман из базы и спроси прямо, не крутится ли эта пластинка прямо сейчас.$brevityHint",
                    topicIds = listOf("cbt_distortions", "cbt_abc", sk.id),
                    states = setOf("anxiety", "guilt", "procrastination", "failure"),
                    preferredTones = setOf("firm", "push"),
                    preferredKinds = setOf("cbt", "bias")
                )
            }

            "calm_reset" -> return PingPlan(
                kind = kind,
                task = "СБРОС НАПРЯЖЕНИЯ ДНЯ. Короткий 60-секундный соматический или дыхательный пит-стоп из базы знаний (вздох, челюсть/плечи, панорамный взгляд).$brevityHint",
                topicIds = listOf("breathing_reset", "grounding_54321"),
                states = setOf("anxiety", "burnout", "panic"),
                preferredTones = setOf("calm"),
                preferredKinds = setOf("practice"),
                gentleOnly = true
            )

            "halt_check" -> return PingPlan(
                kind = kind,
                task = "ЧЕК-ИН ТЕЛА (HALT). Быстрый аудит за 30 секунд: еда, вода, воздух, экранное переутомление — и 1 микро-действие.$brevityHint",
                topicIds = listOf("halt_foundation", "action_dopamine"),
                states = setOf("burnout", "apathy", "depression"),
                preferredTones = setOf("firm", "push"),
                preferredKinds = setOf("practice", "breakthrough")
            )

            else -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = "breakthrough",
                    task = "НАПОРИСТЫЙ МИКРО-ВЫЗОВ. Сломай инерцию дня через механику из базы знаний (120 секунд, уродливый черновик, 1 кв. метр, 1 страшный звонок) с чётким отчётом после.$brevityHint",
                    topicIds = listOf("action_120sec", "action_activation", sk.id),
                    states = setOf("procrastination", "apathy", "deadend"),
                    preferredTones = setOf("push"),
                    preferredKinds = setOf("breakthrough")
                )
            }
        }
    }

    companion object {
        /** Бережная проверка безопасности в режиме «после кризиса». */
        const val SAFE_CHECKIN = "safe_checkin"
    }
}