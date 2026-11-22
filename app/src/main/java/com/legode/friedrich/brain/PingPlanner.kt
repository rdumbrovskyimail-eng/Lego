package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.db.SessionEntity
import kotlin.random.Random

/** План проактивного «СМС» от Атласа. */
data class PingPlan(
    val kind: String,
    /** Директива модели. */
    val task: String,
    /** Ссылка на проверяемый элемент (A12 / S7), чтобы зафиксировать отклик. */
    val askedRef: String = "",
    /** id протоколов из Curriculum для шпаргалки. */
    val topicIds: List<String> = emptyList(),
    /** Состояния и фильтры для RAG-выборки из KnowledgeBase. */
    val states: Set<String> = emptySet(),
    val preferredTones: Set<String> = emptySet(),
    val preferredKinds: Set<String> = emptySet(),
    /** true, если нужен бережный успокаивающий тон (ночь, тяжёлый спад). */
    val gentleOnly: Boolean = false
)

/** Расписание «смс»: чистые функции вычисления следующего момента контакта. */
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

    /** Ближайший момент после [at], когда тихие часы закончились. */
    fun endOfQuiet(at: Long, s: Settings): Long {
        val dt = Timex.dateTime(at)
        val minute = dt.hour * 60 + dt.minute
        val endH = s.quietEndMin / 60
        val endM = s.quietEndMin % 60
        var end = dt.withHour(endH).withMinute(endM).withSecond(0).withNano(0)
        if (minute >= s.quietEndMin) end = end.plusDays(1)
        return end.atZone(Timex.zone()).toInstant().toEpochMilli()
    }

    /**
     * Время следующего «смс».
     * @param unanswered сколько последних пингов остались без ответа (игнор чуть увеличивает паузу, но Атлас не исчезает).
     */
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
 * Умный психо-эмоциональный планировщик:
 * подбирает содержание СМС в зависимости от времени суток, тишины пользователя,
 * последних оценок состояния (mood), активного плана и ловушек ума.
 */
class PingPlanner(private val repo: Repository, private val runtime: RuntimeState) {

