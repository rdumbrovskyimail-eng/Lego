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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиент Gemini API (REST, generateContent + streamGenerateContent SSE) без сторонних библиотек.
 *
 * Возможности:
 *  • потоковый ответ по SSE с инкрементальным коллбеком;
 *  • автоматическая лестница совместимости при 400 (thinkingConfig → tools → safetySettings → schema → jsonMode);
 *  • запасные модели при 404 (поддерживает отдельные цепочки для быстрой и глубокой моделей);
 *  • инструменты Google Search и URL Context для чтения статей и проверки фактов/базовых ставок.
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
        val retries: Int = 2,
        val useSearch: Boolean = false,
        val useUrlContext: Boolean = false
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
            fun human(): String = when (kind) {
                Kind.NO_KEY -> "Не указан API-ключ Gemini. Добавь его в настройках."
                Kind.NETWORK -> "Нет связи с сервером. Проверь интернет."
                Kind.AUTH -> "API-ключ не принят. Проверь ключ в настройках."
                Kind.QUOTA -> "Превышен лимит запросов Gemini. Подожди немного или проверь квоту ключа."
                Kind.OVERLOAD -> "Сервер Gemini перегружен. Попробуй ещё раз через минуту."
                Kind.BLOCKED -> "Ответ был ограничен фильтром безопасности API. Попробуй переформулировать иначе."
                Kind.MODEL -> "Модель недоступна для этого ключа. Проверь название модели в настройках."
                Kind.EMPTY -> "Модель вернула пустой ответ. Попробуй ещё раз."
                Kind.BAD_REQUEST -> "Сервер отклонил запрос: ${message.take(160)}"
                Kind.UNKNOWN -> "Неизвестная ошибка: ${message.take(160)}"
            }
        }
    }

    private class Caps(
        var thinking: Boolean = true,
        var tools: Boolean = true,
        var safety: Boolean = true,
        var schema: Boolean = true,
        var json: Boolean = true
    ) {
        fun copy(): Caps = Caps(thinking, tools, safety, schema, json)
    }

    private val caps = ConcurrentHashMap<String, Caps>()
    private val resolved = ConcurrentHashMap<String, String>()

    fun effectiveModel(preferred: String): String = resolved[preferred] ?: preferred

    suspend fun generate(
        apiKey: String,
        preferredModel: String,
        req: Request,
        fallbackModels: List<String> = Settings.FALLBACK_MODELS
    ): Result = runWithFallback(apiKey, preferredModel, fallbackModels) { model ->
        generateWith(apiKey, model, req, streamCallback = null)
    }

    suspend fun streamGenerate(
        apiKey: String,
        preferredModel: String,
        req: Request,
        fallbackModels: List<String> = Settings.FALLBACK_MODELS,
        onAccumulatedText: suspend (String) -> Unit
    ): Result = runWithFallback(apiKey, preferredModel, fallbackModels) { model ->
        generateWith(apiKey, model, req, streamCallback = onAccumulatedText)
    }

    private suspend fun runWithFallback(
        apiKey: String,
        preferredModel: String,
        fallbackModels: List<String>,
        call: suspend (String) -> Result
    ): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.Fail(Kind.NO_KEY, "no api key")
        val wanted = preferredModel.trim().removePrefix("models/").ifEmpty { Settings.DEFAULT_MODEL }
        val candidates = LinkedHashSet<String>()
        resolved[wanted]?.let { candidates.add(it) }
        candidates.add(wanted)
        candidates.addAll(fallbackModels)

        var last: Result.Fail = Result.Fail(Kind.UNKNOWN, "no attempt")
        for (model in candidates) {
            when (val r = call(model)) {
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

    private suspend fun generateWith(
        apiKey: String,
        model: String,
        req: Request,
        streamCallback: (suspend (String) -> Unit)?
    ): Result {
        val stored = caps[model] ?: Caps()
        val c = stored.copy()
        var transient = 0
        var downgrades = 0
        var forceNonStream = false

        while (true) {
            val body = buildBody(req, c)
            val useStream = streamCallback != null && !forceNonStream

            if (useStream) {
                val streamOutcome = try {
                    postStream("$BASE/models/$model:streamGenerateContent?alt=sse", apiKey, body, streamCallback!!)
                } catch (e: IOException) {
                    if (transient < req.retries) {
                        transient++
                        delay(800L * transient * transient)
                        continue
                    }
                    // Если стриминг оборвался, пробуем обычный запрос как страховку.
                    forceNonStream = true
                    continue
                } catch (e: Exception) {
                    forceNonStream = true
                    continue
                }

                when (streamOutcome) {
                    is StreamOutcome.Ok -> {
                        if (downgrades > 0) caps[model] = c
                        return streamOutcome.result
                    }

                    is StreamOutcome.HttpError -> {
                        val handled = handleHttpError(
                            code = streamOutcome.code,
                            text = streamOutcome.body,
                            model = model,
                            req = req,
                            c = c,
                            transient = transient,
                            downgrades = downgrades
                        )
                        when (handled) {
                            is ErrorStep.RetryTransient -> {
                                transient++
                                delay(handled.delayMs)
                                continue
                            }

                            is ErrorStep.RetryDowngrade -> {
                                downgrades++
                                continue
                            }

                            is ErrorStep.Fatal -> return handled.fail
                        }
                    }

                    StreamOutcome.FallbackToUnary -> {
                        forceNonStream = true
                        continue
                    }
                }
            } else {
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
                if (code in 200..299) {
                    val parsed = parseSuccess(text, model)
                    if (parsed is Result.Ok && downgrades > 0) caps[model] = c
                    if (parsed is Result.Fail && parsed.kind == Kind.EMPTY && transient < req.retries) {
                        transient++
                        delay(600L)
                        continue
                    }
                    return parsed
                }

                when (val handled = handleHttpError(code, text, model, req, c, transient, downgrades)) {
                    is ErrorStep.RetryTransient -> {
                        transient++
                        delay(handled.delayMs)
                        continue
                    }

                    is ErrorStep.RetryDowngrade -> {
                        downgrades++
                        continue
                    }

                    is ErrorStep.Fatal -> return handled.fail
                }
            }
        }
    }

    private sealed class ErrorStep {
        data class RetryTransient(val delayMs: Long) : ErrorStep()
        object RetryDowngrade : ErrorStep()
        data class Fatal(val fail: Result.Fail) : ErrorStep()
    }

    private fun handleHttpError(
        code: Int,
        text: String,
        model: String,
        req: Request,
        c: Caps,
        transient: Int,
        downgrades: Int
    ): ErrorStep = when {
        code == 400 -> {
            val msg = errorMessage(text)
            val low = msg.lowercase()
            when {
                low.contains("api key") || low.contains("api_key") -> ErrorStep.Fatal(Result.Fail(Kind.AUTH, msg, code))
                low.contains("not found") && low.contains("model") -> ErrorStep.Fatal(Result.Fail(Kind.MODEL, msg, code))
                else -> {
                    val changed = downgrade(c, low, req)
                    if (changed && downgrades < 6) {
                        Logx.w("400 from $model → downgrade #$downgrades: $msg")
                        ErrorStep.RetryDowngrade
                    } else {
                        ErrorStep.Fatal(Result.Fail(Kind.BAD_REQUEST, msg, code))
                    }
                }
            }
        }

        code == 401 || code == 403 -> ErrorStep.Fatal(Result.Fail(Kind.AUTH, errorMessage(text), code))
        code == 404 -> ErrorStep.Fatal(Result.Fail(Kind.MODEL, errorMessage(text), code))
        code == 429 -> if (transient < req.retries) ErrorStep.RetryTransient(2500L * (transient + 1))
        else ErrorStep.Fatal(Result.Fail(Kind.QUOTA, errorMessage(text), code))

        code in 500..599 -> if (transient < req.retries) ErrorStep.RetryTransient(1500L * (transient + 1))
        else ErrorStep.Fatal(Result.Fail(Kind.OVERLOAD, errorMessage(text), code))

        else -> ErrorStep.Fatal(Result.Fail(Kind.UNKNOWN, "HTTP $code ${errorMessage(text)}", code))
    }

    private fun downgrade(c: Caps, lowMessage: String, req: Request): Boolean {
        val hasThinking = c.thinking && req.thinking != "auto"
        val hasTools = c.tools && (req.useSearch || req.useUrlContext)
        val hasSafety = c.safety
        val hasSchema = c.schema && req.schema != null && req.jsonMode
        val hasJson = c.json && req.jsonMode

        when {
            hasThinking && lowMessage.contains("thinking") -> c.thinking = false
            hasTools && (lowMessage.contains("tool") || lowMessage.contains("search") || lowMessage.contains("url_context") || lowMessage.contains("controlled generation")) -> {
                if (hasSchema) c.schema = false
                else if (hasJson) c.json = false
                else c.tools = false
            }

            hasSafety && (lowMessage.contains("safety") || lowMessage.contains("harm")) -> c.safety = false
            hasSchema && (lowMessage.contains("schema") || lowMessage.contains("responsejsonschema")) -> c.schema = false
            hasJson && (lowMessage.contains("mime") || lowMessage.contains("json")) -> {
                if (hasSchema) c.schema = false else c.json = false
            }

            hasThinking -> c.thinking = false
            hasSchema -> c.schema = false
            hasJson -> c.json = false
            hasTools -> c.tools = false
            hasSafety -> c.safety = false
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

        if (c.tools && (req.useSearch || req.useUrlContext)) {
            val toolsArr = JSONArray()
            if (req.useSearch) toolsArr.put(JSONObject().put("google_search", JSONObject()))
            if (req.useUrlContext) toolsArr.put(JSONObject().put("url_context", JSONObject()))
            root.put("tools", toolsArr)
        }

        if (c.safety) {
            val safetyArr = JSONArray()
            for (cat in SAFETY_CATEGORIES) {
                safetyArr.put(JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"))
            }
            root.put("safetySettings", safetyArr)
        }

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

    private sealed class StreamOutcome {
        data class Ok(val result: Result.Ok) : StreamOutcome()
        data class HttpError(val code: Int, val body: String) : StreamOutcome()
        object FallbackToUnary : StreamOutcome()
    }

    @Throws(IOException::class)
    private suspend fun postStream(
        url: String,
        apiKey: String,
        body: String,
        onAccumulatedText: suspend (String) -> Unit
    ): StreamOutcome {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("x-goog-api-key", apiKey)

            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                return StreamOutcome.HttpError(code, errBody)
            }

            val acc = StringBuilder()
            var promptTokens = 0
            var outputTokens = 0
            var cachedTokens = 0
            var blockReason = ""
            var finishReason = ""

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                val dataBuf = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("data:")) {
                        if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                        dataBuf.append(line.substring(5).trimStart())
                    } else if (line.isBlank() && dataBuf.isNotEmpty()) {
                        val payload = dataBuf.toString()
                        dataBuf.setLength(0)
                        if (payload == "[DONE]") continue
                        val root = try {
                            JSONObject(payload)
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        val block = root.obj("promptFeedback")?.str("blockReason") ?: ""
                        if (block.isNotEmpty()) blockReason = block

                        val cand = root.arr("candidates")?.objects()?.firstOrNull()
                        val fr = cand?.str("finishReason") ?: ""
                        if (fr.isNotEmpty()) finishReason = fr

                        var appended = false
                        cand?.obj("content")?.arr("parts")?.objects()?.forEach { p ->
                            if (!p.bool("thought")) {
                                val t = p.str("text")
                                if (t.isNotEmpty()) {
                                    acc.append(t)
                                    appended = true
                                }
                            }
                        }

                        root.obj("usageMetadata")?.let { usage ->
                            promptTokens = usage.int("promptTokenCount", promptTokens)
                            val candTok = usage.int("candidatesTokenCount", 0)
                            val thTok = usage.int("thoughtsTokenCount", 0)
                            if (candTok + thTok > 0) outputTokens = candTok + thTok
                            cachedTokens = usage.int("cachedContentTokenCount", cachedTokens)
                        }

                        if (appended) {
                            onAccumulatedText(acc.toString())
                        }
                    }
                }
            }

            val finalStr = acc.toString().trim()
            if (finalStr.isEmpty()) {
                if (blockReason.isNotEmpty() || finishReason in BLOCK_REASONS) {
                    return StreamOutcome.HttpError(200, "").let {
                        StreamOutcome.Ok(Result.Ok("", "", 0, 0, 0)) // обработается фолбэком ниже
                    }.let { StreamOutcome.FallbackToUnary }
                }
                return StreamOutcome.FallbackToUnary
            }

            val modelName = url.substringAfter("/models/").substringBefore(":")
            return StreamOutcome.Ok(
                Result.Ok(
                    text = finalStr,
                    model = modelName,
                    promptTokens = promptTokens,
                    outputTokens = outputTokens,
                    cachedTokens = cachedTokens
                )
            )
        } finally {
            conn.disconnect()
        }
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
        val SAFETY_CATEGORIES = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )
    }
}