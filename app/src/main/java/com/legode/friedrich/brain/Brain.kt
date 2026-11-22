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
 * Мозг Атласа — оркестратор психо-эмоционального движка:
 *  • respond()    — мгновенный перехват CRISIS через SafetyGuard, либо контекстный ответ на базе RAG и Gemini;
 *  • kickoff()    — Атлас начинает беседу первым (или даёт сильный офлайн-старт);
 *  • pingCycle()  — проактивное СМС по расписанию (Gemini, а без сети — умный офлайн-банк из KnowledgeBase);
 *  • reflect()    — периодический психологический разбор динамики и обновление тактики в досье;
 *  • summarize()  — сжатие длинных бесед без потери смысла.
 */
class Brain(
    private val context: Context,
    private val repo: Repository,
    private val gemini: GeminiClient,
    private val settings: SettingsStore,
    private val runtime: RuntimeState,
    private val attachments: AttachmentStore,
    private val kb: KnowledgeBase,
    private val notifier: Notifier,
    private val alarms: AlarmScheduler,
    private val scope: CoroutineScope
) {
    private val builder = ContextBuilder(repo, settings, attachments, kb)
    private val memory = MemoryEngine(repo)
    private val planner = PingPlanner(repo, runtime)
    private val rnd = Random(System.nanoTime())

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val handled = ConcurrentHashMap<Long, Long>()
    private val pingLock = Mutex()
    private val reflecting = AtomicBoolean(false)
    private val summarizing = AtomicBoolean(false)

    private val typingState = MutableStateFlow<Set<Long>>(emptySet())

    /** В каких чатах Атлас сейчас «печатает». */
    val typing: StateFlow<Set<Long>> = typingState.asStateFlow()

    /** Какой чат сейчас открыт на экране (0 — никакой): туда не шлём пуш-уведомление. */
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

    /** Переспросить: прежний ответ уже удалён, отвечаем на последнее сообщение заново. */
    fun requestRegenerate(sessionId: Long) {
        scope.launch {
            handled.remove(sessionId)
            respond(sessionId, paced = true)
        }
    }

    /** @return текст ошибки или null, если всё прошло штатно. */
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

                    // Критический протокол: при остром риске самоповреждения не ждём сеть/фильтры API.
                    if (built.assessment.level == SafetyGuard.RiskLevel.CRISIS) {
                        val userName = repo.profile().name
                        val crisisReply = Reply(
                            messages = SafetyGuard.crisisResponse(userName),
                            quick = SafetyGuard.crisisQuickReplies(),
                            mood = 1,
                            emotion = "кризис"
                        )
                        if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                        deliver(session, crisisReply, "chat", paced = false, usage = null)
                        handled[sessionId] = maxOf(built.lastUserId, lastUser.id)
                        break
                    }

                    val result = call(built, Protocol.chatSchema, 6000, 2)
                    if (result is GeminiClient.Result.Fail) {
                        // Если API заблокировал эмоциональный текст фильтром SAFETY — даём тёплую страховку, а не ошибку.
                        if (result.kind == GeminiClient.Result.Kind.BLOCKED) {
                            val userName = repo.profile().name
                            val fallbackReply = Reply(
                                messages = SafetyGuard.blockedFallback(built.assessment.level, userName),
                                quick = listOf("Нужно заземление 🌊", "Разберём по фактам", "Давай дальше"),
                                emotion = "тяжесть"
                            )
                            if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                            deliver(session, fallbackReply, "chat", paced = paced, usage = null)
                            handled[sessionId] = maxOf(built.lastUserId, lastUser.id)
                            break
                        }

                        failure = result.human()
                        runtime.lastError = Timex.hhmm(now) + " · " + result.kind.name + " · " + result.message.take(200)
                        markFailed(lastUser.id, failure)
                        break
                    }

                    val ok = result as GeminiClient.Result.Ok
                    val reply = Protocol.parse(ok.text)
                    if (reply == null) {
                        failure = "Ответ сорвался. Нажми «Повторить»."
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

    /** Атлас начинает новую беседу первым. */
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
                        deliver(
                            session,
                            Reply(
                                messages = Persona.offlineGreeting(name),
                                quick = listOf("Я в тупике 🧱", "Накрыла тревога 🌊", "Апатия и ступор ⚡")
                            ),
                            "chat",
                            paced = true,
                            usage = null
                        )
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

    /** Применяет аналитику в память, кладёт СМС в чат, обновляет сессию и шлёт уведомление. */
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
            if (pacing && i > 0) delay((320L + text.length * 10L).coerceAtMost(1400L))
            val visible = visibleSession.value == session.id
            repo.addAtlasMessage(
                sessionId = session.id, text = text, kind = kind,
                quick = if (last) reply.quick else emptyList(),
                meta = if (last) meta else "",
                countUnread = !visible
            )
        }

        if (delta.planCreated.isNotEmpty()) {
            repo.addInfo(session.id, "📋 План прорыва «${delta.planCreated}» зафиксирован: ${delta.planSteps} шаг(ов). Вперёд!")
        }
        if (delta.planFinished.isNotEmpty()) {
            repo.addInfo(session.id, "🔥 План «${delta.planFinished}» отработан полностью")
        }
        if (delta.stageUp.isNotEmpty()) {
            repo.addInfo(session.id, "🏔️ Уровень устойчивости вырос: ${delta.stageUp}")
            repo.addJournal("milestone", "Достигнута стадия устойчивости: ${delta.stageUp}", now)
        }

        val fresh = repo.sessionDao.get(session.id)
        if (fresh != null) {
            var updated = fresh.copy(askedRef = delta.newAskedRef, turnCount = fresh.turnCount + 1)
            if (delta.newPlanId >= 0L) updated = updated.copy(planId = delta.newPlanId)
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

    /** Ответ пользователя прямо из шторки уведомлений. */
    suspend fun replyFromNotification(sessionId: Long, text: String) {
        if (repo.sessionDao.get(sessionId) == null) return
        repo.addUserMessage(sessionId, text, emptyList())
        repo.sessionDao.clearUnread(sessionId)
        postNotification(sessionId, alert = false)
        val error = respond(sessionId, paced = false)
        if (error != null) {
            repo.addAtlasMessage(
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
        val title = if (session.isHome) Persona.NAME else "${Persona.NAME} · ${session.title}"
        notifier.showChat(sessionId, title, lines, session.unread, alert)
    }

    suspend fun markRead(sessionId: Long) {
        repo.sessionDao.clearUnread(sessionId)
        notifier.cancelChat(sessionId)
    }

    // ───────────────────────── Проактивные «СМС» по расписанию ─────────────────────────

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
        var kbId = ""
        var usage: GeminiClient.Result.Ok? = null
        var anchors: List<AnchorNote> = emptyList()
        var mood = 0
        var emotion = ""

        if (settings.apiKey().isNotBlank() && online()) {
            val built = builder.ping(session, plan, now)
            val result = call(built, Protocol.chatSchema, 2500, 1)
            if (result is GeminiClient.Result.Ok) {
                val reply = Protocol.parse(result.text)
                if (reply != null) {
                    texts = reply.messages.take(2)
                    quick = reply.quick
                    anchors = reply.anchors
                    mood = reply.mood
                    emotion = reply.emotion
                    if (reply.asked.isNotEmpty()) asked = reply.asked
                    usage = result
                }
            } else if (result is GeminiClient.Result.Fail) {
                runtime.lastError = Timex.hhmm(now) + " · пинг · " + result.kind.name + " · " + result.message.take(200)
            }
        }

        if (texts.isEmpty()) {
            source = "offline"
            val due = repo.anchorDao.due(now, 5).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }
            val lastUserMsg = repo.messageDao.recentDesc(session.id, 10)
                .firstOrNull { it.role == Repository.ROLE_USER }?.text.orEmpty()
            val states = kb.inferStates(lastUserMsg, now) + plan.states
            val recentKbIds = repo.pingDao.recentKbIds(24).toHashSet()
            val off = OfflineBank.make(
                kb = kb,
                rnd = rnd,
                dueAnchorPhrase = due?.phrase,
                dueAnchorWhy = due?.why,
                states = states,
                unanswered = unanswered,
                excludeKbIds = recentKbIds,
                now = now
            )
            texts = listOf(off.text)
            quick = off.quick
            kind = off.kind
            kbId = off.kbId
            asked = if (off.kind == "anchor_due" && due != null) "A${due.id}" else ""
            anchors = emptyList()
        }

        // Применяем память пинга (новые якоря и ссылку на заданный шаг/якорь, без оценки ответа до отклика).
        val delta = memory.apply(
            session.copy(askedRef = ""),
            Reply(messages = texts, quick = quick, mood = mood, emotion = emotion, asked = asked, anchors = anchors),
            now
        )
        val finalAsked = delta.newAskedRef.ifEmpty { if (source == "offline") asked else plan.askedRef }
        val visible = visibleSession.value == session.id
        var lastId = 0L
        for ((i, text) in texts.withIndex()) {
            val last = i == texts.lastIndex
            val meta = if (last) {
                val o = JSONObject().put("ping", kind).put("source", source)
                if (delta.anchorsAdded > 0) o.put("anchors", delta.anchorsAdded)
                if (usage != null) o.put("in", usage.promptTokens).put("out", usage.outputTokens).put("model", usage.model)
                o.toString()
            } else ""
            lastId = repo.addAtlasMessage(session.id, text, "ping", if (last) quick else emptyList(), meta, !visible)
        }
        repo.sessionDao.setAsked(session.id, finalAsked)
        repo.recordPing(session.id, lastId, kind, source, kbId, now)
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

    /** Проверка «расписание живо?» — при запуске, после перезагрузки и сторожем каждые 15 минут. */
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

    /** Раз в ~25 сообщений пользователя Атлас пересматривает досье: пишет заметки и тактику. */
    suspend fun reflect(force: Boolean): Boolean {
        if (settings.apiKey().isBlank()) return false
        if (!reflecting.compareAndSet(false, true)) return false
        try {
            val now = Timex.now()
            val p = repo.profile()
            val userCount = repo.messageDao.userCount()
            val since = userCount - p.userMsgsAtReflection
            val hours = (now - p.lastReflectionAt) / Srs.HOUR
            if (!force && !(since >= 25 || (since >= 10 && hours >= 20))) return false
            if (userCount == 0) return false

            val built = builder.reflection(now)
            val result = call(built, Protocol.reflectionSchema, 1800, 1, "low")
            if (result !is GeminiClient.Result.Ok) return false
            val obj = Protocol.extractObject(result.text) ?: return false
            val note = obj.str("note").trim()
            val plan = obj.str("plan").trim()
            if (note.isEmpty() && plan.isEmpty()) return false
            if (note.isNotEmpty()) repo.addJournal("reflection", note, now)
            obj.arr("facts")?.strings()?.take(3)?.forEach { repo.addFact(it, "atlas", now) }
            repo.updateProfile {
                it.copy(
                    tactic = if (plan.isNotEmpty()) plan.take(900) else it.tactic,
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

    /** Длинную беседу сжимаем: старые сообщения превращаются в выжимку. */
    suspend fun summarize(sessionId: Long) {
        if (settings.apiKey().isBlank()) return
        if (!summarizing.compareAndSet(false, true)) return
        try {
            val session = repo.sessionDao.get(sessionId) ?: return
            if (repo.messageDao.countAfter(sessionId, session.summarizedUpToId) < 44) return
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
            system = "", parts = listOf(GeminiClient.Part.Text("Ответь только одним словом: OK")),
            schema = null, jsonMode = false, maxTokens = 256, thinking = "auto", retries = 0
        )
    )

    fun effectiveModel(): String = gemini.effectiveModel(settings.value.model)
}