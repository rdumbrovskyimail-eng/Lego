package com.legode.friedrich.brain

import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.bool
import com.legode.friedrich.core.int
import com.legode.friedrich.core.obj
import com.legode.friedrich.core.objects
import com.legode.friedrich.core.str
import com.legode.friedrich.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Клиент Gemini API (REST: generateContent + streamGenerateContent по SSE) без сторонних библиотек.
 *
 *  • одна модель на всё приложение — [Settings.MODEL] (Gemini 3.1 Flash-Lite)
 *    и один уровень раздумий — [Settings.THINKING_LEVEL];
 *  • диалог уходит ролями user/model, служебный контекст — в systemInstruction;
 *  • потоковый ответ с инкрементальным коллбеком и честной отменой: отмена корутины сразу рвёт соединение;
 *  • повтор с экспоненциальной задержкой и джиттером при сетевых сбоях, 429 и 5xx;
 *  • при 400 — упрощение запроса (инструменты → схема → JSON-режим);
 *    фильтры безопасности и уровень раздумий не снимаются никогда.
 */
class GeminiClient {

    sealed class Part {
        data class Text(val text: String) : Part()
        data class Blob(val mime: String, val base64: String) : Part()
    }

    /** Один ход диалога: role = [ROLE_USER] | [ROLE_MODEL]. */
    data class Turn(val role: String, val parts: List<Part>)