    suspend fun plan(session: SessionEntity, s: Settings, now: Long, rnd: Random, forcedKind: String?): PingPlan {
        val today = Timex.today()
        val hour = Timex.dateTime(now).hour
        val night = Timex.isNightRisk(now)
        val mastery = repo.masteryMap()
        val profile = repo.profile()
        val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
        val lastUserAt = repo.messageDao.lastUserAt() ?: 0L
        val silentDays = if (lastUserAt == 0L) 0 else ((now - lastUserAt) / Srs.DAY).toInt()

        if (forcedKind != null) {
            return build(forcedKind, session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, night)
        }

        // Ночное окно (если тихие часы отключены): только успокоение, никакого агрессивного прессинга.
        if (night) {
            return build("night_calm", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, true)
        }

        // Утренний боевой заряд (один раз в день).
        if (runtime.morningDay != today && hour in 5..11) {
            runtime.morningDay = today
            return build("morning", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false)
        }

        // Возврат пропавшего пользователя.
        if (silentDays >= 2 && rnd.nextInt(100) < 65) {
            return build("comeback", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false)
        }

        // Вечерняя разгрузка и фиксация побед дня.
        val quietStartHour = s.quietStartMin / 60
        val eveningFrom = if (s.quietStartMin == s.quietEndMin) 21 else ((quietStartHour + 24 - 2) % 24)
        if (runtime.eveningDay != today && hour >= 19 && hour >= eveningFrom && repo.today(now).userMsgs > 0) {
            runtime.eveningDay = today
            return build("evening", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false)
        }

        // Напористый чек-ин при молчании.
        if (unanswered >= 3 && rnd.nextInt(100) < 40) {
            return build("nudge", session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false)
        }

        // Взвешенный выбор интервенции под состояние пользователя.
        val weights = LinkedHashMap<String, Int>()
        val dueAnchors = repo.anchorDao.dueCount(now)
        if (dueAnchors > 0) weights["anchor_due"] = 20 + minOf(dueAnchors, 10) * 2
        if (session.planId != 0L) weights["plan_push"] = 36
        if (repo.trapDao.activeCount() > 0) weights["trap_hunt"] = 16
        if (profile.lastMood in 1..4) weights["mood_rescue"] = 28
        weights["breakthrough"] = 26
        weights["philosophy"] = 20
        weights["cbt_check"] = 18
        weights["calm_reset"] = if (profile.lastMood in 1..5) 14 else 6
        weights["halt_check"] = if (Timex.circadianPhase(now) == Timex.CircadianPhase.AFTERNOON_DIP) 12 else 5

        // Не повторяем один тип дважды подряд.
        val recent = repo.pingDao.recentKinds(2)
        for (k in recent) weights[k]?.let { weights[k] = maxOf(1, it / 5) }

        var roll = rnd.nextInt(weights.values.sum())
        var chosen = "breakthrough"
        for ((k, w) in weights) {
            if (roll < w) {
                chosen = k
                break
            }
            roll -= w
        }
        return build(chosen, session, mastery, profile.lastMood, now, rnd, unanswered, silentDays, false)
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
        night: Boolean
    ): PingPlan {
        val focus = Curriculum.focus(mastery, 4)
        val touched = repo.skillDao.all().filter { it.exposures > 0 }.map { it.skillId }.toSet()
        val weak = Curriculum.weakest(mastery, touched, 3)

        fun pickSkill(): Topic {
            val pool = ArrayList<Topic>()
            pool.addAll(focus)
            pool.addAll(weak)
            if (pool.isEmpty()) pool.addAll(Curriculum.topics)
            return pool[rnd.nextInt(pool.size)]
        }

        when (kind) {
            "morning" -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = kind,
                    task = "УТРЕННИЙ ЗАРЯД. Первое сообщение дня: задай твёрдый утренний вектор без воды (свет, вода, спина, старт), " +
                        "подвяжи 1 сильную стоическую или мотивационную мысль из базы и попроси назвать ОДИН главный шаг или якорь этого дня, " +
                        "который пользователь не сольёт до вечера.",
                    topicIds = listOf("halt_foundation", "action_activation", sk.id),
                    states = setOf("apathy", "procrastination", "deadend"),
                    preferredTones = setOf("push", "firm"),
                    preferredKinds = setOf("breakthrough", "philosophy")
                )
            }

            "evening" -> {
                val d = repo.today(now)
                return PingPlan(
                    kind = kind,
                    task = "ВЕЧЕРНЯЯ РАЗГРУЗКА И ИТОГ. Сегодня пользователь написал ${d.userMsgs} сообщений, закрыл шагов: ${d.stepsDone}, " +
                        "срывов: ${d.slips}. Прежде чем он начнёт обесценивать свой день, помоги честно увидеть хотя бы 1–2 твёрдых факта или победы за сегодня, " +
                        "выгрузить тревоги из головы до утра и спокойно закрыть день.",
                    topicIds = listOf("night_shutdown", "cbt_distortions"),
                    states = setOf("night", "rumination", "guilt"),
                    preferredTones = setOf("calm", "deep"),
                    preferredKinds = setOf("practice", "philosophy"),
                    gentleOnly = true
                )
            }

            "night_calm" -> return PingPlan(
                kind = kind,
                task = "НОЧНОЕ УСПОКОЕНИЕ. Сейчас поздний час / ночь. Запрещён прессинг. Если человек не спит, скорее всего его крутит тревога или самобичевание. " +
                    "Коротко напомни правило ночного мозга («ночью кора спит, а страх врёт»), дай быструю технику выгрузки мыслей или дыхания 4–7–8 с паузами.",
                topicIds = listOf("night_shutdown", "breathing_reset"),
                states = setOf("night", "rumination", "anxiety"),
                preferredTones = setOf("calm"),
                preferredKinds = setOf("practice", "cbt"),
                gentleOnly = true
            )

