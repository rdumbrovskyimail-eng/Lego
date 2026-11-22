package com.legode.friedrich.brain

import com.legode.friedrich.core.arr
import com.legode.friedrich.core.bool
import com.legode.friedrich.core.int
import com.legode.friedrich.core.obj
import com.legode.friedrich.core.objects
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import org.json.JSONArray
import org.json.JSONObject

/** Разобранный ответ Атласа: СМС для пользователя + психо-когнитивная аналитика для досье. */
data class Reply(
    val messages: List<String>,
    val quick: List<String> = emptyList(),
    val title: String = "",
    /** 0 = не оценивалось, 1..10 = оценка состояния пользователя */
    val mood: Int = 0,
    val emotion: String = "",
    /** none | done | partial | skipped */
    val result: String = "none",
    /** A12 (якорь) | S7 (шаг плана) | N3 (новый шаг плана) | K5 (карточка) | "" */
    val asked: String = "",
    val anchors: List<AnchorNote> = emptyList(),
    val traps: List<TrapNote> = emptyList(),
    val skills: List<SkillNote> = emptyList(),
    val facts: List<String> = emptyList(),
    val name: String = "",
    val goal: String = "",
    val interests: String = "",
    val stage: String = "",
    val plan: PlanNote? = null,
    val planDone: Boolean = false,
    /** none | distress | crisis — оценка риска моделью (второй контур безопасности). */
    val risk: String = "none"
)

data class AnchorNote(val phrase: String, val why: String, val source: String, val trigger: String)
data class TrapNote(val thought: String, val antidote: String, val skillId: String, val why: String)
data class SkillNote(val id: String, val d: Int)
data class PlanStepNote(val step: String, val why: String, val obstacle: String = "", val ifThenRule: String = "")
data class PlanNote(
    val title: String,
    val steps: List<PlanStepNote>,
    val wish: String = "",
    val outcome: String = "",
    val mainObstacle: String = ""
)

data class ExtractedCardNote(
    val kind: String,
    val front: String,
    val back: String,
    val hint: String,
    val skillId: String,
    val tags: String
)

data class ExtractedSourceNote(
    val title: String,
    val summary: String,
    val keyModels: List<String>,
    val blindSpots: String,
    val cards: List<ExtractedCardNote>
)

data class DecisionAssistNote(
    val baseRateNote: String,
    val preMortem: String,
    val ifThenSafeguard: String,
    val suggestedPct: Int
)

data class WeeklyReviewNote(
    val summary: String,
    val nextWeekChallenge: String,
    val topTrigger: String,
    val bestWin: String,
    val scoresJson: String
)

object Protocol {

    private fun stringType(desc: String): JSONObject = JSONObject().put("type", "string").put("description", desc)

    private fun intType(desc: String): JSONObject = JSONObject().put("type", "integer").put("description", desc)

    private fun arrayType(items: JSONObject, desc: String): JSONObject =
        JSONObject().put("type", "array").put("items", items).put("description", desc)

    private fun objectType(props: List<Pair<String, JSONObject>>, required: List<String>): JSONObject {
        val p = JSONObject()
        for ((k, v) in props) p.put(k, v)
        val o = JSONObject().put("type", "object").put("properties", p)
        if (required.isNotEmpty()) {
            val r = JSONArray()
            for (s in required) r.put(s)
            o.put("required", r)
        }
        return o
    }

