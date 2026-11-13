package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.db.SessionEntity
import kotlin.random.Random

/** Что именно Friedrich напишет в очередном «смс». */
data class PingPlan(
    val kind: String,
    /** Задание для модели. */
    val task: String,
    /** Ссылка на проверяемый элемент (V12 / L7) — чтобы оценить ответ ученика. */
    val askedRef: String = "",
    /** Темы, шпаргалки которых стоит приложить к запросу. */
    val topicIds: List<String> = emptyList()
)

/** Расписание «смс»: когда придёт следующее сообщение. Чистые функции — легко проверять. */
object PingScheduler {

    data class Level(val title: String, val minMinutes: Int, val maxMinutes: Int, val about: String)

    val INTENSITY: List<Level> = listOf(
        Level("Редко", 150, 300, "каждые 2,5–5 часов"),
        Level("Спокойно", 60, 120, "каждые 1–2 часа"),
        Level("Бодро", 30, 70, "каждые 30–70 минут"),
        Level("Настойчиво", 12, 40, "каждые 12–40 минут"),
        Level("Тамагочи 🔥", 4, 15, "каждые 4–15 минут")
    )

    fun level(index: Int): Level = INTENSITY[index.coerceIn(0, INTENSITY.lastIndex)]

    fun inQuiet(minuteOfDay: Int, s: Settings): Boolean {
        val a = s.quietStartMin
        val b = s.quietEndMin
        if (a == b) return false
        return if (a < b) minuteOfDay in a until b else (minuteOfDay >= a || minuteOfDay < b)
    }

    /** Ближайший момент после [at], когда тихие часы уже закончились. */
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
     * @param unanswered сколько последних сообщений остались без ответа: игнор замедляет Фридриха, но не останавливает.
     */
    fun next(now: Long, s: Settings, unanswered: Int, rnd: Random): Long {
        val lv = level(s.pingIntensity)
        val base = rnd.nextInt(lv.minMinutes, lv.maxMinutes + 1)
        val factor = when {
            unanswered >= 8 -> 2.5
            unanswered >= 4 -> 1.5
            else -> 1.0
        }
        val jitterSec = rnd.nextInt(0, 50)
        var at = now + (base * factor * 60_000L).toLong() + jitterSec * 1000L
        if (inQuiet(Timex.minuteOfDay(at), s)) {
            at = endOfQuiet(at, s) + rnd.nextInt(1, 22) * 60_000L
        }
        return at
    }
}

/** Выбор содержания «смс»: случайный, но взвешенный по состоянию досье. */
class PingPlanner(private val repo: Repository, private val runtime: RuntimeState) {