            "comeback" -> return PingPlan(
                kind = kind,
                task = "ВОЗВРАЩЕНИЕ ИЗ ТИШИНЫ. Человек не выходил на связь уже $silentDays дн. Напиши прямо, по-товарищески напористо, без упрёков и стыда: " +
                    "«Молчание и изоляция кормят болото. Я не отстал и жду одного честного сигнала — где ты сейчас от 1 до 10?» и дай быстрые кнопки.",
                topicIds = listOf("meaning_loneliness", "action_activation"),
                states = setOf("loneliness", "depression", "apathy"),
                preferredTones = setOf("firm", "push"),
                preferredKinds = setOf("breakthrough", "cbt")
            )

            "nudge" -> return PingPlan(
                kind = kind,
                task = "НАПОРИСТЫЙ ДОЖИМ. Пользователь проигнорировал $unanswered последних пингов. Напиши коротко, с характером и сухой иронией союзника: " +
                    "застрял в рутине, накрыла апатия или просто избегаешь неприятного дела? Попроси тыкнуть одну кнопку или написать 2 слова.",
                topicIds = listOf("action_120sec"),
                states = setOf("procrastination", "apathy", "deadend"),
                preferredTones = setOf("push", "firm"),
                preferredKinds = setOf("breakthrough", "cbt")
            )

            "anchor_due" -> {
                val due = repo.anchorDao.due(now, 6)
                if (due.isEmpty()) return build("breakthrough", session, mastery, lastMood, now, rnd, unanswered, silentDays, night)
                val a = due[rnd.nextInt(due.size)]
                return PingPlan(
                    kind = kind,
                    task = "ПОДКРЕПЛЕНИЕ ЯКОРЯ. Верни личный якорь пользователя A${a.id}: «${a.phrase}» (${a.why}). " +
                        "Не просто процитируй, а проверь: сработала ли эта опора в последние дни или где мозг снова пытается её обойти? " +
                        "В asked обязательно поставь \"A${a.id}\".",
                    askedRef = "A${a.id}",
                    states = setOf("deadend", "anxiety", "depression"),
                    preferredTones = setOf("firm", "deep")
                )
            }

            "plan_push" -> {
                val steps = repo.planDao.steps(session.planId).filter { it.streak < 1 }
                if (steps.isEmpty()) return build("breakthrough", session, mastery, lastMood, now, rnd, unanswered, silentDays, night)
                val st = steps.first()
                return PingPlan(
                    kind = kind,
                    task = "ДОЖИМ ПЛАНА ПРОРЫВА. Напомни прямо про активный шаг S${st.id}: «${st.step}»" +
                        (if (st.why.isNotBlank()) " (${st.why})" else "") +
                        (if (st.skippedCount > 0) ". Это уже откладывалось ${st.skippedCount} раз(а) — не дай саботажу победить снова, предложи урезать до 5 минут прямо сейчас" else ". Спроси статус или запусти 10-минутный таймер") +
                        ". В asked обязательно поставь \"S${st.id}\".",
                    askedRef = "S${st.id}",
                    topicIds = listOf("action_120sec", "action_momentum"),
                    states = setOf("procrastination", "deadend", "apathy"),
                    preferredTones = setOf("push"),
                    preferredKinds = setOf("breakthrough")
                )
            }

            "trap_hunt" -> {
                val traps = repo.trapDao.top(5)
                if (traps.isEmpty()) return build("cbt_check", session, mastery, lastMood, now, rnd, unanswered, silentDays, night)
                val t = traps[rnd.nextInt(traps.size)]
                return PingPlan(
                    kind = kind,
                    task = "ОХОТА НА ЛОВУШКУ УМА. Пользователь уже ${t.times} раз(а) проваливался в мысль «${t.thought}». " +
                        "Не читай нотаций — подкинь живую жизненную проверку или провокационный вопрос, чтобы поймать эту ловушку на горячем и закрепить антидот («${t.antidote}»).",
                    topicIds = if (t.skillId.isNotEmpty()) listOf(t.skillId) else listOf("cbt_distortions"),
                    states = setOf("guilt", "depression", "anxiety", "failure"),
                    preferredTones = setOf("firm", "push"),
                    preferredKinds = setOf("cbt")
                )
            }