    /** Компактная схема для чата и пингов — не раздуваем, чтобы быстрая модель не теряла точность. */
    val chatSchema: JSONObject by lazy {
        val anchor = objectType(
            listOf(
                "phrase" to stringType("Сильная опорная фраза, цитата или рефрейм"),
                "why" to stringType("Короткий разбор: почему это работает"),
                "source" to stringType("Автор, школа или метод"),
                "trigger" to stringType("При каком состоянии применять")
            ),
            listOf("phrase", "why")
        )
        val trap = objectType(
            listOf(
                "thought" to stringType("Негативная мысль, отговорка или искажение пользователя"),
                "antidote" to stringType("Трезвый сильный антидот-рефрейм"),
                "skill" to stringType("id навыка из указателя"),
                "why" to stringType("Механизм когнитивной ловушки в 1 фразу")
            ),
            listOf("thought", "antidote")
        )
        val skill = objectType(
            listOf(
                "id" to stringType("id навыка из указателя"),
                "d" to intType("2, 1, -1 или -2")
            ),
            listOf("id", "d")
        )
        val planStep = objectType(
            listOf(
                "step" to stringType("Конкретный шаг-глагол"),
                "why" to stringType("Зачем это делать или критерий"),
                "obstacle" to stringType("Главная внутренняя помеха для этого шага (WOOP)"),
                "if_then" to stringType("Правило «Если [триггер], то я [действие]»")
            ),
            listOf("step")
        )
        val plan = objectType(
            listOf(
                "title" to stringType("Название плана прорыва"),
                "wish" to stringType("Главная цель (WOOP)"),
                "outcome" to stringType("Осязаемый лучший результат"),
                "main_obstacle" to stringType("Ключевая внутренняя помеха"),
                "steps" to arrayType(planStep, "Шаги плана, до 30")
            ),
            listOf("title", "steps")
        )
        val profile = objectType(
            listOf(
                "name" to stringType("Имя пользователя"),
                "goal" to stringType("Главная жизненная цель"),
                "interests" to stringType("Ценности, опоры, что даёт силы"),
                "stage" to stringType("Только в аудите: истощение, спад, стабильность или сила")
            ),
            emptyList()
        )
        objectType(
            listOf(
                "messages" to arrayType(JSONObject().put("type", "string"), "1–3 СМС для пользователя"),
                "quick" to arrayType(JSONObject().put("type", "string"), "0–4 кнопки быстрого ответа"),
                "mood" to intType("Оценка состояния 1..10 (или 0 если рано оценивать)"),
                "emotion" to stringType("Главная эмоция в 1-2 слова"),
                "risk" to JSONObject().put("type", "string")
                    .put("enum", JSONArray().put("none").put("distress").put("crisis"))
                    .put("description", "Риск: crisis — любые, даже завуалированные мысли о смерти или самоповреждении; distress — острая боль, паника, безнадёжность; иначе none"),
                "result" to JSONObject().put("type", "string")
                    .put("enum", JSONArray().put("none").put("done").put("partial").put("skipped")),
                "asked" to stringType("Ссылка на проверяемый элемент: A12, S7, N3, K5 или пустая строка"),
                "anchors" to arrayType(anchor, "Новые сильные якоря для банка опор"),
                "traps" to arrayType(trap, "Выявленные когнитивные ловушки"),
                "skills" to arrayType(skill, "Практика навыков устойчивости и мышления"),
                "facts" to arrayType(JSONObject().put("type", "string"), "Новые долговременные факты и триггеры"),
                "profile" to profile,
                "plan" to plan,
                "plan_done" to JSONObject().put("type", "boolean"),
                "title" to stringType("Название беседы, если просили")
            ),
            listOf("messages", "risk")
        )
    }

    /** Изолированная схема для Синтезатора знаний (разбор книг, PDF, URL, фото страниц). */
    val sourceExtractSchema: JSONObject by lazy {
        val card = objectType(
            listOf(
                "kind" to stringType("recall | transfer | critique | feynman | debias"),
                "front" to stringType("Сильный вопрос активного извлечения или кейс на перенос"),
                "back" to stringType("Эталонный глубокий ответ, критерии и практический вывод"),
                "hint" to stringType("Мягкая подсказка без спойлера"),
                "skill" to stringType("Наиболее подходящий id навыка из указателя"),
                "tags" to stringType("2-4 ключевых слова через запятую")
            ),
            listOf("kind", "front", "back")
        )
        objectType(
            listOf(
                "title" to stringType("Ёмкое название материала"),
                "summary" to stringType("Концентрированная выжимка сути в 4-6 предложениях без воды"),
                "key_models" to arrayType(JSONObject().put("type", "string"), "3-7 главных ментальных моделей или тезисов"),
                "blind_spots" to stringType("Какие допущения, слабые места или однобокость есть у автора"),
                "cards" to arrayType(card, "5-10 карточек активного припоминания и критического переноса")
            ),
            listOf("title", "summary", "key_models", "cards")
        )
    }

    /** Изолированная схема для помощника Лаборатории решений (Outside View + Pre-mortem). */
    val decisionAssistSchema: JSONObject by lazy {
        objectType(
            listOf(
                "base_rate_note" to stringType("Трезвый взгляд снаружи и базовая статистика для таких решений"),
                "pre_mortem" to stringType("3 реалистичные причины, почему решение может провалиться через 6-12 месяцев"),
                "if_then_safeguard" to stringType("Конкретный WOOP-предохранитель: Если [сигнал сбоя], то [действие]"),
                "suggested_pct" to intType("Реалистичная базовая вероятность успеха 1..99")
            ),
            listOf("base_rate_note", "pre_mortem", "if_then_safeguard")
        )
    }

