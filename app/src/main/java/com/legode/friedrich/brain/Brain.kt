package com.legode.friedrich.brain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.notify.AlarmScheduler
import com.legode.friedrich.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Мозг Фридриха — оркестратор агента.
 *
 *  respond()    — ответ на сообщения ученика (контекст → Gemini → разбор → память → СМС в чат);
 *  kickoff()    — Friedrich начинает беседу первым;
 *  pingCycle()  — случайное «смс» по расписанию (ИИ, а без сети — офлайн-банк) и постановка следующего;
 *  reflect()    — периодический «разбор полётов»: заметки и план в досье;
 *  summarize()  — сжатие старой части длинной беседы.
 */
class Brain(
    private val context: Context,
    private val repo: Repository,
    private val gemini: GeminiClient,
    private val settings: SettingsStore,
    private val runtime: RuntimeState,
    private val attachments: AttachmentStore,
    private val notifier: Notifier,
    private val alarms: AlarmScheduler,
    private val scope: CoroutineScope
) {
    private val builder = ContextBuilder(repo, settings, attachments)
    private val memory = MemoryEngine(repo)
    private val planner = PingPlanner(repo, runtime)
    private val rnd = Random(System.nanoTime())

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val handled = ConcurrentHashMap<Long, Long>()
    private val pingLock = Mutex()
    private val reflecting = AtomicBoolean(false)
    private val summarizing = AtomicBoolean(false)

    private val typingState = MutableStateFlow<Set<Long>>(emptySet())

    /** В каких чатах Friedrich сейчас «печатает». */
    val typing: StateFlow<Set<Long>> = typingState.asStateFlow()

    /** Какой чат сейчас открыт на экране (0 — никакой): туда не шлём уведомления. */
    val visibleSession = MutableStateFlow(0L)

    private fun setTyping(sessionId: Long, on: Boolean) {
        synchronized(typingState) {
            typingState.value = if (on) typingState.value + sessionId else typingState.value - sessionId
        }
    }

    // ───────────────────────── Ответ на сообщения ─────────────────────────

    fun requestRespond(sessionId: Long) {
        scope.launch { respond(sessionId, paced = true) }
    }

    fun requestKickoff(sessionId: Long) {
        scope.launch { kickoff(sessionId) }
    }

    /** Переспросить: прежний ответ уже удалён, отвечаем на последнее сообщение ученика заново. */
    fun requestRegenerate(sessionId: Long) {
        scope.launch {
            handled.remove(sessionId)
            respond(sessionId, paced = true)
        }
    }

    /** @return текст ошибки или null, если всё хорошо. */
    suspend fun respond(sessionId: Long, paced: Boolean): String? {
        val lock = locks.getOrPut(sessionId) { Mutex() }
        val error: String? = lock.withLock {
            var failure: String? = null
            var rounds = 0
            while (rounds < 3) {
                rounds++
                val session = repo.sessionDao.get(sessionId) ?: break
                val lastUser = repo.messageDao.recentDesc(sessionId, 20)
                    .firstOrNull { it.role == Repository.ROLE_USER } ?: break
                if (lastUser.id <= (handled[sessionId] ?: 0L)) break

                setTyping(sessionId, true)
                try {
                    val now = Timex.now()
                    val built = builder.chatTurn(session, kickoff = false, now = now)
                    val result = call(built, Protocol.chatSchema, 6000, 2)
                    if (result is GeminiClient.Result.Fail) {
                        failure = result.human()
                        runtime.lastError = Timex.hhmm(now) + " · " + result.kind.name + " · " + result.message.take(200)
                        markFailed(lastUser.id, failure)
                        break
                    }
                    val ok = result as GeminiClient.Result.Ok
                    val reply = Protocol.parse(ok.text)
                    if (reply == null) {
                        failure = "Friedrich ответил неразборчиво. Нажми «Повторить»."
                        markFailed(lastUser.id, failure)
                        break
                    }
                    if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                    deliver(session, reply, "chat", paced, ok)
                    handled[sessionId] = maxOf(built.lastUserId, lastUser.id)
                } catch (t: Throwable) {
                    Logx.e("respond failed", t)
                    failure = "Внутренняя ошибка: " + (t.message ?: t.javaClass.simpleName)
                    markFailed(lastUser.id, failure)
                    break
                } finally {
                    setTyping(sessionId, false)
                }
            }
            failure
        }
        scope.launch {
            summarize(sessionId)
            reflect(false)
        }
        return error
    }

    private suspend fun markFailed(messageId: Long, error: String) {
        repo.messageDao.setStatus(messageId, "error", JSONObject().put("error", error).toString())
    }

    /** Friedrich пишет первым в новой беседе. */
    suspend fun kickoff(sessionId: Long) {
        val lock = locks.getOrPut(sessionId) { Mutex() }
        lock.withLock {
            val session = repo.sessionDao.get(sessionId) ?: return@withLock
            if (repo.messageDao.count(sessionId) > 0) return@withLock
            setTyping(sessionId, true)
            try {
                val now = Timex.now()
                val built = builder.chatTurn(session, kickoff = true, now = now)
                val result = call(built, Protocol.chatSchema, 4000, 1)
                val reply = if (result is GeminiClient.Result.Ok) Protocol.parse(result.text) else null
                if (reply != null) {
                    deliver(session, reply, "chat", paced = true, usage = result as GeminiClient.Result.Ok)
                } else {
                    val fail = result as? GeminiClient.Result.Fail
                    if (session.isHome) {
                        val name = repo.profile().name
                        deliver(session, Reply(messages = Persona.offlineGreeting(name)), "chat", paced = true, usage = null)
                    }
                    if (fail != null) {
                        runtime.lastError = Timex.hhmm(now) + " · " + fail.kind.name + " · " + fail.message.take(200)
                        repo.addInfo(sessionId, "⚠️ " + fail.human())
                    }
                }
            } catch (t: Throwable) {
                Logx.e("kickoff failed", t)
            } finally {
                setTyping(sessionId, false)
            }
        }
    }

    private suspend fun call(
        built: ContextBuilder.Built,
        schema: JSONObject,
        maxTokens: Int,
        retries: Int,
        thinking: String? = null
    ): GeminiClient.Result {
        val s = settings.value
        return gemini.generate(
            settings.apiKey(), s.model,
            GeminiClient.Request(
                system = built.system, parts = built.parts, schema = schema, jsonMode = true,
                maxTokens = maxTokens, thinking = thinking ?: s.thinking, retries = retries
            )
        )
    }

    /** Применяет память, кладёт СМС в чат (с «человеческими» паузами), обновляет сессию и уведомление. */
    private suspend fun deliver(
        session: SessionEntity,
        reply: Reply,
        kind: String,
        paced: Boolean,
        usage: GeminiClient.Result.Ok?
    ) {
        val now = Timex.now()
        val delta = memory.apply(session, reply, now)
        val texts = reply.messages
        val meta = buildMeta(delta, usage)
        val pacing = paced && settings.value.humanPacing

        for ((i, text) in texts.withIndex()) {
            val last = i == texts.lastIndex
            if (pacing && i > 0) delay((350L + text.length * 12L).coerceAtMost(1600L))
            val visible = visibleSession.value == session.id
            repo.addFriedrichMessage(
                sessionId = session.id, text = text, kind = kind,
                quick = if (last) reply.quick else emptyList(),
                meta = if (last) meta else "",
                countUnread = !visible
            )
        }

        if (delta.listCreated.isNotEmpty()) {
            repo.addInfo(session.id, "📋 Список «${delta.listCreated}» сохранён: ${delta.listItems} эл. Отрабатываем!")
        }
        if (delta.listFinished.isNotEmpty()) {
            repo.addInfo(session.id, "🎉 Список «${delta.listFinished}» отработан полностью")
        }
        if (delta.levelUp.isNotEmpty()) {
            repo.addInfo(session.id, "🏅 Новый уровень: ${delta.levelUp}")
            repo.addJournal("milestone", "Достигнут уровень ${delta.levelUp}", now)
        }

        val fresh = repo.sessionDao.get(session.id)
        if (fresh != null) {
            var updated = fresh.copy(askedRef = delta.newAskedRef, turnCount = fresh.turnCount + 1)
            if (delta.newListId >= 0L) updated = updated.copy(listId = delta.newListId)
            if (reply.title.isNotBlank() && !fresh.titleLocked && !fresh.isHome) {
                updated = updated.copy(title = reply.title, titleLocked = true)
            }
            repo.sessionDao.update(updated)
        }

        if (visibleSession.value != session.id) postNotification(session.id, alert = true)
    }

    private fun buildMeta(delta: MemoryEngine.Delta, usage: GeminiClient.Result.Ok?): String {
        val base = delta.toMeta()
        if (usage == null) return base
        val o = if (base.isEmpty()) JSONObject() else JSONObject(base)
        o.put("in", usage.promptTokens).put("out", usage.outputTokens).put("cached", usage.cachedTokens)
        o.put("model", usage.model)
        return o.toString()
    }

    /** Ответ ученика из шторки уведомлений: сохраняем, обновляем уведомление, отвечаем. */
    suspend fun replyFromNotification(sessionId: Long, text: String) {
        if (repo.sessionDao.get(sessionId) == null) return
        repo.addUserMessage(sessionId, text, emptyList())
        repo.sessionDao.clearUnread(sessionId)
        postNotification(sessionId, alert = false)
        val error = respond(sessionId, paced = false)
        if (error != null) {
            repo.addFriedrichMessage(
                sessionId, "⚠️ $error", "info", emptyList(), "", countUnread = visibleSession.value != sessionId
            )
            postNotification(sessionId, alert = false)
        }
    }

    suspend fun postNotification(sessionId: Long, alert: Boolean) {
        val session = repo.sessionDao.get(sessionId) ?: return
        val lines = repo.messageDao.recentDesc(sessionId, 6)
            .filter { it.role != Repository.ROLE_INFO }
            .reversed()
            .map { m ->
                val body = Norm.plain(m.text).take(600).ifBlank {
                    Attachment.listFromJson(m.attachments).firstOrNull()?.label() ?: "…"
                }
                Notifier.Line(body, m.role == Repository.ROLE_USER, m.createdAt)
            }
        val title = if (session.isHome) "Friedrich" else "Friedrich · ${session.title}"
        notifier.showChat(sessionId, title, lines, session.unread, alert)
    }

    suspend fun markRead(sessionId: Long) {
        repo.sessionDao.clearUnread(sessionId)
        notifier.cancelChat(sessionId)
    }

    // ───────────────────────── «СМС» по расписанию ─────────────────────────

    /** Полный цикл: написать (если уместно) и поставить следующий будильник. */
    suspend fun pingCycle(force: Boolean, forcedKind: String?) {
        val status = try {
            firePing(force, forcedKind)
        } catch (t: Throwable) {
            Logx.e("ping failed", t)
            "error"
        }
        Logx.d("ping status: $status")
        if (status == "active" || status == "busy") {
            val at = Timex.now() + rnd.nextInt(5, 10) * 60_000L
            runtime.nextPingAt = at
            alarms.arm(at)
        } else {
            rearm()
        }
    }

    private suspend fun firePing(force: Boolean, forcedKind: String?): String = pingLock.withLock {
        val s = settings.value
        val now = Timex.now()
        if (!s.onboarded) return@withLock "not_onboarded"
        if (!force) {
            if (!s.pingsEnabled) return@withLock "disabled"
            if (runtime.snoozeUntil > now) return@withLock "snoozed"
            if (PingScheduler.inQuiet(Timex.minuteOfDay(now), s)) return@withLock "quiet"
            if (repo.pingDao.countSince(startOfDay(now)) >= s.dailyPingCap) return@withLock "cap"
            val lastUserAt = repo.messageDao.lastUserAt() ?: 0L
            if (now - lastUserAt < 3 * 60_000L) return@withLock "active"
            if (typingState.value.isNotEmpty()) return@withLock "busy"
        }

        val session = targetSession()
        val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
        val plan = planner.plan(session, s, now, rnd, forcedKind)

        var texts: List<String> = emptyList()
        var quick: List<String> = emptyList()
        var asked = plan.askedRef
        var source = "ai"
        var kind = plan.kind
        var usage: GeminiClient.Result.Ok? = null
        var words: List<WordNote> = emptyList()

        if (settings.apiKey().isNotBlank() && online()) {
            val built = builder.ping(session, plan, now)
            val result = call(built, Protocol.chatSchema, 2500, 1)
            if (result is GeminiClient.Result.Ok) {
                val reply = Protocol.parse(result.text)
                if (reply != null) {
                    texts = reply.messages.take(2)
                    quick = reply.quick
                    words = reply.words
                    if (reply.asked.isNotEmpty()) asked = reply.asked
                    usage = result
                }
            } else if (result is GeminiClient.Result.Fail) {
                runtime.lastError = Timex.hhmm(now) + " · пинг · " + result.kind.name + " · " + result.message.take(200)
            }
        }

        if (texts.isEmpty()) {
            source = "offline"
            val due = repo.vocabDao.due(now, 5).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }
            val off = OfflineBank.make(rnd, due?.de, due?.ru, unanswered, now)
            texts = listOf(off.text)
            quick = off.quick
            kind = off.kind
            asked = if (off.kind == "vocab_due" && due != null) "V${due.id}" else ""
            words = emptyList()
        }

        // Память пинга: новые слова и ссылка на заданный элемент (без оценок — ученик ещё не отвечал).
        val delta = memory.apply(
            session.copy(askedRef = ""),
            Reply(messages = texts, quick = quick, asked = asked, words = words),
            now
        )
        val finalAsked = delta.newAskedRef.ifEmpty { if (source == "offline") asked else plan.askedRef }
        val visible = visibleSession.value == session.id
        var lastId = 0L
        for ((i, text) in texts.withIndex()) {
            val last = i == texts.lastIndex
            val meta = if (last) {
                val o = JSONObject().put("ping", kind).put("source", source)
                if (delta.wordsAdded > 0) o.put("words", delta.wordsAdded)
                if (usage != null) o.put("in", usage.promptTokens).put("out", usage.outputTokens).put("model", usage.model)
                o.toString()
            } else ""
            lastId = repo.addFriedrichMessage(session.id, text, "ping", if (last) quick else emptyList(), meta, !visible)
        }
        repo.sessionDao.setAsked(session.id, finalAsked)
        repo.recordPing(session.id, lastId, kind, source, now)
        runtime.lastPingAt = now
        if (!visible) postNotification(session.id, alert = true)
        "sent:$kind:$source"
    }

    private suspend fun targetSession(): SessionEntity {
        val id = settings.value.pingSessionId
        if (id != 0L) repo.sessionDao.get(id)?.let { return it }
        return repo.ensureHome()
    }

    /** Ставит будильник следующего «смс». */
    suspend fun rearm(): Long {
        val s = settings.value
        if (!s.pingsEnabled || !s.onboarded) {
            alarms.cancel()
            runtime.nextPingAt = 0L
            return 0L
        }
        val now = Timex.now()
        val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
        var at = PingScheduler.next(now, s, unanswered, rnd)
        if (runtime.snoozeUntil > at) at = runtime.snoozeUntil + rnd.nextInt(1, 6) * 60_000L
        if (repo.pingDao.countSince(startOfDay(now)) >= s.dailyPingCap) {
            at = maxOf(at, nextMorning(now) + rnd.nextInt(2, 25) * 60_000L)
        }
        runtime.nextPingAt = at
        alarms.arm(at)
        return at
    }

    /** Проверка «расписание живо?» — вызывается при запуске, после перезагрузки и сторожем каждые 15 минут. */
    suspend fun ensureScheduled() {
        val s = settings.value
        if (!s.onboarded) return
        if (!s.pingsEnabled) {
            alarms.cancel()
            return
        }
        val at = runtime.nextPingAt
        val now = Timex.now()
        when {
            at <= 0L -> rearm()
            at < now - 90_000L -> pingCycle(force = false, forcedKind = null)
            else -> alarms.arm(at)
        }
    }

    suspend fun snooze(minutes: Int) {
        runtime.snoozeUntil = Timex.now() + minutes * 60_000L
        notifier.cancelAllChats()
        rearm()
    }

    suspend fun cancelSnooze() {
        runtime.snoozeUntil = 0L
        rearm()
    }

    private fun startOfDay(now: Long): Long =
        Timex.dateTime(now).toLocalDate().atStartOfDay(Timex.zone()).toInstant().toEpochMilli()

    private fun nextMorning(now: Long): Long {
        val s = settings.value
        val tomorrow = Timex.dateTime(now).toLocalDate().plusDays(1)
        val minutes = if (s.quietStartMin == s.quietEndMin) 8 * 60 else s.quietEndMin
        return tomorrow.atStartOfDay(Timex.zone()).toInstant().toEpochMilli() + minutes * 60_000L
    }

    fun online(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
        true
    }

    // ───────────────────────── Рефлексия и сжатие ─────────────────────────

    /** Раз в ~30 сообщений ученика Friedrich пересматривает досье: пишет заметки и план. */
    suspend fun reflect(force: Boolean): Boolean {
        if (settings.apiKey().isBlank()) return false
        if (!reflecting.compareAndSet(false, true)) return false
        try {
            val now = Timex.now()
            val p = repo.profile()
            val userCount = repo.messageDao.userCount()
            val since = userCount - p.userMsgsAtReflection
            val hours = (now - p.lastReflectionAt) / Srs.HOUR
            if (!force && !(since >= 30 || (since >= 10 && hours >= 20))) return false
            if (userCount == 0) return false

            val built = builder.reflection(now)
            val result = call(built, Protocol.reflectionSchema, 1800, 1, "low")
            if (result !is GeminiClient.Result.Ok) return false
            val obj = Protocol.extractObject(result.text) ?: return false
            val note = obj.str("note").trim()
            val plan = obj.str("plan").trim()
            if (note.isEmpty() && plan.isEmpty()) return false
            if (note.isNotEmpty()) repo.addJournal("reflection", note, now)
            obj.arr("facts")?.strings()?.take(3)?.forEach { repo.addFact(it, "friedrich", now) }
            repo.updateProfile {
                it.copy(
                    plan = if (plan.isNotEmpty()) plan.take(900) else it.plan,
                    lastReflectionAt = now,
                    userMsgsAtReflection = userCount
                )
            }
            return true
        } catch (t: Throwable) {
            Logx.w("reflect failed", t)
            return false
        } finally {
            reflecting.set(false)
        }
    }

    /** Длинную беседу сжимаем: старые сообщения превращаются в краткое содержание. */
    suspend fun summarize(sessionId: Long) {
        if (settings.apiKey().isBlank()) return
        if (!summarizing.compareAndSet(false, true)) return
        try {
            val session = repo.sessionDao.get(sessionId) ?: return
            if (repo.messageDao.countAfter(sessionId, session.summarizedUpToId) < 46) return
            val chunk = repo.messageDao.after(sessionId, session.summarizedUpToId, 24)
            if (chunk.size < 12) return
            val built = builder.summary(session.summary, chunk, Timex.now())
            val result = call(built, Protocol.summarySchema, 1200, 1, "minimal")
            if (result !is GeminiClient.Result.Ok) return
            val summary = Protocol.extractObject(result.text)?.str("summary")?.trim().orEmpty()
            if (summary.length < 20) return
            repo.sessionDao.setSummary(sessionId, summary.take(1500), chunk.last().id)
        } catch (t: Throwable) {
            Logx.w("summarize failed", t)
        } finally {
            summarizing.set(false)
        }
    }

    // ───────────────────────── Служебное ─────────────────────────

    suspend fun testKey(key: String, model: String): GeminiClient.Result = gemini.generate(
        key, model,
        GeminiClient.Request(
            system = "", parts = listOf(GeminiClient.Part.Text("Antworte nur mit dem Wort: OK")),
            schema = null, jsonMode = false, maxTokens = 256, thinking = "auto", retries = 0
        )
    )

    fun effectiveModel(): String = gemini.effectiveModel(settings.value.model)
}
