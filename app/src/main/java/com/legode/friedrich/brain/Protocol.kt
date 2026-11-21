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

/** Разобранный ответ Атласа: СМС для пользователя + психо-эмоциональная аналитика для досье. */
data class Reply(
    val messages: List<String>,
    val quick: List<String> = emptyList(),
    val title: String = "",
    /** 0 = не оценивалось, 1..10 = оценка состояния пользователя */
    val mood: Int = 0,
    val emotion: String = "",
    /** none | done | partial | skipped */
    val result: String = "none",
    /** A12 (якорь) | S7 (шаг плана) | N3 (новый шаг плана) | "" */
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
    val planDone: Boolean = false
)

data class AnchorNote(val phrase: String, val why: String, val source: String, val trigger: String)
data class TrapNote(val thought: String, val antidote: String, val skillId: String, val why: String)
data class SkillNote(val id: String, val d: Int)
data class PlanStepNote(val step: String, val why: String)
data class PlanNote(val title: String, val steps: List<PlanStepNote>)

object Protocol {

    private fun stringType(desc: String): JSONObject = JSONObject().put("type", "string").put("description", desc)

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

    /** JSON-схема ответа в чате и в проактивных «СМС-пингах». */
    val chatSchema: JSONObject by lazy {
        val anchor = objectType(
            listOf(
                "phrase" to stringType("Сильная опорная фраза, цитата или рефрейм"),
                "why" to stringType("Короткий разбор: почему это работает"),
                "source" to stringType("Автор, философская школа или метод"),
                "trigger" to stringType("При каком состоянии применять (тревога, апатия, тупик, ночь и т.п.)")
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
                "d" to JSONObject().put("type", "integer").put("description", "2, 1, -1 или -2")
            ),
            listOf("id", "d")
        )
        val planStep = objectType(
            listOf(
                "step" to stringType("Конкретный шаг-глагол прорыва"),
                "why" to stringType("Опора / зачем это делать или микро-критерий")
            ),
            listOf("step")
        )
        val plan = objectType(
            listOf(
                "title" to stringType("Название плана выхода/прорыва"),
                "steps" to arrayType(planStep, "Шаги плана, до 30")
            ),
            listOf("title", "steps")
        )
        val profile = objectType(
            listOf(
                "name" to stringType("Имя пользователя"),
                "goal" to stringType("Главная жизненная цель или точка выхода"),
                "interests" to stringType("Ценности, опоры, что даёт силы"),
                "stage" to stringType("Только в аудите состояния: истощение, спад, стабильность или сила")
            ),
            emptyList()
        )
        objectType(
            listOf(
                "messages" to arrayType(JSONObject().put("type", "string"), "1–3 СМС для пользователя"),
                "quick" to arrayType(JSONObject().put("type", "string"), "0–4 кнопки быстрого ответа"),
                "mood" to JSONObject().put("type", "integer").put("description", "Оценка состояния 1..10 (или 0 если рано оценивать)"),
                "emotion" to stringType("Главная эмоция в 1-2 слова"),
                "result" to JSONObject().put("type", "string")
                    .put("enum", JSONArray().put("none").put("done").put("partial").put("skipped")),
                "asked" to stringType("Ссылка на проверяемый элемент: A12, S7, N3 или пустая строка"),
                "anchors" to arrayType(anchor, "Новые сильные якоря для банка опор"),
                "traps" to arrayType(trap, "Выявленные когнитивные ловушки пользователя"),
                "skills" to arrayType(skill, "Практика навыков устойчивости"),
                "facts" to arrayType(JSONObject().put("type", "string"), "Новые долговременные факты и триггеры"),
                "profile" to profile,
                "plan" to plan,
                "plan_done" to JSONObject().put("type", "boolean"),
                "title" to stringType("Название беседы, если просили")
            ),
            listOf("messages")
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

    private val REF = Regex("^[ASN]\\d{1,9}$")
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

    /** Достаёт JSON-объект из ответа модели, даже если он обёрнут в текст или блок кода. */
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
     * Безопасный разбор ответа модели:
     *  • корректный JSON → полноценный Reply;
     *  • обрезанный JSON → спасает хотя бы сообщения;
     *  • обычный текст (при фолбэке без JSON-режима) → одно сообщение;
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

        // На случай вложенного "memory" поднимаем поля наверх.
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

        val skills = root.arr("skills", root.arr("topics"))?.objects().orEmpty().mapNotNull { o ->
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
            planDone = root.bool("plan_done") || root.bool("planDone") || root.bool("list_done") || root.bool("listDone")
        )
    }

    private fun JSONObject.arr(primary: String, fallback: JSONArray?): JSONArray? =
        arr(primary) ?: fallback

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
                val v = arr.opt(i)
                when (v) {
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
                    val step = v.str("step", v.str("de", v.str("title"))).trim().take(220)
                    val why = v.str("why", v.str("ru")).trim().take(220)
                    if (step.isNotEmpty()) steps.add(PlanStepNote(step, why))
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
        return PlanNote(o.str("title").trim().take(60).ifEmpty { "План прорыва" }, steps)
    }

    /** Из обрезанного JSON вытаскиваем строки массива "messages". */
    private fun salvageMessages(text: String): List<String> {
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
                if (ch == '\\' && i + 1 < text.length) {
                    when (val n = text[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> Unit
                        'u' -> {
                            if (i + 5 < text.length) {
                                val code = text.substring(i + 2, i + 6).toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                i += 4
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
            if (s.isNotEmpty() && (closed || s.length > 20)) out.add(s)
            if (out.size >= 4) break
        }
        return out
    }
}