    /** Изолированная схема для Воскресного Executive Review. */
    val weeklyReviewSchema: JSONObject by lazy {
        val scores = objectType(
            listOf(
                "resilience" to intType("0..100"),
                "critical" to intType("0..100"),
                "systems" to intType("0..100"),
                "calibration" to intType("0..100"),
                "learning" to intType("0..100"),
                "execution" to intType("0..100")
            ),
            listOf("resilience", "critical", "systems", "calibration", "learning", "execution")
        )
        objectType(
            listOf(
                "summary" to stringType("Глубокий честный синтез недели: паттерны ума, эмоции, прогресс (5-7 предложений)"),
                "next_week_challenge" to stringType("Один конкретный интеллектуально-поведенческий вызов на следующую неделю"),
                "top_trigger" to stringType("Главный триггер откатов за неделю"),
                "best_win" to stringType("Самый сильный шаг или инсайт недели"),
                "scores" to scores
            ),
            listOf("summary", "next_week_challenge", "scores")
        )
    }

    /** Изолированная схема калибровочной дуэли (тест уверенности на фактах). */
    val calibrationQuizSchema: JSONObject by lazy {
        objectType(
            listOf(
                "question" to stringType("Чёткий вопрос «да/нет» с проверяемым реальным фактом"),
                "outcome" to intType("1 если утверждение верно, 0 если неверно"),
                "explanation" to stringType("Короткое фактическое объяснение с цифрами")
            ),
            listOf("question", "outcome", "explanation")
        )
    }

    val reflectionSchema: JSONObject by lazy {
        objectType(
            listOf(
                "note" to stringType("Психологический разбор динамики: 3–5 конкретных предложений"),
                "plan" to stringType("Тактика на ближайшие дни: 3–5 коротких пунктов через «;»"),
                "facts" to arrayType(JSONObject().put("type", "string"), "Новые устойчивые факты и триггеры")
            ),
            listOf("note", "plan")
        )
    }

    val summarySchema: JSONObject by lazy {
        objectType(listOf("summary" to stringType("Краткое содержание беседы")), listOf("summary"))
    }

    private val REF = Regex("^[ASNK]\\d{1,9}$")
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

    fun extractObject(raw: String): JSONObject? {
        val cleaned = raw.trim().replace(FENCE, "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Безопасный разбор ответа модели для чата:
     *  • корректный JSON → полноценный Reply;
     *  • обрезанный JSON → спасает сообщения;
     *  • обычный текст (без JSON) → одно сообщение;
     *  • пусто/битый мусор → null.
     */
    fun parse(raw: String): Reply? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val root = extractObject(trimmed)
        if (root == null) {
            if (trimmed.contains("\"messages\"")) {
                val saved = salvageMessages(trimmed)
                return if (saved.isEmpty()) null else Reply(messages = saved)
            }
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return null
            return Reply(messages = listOf(trimmed.replace(FENCE, "").trim()))
        }

        root.obj("memory")?.let { mem ->
            val keys = mem.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!root.has(k)) root.put(k, mem.opt(k))
            }
        }

        val messages = readMessages(root)
        if (messages.isEmpty()) return null

        val anchors = root.arr("anchors")?.objects().orEmpty().mapNotNull { o ->
            val phrase = o.str("phrase", o.str("text")).trim().take(220)
            val why = o.str("why", o.str("meaning")).trim().take(240)
            if (phrase.isEmpty() || why.isEmpty()) null
            else AnchorNote(
                phrase = phrase,
                why = why,
                source = o.str("source", o.str("author")).trim().take(70),
                trigger = o.str("trigger", o.str("context")).trim().take(120)
            )
        }.take(4)

        val traps = root.arr("traps")?.objects().orEmpty().mapNotNull { o ->
            val thought = o.str("thought", o.str("bad")).trim().take(220)
            val antidote = o.str("antidote", o.str("good")).trim().take(240)
            if (thought.isEmpty() || antidote.isEmpty() || thought == antidote) null
            else TrapNote(
                thought = thought,
                antidote = antidote,
                skillId = Curriculum.resolve(o.str("skill", o.str("topic")))?.id ?: "",
                why = o.str("why").trim().take(240)
            )
        }.take(3)