    data class Request(
        val system: String,
        /** Однократный запрос (одно сообщение пользователя). Не используется, если задан [turns]. */
        val parts: List<Part> = emptyList(),
        val schema: JSONObject?,
        val jsonMode: Boolean = true,
        val maxTokens: Int = 4096,
        val retries: Int = 2,
        val useSearch: Boolean = false,
        val useUrlContext: Boolean = false,
        /** Многоходовой диалог с ролями user/model. */
        val turns: List<Turn> = emptyList()
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
                Kind.MODEL -> "Модель Gemini 3.1 Flash-Lite недоступна для этого ключа. Проверь ключ в Google AI Studio."
                Kind.EMPTY -> "Модель вернула пустой ответ. Попробуй ещё раз."
                Kind.BAD_REQUEST -> "Сервер отклонил запрос: ${message.take(160)}"
                Kind.UNKNOWN -> "Неизвестная ошибка: ${message.take(160)}"
            }
        }
    }

    /** Что из необязательных возможностей запроса сервер принимает (выясняется по ответам 400). */
    private data class Caps(val tools: Boolean = true, val schema: Boolean = true, val json: Boolean = true)

    /** Отдельно для запросов с инструментами (поиск/URL) и без — чтобы упрощение одного не портило другой. */
    private val capsByTools = ConcurrentHashMap<Boolean, Caps>()

    private fun Request.hasTools(): Boolean = useSearch || useUrlContext

    suspend fun generate(apiKey: String, req: Request): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) Result.Fail(Kind.NO_KEY, "no api key")
        else generateWith(apiKey, req, streamCallback = null)
    }

    suspend fun streamGenerate(
        apiKey: String,
        req: Request,
        onAccumulatedText: suspend (String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) Result.Fail(Kind.NO_KEY, "no api key")
        else generateWith(apiKey, req, streamCallback = onAccumulatedText)
    }

    private sealed class Outcome {
        data class Done(val result: Result) : Outcome()
        data class Http(val code: Int, val body: String) : Outcome()
    }

    private sealed class ErrorStep {
        data class RetryTransient(val delayMs: Long) : ErrorStep()
        data class RetryDowngrade(val caps: Caps) : ErrorStep()
        data class Fatal(val fail: Result.Fail) : ErrorStep()
    }

    private suspend fun generateWith(
        apiKey: String,
        req: Request,
        streamCallback: (suspend (String) -> Unit)?
    ): Result {
        var caps = capsByTools[req.hasTools()] ?: Caps()
        var transient = 0
        var downgrades = 0
        var forceUnary = false

        while (true) {
            coroutineContext.ensureActive()
            val body = buildBody(req, caps)
            val useStream = streamCallback != null && !forceUnary

            val outcome: Outcome = try {
                if (useStream) postStream(STREAM_URL, apiKey, body, streamCallback!!) else post(UNARY_URL, apiKey, body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                if (transient < req.retries) {
                    transient++
                    delay(backoff(transient, 800L))
                    continue
                }
                if (useStream) {
                    // Стрим оборвался и повторы исчерпаны — последняя попытка обычным запросом.
                    forceUnary = true
                    continue
                }
                return Result.Fail(Kind.NETWORK, e.message ?: e.javaClass.simpleName)
            } catch (e: Exception) {
                if (useStream) {
                    forceUnary = true
                    continue
                }
                return Result.Fail(Kind.UNKNOWN, e.message ?: e.javaClass.simpleName)
            }

            when (outcome) {
                is Outcome.Done -> {
                    val r = outcome.result
                    if (r is Result.Fail && r.kind == Kind.EMPTY) {
                        if (useStream) {
                            forceUnary = true
                            continue
                        }
                        if (transient < req.retries) {
                            transient++
                            delay(backoff(transient, 600L))
                            continue
                        }
                    }
                    if (r is Result.Ok && downgrades > 0) capsByTools[req.hasTools()] = caps
                    return r
                }

                is Outcome.Http -> when (val step = handleHttpError(outcome.code, outcome.body, req, caps, transient, downgrades)) {
                    is ErrorStep.RetryTransient -> {
                        transient++
                        delay(step.delayMs)
                    }

                    is ErrorStep.RetryDowngrade -> {
                        downgrades++
                        caps = step.caps
                    }

                    is ErrorStep.Fatal -> return step.fail
                }
            }
        }
    }

    private fun backoff(attempt: Int, baseMs: Long): Long {
        val exp = baseMs * (1L shl (attempt - 1).coerceIn(0, 4))
        return (exp + Random.nextLong(0, baseMs / 2 + 1)).coerceAtMost(12_000L)
    }

    private fun handleHttpError(
        code: Int,
        text: String,
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
                    val next = downgrade(c, low, req)
                    if (next != null && downgrades < 4) {
                        Logx.w("400 → упрощаю запрос (#$downgrades): $msg")
                        ErrorStep.RetryDowngrade(next)
                    } else {
                        ErrorStep.Fatal(Result.Fail(Kind.BAD_REQUEST, msg, code))
                    }
                }
            }
        }

        code == 401 || code == 403 -> ErrorStep.Fatal(Result.Fail(Kind.AUTH, errorMessage(text), code))
        code == 404 -> ErrorStep.Fatal(Result.Fail(Kind.MODEL, errorMessage(text), code))
        code == 408 || code == 429 -> if (transient < req.retries) ErrorStep.RetryTransient(backoff(transient + 1, 2500L))
        else ErrorStep.Fatal(Result.Fail(Kind.QUOTA, errorMessage(text), code))

        code in 500..599 -> if (transient < req.retries) ErrorStep.RetryTransient(backoff(transient + 1, 1500L))
        else ErrorStep.Fatal(Result.Fail(Kind.OVERLOAD, errorMessage(text), code))

        else -> ErrorStep.Fatal(Result.Fail(Kind.UNKNOWN, "HTTP $code ${errorMessage(text)}", code))
    }

    /** Упрощает запрос после 400. Фильтры безопасности и раздумья не трогаем. @return null — упрощать нечего. */
    private fun downgrade(c: Caps, low: String, req: Request): Caps? {
        val hasTools = c.tools && req.hasTools()
        val hasSchema = c.schema && req.schema != null && req.jsonMode
        val hasJson = c.json && req.jsonMode
        val toolHint = low.contains("tool") || low.contains("search") || low.contains("url_context") || low.contains("controlled generation")
        return when {
            hasTools && toolHint -> when {
                hasSchema -> c.copy(schema = false)
                hasJson -> c.copy(json = false)
                else -> c.copy(tools = false)
            }

            hasSchema && (low.contains("schema") || low.contains("responsejsonschema")) -> c.copy(schema = false)
            hasJson && (low.contains("mime") || low.contains("json")) -> if (hasSchema) c.copy(schema = false) else c.copy(json = false)
            hasSchema -> c.copy(schema = false)
            hasTools -> c.copy(tools = false)
            hasJson -> c.copy(json = false)
            else -> null
        }
    }

    private fun partJson(p: Part): JSONObject? = when (p) {
        is Part.Text -> if (p.text.isEmpty()) null else JSONObject().put("text", p.text)
        is Part.Blob -> JSONObject().put("inlineData", JSONObject().put("mimeType", p.mime).put("data", p.base64))
    }

    /** Склеивает соседние ходы одной роли и гарантирует, что диалог начинается и заканчивается пользователем. */
    private fun normalizeTurns(turns: List<Turn>): List<Turn> {
        val out = ArrayList<Turn>()
        for (t in turns) {
            val parts = t.parts.filter { it !is Part.Text || it.text.isNotEmpty() }
            if (parts.isEmpty()) continue
            val role = if (t.role == ROLE_MODEL) ROLE_MODEL else ROLE_USER
            if (out.isNotEmpty() && out.last().role == role) {
                val prev = out.removeAt(out.lastIndex)
                out.add(Turn(role, prev.parts + parts))
            } else {
                out.add(Turn(role, parts))
            }
        }
        if (out.isEmpty() || out.first().role != ROLE_USER) out.add(0, Turn(ROLE_USER, listOf(Part.Text("(начало переписки)"))))
        if (out.last().role != ROLE_USER) out.add(Turn(ROLE_USER, listOf(Part.Text("(продолжай)"))))
        return out
    }

    private fun buildBody(req: Request, c: Caps): String {
        val root = JSONObject()
        if (req.system.isNotBlank()) {
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", req.system))))
        }

        val turns = if (req.turns.isNotEmpty()) req.turns else listOf(Turn(ROLE_USER, req.parts))
        val contents = JSONArray()
        for (t in normalizeTurns(turns)) {
            val parts = JSONArray()
            for (p in t.parts) partJson(p)?.let { parts.put(it) }
            if (parts.length() == 0) parts.put(JSONObject().put("text", "…"))
            contents.put(JSONObject().put("role", t.role).put("parts", parts))
        }
        root.put("contents", contents)

        if (c.tools && req.hasTools()) {
            val toolsArr = JSONArray()
            if (req.useSearch) toolsArr.put(JSONObject().put("google_search", JSONObject()))
            if (req.useUrlContext) toolsArr.put(JSONObject().put("url_context", JSONObject()))
            root.put("tools", toolsArr)
        }

        val safetyArr = JSONArray()
        for (cat in SAFETY_CATEGORIES) {
            safetyArr.put(JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"))
        }
        root.put("safetySettings", safetyArr)

        val gen = JSONObject().put("maxOutputTokens", req.maxTokens)
        if (req.jsonMode && c.json) {
            gen.put("responseMimeType", "application/json")
            if (req.schema != null && c.schema) gen.put("responseJsonSchema", req.schema)
        }
        gen.put("thinkingConfig", JSONObject().put("thinkingLevel", Settings.THINKING_LEVEL))
        root.put("generationConfig", gen)
        return root.toString()
    }

    /** Держатель соединения, чтобы «сторож» мог разорвать его из другого потока. */
    private class ConnHolder {
        @Volatile
        var conn: HttpURLConnection? = null
    }

    /**
     * Блокирующий HTTP-обмен с честной отменой: пока идёт запрос, рядом висит «сторожевая» корутина;
     * при отмене она рвёт соединение, и блокирующее чтение мгновенно завершается.
     */
    private suspend fun <T> cancellableHttp(block: suspend (ConnHolder) -> T): T = coroutineScope {
        val holder = ConnHolder()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                holder.conn?.disconnect()
            }
        }
        try {
            block(holder)
        } finally {
            watcher.cancel()
        }
    }

    private fun open(holder: ConnHolder, url: String, apiKey: String, body: String, stream: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        holder.conn = conn
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 90_000
        conn.doOutput = true
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (stream) conn.setRequestProperty("Accept", "text/event-stream")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        val bytes = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        return conn
    }

    @Throws(IOException::class)
    private suspend fun postStream(
        url: String,
        apiKey: String,
        body: String,
        onAccumulatedText: suspend (String) -> Unit
    ): Outcome = cancellableHttp { holder ->
        val conn = open(holder, url, apiKey, body, stream = true)
        try {
            readStream(conn, onAccumulatedText)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun readStream(conn: HttpURLConnection, onAccumulatedText: suspend (String) -> Unit): Outcome {
        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Outcome.Http(code, errBody)
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
                coroutineContext.ensureActive()
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                    dataBuf.append(line.substring(5).trimStart())
                    continue
                }
                if (!line.isBlank() || dataBuf.isEmpty()) continue
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

                if (appended) onAccumulatedText(acc.toString())
            }
        }

        val finalStr = acc.toString().trim()
        if (finalStr.isEmpty()) {
            // Блокировку повторять бессмысленно: сразу BLOCKED, Brain покажет бережный запасной ответ.
            return if (blockReason.isNotEmpty() || finishReason in BLOCK_REASONS) {
                Outcome.Done(Result.Fail(Kind.BLOCKED, blockReason.ifEmpty { finishReason }))
            } else {
                Outcome.Done(Result.Fail(Kind.EMPTY, "empty stream, finish=$finishReason"))
            }
        }
        return Outcome.Done(Result.Ok(finalStr, Settings.MODEL, promptTokens, outputTokens, cachedTokens))
    }

    @Throws(IOException::class)
    private suspend fun post(url: String, apiKey: String, body: String): Outcome = cancellableHttp { holder ->
        val conn = open(holder, url, apiKey, body, stream = false)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code in 200..299) Outcome.Done(parseSuccess(text)) else Outcome.Http(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSuccess(text: String): Result {
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
            model = Settings.MODEL,
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

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"

        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val UNARY_URL = "$BASE/models/${Settings.MODEL}:generateContent"
        private val STREAM_URL = "$BASE/models/${Settings.MODEL}:streamGenerateContent?alt=sse"
        private val BLOCK_REASONS = setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "OTHER")
        private val SAFETY_CATEGORIES = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )
    }
}