    suspend fun plan(session: SessionEntity, s: Settings, now: Long, rnd: Random, forcedKind: String?): PingPlan {
        val today = Timex.today()
        val hour = Timex.dateTime(now).hour
        val mastery = repo.masteryMap()
        val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
        val lastUserAt = repo.messageDao.lastUserAt() ?: 0L
        val silentDays = if (lastUserAt == 0L) 0 else ((now - lastUserAt) / Srs.DAY).toInt()

        if (forcedKind != null) return build(forcedKind, session, mastery, now, rnd, unanswered, silentDays)

        // Особые случаи дня.
        if (runtime.morningDay != today && hour in 5..11) {
            runtime.morningDay = today
            return build("morning", session, mastery, now, rnd, unanswered, silentDays)
        }
        if (silentDays >= 2 && rnd.nextInt(100) < 60) {
            return build("comeback", session, mastery, now, rnd, unanswered, silentDays)
        }
        val quietStartHour = s.quietStartMin / 60
        val eveningFrom = if (s.quietStartMin == s.quietEndMin) 21 else ((quietStartHour + 24 - 2) % 24)
        if (runtime.eveningDay != today && hour >= 19 && hour >= eveningFrom && repo.today(now).userMsgs > 0) {
            runtime.eveningDay = today
            return build("evening", session, mastery, now, rnd, unanswered, silentDays)
        }
        if (unanswered >= 3 && rnd.nextInt(100) < 35) {
            return build("nudge", session, mastery, now, rnd, unanswered, silentDays)
        }

        // Взвешенный случайный выбор.
        val weights = LinkedHashMap<String, Int>()
        val dueCount = repo.vocabDao.dueCount(now)
        if (dueCount > 0) weights["vocab_due"] = 22 + minOf(dueCount, 12) * 2
        if (session.listId != 0L) weights["list_drill"] = 34
        if (repo.mistakeDao.activeCount() > 0) weights["mistake"] = 13
        weights["grammar"] = 24
        weights["new_word"] = 12
        weights["question"] = 11
        weights["translate"] = 10
        weights["clock"] = if ((mastery["time_clock"] ?: 0) < 80) 6 else 2
        weights["number"] = if ((mastery["numbers"] ?: 0) < 80) 6 else 2
        weights["situation"] = 6
        weights["today"] = 4
        weights["culture"] = 2

        // Не повторяем один и тот же вид подряд.
        val recent = repo.pingDao.recentKinds(2)
        for (k in recent) weights[k]?.let { weights[k] = maxOf(1, it / 5) }

        var roll = rnd.nextInt(weights.values.sum())
        var chosen = "grammar"
        for ((k, w) in weights) {
            if (roll < w) {
                chosen = k
                break
            }
            roll -= w
        }
        return build(chosen, session, mastery, now, rnd, unanswered, silentDays)
    }