        val skills = (root.arr("skills") ?: root.arr("topics"))?.objects().orEmpty().mapNotNull { o ->
            val t = Curriculum.resolve(o.str("id")) ?: return@mapNotNull null
            val d = o.int("d").coerceIn(-2, 2)
            if (d == 0) null else SkillNote(t.id, d)
        }.distinctBy { it.id }.take(3)

        val facts = root.arr("facts")?.strings().orEmpty()
            .map { it.trim() }.filter { it.length in 3..200 }.take(2)

        val profile = root.obj("profile")
        val plan = (root.obj("plan") ?: root.obj("list"))?.let { readPlan(it) }

        val asked = root.str("asked").trim().uppercase().replace(" ", "").let { if (REF.matches(it)) it else "" }
        val result = when (root.str("result").trim().lowercase()) {
            "done", "correct", "completed", "сделано", "выполнено", "true" -> "done"
            "partial", "almost", "частично" -> "partial"
            "skipped", "wrong", "failed", "слито", "пропущено", "false" -> "skipped"
            else -> "none"
        }

        val mood = root.int("mood").let { if (it in 1..10) it else 0 }

        return Reply(
            messages = messages,
            quick = root.arr("quick")?.strings().orEmpty().map { it.trim() }.filter { it.isNotEmpty() && it.length <= 34 }.take(4),
            title = root.str("title").trim().take(40),
            mood = mood,
            emotion = root.str("emotion").trim().lowercase().take(40),
            result = result,
            asked = asked,
            anchors = anchors,
            traps = traps,
            skills = skills,
            facts = facts,
            name = profile?.str("name")?.trim()?.take(40) ?: "",
            goal = profile?.str("goal")?.trim()?.take(220) ?: "",
            interests = profile?.str("interests")?.trim()?.take(220) ?: "",
            stage = normalizeStage(profile?.str("stage", profile.str("level")) ?: ""),
            plan = plan,
            planDone = root.bool("plan_done") || root.bool("planDone") || root.bool("list_done") || root.bool("listDone"),
            risk = when (root.str("risk").trim().lowercase()) {
                "crisis", "кризис", "high", "высокий" -> "crisis"
                "distress", "дистресс", "medium", "средний" -> "distress"
                else -> "none"
            }
        )
    }

    fun parseExtractedSource(raw: String): ExtractedSourceNote? {
        val o = extractObject(raw) ?: return null
        val title = o.str("title").trim()
        val summary = o.str("summary").trim()
        if (title.isEmpty() || summary.isEmpty()) return null
        val models = o.arr("key_models")?.strings().orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        val blindSpots = o.str("blind_spots").trim()
        val cards = o.arr("cards")?.objects().orEmpty().mapNotNull { co ->
            val front = co.str("front").trim()
            val back = co.str("back").trim()
            if (front.isEmpty() || back.isEmpty()) null
            else ExtractedCardNote(
                kind = co.str("kind", "recall").lowercase(),
                front = front,
                back = back,
                hint = co.str("hint").trim(),
                skillId = Curriculum.resolve(co.str("skill"))?.id ?: "",
                tags = co.str("tags").trim()
            )
        }.take(15)
        return ExtractedSourceNote(title, summary, models, blindSpots, cards)
    }

    fun parseDecisionAssist(raw: String): DecisionAssistNote? {
        val o = extractObject(raw) ?: return null
        val base = o.str("base_rate_note").trim()
        val pm = o.str("pre_mortem").trim()
        val guard = o.str("if_then_safeguard").trim()
        if (base.isEmpty() && pm.isEmpty()) return null
        return DecisionAssistNote(base, pm, guard, o.int("suggested_pct", 50).coerceIn(1, 99))
    }

    fun parseWeeklyReview(raw: String): WeeklyReviewNote? {
        val o = extractObject(raw) ?: return null
        val summary = o.str("summary").trim()
        if (summary.isEmpty()) return null
        val scoresObj = o.obj("scores") ?: JSONObject()
        val cleanScores = JSONObject()
            .put("resilience", scoresObj.int("resilience", 50).coerceIn(0, 100))
            .put("critical", scoresObj.int("critical", 50).coerceIn(0, 100))
            .put("systems", scoresObj.int("systems", 50).coerceIn(0, 100))
            .put("calibration", scoresObj.int("calibration", 50).coerceIn(0, 100))
            .put("learning", scoresObj.int("learning", 50).coerceIn(0, 100))
            .put("execution", scoresObj.int("execution", 50).coerceIn(0, 100))
        return WeeklyReviewNote(
            summary = summary,
            nextWeekChallenge = o.str("next_week_challenge").trim(),
            topTrigger = o.str("top_trigger").trim(),
            bestWin = o.str("best_win").trim(),
            scoresJson = cleanScores.toString()
        )
    }

    /** Инкрементально вытаскивает готовые и печатающиеся строки массива "messages" во время SSE-стриминга. */
    fun extractPartialMessages(partialJson: String): List<String> = salvageMessages(partialJson, allowShortIncomplete = true)

    fun normalizeStage(raw: String): String = when (raw.trim().lowercase()) {
        "истощение", "кризис", "дно", "r0" -> "истощение"
        "спад", "неустойчивость", "r1" -> "спад"
        "стабильность", "выравнивание", "r2" -> "стабильность"
        "сила", "устойчивость", "рост", "r3" -> "сила"
        else -> ""
    }

    private fun readMessages(root: JSONObject): List<String> {
        val out = ArrayList<String>()
        val arr = root.arr("messages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                when (val v = arr.opt(i)) {
                    is String -> out.add(v)
                    is JSONObject -> out.add(v.str("text", v.str("message")))
                    else -> Unit
                }
            }
        } else {
            val single = root.str("messages", root.str("message", root.str("text")))
            if (single.isNotBlank()) out.add(single)
        }
        return out.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
    }

    private fun readPlan(o: JSONObject): PlanNote? {
        val steps = ArrayList<PlanStepNote>()
        val arr = o.arr("steps") ?: o.arr("items") ?: return null
        for (i in 0 until arr.length()) {
            when (val v = arr.opt(i)) {
                is JSONObject -> {
                    val step = v.str("step", v.str("title")).trim().take(220)
                    val why = v.str("why").trim().take(220)
                    val obstacle = v.str("obstacle").trim().take(180)
                    val ifThen = v.str("if_then", v.str("ifThenRule")).trim().take(220)
                    if (step.isNotEmpty()) steps.add(PlanStepNote(step, why, obstacle, ifThen))
                }

                is String -> {
                    val parts = v.split(" — ", " – ", " - ", "=", limit = 2)
                    val step = parts[0].trim().take(220)
                    if (step.isNotEmpty()) steps.add(PlanStepNote(step, parts.getOrElse(1) { "" }.trim().take(220)))
                }

                else -> Unit
            }
            if (steps.size >= 30) break
        }
        if (steps.isEmpty()) return null
        return PlanNote(
            title = o.str("title").trim().take(60).ifEmpty { "План прорыва" },
            steps = steps,
            wish = o.str("wish").trim().take(220),
            outcome = o.str("outcome").trim().take(220),
            mainObstacle = o.str("main_obstacle", o.str("mainObstacle")).trim().take(220)
        )
    }

    /** Из обрезанного или стримящегося JSON аккуратно достаёт строки массива "messages". */
    private fun salvageMessages(text: String, allowShortIncomplete: Boolean = false): List<String> {
        val at = text.indexOf("\"messages\"")
        if (at < 0) return emptyList()
        var i = text.indexOf('[', at)
        if (i < 0) return emptyList()
        i++
        val out = ArrayList<String>()
        while (i < text.length) {
            val c = text[i]
            if (c == ']') break
            if (c != '"') {
                i++
                continue
            }
            val sb = StringBuilder()
            i++
            var closed = false
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\\') {
                    if (i + 1 >= text.length) break // ждём следующий чанк SSE
                    when (val n = text[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> Unit
                        'u' -> {
                            if (i + 5 < text.length) {
                                val code = text.substring(i + 2, i + 6).toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                i += 4
                            } else {
                                i = text.length
                                break
                            }
                        }

                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                if (ch == '"') {
                    closed = true
                    i++
                    break
                }
                sb.append(ch)
                i++
            }
            val s = sb.toString().trim()
            if (s.isNotEmpty() && (closed || allowShortIncomplete || s.length > 20)) out.add(s)
            if (out.size >= 4) break
        }
        return out
    }
}