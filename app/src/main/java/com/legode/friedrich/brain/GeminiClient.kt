package com.legode.friedrich.brain

import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.bool
import com.legode.friedrich.core.int
import com.legode.friedrich.core.obj
import com.legode.friedrich.core.objects
import com.legode.friedrich.core.str
import com.legode.friedrich.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиент Gemini API (REST, generateContent) без сторонних библиотек.
 *
 * Устойчивость встроена в сам клиент:
 *  • «лестница совместимости»: если API отклоняет запрос (400) из-за thinkingConfig, JSON-схемы или JSON-режима,
 *    клиент по очереди отключает эти возможности и повторяет запрос; удачную конфигурацию запоминает;
 *  • запасные модели: если выбранная модель не найдена (404), пробуются следующие из Settings.FALLBACK_MODELS;
 *  • повторы с паузой при 429 / 5xx / сетевых сбоях.
 */
class GeminiClient {

    sealed class Part {
        data class Text(val text: String) : Part()
        data class Blob(val mime: String, val base64: String) : Part()
    }

    data class Request(
        val system: String,
        val parts: List<Part>,
        val schema: JSONObject?,
        val jsonMode: Boolean = true,
        val maxTokens: Int = 4096,
        /** auto | minimal | low | medium | high */
        val thinking: String = "low",
        val retries: Int = 2
    )

    enum class Kind { NO_KEY, NETWORK, AUTH, QUOTA, OVERLOAD, BLOCKED, BAD_REQUEST, MODEL, EMPTY, UNKNOWN }

    sealed class Result {
        data class Ok(
            val text: String,
            val model: String,
            val promptTokens: Int,
            val outputTokens: Int,
            val cachedTokens: Int
        ) : Result()

        data class Fail(val kind: Kind, val message: String, val http: Int = 0) : Result() {
            /** Понятное человеку объяснение по-русски. */
            fun human(): String = when (kind) {
                Kind.NO_KEY -> "Не указан API-ключ Gemini. Добавь его в настройках."
                Kind.NETWORK -> "Нет связи с сервером. Проверь интернет."
                Kind.AUTH -> "API-ключ не принят. Проверь ключ в настройках."
                Kind.QUOTA -> "Превышен лимит запросов Gemini. Подожди немного или проверь квоту ключа."
                Kind.OVERLOAD -> "Сервер Gemini перегружен. Попробуй ещё раз через минуту."
                Kind.BLOCKED -> "Модель отказалась отвечать на это сообщение. Попробуй сформулировать иначе."
                Kind.MODEL -> "Модель недоступна для этого ключа. Проверь название модели в настройках."
                Kind.EMPTY -> "Модель вернула пустой ответ. Попробуй ещё раз."
                Kind.BAD_REQUEST -> "Сервер отклонил запрос: ${message.take(160)}"
                Kind.UNKNOWN -> "Неизвестная ошибка: ${message.take(160)}"
            }
        }
    }

    private class Caps(var thinking: Boolean = true, var schema: Boolean = true, var json: Boolean = true) {
        fun copy(): Caps = Caps(thinking, schema, json)
    }

    private val caps = ConcurrentHashMap<String, Caps>()
    private val resolved = ConcurrentHashMap<String, String>()

    /** Какая модель реально отвечает вместо запрошенной (если пришлось переключиться на запасную). */
    fun effectiveModel(preferred: String): String = resolved[preferred] ?: preferred

    suspend fun generate(apiKey: String, preferredModel: String, req: Request): Result =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext Result.Fail(Kind.NO_KEY, "no api key")
            val wanted = preferredModel.trim().removePrefix("models/").ifEmpty { Settings.DEFAULT_MODEL }
            val candidates = LinkedHashSet<String>()
            resolved[wanted]?.let { candidates.add(it) }
            candidates.add(wanted)
            candidates.addAll(Settings.FALLBACK_MODELS)