    private suspend fun build(
        kind: String,
        session: SessionEntity,
        mastery: Map<String, Int>,
        now: Long,
        rnd: Random,
        unanswered: Int,
        silentDays: Int
    ): PingPlan {
        val focus = Curriculum.focus(mastery, 4)
        val touched = repo.topicDao.all().filter { it.exposures > 0 }.map { it.topicId }.toSet()
        val weak = Curriculum.weakest(mastery, touched, 3)

        fun pickTopic(): Topic {
            val pool = ArrayList<Topic>()
            pool.addAll(focus)
            pool.addAll(focus.take(2))
            pool.addAll(weak)
            // иногда — возврат к уже пройденному, чтобы не забывалось
            Curriculum.topics.filter { (mastery[it.id] ?: 0) in 40..95 }.shuffled(rnd).take(2).forEach { pool.add(it) }
            return if (pool.isEmpty()) Curriculum.topics.first() else pool[rnd.nextInt(pool.size)]
        }

        when (kind) {
            "morning" -> {
                val t = pickTopic()
                return PingPlan(
                    kind,
                    "УТРЕННИЙ ПИНГ. Это первое сообщение дня: поздоровайся по-немецки по времени суток, одной фразой задай настрой " +
                        "и дай «слово дня» по теме ${t.id} — ${t.title} (новое полезное слово A1, запиши его в words). " +
                        "Закончи одним лёгким вопросом с этим словом.",
                    topicIds = listOf(t.id)
                )
            }

            "evening" -> {
                val d = repo.today(now)
                return PingPlan(
                    kind,
                    "ВЕЧЕРНИЙ ИТОГ. Сегодня ученик написал ${d.userMsgs} сообщений, верных ответов ${d.correct}, ошибок ${d.wrong}, " +
                        "новых слов ${d.wordsAdded}. Коротко и по-человечески подведи итог дня (похвали за конкретное или подбодри), " +
                        "скажи одной фразой, что будет завтра, и задай последний на сегодня совсем лёгкий вопрос."
                )
            }

            "comeback" -> return PingPlan(
                kind,
                "ВОЗВРАЩЕНИЕ. Ученик молчит уже $silentDays дн. Напиши тёплое, чуть ироничное сообщение в духе «я скучал» " +
                    "(без упрёков) и дай САМОЕ лёгкое задание на 10 секунд с кнопками-вариантами, чтобы вернуть его."
            )

            "nudge" -> return PingPlan(
                kind,
                "НАПОМИНАНИЕ. Ученик не ответил на $unanswered твоих последних сообщений. Напиши коротко и живо " +
                    "(можно с юмором, можно слегка поворчать, но без давления и стыда) и задай очень простой вопрос с кнопками-вариантами."
            )

            "vocab_due" -> {
                val due = repo.vocabDao.due(now, 6)
                if (due.isEmpty()) return build("grammar", session, mastery, now, rnd, unanswered, silentDays)
                val w = due[rnd.nextInt(due.size)]
                val isNoun = Norm.articleOf(w.de).isNotEmpty()
                val variants = ArrayList<String>()
                variants.add("перевод с русского на немецкий")
                variants.add("перевод с немецкого на русский")
                variants.add("вставить это слово в пропуск в простом предложении (само слово не показывай, дай русскую подсказку)")
                variants.add("попросить составить короткое предложение с этим словом")
                if (isNoun) variants.add("спросить артикль (слово покажи без артикля, кнопки der / die / das)")
                if (isNoun && w.plural.isNotBlank()) variants.add("спросить множественное число")
                return PingPlan(
                    kind,
                    "ПОВТОРЕНИЕ СЛОВА. Проверь слово из словаря ученика: V${w.id} «${w.de}» — «${w.ru}»" +
                        (if (w.plural.isNotBlank()) " (мн. ч. ${w.plural})" else "") +
                        ". Тип задания: ${variants[rnd.nextInt(variants.size)]}. Ответ не показывай. В asked поставь \"V${w.id}\".",
                    askedRef = "V${w.id}"
                )
            }

            "list_drill" -> {
                val items = repo.listDao.items(session.listId).filter { it.streak < 2 }
                if (items.isEmpty()) return build("grammar", session, mastery, now, rnd, unanswered, silentDays)
                val sorted = items.sortedByDescending { it.wrong - it.correct }
                val it0 = sorted[rnd.nextInt(minOf(sorted.size, 5))]
                val variant = listOf(
                    "перевод с русского на немецкий", "перевод с немецкого на русский",
                    "вставить в пропуск", "составить с этим предложение"
                )[rnd.nextInt(4)]
                return PingPlan(
                    kind,
                    "ОТРАБОТКА СПИСКА. Продолжи активный список: элемент L${it0.id} «${it0.de}»" +
                        (if (it0.ru.isNotBlank()) " — «${it0.ru}»" else "") +
                        ". Тип задания: $variant. Ответ не показывай. В asked поставь \"L${it0.id}\".",
                    askedRef = "L${it0.id}"
                )
            }

            "mistake" -> {
                val list = repo.mistakeDao.top(5)
                if (list.isEmpty()) return build("grammar", session, mastery, now, rnd, unanswered, silentDays)
                val m = list[rnd.nextInt(list.size)]
                return PingPlan(
                    kind,
                    "РАБОТА НАД ОШИБКОЙ. Ученик уже ${m.times} раз(а) писал «${m.bad}» вместо «${m.good}»" +
                        (if (m.why.isNotBlank()) " (правило: ${m.why})" else "") +
                        ". Не показывай правильный вариант и не напоминай про саму ошибку: дай новое короткое задание, " +
                        "в котором нужно применить это же правило (перевод, пропуск или «найди ошибку» с другими словами).",
                    topicIds = if (m.topicId.isNotEmpty()) listOf(m.topicId) else emptyList()
                )
            }

            "new_word" -> {
                val t = pickTopic()
                return PingPlan(
                    kind,
                    "НОВОЕ СЛОВО. Научи одному новому полезному слову или короткой фразе A1 по теме ${t.id} — ${t.title} " +
                        "(которого нет в словаре ученика), по возможности свяжи с его жизнью и интересами. " +
                        "Дай слово с переводом и одним примером и сразу задай мини-вопрос, где его нужно употребить. Слово запиши в words.",
                    topicIds = listOf(t.id)
                )
            }

            "question" -> {
                val t = pickTopic()
                return PingPlan(
                    kind,
                    "ЛИЧНЫЙ ВОПРОС. Задай один простой личный вопрос по-немецки (тема ${t.id} — ${t.title}); " +
                        "ученик должен ответить полным предложением по-немецки. Для уровня A0 добавь перевод вопроса в скобках " +
                        "и начало ответа-образца.",
                    topicIds = listOf(t.id)
                )
            }

            "translate" -> {
                val t = pickTopic()
                return PingPlan(
                    kind,
                    "ПЕРЕВОД. Попроси перевести на немецкий одно короткое предложение (4–7 слов) с лексикой A1 " +
                        "на тему ${t.id} — ${t.title}. Предложение должно требовать применить правило этой темы.",
                    topicIds = listOf(t.id)
                )
            }

            "clock" -> {
                val h = rnd.nextInt(1, 13)
                val m = listOf(0, 15, 30, 45, 5, 10, 20, 25, 35, 40, 50, 55)[rnd.nextInt(12)]
                return PingPlan(
                    kind,
                    "ЧАСЫ. Спроси «Wie spät ist es?» и покажи время цифрами: %d:%02d. Попроси написать по-немецки ".format(h, m) +
                        "разговорным способом (halb / Viertel / nach / vor).",
                    topicIds = listOf("time_clock")
                )
            }

            "number" -> {
                val price = rnd.nextBoolean()
                val value = if (price) "%d,%02d €".format(rnd.nextInt(1, 60), listOf(0, 50, 99, 20, 95)[rnd.nextInt(5)])
                else listOf(rnd.nextInt(13, 100), rnd.nextInt(100, 1000), 1900 + rnd.nextInt(60, 127)).random(rnd).toString()
                return PingPlan(
                    kind,
                    "ЧИСЛА. Попроси написать словами по-немецки: $value " +
                        (if (price) "(это цена)." else "(число или год).") + " Оберни в живую мини-ситуацию одной фразой.",
                    topicIds = listOf("numbers")
                )
            }

            "situation" -> {
                val sc = Skills.scenarios[rnd.nextInt(Skills.scenarios.size)]
                return PingPlan(
                    kind,
                    "МИНИ-СЦЕНКА «${sc.title}». ${sc.brief} Одной строкой по-русски обозначь ситуацию и задачу ученика, " +
                        "затем напиши ОДНУ свою реплику в роли по-немецки и жди его ответа."
                )
            }

            "today" -> return PingPlan(
                kind,
                "СЕЙЧАС. Используй текущие день недели, дату или время суток из раздела «СЕЙЧАС»: спроси по-немецки, " +
                    "какой сегодня день / какое число / который час / что ученик делает прямо сейчас. Один вопрос.",
                topicIds = listOf("days_dates")
            )

            "culture" -> return PingPlan(
                kind,
                "ФАКТ. Один короткий любопытный и достоверный факт о Германии, быте или немецком языке (1–2 строки по-русски) " +
                    "+ одно связанное слово A1 (запиши в words) + один вопрос ученику."
            )

            else -> {
                val t = pickTopic()
                val variant = listOf(
                    "вставить пропущенное слово или окончание",
                    "выбрать правильный вариант из двух-трёх (дай кнопки quick)",
                    "перевести короткую фразу на немецкий",
                    "найти и исправить ошибку в коротком предложении",
                    "поставить слова в правильном порядке",
                    "задать вопрос к готовому ответу",
                    "преобразовать (ты → Вы, единственное → множественное, утверждение → отрицание или вопрос)"
                )[rnd.nextInt(7)]
                return PingPlan(
                    "grammar",
                    "МИКРО-ЗАДАНИЕ по теме ${t.id} — ${t.title} (${t.de}). Тип: $variant. " +
                        "Используй лексику, которую ученик уже знает. Одна строка контекста + один вопрос.",
                    topicIds = listOf(t.id)
                )
            }
        }
    }
}
