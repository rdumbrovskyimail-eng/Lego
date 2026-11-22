package com.legode.friedrich.brain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.int
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.WeeklyReviewEntity
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
 * Мозг Атласа — оркестратор психо-когнитивного движка:
 *  • respond()              — SafetyGuard + SSE-стриминг (с фолбэком) + RAG + память;
 *  • kickoff()              — старт новой беседы или арены;
 *  • pingCycle()            — JITAI-пинг без гонок с чатом и двойных срабатываний;
 *  • extractAndSaveSource() — разбор книги/PDF/URL в выжимку и карточки FSRS;
 *  • assistDecision()       — аудит решения (Outside View + Pre-mortem + WOOP);
 *  • generateWeeklyReview() — Воскресный Executive Review + 6-осевой радар.
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
    data class CalibrationQuiz(val question: String, val outcome: Int, val explanation: String)

    private val builder = ContextBuilder(repo, settings, attachments, kb)
    private val memory = MemoryEngine(repo)
    private val planner = PingPlanner(repo, runtime)
    private val rnd = Random(System.nanoTime())

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val handled = ConcurrentHashMap<Long, Long>()
    private val pingLock = Mutex()
    private val reflecting = AtomicBoolean(false)
    private val summarizing = AtomicBoolean(false)
    private val weeklyRunning = AtomicBoolean(false)

    private val typingState = MutableStateFlow<Set<Long>>(emptySet())
    private val streamingState = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    val typing: StateFlow<Set<Long>> = typingState.asStateFlow()
    val streaming: StateFlow<Map<Long, List<String>>> = streamingState.asStateFlow()
    val visibleSession = MutableStateFlow(0L)

    private fun setTyping(sessionId: Long, on: Boolean) {
        synchronized(typingState) {
            typingState.value = if (on) typingState.value + sessionId else typingState.value - sessionId
        }
    }

    private fun setStreaming(sessionId: Long, partials: List<String>) {
        synchronized(streamingState) {
            val map = streamingState.value.toMutableMap()
            if (partials.isEmpty()) map.remove(sessionId) else map[sessionId] = partials
            streamingState.value = map
        }
    }

    // ───────────────────────── Ответ в чате (Streaming + SafetyGuard) ─────────────────────────

    fun requestRespond(sessionId: Long) {
        scope.launch { respond(sessionId, paced = true) }
    }

    fun requestRetry(sessionId: Long, messageId: Long) {
        scope.launch {
            repo.messageDao.setStatus(messageId, "ok", "")
            handled.remove(sessionId)
            respond(sessionId, paced = true)
        }
    }

    fun requestKickoff(sessionId: Long) {
        scope.launch { kickoff(sessionId) }
    }

    fun requestRegenerate(sessionId: Long) {
        scope.launch {
            handled.remove(sessionId)
            respond(sessionId, paced = true)
        }
    }

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
                    val built = builder.chatTurn(session, kickoff = false, now = now, aftercare = runtime.aftercareActive(now))
                    val assessment = built.assessment
                    val userName = repo.profile().name
                    val doneId = maxOf(built.lastUserId, lastUser.id)

                    // 1. Кризис: проверенный ответ без участия модели (работает офлайн) + бережный режим на сутки.
                    if (assessment.level == SafetyGuard.RiskLevel.CRISIS) {
                        runtime.markCrisis(now, SafetyGuard.AFTERCARE_HOURS)
                        val crisisReply = Reply(
                            messages = SafetyGuard.crisisResponse(userName),
                            quick = SafetyGuard.crisisQuickReplies(),
                            mood = 1,
                            emotion = "кризис"
                        )
                        if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                        deliver(session, crisisReply, "chat", paced = false, usage = null, gentle = true)
                        handled[sessionId] = doneId
                        break
                    }

                    // 2. Прямая просьба о контактах помощи — тоже мгновенно и без модели.
                    if (assessment.helpRequested) {
                        runtime.markCrisis(now, SafetyGuard.AFTERCARE_HOURS)
                        val helpReply = Reply(
                            messages = SafetyGuard.resourcesResponse(userName),
                            quick = SafetyGuard.resourcesQuickReplies(),
                            emotion = "тяжесть"
                        )
                        if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                        deliver(session, helpReply, "chat", paced = false, usage = null, gentle = true)
                        handled[sessionId] = doneId
                        break
                    }

                    val canStream = settings.value.streamingEnabled &&
                        !built.useSearch && !built.useUrlContext &&
                        visibleSession.value == sessionId

                    var result = if (canStream) {
                        var lastEmit = 0L
                        streamCall(built, Protocol.chatSchema, TOKENS_CHAT, 2) { acc ->
                            val t = System.currentTimeMillis()
                            if (t - lastEmit >= 75L) {
                                val parts = Protocol.extractPartialMessages(acc)
                                if (parts.isNotEmpty()) {
                                    setStreaming(sessionId, parts)
                                    lastEmit = t
                                }
                            }
                        }
                    } else {
                        call(built, Protocol.chatSchema, TOKENS_CHAT, 2)
                    }

                    setStreaming(sessionId, emptyList())

                    // Битый или пустой JSON — один тихий повтор обычным запросом.
                    var reply = (result as? GeminiClient.Result.Ok)?.let { Protocol.parse(it.text) }
                    if (result is GeminiClient.Result.Ok && reply == null) {
                        result = call(built, Protocol.chatSchema, TOKENS_CHAT, 1)
                        reply = (result as? GeminiClient.Result.Ok)?.let { Protocol.parse(it.text) }
                    }

                    if (result is GeminiClient.Result.Fail) {
                        if (result.kind == GeminiClient.Kind.BLOCKED) {
                            val fallbackReply = Reply(
                                messages = SafetyGuard.blockedFallback(assessment.level, userName),
                                quick = listOf("Нужно заземление 🌊", "Мне очень тяжело", "Давай дальше"),
                                emotion = "тяжесть"
                            )
                            if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                            deliver(session, fallbackReply, "chat", paced = paced, usage = null, gentle = true)
                            handled[sessionId] = doneId
                            break
                        }

                        failure = result.human()
                        runtime.lastError = Timex.hhmm(now) + " · " + result.kind.name + " · " + result.message.take(200)
                        markFailed(lastUser.id, failure)
                        break
                    }

                    val ok = result as GeminiClient.Result.Ok
                    if (reply == null) {
                        failure = "Ответ сорвался. Нажми «Повторить»."
                        markFailed(lastUser.id, failure)
                        break
                    }

                    // 3. Второй контур: модель распознала скрытый риск — бережный режим и гарантированные контакты помощи.
                    var finalReply: Reply = reply
                    if (reply.risk == "crisis") {
                        runtime.markCrisis(now, SafetyGuard.AFTERCARE_HOURS)
                        if (reply.messages.none { it.contains("112") }) {
                            finalReply = reply.copy(
                                messages = reply.messages + ("Если становится опасно — пожалуйста, не оставайся с этим один на один:\n" + SafetyGuard.RESOURCES)
                            )
                        }
                    }
                    if (lastUser.status != "ok") repo.messageDao.setStatus(lastUser.id, "ok", "")
                    deliver(
                        session, finalReply, "chat", paced = paced && !canStream, usage = ok,
                        gentle = assessment.suppressPushy || reply.risk != "none"
                    )
                    handled[sessionId] = doneId
                } catch (t: Throwable) {
                    Logx.e("respond failed", t)
                    failure = "Внутренняя ошибка: " + (t.message ?: t.javaClass.simpleName)
                    markFailed(lastUser.id, failure)
                    break
                } finally {
                    setStreaming(sessionId, emptyList())
                    setTyping(sessionId, false)
                }
            }
            failure
        }
        scope.launch {
            summarize(sessionId)
            reflect(false)
            maybeAutoWeeklyReview()
        }
        return error
    }

    private suspend fun markFailed(messageId: Long, error: String) {
        repo.messageDao.setStatus(messageId, "error", JSONObject().put("error", error).toString())
    }

    suspend fun kickoff(sessionId: Long) {
        val lock = locks.getOrPut(sessionId) { Mutex() }
        lock.withLock {
            val session = repo.sessionDao.get(sessionId) ?: return@withLock
            if (repo.messageDao.count(sessionId) > 0) return@withLock
            setTyping(sessionId, true)
            try {
                val now = Timex.now()
                val built = builder.chatTurn(session, kickoff = true, now = now, aftercare = runtime.aftercareActive(now))
                val result = call(built, Protocol.chatSchema, TOKENS_CHAT, 1)
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
                                quick = listOf("Я в тупике 🧱", "Накрыла тревога 🌊", "Режим Учитель 🎓")
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

    /** Все запросы идут в одну модель ([Settings.MODEL]) с одним уровнем раздумий ([Settings.THINKING_LEVEL]). */
    private fun request(built: ContextBuilder.Built, schema: JSONObject, maxTokens: Int, retries: Int) = GeminiClient.Request(
        system = built.system,
        parts = built.parts,
        turns = built.turns,
        schema = schema,
        jsonMode = true,
        maxTokens = maxTokens,
        retries = retries,
        useSearch = built.useSearch,
        useUrlContext = built.useUrlContext
    )

    private suspend fun call(
        built: ContextBuilder.Built,
        schema: JSONObject,
        maxTokens: Int,
        retries: Int
    ): GeminiClient.Result = gemini.generate(settings.apiKey(), request(built, schema, maxTokens, retries))

    private suspend fun streamCall(
        built: ContextBuilder.Built,
        schema: JSONObject,
        maxTokens: Int,
        retries: Int,
        onDelta: suspend (String) -> Unit
    ): GeminiClient.Result = gemini.streamGenerate(settings.apiKey(), request(built, schema, maxTokens, retries), onDelta)

    private suspend fun deliver(
        session: SessionEntity,
        reply: Reply,
        kind: String,
        paced: Boolean,
        usage: GeminiClient.Result.Ok?,
        gentle: Boolean = false
    ) {
        val now = Timex.now()
        val delta = memory.apply(session, reply, now, gentle)
        val texts = reply.messages
        val meta = buildMeta(delta, usage)
        val pacing = paced && settings.value.humanPacing

        for ((i, text) in texts.withIndex()) {
            val last = i == texts.lastIndex
            if (pacing && i > 0) delay((320L + text.length * 10L).coerceAtMost(1400L))
            val visible = visibleSession.value == session.id
            repo.addAtlasMessage(
                sessionId = session.id,
                text = text,
                kind = kind,
                quick = if (last) reply.quick else emptyList(),
                meta = if (last) meta else "",
                countUnread = !visible
            )
        }

        if (delta.planCreated.isNotEmpty()) {
            repo.addInfo(session.id, "📋 WOOP-план «${delta.planCreated}» зафиксирован: ${delta.planSteps} шаг(ов). Вперёд!")
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
            if (delta.newPlanId >= 0L) repo.sessionDao.setPlanId(session.id, delta.newPlanId)
            repo.sessionDao.setAsked(session.id, delta.newAskedRef)
            repo.sessionDao.bumpTurn(session.id)
            if (reply.title.isNotBlank() && !fresh.titleLocked && !fresh.isHome) {
                repo.sessionDao.rename(session.id, reply.title, true)
            }
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

    // ───────────────────────── Проактивные «СМС» (без гонок) ─────────────────────────

    suspend fun pingCycle(force: Boolean, forcedKind: String?) {
        pingLock.withLock {
            val status = try {
                firePingLocked(force, forcedKind)
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
                rearmLocked()
            }
        }
    }

    private suspend fun firePingLocked(force: Boolean, forcedKind: String?): String {
        val s = settings.value
        val now = Timex.now()
        if (!s.onboarded) return "not_onboarded"
        if (!force) {
            if (!s.pingsEnabled) return "disabled"
            if (runtime.snoozeUntil > now) return "snoozed"
            if (PingScheduler.inQuiet(Timex.minuteOfDay(now), s)) return "quiet"
            if (now - runtime.lastPingAt < 2 * 60_000L) return "too_soon"
            if (repo.pingDao.countSince(Timex.startOfDay(now)) >= s.dailyPingCap) return "cap"
            val lastUserAt = repo.messageDao.lastUserAt() ?: 0L
            if (now - lastUserAt < 3 * 60_000L) return "active"
            if (typingState.value.isNotEmpty()) return "busy"
            if (runtime.aftercareActive(now) && now - runtime.lastPingAt < AFTERCARE_PING_GAP) return "aftercare_hold"
        }

        val session = targetSession()
        val sessionLock = locks.getOrPut(session.id) { Mutex() }
        return sessionLock.withLock {
            val freshSession = repo.sessionDao.get(session.id) ?: session
            val unanswered = repo.pingDao.unansweredSince(now - 36 * Srs.HOUR)
            val plan = planner.plan(freshSession, s, now, rnd, forcedKind, aftercare = runtime.aftercareActive(now))

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
                val built = builder.ping(freshSession, plan, now)
                val result = call(built, Protocol.chatSchema, TOKENS_PING, 1)
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

            if (texts.isEmpty() && plan.kind == PingPlanner.SAFE_CHECKIN) {
                source = "offline"
                texts = listOf(SafetyGuard.aftercareCheckIn(repo.profile().name))
                quick = SafetyGuard.aftercareQuickReplies()
            }

            if (texts.isEmpty()) {
                source = "offline"
                val dueAnchor = repo.anchorDao.due(now, 5).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }
                val dueCard = repo.knowledgeCardDao.due(now, 5).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }
                val duePred = repo.predictionDao.due(now, 3).firstOrNull()
                val lastUserMsg = repo.messageDao.recentDesc(freshSession.id, 10)
                    .firstOrNull { it.role == Repository.ROLE_USER }?.text.orEmpty()
                val states = kb.inferStates(lastUserMsg, now) + plan.states
                val recentKbIds = repo.pingDao.recentKbIds(24).toHashSet()
                val off = OfflineBank.make(
                    kb = kb,
                    rnd = rnd,
                    dueAnchorPhrase = dueAnchor?.phrase,
                    dueAnchorWhy = dueAnchor?.why,
                    states = states,
                    unanswered = unanswered,
                    excludeKbIds = recentKbIds,
                    now = now,
                    dueCardFront = dueCard?.front,
                    duePredQuestion = duePred?.question,
                    duePredPct = duePred?.probabilityPct ?: 50
                )
                texts = listOf(off.text)
                quick = off.quick
                kind = off.kind
                kbId = off.kbId
                asked = when {
                    off.kind == "anchor_due" && dueAnchor != null -> "A${dueAnchor.id}"
                    off.kind == "card_due" && dueCard != null -> "K${dueCard.id}"
                    else -> ""
                }
                anchors = emptyList()
            }

            val delta = memory.apply(
                freshSession.copy(askedRef = ""),
                Reply(messages = texts, quick = quick, mood = mood, emotion = emotion, asked = asked, anchors = anchors),
                now,
                gentle = plan.gentleOnly
            )
            val finalAsked = delta.newAskedRef.ifEmpty { if (source == "offline") asked else plan.askedRef }
            val visible = visibleSession.value == freshSession.id
            var lastId = 0L
            for ((i, text) in texts.withIndex()) {
                val last = i == texts.lastIndex
                val meta = if (last) {
                    val o = JSONObject().put("ping", kind).put("source", source)
                    if (delta.anchorsAdded > 0) o.put("anchors", delta.anchorsAdded)
                    if (usage != null) o.put("in", usage.promptTokens).put("out", usage.outputTokens).put("model", usage.model)
                    o.toString()
                } else ""
                lastId = repo.addAtlasMessage(freshSession.id, text, "ping", if (last) quick else emptyList(), meta, !visible)
            }
            repo.sessionDao.setAsked(freshSession.id, finalAsked)
            repo.recordPing(freshSession.id, lastId, kind, source, kbId, now)
            runtime.lastPingAt = now
            if (!visible) postNotification(freshSession.id, alert = true)
            "sent:$kind:$source"
        }
    }

    private suspend fun targetSession(): SessionEntity {
        val id = settings.value.pingSessionId
        if (id != 0L) repo.sessionDao.get(id)?.let { return it }
        return repo.ensureHome()
    }

    suspend fun rearm(): Long = pingLock.withLock { rearmLocked() }

    private suspend fun rearmLocked(): Long {
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
        if (runtime.aftercareActive(now)) {
            at = maxOf(at, runtime.lastPingAt + AFTERCARE_PING_GAP + rnd.nextInt(1, 15) * 60_000L)
            if (PingScheduler.inQuiet(Timex.minuteOfDay(at), s)) at = PingScheduler.endOfQuiet(at, s) + rnd.nextInt(5, 30) * 60_000L
        }
        if (repo.pingDao.countSince(Timex.startOfDay(now)) >= s.dailyPingCap) {
            at = maxOf(at, nextMorning(now) + rnd.nextInt(2, 25) * 60_000L)
        }
        runtime.nextPingAt = at
        alarms.arm(at)
        return at
    }

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

    // ───────────────────────── Новые когнитивные движки (Синтезатор, Решения, Калибровка, Обзор) ─────────────────────────

    suspend fun extractAndSaveSource(
        titleHint: String,
        kind: String,
        contentOrUrl: String,
        attachment: Attachment? = null
    ): Result<Long> {
        if (settings.apiKey().isBlank()) return Result.failure(IllegalStateException("Добавь API-ключ Gemini в настройках"))
        val built = builder.sourceExtraction(
            titleHint = titleHint,
            kind = kind,
            contentOrUrl = contentOrUrl,
            attachment = attachment,
            webEnabled = settings.value.webSearchEnabled
        )
        val result = call(built, Protocol.sourceExtractSchema, TOKENS_LONG, 1)
        if (result is GeminiClient.Result.Fail) return Result.failure(IllegalStateException(result.human()))
        val ok = result as GeminiClient.Result.Ok
        val note = Protocol.parseExtractedSource(ok.text)
            ?: return Result.failure(IllegalStateException("Не удалось структурировать материал. Попробуй сократить фрагмент."))

        val rawExcerpt = when {
            attachment?.isText == true -> attachments.readText(attachment).take(12_000)
            attachment == null -> contentOrUrl.take(12_000)
            else -> note.summary
        }
        val originRef = attachment?.path ?: contentOrUrl.take(500)
        val drafts = note.cards.map { c ->
            Repository.CardDraft(
                kind = c.kind,
                front = c.front,
                back = c.back,
                hint = c.hint,
                skillId = c.skillId,
                tags = c.tags
            )
        }
        val sid = repo.saveSourceWithCards(
            title = note.title,
            kind = kind,
            originRef = originRef,
            summary = note.summary,
            keyModels = note.keyModels,
            blindSpots = note.blindSpots,
            rawExcerpt = rawExcerpt,
            cards = drafts
        )
        repo.addXp(20, Timex.now())
        repo.registerActivity(Timex.now(), 20)
        return Result.success(sid)
    }

    suspend fun assistDecision(
        title: String,
        contextText: String,
        alternatives: List<String>,
        chosenOption: String,
        confidencePct: Int
    ): Result<DecisionAssistNote> {
        if (settings.apiKey().isBlank()) return Result.failure(IllegalStateException("Нужен API-ключ Gemini"))
        val built = builder.decisionAssist(
            title = title,
            context = contextText,
            alternatives = alternatives,
            chosenOption = chosenOption,
            confidencePct = confidencePct,
            webSearch = settings.value.webSearchEnabled
        )
        val result = call(built, Protocol.decisionAssistSchema, TOKENS_MID, 1)
        if (result is GeminiClient.Result.Fail) return Result.failure(IllegalStateException(result.human()))
        val ok = result as GeminiClient.Result.Ok
        val note = Protocol.parseDecisionAssist(ok.text)
            ?: return Result.failure(IllegalStateException("Не удалось разобрать ответ аудитора"))
        return Result.success(note)
    }

    suspend fun generateCalibrationQuiz(topicHint: String): Result<CalibrationQuiz> {
        if (settings.apiKey().isBlank()) return Result.failure(IllegalStateException("Нужен API-ключ Gemini"))
        val built = builder.calibrationQuiz(topicHint, settings.value.webSearchEnabled)
        val result = call(built, Protocol.calibrationQuizSchema, TOKENS_SHORT, 1)
        if (result is GeminiClient.Result.Fail) return Result.failure(IllegalStateException(result.human()))
        val o = Protocol.extractObject((result as GeminiClient.Result.Ok).text)
            ?: return Result.failure(IllegalStateException("Неверный формат вопроса"))
        val q = o.str("question").trim()
        val exp = o.str("explanation").trim()
        if (q.isEmpty()) return Result.failure(IllegalStateException("Пустой вопрос"))
        val out = if (o.int("outcome", 0) > 0) 1 else 0
        return Result.success(CalibrationQuiz(q, out, exp))
    }

    private suspend fun maybeAutoWeeklyReview() {
        val s = settings.value
        if (!s.weeklyReviewEnabled || settings.apiKey().isBlank()) return
        val now = Timex.now()
        val dt = Timex.dateTime(now)
        val currentWeek = Timex.dayOf(Timex.startOfWeek(now))
        if (dt.dayOfWeek.value == 7 && dt.hour >= 17 && runtime.weeklyReviewWeek != currentWeek) {
            generateWeeklyReview(force = false)
        }
    }

    suspend fun generateWeeklyReview(force: Boolean): Boolean {
        if (settings.apiKey().isBlank()) return false
        if (!weeklyRunning.compareAndSet(false, true)) return false
        try {
            val now = Timex.now()
            val weekStartDay = Timex.dayOf(Timex.startOfWeek(now))
            if (!force && runtime.weeklyReviewWeek == weekStartDay) return false
            val userCount = repo.messageDao.userCount()
            if (!force && userCount < 5) return false

            val built = builder.weeklyReview(now)
            val result = call(built, Protocol.weeklyReviewSchema, TOKENS_MID, 1)
            if (result !is GeminiClient.Result.Ok) return false
            val note = Protocol.parseWeeklyReview(result.text) ?: return false

            val resolved = repo.predictionDao.allResolved()
            val brier = if (resolved.isEmpty()) -1.0 else Srs.computeCalibration(resolved.map { it.probabilityPct to it.outcome }).brierScore
            val recentMoods = repo.dailyDao.recent(7).filter { it.moodCount > 0 }
            val avgMood = if (recentMoods.isEmpty()) 0.0
            else recentMoods.sumOf { it.moodSum }.toDouble() / recentMoods.sumOf { it.moodCount }.coerceAtLeast(1)

            repo.weeklyReviewDao.insert(
                WeeklyReviewEntity(
                    weekStartDay = weekStartDay,
                    summary = note.summary,
                    nextWeekChallenge = note.nextWeekChallenge,
                    topTrigger = note.topTrigger,
                    bestWin = note.bestWin,
                    scoresJson = note.scoresJson,
                    brierAvg = brier,
                    avgMood = avgMood,
                    createdAt = now
                )
            )
            runtime.weeklyReviewWeek = weekStartDay
            repo.updateProfile { it.copy(lastWeeklyReviewAt = now) }
            return true
        } catch (t: Throwable) {
            Logx.w("weekly review failed", t)
            return false
        } finally {
            weeklyRunning.set(false)
        }
    }

    // ───────────────────────── Рефлексия и сжатие ─────────────────────────

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
            val result = call(built, Protocol.reflectionSchema, TOKENS_MID, 1)
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

    suspend fun summarize(sessionId: Long) {
        if (settings.apiKey().isBlank()) return
        if (!summarizing.compareAndSet(false, true)) return
        try {
            val session = repo.sessionDao.get(sessionId) ?: return
            if (repo.messageDao.countAfter(sessionId, session.summarizedUpToId) < 44) return
            val chunk = repo.messageDao.after(sessionId, session.summarizedUpToId, 24)
            if (chunk.size < 12) return
            val built = builder.summary(session.summary, chunk, Timex.now())
            val result = call(built, Protocol.summarySchema, TOKENS_SHORT, 1)
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

    suspend fun testKey(key: String): GeminiClient.Result = gemini.generate(
        key,
        GeminiClient.Request(
            system = "",
            parts = listOf(GeminiClient.Part.Text("Ответь только одним словом: OK")),
            schema = null,
            jsonMode = false,
            maxTokens = TOKENS_SHORT,
            retries = 0
        )
    )

    private companion object {
        /** Лимиты вывода с запасом: токены раздумий тоже входят в вывод модели. */
        const val TOKENS_CHAT = 8192
        const val TOKENS_PING = 4096
        const val TOKENS_LONG = 12_288
        const val TOKENS_MID = 4096
        const val TOKENS_SHORT = 2048

        /** Минимальный интервал между СМС в режиме «после кризиса». */
        const val AFTERCARE_PING_GAP = 3 * 60 * 60_000L
    }
}