            var last: Result.Fail = Result.Fail(Kind.UNKNOWN, "no attempt")
            for (model in candidates) {
                when (val r = generateWith(apiKey, model, req)) {
                    is Result.Ok -> {
                        if (model != wanted) resolved[wanted] = model else resolved.remove(wanted)
                        return@withContext r
                    }

                    is Result.Fail -> {
                        last = r
                        if (r.kind != Kind.MODEL) return@withContext r
                        Logx.w("model $model unavailable: ${r.message}")
                    }
                }
            }
            last
        }

    private suspend fun generateWith(apiKey: String, model: String, req: Request): Result {
        val stored = caps[model] ?: Caps()
        val c = stored.copy()
        var transient = 0
        var downgrades = 0

        while (true) {
            val body = buildBody(req, c)
            val response: Pair<Int, String> = try {
                post("$BASE/models/$model:generateContent", apiKey, body)
            } catch (e: IOException) {
                if (transient < req.retries) {
                    transient++
                    delay(900L * transient * transient)
                    continue
                }
                return Result.Fail(Kind.NETWORK, e.message ?: e.javaClass.simpleName)
            } catch (e: Exception) {
                return Result.Fail(Kind.UNKNOWN, e.message ?: e.javaClass.simpleName)
            }

            val code = response.first
            val text = response.second

            when {
                code in 200..299 -> {
                    val parsed = parseSuccess(text, model)
                    if (parsed is Result.Ok && downgrades > 0) caps[model] = c
                    if (parsed is Result.Fail && parsed.kind == Kind.EMPTY && transient < req.retries) {
                        transient++
                        delay(600L)
                        continue
                    }
                    return parsed
                }

                code == 400 -> {
                    val msg = errorMessage(text)
                    val low = msg.lowercase()
                    if (low.contains("api key") || low.contains("api_key")) {
                        return Result.Fail(Kind.AUTH, msg, code)
                    }
                    if (low.contains("not found") && low.contains("model")) {
                        return Result.Fail(Kind.MODEL, msg, code)
                    }
                    val changed = downgrade(c, low, req)
                    if (changed && downgrades < 4) {
                        downgrades++
                        Logx.w("400 from $model → downgrade #$downgrades (thinking=${c.thinking}, schema=${c.schema}, json=${c.json}): $msg")
                        continue
                    }
                    return Result.Fail(Kind.BAD_REQUEST, msg, code)
                }

                code == 401 || code == 403 -> return Result.Fail(Kind.AUTH, errorMessage(text), code)

                code == 404 -> return Result.Fail(Kind.MODEL, errorMessage(text), code)

                code == 429 -> {
                    if (transient < req.retries) {
                        transient++
                        delay(2500L * transient)
                        continue
                    }
                    return Result.Fail(Kind.QUOTA, errorMessage(text), code)
                }

                code in 500..599 -> {
                    if (transient < req.retries) {
                        transient++
                        delay(1500L * transient)
                        continue
                    }
                    return Result.Fail(Kind.OVERLOAD, errorMessage(text), code)
                }

                else -> return Result.Fail(Kind.UNKNOWN, "HTTP $code ${errorMessage(text)}", code)
            }
        }
    }

    /** Отключает одну возможность, на которую жалуется сервер. Возвращает false, если отключать больше нечего. */
    private fun downgrade(c: Caps, lowMessage: String, req: Request): Boolean {
        val hasThinking = c.thinking && req.thinking != "auto"
        val hasSchema = c.schema && req.schema != null && req.jsonMode
        val hasJson = c.json && req.jsonMode
        when {
            hasThinking && lowMessage.contains("thinking") -> c.thinking = false
            hasSchema && (lowMessage.contains("schema") || lowMessage.contains("states")) -> c.schema = false
            hasJson && (lowMessage.contains("mime") || lowMessage.contains("json")) -> {
                if (hasSchema) c.schema = false else c.json = false
            }

            hasThinking -> c.thinking = false
            hasSchema -> c.schema = false
            hasJson -> c.json = false
            else -> return false
        }
        return true
    }

    private fun buildBody(req: Request, c: Caps): String {
        val root = JSONObject()
        if (req.system.isNotBlank()) {
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", req.system))))
        }
        val parts = JSONArray()
        for (p in req.parts) {
            when (p) {
                is Part.Text -> if (p.text.isNotEmpty()) parts.put(JSONObject().put("text", p.text))
                is Part.Blob -> parts.put(
                    JSONObject().put("inlineData", JSONObject().put("mimeType", p.mime).put("data", p.base64))
                )
            }
        }
        if (parts.length() == 0) parts.put(JSONObject().put("text", "…"))
        root.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))

        val gen = JSONObject().put("maxOutputTokens", req.maxTokens)
        if (req.jsonMode && c.json) {
            gen.put("responseMimeType", "application/json")
            if (req.schema != null && c.schema) gen.put("responseJsonSchema", req.schema)
        }
        if (c.thinking && req.thinking != "auto" && req.thinking.isNotBlank()) {
            gen.put("thinkingConfig", JSONObject().put("thinkingLevel", req.thinking))
        }
        root.put("generationConfig", gen)
        return root.toString()
    }

    private fun parseSuccess(text: String, model: String): Result {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return Result.Fail(Kind.UNKNOWN, "bad response body")
        }
        val block = root.obj("promptFeedback")?.str("blockReason") ?: ""
        if (block.isNotEmpty()) return Result.Fail(Kind.BLOCKED, block)

        val cand = root.arr("candidates")?.objects()?.firstOrNull()
            ?: return Result.Fail(Kind.EMPTY, "no candidates")
        val sb = StringBuilder()
        cand.obj("content")?.arr("parts")?.objects()?.forEach { p ->
            if (!p.bool("thought")) sb.append(p.str("text"))
        }
        val out = sb.toString().trim()
        val finish = cand.str("finishReason")
        if (out.isEmpty()) {
            return if (finish in BLOCK_REASONS) Result.Fail(Kind.BLOCKED, finish)
            else Result.Fail(Kind.EMPTY, "empty text, finish=$finish")
        }
        val usage = root.obj("usageMetadata")
        return Result.Ok(
            text = out,
            model = model,
            promptTokens = usage?.int("promptTokenCount") ?: 0,
            outputTokens = (usage?.int("candidatesTokenCount") ?: 0) + (usage?.int("thoughtsTokenCount") ?: 0),
            cachedTokens = usage?.int("cachedContentTokenCount") ?: 0
        )
    }

    private fun errorMessage(body: String): String {
        if (body.isBlank()) return ""
        return try {
            val err = JSONObject(body).obj("error")
            val msg = err?.str("message") ?: ""
            val status = err?.str("status") ?: ""
            if (msg.isNotEmpty()) msg else status.ifEmpty { body.take(300) }
        } catch (_: Exception) {
            body.take(300)
        }
    }

    @Throws(IOException::class)
    private fun post(url: String, apiKey: String, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        val BLOCK_REASONS = setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "OTHER")
    }
}
