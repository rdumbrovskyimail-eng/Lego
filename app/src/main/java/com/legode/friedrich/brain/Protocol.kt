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

/** Разобранный ответ Фридриха: СМС для ученика + служебные поля памяти. */
data class Reply(
    val messages: List<String>,
    val quick: List<String> = emptyList(),
    val title: String = "",
    /** none | correct | partial | wrong */
    val result: String = "none",
    /** V12 | L7 | N3 | "" */
    val asked: String = "",
    val words: List<WordNote> = emptyList(),
    val fixes: List<FixNote> = emptyList(),
    val topics: List<TopicNote> = emptyList(),
    val facts: List<String> = emptyList(),
    val name: String = "",
    val goal: String = "",
    val interests: String = "",
    val level: String = "",
    val list: ListNote? = null,
    val listDone: Boolean = false
)

data class WordNote(val de: String, val ru: String, val plural: String, val example: String)
data class FixNote(val bad: String, val good: String, val topicId: String, val why: String)
data class TopicNote(val id: String, val d: Int)
data class ListItemNote(val de: String, val ru: String)
data class ListNote(val title: String, val items: List<ListItemNote>)

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

    /** JSON-схема ответа в чате и в «смс-пингах». */
    val chatSchema: JSONObject by lazy {
        val word = objectType(
            listOf(
                "de" to stringType("Немецкое слово или фраза; существительное с артиклем"),
                "ru" to stringType("Перевод на русский"),
                "pl" to stringType("Множественное число (только для существительных)"),
                "ex" to stringType("Короткий пример на немецком")
            ),
            listOf("de", "ru")
        )
        val fix = objectType(
            listOf(
                "bad" to stringType("Как написал ученик"),
                "good" to stringType("Как правильно"),
                "topic" to stringType("id темы из указателя"),
                "why" to stringType("Правило одной фразой по-русски")
            ),
            listOf("bad", "good")
        )
        val topic = objectType(
            listOf(
                "id" to stringType("id темы из указателя"),
                "d" to JSONObject().put("type", "integer").put("description", "2, 1, -1 или -2")
            ),
            listOf("id", "d")
        )
        val listItem = objectType(
            listOf("de" to stringType("Немецкий вариант"), "ru" to stringType("Перевод")),
            listOf("de")
        )
        val list = objectType(
            listOf(
                "title" to stringType("Название списка"),
                "items" to arrayType(listItem, "Элементы списка, до 60")
            ),
            listOf("title", "items")
        )
        val profile = objectType(
            listOf(
                "name" to stringType("Имя ученика"),
                "goal" to stringType("Цель изучения"),
                "interests" to stringType("Интересы"),
                "level" to stringType("Только в тесте уровня: A0, A1.1, A1.2 или A1")
            ),
            emptyList()
        )
        objectType(
            listOf(
                "messages" to arrayType(JSONObject().put("type", "string"), "1–3 СМС для ученика"),
                "quick" to arrayType(JSONObject().put("type", "string"), "0–4 кнопки быстрого ответа"),
                "result" to JSONObject().put("type", "string")
                    .put("enum", JSONArray().put("none").put("correct").put("partial").put("wrong")),
                "asked" to stringType("Ссылка на проверяемый элемент: V12, L7, N3 или пустая строка"),
                "words" to arrayType(word, "Новые слова для словаря"),
                "fixes" to arrayType(fix, "Ошибки ученика"),
                "topics" to arrayType(topic, "Практика по темам"),
                "facts" to arrayType(JSONObject().put("type", "string"), "Новые факты об ученике"),
                "profile" to profile,
                "list" to list,
                "list_done" to JSONObject().put("type", "boolean"),
                "title" to stringType("Название беседы, если просили")
            ),
            listOf("messages")
        )
    }

    val reflectionSchema: JSONObject by lazy {
        objectType(
            listOf(
                "note" to stringType("Наблюдения об ученике: 3–5 предложений"),
                "plan" to stringType("План на ближайшие дни: 3–5 коротких пунктов"),
                "facts" to arrayType(JSONObject().put("type", "string"), "Новые факты об ученике")
            ),
            listOf("note", "plan")
        )
    }

    val summarySchema: JSONObject by lazy {
        objectType(listOf("summary" to stringType("Краткое содержание беседы")), listOf("summary"))
    }

    private val REF = Regex("^[VLN]\\d{1,9}$")
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
     * Разбор ответа. Никогда не бросает исключений:
     *  • корректный JSON → полноценный Reply;
     *  • обрезанный JSON → спасаем хотя бы сообщения;
     *  • обычный текст (модель не в JSON-режиме) → одно сообщение;
     *  • совсем мусор → null (вызывающий покажет ошибку и даст повторить).
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

        // Если модель вложила служебные поля в "memory" — поднимаем их на верхний уровень.
        root.obj("memory")?.let { mem ->
            val keys = mem.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!root.has(k)) root.put(k, mem.opt(k))
            }
        }

        val messages = readMessages(root)
        if (messages.isEmpty()) return null

        val words = root.arr("words")?.objects().orEmpty().mapNotNull { o ->
            val de = o.str("de").trim().take(90)
            val ru = o.str("ru").trim().take(140)
            if (de.isEmpty() || ru.isEmpty()) null
            else WordNote(de, ru, o.str("pl", o.str("plural")).trim().take(60), o.str("ex", o.str("example")).trim().take(160))
        }.take(6)

        val fixes = root.arr("fixes")?.objects().orEmpty().mapNotNull { o ->
            val bad = o.str("bad").trim().take(200)
            val good = o.str("good").trim().take(200)
            if (bad.isEmpty() || good.isEmpty() || bad == good) null
            else FixNote(bad, good, Curriculum.resolve(o.str("topic"))?.id ?: "", o.str("why").trim().take(220))
        }.take(4)

        val topics = root.arr("topics")?.objects().orEmpty().mapNotNull { o ->
            val t = Curriculum.resolve(o.str("id")) ?: return@mapNotNull null
            val d = o.int("d").coerceIn(-2, 2)
            if (d == 0) null else TopicNote(t.id, d)
        }.distinctBy { it.id }.take(3)

        val facts = root.arr("facts")?.strings().orEmpty()
            .map { it.trim() }.filter { it.length in 3..180 }.take(2)

        val profile = root.obj("profile")
        val list = root.obj("list")?.let { readList(it) }

        val asked = root.str("asked").trim().uppercase().replace(" ", "").let { if (REF.matches(it)) it else "" }
        val result = when (root.str("result").trim().lowercase()) {
            "correct", "richtig", "right", "true", "верно" -> "correct"
            "partial", "almost", "почти" -> "partial"
            "wrong", "falsch", "incorrect", "false", "неверно" -> "wrong"
            else -> "none"
        }

        return Reply(
            messages = messages,
            quick = root.arr("quick")?.strings().orEmpty().map { it.trim() }.filter { it.isNotEmpty() && it.length <= 32 }.take(4),
            title = root.str("title").trim().take(40),
            result = result,
            asked = asked,
            words = words,
            fixes = fixes,
            topics = topics,
            facts = facts,
            name = profile?.str("name")?.trim()?.take(40) ?: "",
            goal = profile?.str("goal")?.trim()?.take(200) ?: "",
            interests = profile?.str("interests")?.trim()?.take(200) ?: "",
            level = normalizeLevel(profile?.str("level") ?: ""),
            list = list,
            listDone = root.bool("list_done") || root.bool("listDone")
        )
    }

    fun normalizeLevel(raw: String): String = when (raw.trim().uppercase().replace(" ", "")) {
        "A0" -> "A0"
        "A1.1", "A1,1", "A11" -> "A1.1"
        "A1.2", "A1,2", "A12" -> "A1.2"
        "A1", "A2", "A2.1" -> "A1"
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

    private fun readList(o: JSONObject): ListNote? {
        val items = ArrayList<ListItemNote>()
        val arr = o.arr("items") ?: return null
        for (i in 0 until arr.length()) {
            when (val v = arr.opt(i)) {
                is JSONObject -> {
                    val de = v.str("de").trim().take(200)
                    if (de.isNotEmpty()) items.add(ListItemNote(de, v.str("ru").trim().take(200)))
                }

                is String -> {
                    val parts = v.split(" — ", " – ", " - ", "=", limit = 2)
                    val de = parts[0].trim().take(200)
                    if (de.isNotEmpty()) items.add(ListItemNote(de, parts.getOrElse(1) { "" }.trim().take(200)))
                }

                else -> Unit
            }
            if (items.size >= 60) break
        }
        if (items.isEmpty()) return null
        return ListNote(o.str("title").trim().take(60).ifEmpty { "Мой список" }, items)
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