            "mood_rescue" -> return PingPlan(
                kind = kind,
                task = "ВЫТЯГИВАНИЕ ИЗ СПАДА. Последнее состояние пользователя было низким ($lastMood/10). " +
                    "Без дешёвого оптимизма «улыбнись!»: дай 1 точную мысль + 1 телесный или поведенческий микро-шаг (не тяжелее 2 минут), " +
                    "чтобы остановить сползание в яму и вернуть чуть-чуть опоры.",
                topicIds = listOf("halt_foundation", "action_activation", "grounding_54321"),
                states = setOf("depression", "burnout", "apathy", "deadend"),
                preferredTones = setOf("firm", "calm"),
                preferredKinds = setOf("practice", "cbt", "breakthrough"),
                gentleOnly = lastMood <= 2
            )

            "philosophy" -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = kind,
                    task = "ФИЛОСОФСКИЙ УДАР В ЦЕЛЬ. Возьми из «ОПОРНОЙ БАЗЫ ЗНАНИЙ» сильную философскую мысль или концепцию " +
                        "(стоицизм, Франкл, Юнг, Ницше, Спиноза, Талеб, дзен) под контекст пользователя. Не кидай пустую открытку: " +
                        "в 2 строках раскрой, какую иллюзию она режет, и задай 1 острый вопрос на осмысление. Если мысль цельная — запиши в anchors.",
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
                    task = "КПТ-ПРОВЕРКА РЕАЛЬНОСТИ. Подними типичное когнитивное искажение или самообман из базы знаний " +
                        "(чёрно-белое зрение, ожидание мотивации, предсказание поражения, тирания «должен»). " +
                        "Спроси прямо, не крутится ли эта пластинка у человека прямо сейчас, и предложи короткий антидот.",
                    topicIds = listOf("cbt_distortions", "cbt_abc", sk.id),
                    states = setOf("anxiety", "guilt", "procrastination", "failure"),
                    preferredTones = setOf("firm", "push"),
                    preferredKinds = setOf("cbt")
                )
            }

            "calm_reset" -> return PingPlan(
                kind = kind,
                task = "СБРОС НАПРЯЖЕНИЯ ДНЯ. Короткий 60-секундный пит-стоп для нервной системы: предложи одну конкретную соматическую " +
                    "или дыхательную практику из базы знаний (физиологический вздох, челюсть/плечи, расфокус взгляда, смена позы), чтобы сбросить накопленный зажим.",
                topicIds = listOf("breathing_reset", "grounding_54321"),
                states = setOf("anxiety", "burnout", "panic"),
                preferredTones = setOf("calm"),
                preferredKinds = setOf("practice"),
                gentleOnly = true
            )

            "halt_check" -> return PingPlan(
                kind = kind,
                task = "ЧЕК-ИН ТЕЛА (HALT). Дневной спад часто маскируется под отчаяние, когда человек просто забыл поесть, попить воды, " +
                    "выйти на воздух или отлипнуть от экрана. Проведи быстрый жёстко-практичный аудит тела за 30 секунд и поставь одно микро-условие.",
                topicIds = listOf("halt_foundation", "action_dopamine"),
                states = setOf("burnout", "apathy", "depression"),
                preferredTones = setOf("firm", "push"),
                preferredKinds = setOf("practice", "breakthrough")
            )

            else -> {
                val sk = pickSkill()
                return PingPlan(
                    kind = "breakthrough",
                    task = "НАПОРИСТЫЙ МИКРО-ВЫЗОВ. Сейчас подходящий момент сломать инерцию дня. Используй механику из базы знаний " +
                        "(120 секунд, уродливый черновик, 1 кв. метр, страшный звонок, выход на 15 мин, отказ от скроллинга) с опорой на досье пользователя. " +
                        "Потребуй чёткий микро-отчёт после выполнения.",
                    topicIds = listOf("action_120sec", "action_activation", sk.id),
                    states = setOf("procrastination", "apathy", "deadend"),
                    preferredTones = setOf("push"),
                    preferredKinds = setOf("breakthrough")
                )
            }
        }
    }
}