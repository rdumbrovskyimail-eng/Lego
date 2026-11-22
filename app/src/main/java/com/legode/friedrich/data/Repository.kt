package com.legode.friedrich.data

import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.ActionPlanEntity
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.data.db.AppDatabase
import com.legode.friedrich.data.db.DailyEntity
import com.legode.friedrich.data.db.FactEntity
import com.legode.friedrich.data.db.JournalEntity
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.PingEntity
import com.legode.friedrich.data.db.PlanProgress
import com.legode.friedrich.data.db.PlanStepEntity
import com.legode.friedrich.data.db.ProfileEntity
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.SkillProgressEntity
import com.legode.friedrich.data.db.TrapEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** Все операции с базой: переписка и психо-эмоциональное досье пользователя. */
class Repository(private val db: AppDatabase, private val attachments: AttachmentStore) {

    val sessionDao = db.sessionDao()
    val messageDao = db.messageDao()
    val pingDao = db.pingDao()
    val profileDao = db.profileDao()
    val anchorDao = db.anchorDao()
    val planDao = db.planDao()
    val trapDao = db.trapDao()
    val skillDao = db.skillDao()
    val factDao = db.factDao()
    val journalDao = db.journalDao()
    val dailyDao = db.dailyDao()

    private val homeLock = Mutex()
    private val statLock = Mutex()

    // ───────────────────────── Сессии ─────────────────────────

    suspend fun ensureHome(): SessionEntity = homeLock.withLock {
        sessionDao.home()?.let { return@withLock it }
        val now = Timex.now()
        val id = sessionDao.insert(
            SessionEntity(
                title = "Атлас", mode = "free", emoji = "⚡", createdAt = now, updatedAt = now,
                pinned = true, isHome = true, titleLocked = true
            )
        )
        sessionDao.get(id) ?: SessionEntity(id = id, title = "Атлас", createdAt = now, updatedAt = now, isHome = true)
    }

    suspend fun createSession(mode: String, modeArg: String, title: String, emoji: String, planId: Long = 0): Long {
        val now = Timex.now()
        return sessionDao.insert(
            SessionEntity(
                title = title, mode = mode, modeArg = modeArg, emoji = emoji,
                createdAt = now, updatedAt = now, planId = planId
            )
        )
    }

    suspend fun deleteSession(id: Long) {
        val s = sessionDao.get(id) ?: return
        if (s.isHome) {
            clearSession(id)
            return
        }
        attachments.deleteAll(messageDao.attachmentsOf(id))
        messageDao.deleteBySession(id)
        pingDao.deleteBySession(id)
        sessionDao.delete(id)
    }

    suspend fun clearSession(id: Long) {
        val s = sessionDao.get(id) ?: return
        attachments.deleteAll(messageDao.attachmentsOf(id))
        messageDao.deleteBySession(id)
        pingDao.deleteBySession(id)
        sessionDao.update(
            s.copy(
                unread = 0, lastPreview = "", summary = "", summarizedUpToId = 0, askedRef = "",
                turnCount = 0, draft = "", updatedAt = Timex.now()
            )
        )
    }

    // ───────────────────────── Сообщения ─────────────────────────

    suspend fun addUserMessage(sessionId: Long, text: String, atts: List<Attachment>, now: Long = Timex.now()): Long {
        messageDao.clearQuickReplies(sessionId)
        val id = messageDao.insert(
            MessageEntity(
                sessionId = sessionId, role = ROLE_USER, text = text, createdAt = now,
                attachments = Attachment.listToJson(atts)
            )
        )
        val preview = when {
            text.isNotBlank() -> "Ты: " + Norm.oneLine(text, 90)
            atts.isNotEmpty() -> "Ты: " + atts.first().label()
            else -> "Ты: …"
        }
        sessionDao.touch(sessionId, now, preview)
        val answered = pingDao.markAnswered(sessionId, now)
        bumpDaily(now) { it.copy(userMsgs = it.userMsgs + 1, pingsAnswered = it.pingsAnswered + answered, xp = it.xp + 3) }
        registerActivity(now, 3)
        return id
    }

    suspend fun addAtlasMessage(
        sessionId: Long,
        text: String,
        kind: String,
        quick: List<String>,
        meta: String,
        countUnread: Boolean,
        now: Long = Timex.now()
    ): Long {
        val quickJson = if (quick.isEmpty()) "" else JSONArray(quick).toString()
        val id = messageDao.insert(
            MessageEntity(
                sessionId = sessionId, role = ROLE_ATLAS, text = text, createdAt = now,
                kind = kind, quickReplies = quickJson, meta = meta
            )
        )
        sessionDao.touch(sessionId, now, Norm.oneLine(text, 90))
        if (countUnread) sessionDao.addUnread(sessionId, 1)
        bumpDaily(now) { it.copy(atlasMsgs = it.atlasMsgs + 1) }
        return id
    }

    suspend fun addInfo(sessionId: Long, text: String, now: Long = Timex.now()): Long =
        messageDao.insert(
            MessageEntity(sessionId = sessionId, role = ROLE_INFO, text = text, createdAt = now, kind = "info")
        )

    suspend fun deleteMessage(id: Long) {
        val m = messageDao.get(id) ?: return
        for (a in Attachment.listFromJson(m.attachments)) attachments.delete(a)
        messageDao.delete(id)
        val last = messageDao.last(m.sessionId)
        val preview = when {
            last == null -> ""
            last.role == ROLE_USER -> "Ты: " + Norm.oneLine(last.text, 90)
            else -> Norm.oneLine(last.text, 90)
        }
        val s = sessionDao.get(m.sessionId) ?: return
        sessionDao.update(s.copy(lastPreview = preview))
    }

    /** Удаляет ответы Атласа после последнего сообщения пользователя (для «переспросить»). @return было ли что удалять. */
    suspend fun deleteTrailingAtlas(sessionId: Long): Boolean {
        var removed = false
        for (m in messageDao.recentDesc(sessionId, 12)) {
            if (m.role == ROLE_USER) break
            messageDao.delete(m.id)
            removed = true
        }
        return removed
    }

    suspend fun recordPing(sessionId: Long, messageId: Long, kind: String, source: String, kbId: String, now: Long) {
        pingDao.insert(
            PingEntity(
                firedAt = now,
                kind = kind,
                source = source,
                sessionId = sessionId,
                messageId = messageId,
                kbId = kbId
            )
        )
        bumpDaily(now) { it.copy(pings = it.pings + 1) }
    }

    // ───────────────────────── Профиль и настроение ─────────────────────────

    suspend fun profile(): ProfileEntity {
        profileDao.get()?.let { return it }
        val fresh = ProfileEntity(createdAt = Timex.now())
        profileDao.upsert(fresh)
        return fresh
    }

    suspend fun updateProfile(block: (ProfileEntity) -> ProfileEntity): ProfileEntity = statLock.withLock {
        val current = profileDao.get() ?: ProfileEntity(createdAt = Timex.now())
        val next = block(current)
        profileDao.upsert(next)
        next
    }

    suspend fun bumpDaily(now: Long, block: (DailyEntity) -> DailyEntity) = statLock.withLock {
        val day = Timex.dayOf(now)
        val current = dailyDao.get(day) ?: DailyEntity(day = day)
        dailyDao.upsert(block(current))
        Unit
    }

    suspend fun today(now: Long = Timex.now()): DailyEntity {
        val day = Timex.dayOf(now)
        return dailyDao.get(day) ?: DailyEntity(day = day)
    }

    suspend fun recordMood(mood: Int, now: Long = Timex.now()) {
        if (mood !in 1..10) return
        updateProfile { it.copy(lastMood = mood) }
        bumpDaily(now) { it.copy(moodSum = it.moodSum + mood, moodCount = it.moodCount + 1) }
    }

    /** Учитывает активность: серия дней подряд и очки внутренней силы (XP). */
    suspend fun registerActivity(now: Long, xp: Int) {
        val today = Timex.dayOf(now)
        updateProfile { p ->
            val streak = when (p.lastActiveDay) {
                today -> max(1, p.streak)
                Timex.yesterdayOf(today) -> p.streak + 1
                else -> 1
            }
            p.copy(lastActiveDay = today, streak = streak, bestStreak = max(p.bestStreak, streak), xp = p.xp + xp)
        }
    }

    suspend fun addXp(xp: Int, now: Long = Timex.now()) {
        if (xp == 0) return
        updateProfile { it.copy(xp = it.xp + xp) }
        bumpDaily(now) { it.copy(xp = it.xp + xp) }
    }

    // ───────────────────────── Навыки и устойчивость ─────────────────────────

    suspend fun masteryMap(): Map<String, Int> = skillDao.all().associate { it.skillId to it.mastery }

    suspend fun applySkill(skillId: String, d: Int, now: Long) {
        if (Curriculum.topic(skillId) == null || d == 0) return
        val cur = skillDao.get(skillId) ?: SkillProgressEntity(skillId = skillId)
        skillDao.upsert(
            cur.copy(
                mastery = Srs.masteryAfter(cur.mastery, d),
                exposures = cur.exposures + 1,
                wins = cur.wins + if (d > 0) 1 else 0,
                slips = cur.slips + if (d < 0) 1 else 0,
                lastAt = now
            )
        )
    }

    suspend fun setMastery(skillId: String, mastery: Int) {
        val cur = skillDao.get(skillId) ?: SkillProgressEntity(skillId = skillId)
        skillDao.upsert(cur.copy(mastery = mastery.coerceIn(0, 100), lastAt = Timex.now()))
    }

    /** Стартовая расстановка по самооценке (не понижает уже набранное). */
    suspend fun seedMastery(seed: Map<String, Int>) {
        val existing = skillDao.all().associateBy { it.skillId }
        val rows = seed.mapNotNull { (id, value) ->
            val cur = existing[id]
            when {
                cur == null -> if (value > 0) SkillProgressEntity(skillId = id, mastery = value) else null
                cur.mastery < value -> cur.copy(mastery = value)
                else -> null
            }
        }
        if (rows.isNotEmpty()) skillDao.upsertAll(rows)
        recomputeStage()
    }

    /** Результат аудита состояния: поднимает базовый пол освоенности навыков под стадию. */
    suspend fun applyStageFloor(stage: String) {
        val floors: Map<String, Int> = when (stage) {
            "спад" -> mapOf("R0" to 55)
            "стабильность" -> mapOf("R0" to 75, "R1" to 60)
            "сила" -> mapOf("R0" to 85, "R1" to 75, "R2" to 60)
            else -> emptyMap()
        }
        if (floors.isEmpty()) return
        val seed = HashMap<String, Int>()
        for (t in Curriculum.topics) floors[t.stage]?.let { seed[t.id] = it }
        seedMastery(seed)
    }

    suspend fun recomputeStage(): String {
        val est = Curriculum.estimate(masteryMap())
        val p = profile()
        if (p.stage != est.label) updateProfile { it.copy(stage = est.label) }
        return est.label
    }

    // ───────────────────────── Банк якорей силы ─────────────────────────

    /** @return true, если якорь новый. */
    suspend fun upsertAnchor(
        phrase: String,
        why: String,
        source: String,
        trigger: String,
        origin: String,
        now: Long
    ): Boolean {
        val key = Norm.anchorKey(phrase)
        if (key.length < 4 || why.isBlank()) return false
        val existing = anchorDao.byKey(key)
        if (existing != null) {
            val patched = existing.copy(
                source = existing.source.ifBlank { source },
                trigger = existing.trigger.ifBlank { trigger },
                lastSeenAt = now
            )
            anchorDao.update(patched)
            return false
        }
        val rowId = anchorDao.insert(
            AnchorEntity(
                keyNorm = key,
                phrase = phrase.trim(),
                why = why.trim(),
                source = source.trim(),
                trigger = trigger.trim(),
                origin = origin,
                createdAt = now,
                lastSeenAt = now,
                dueAt = now + Srs.FIRST_DELAY
            )
        )
        if (rowId <= 0L) return false
        bumpDaily(now) { it.copy(anchorsAdded = it.anchorsAdded + 1) }
        return true
    }

    suspend fun gradeAnchor(id: Long, result: String, now: Long) {
        val a = anchorDao.get(id) ?: return
        val state = Srs.State(a.intervalDays, a.ease, a.reps, a.lapses)
        val next = when (result) {
            "done" -> {
                val step = Srs.onReinforced(state, now)
                a.copy(
                    intervalDays = step.state.intervalDays, ease = step.state.ease, reps = step.state.reps,
                    lapses = step.state.lapses, dueAt = step.dueAt, status = step.status,
                    reinforced = a.reinforced + 1, lastSeenAt = now
                )
            }

            "skipped" -> {
                val step = Srs.onRelapse(state, now)
                a.copy(
                    intervalDays = step.state.intervalDays, ease = step.state.ease, reps = step.state.reps,
                    lapses = step.state.lapses, dueAt = step.dueAt, status = step.status,
                    relapses = a.relapses + 1, lastSeenAt = now
                )
            }

            "partial" -> a.copy(dueAt = now + 3 * Srs.HOUR, status = "active", lastSeenAt = now)
            else -> return
        }
        anchorDao.update(next)
    }

    // ───────────────────────── Планы прорыва ─────────────────────────

    /** Создаёт план прорыва и возвращает id шагов по порядку. */
    suspend fun createPlan(title: String, steps: List<Pair<String, String>>, sessionId: Long, now: Long): Pair<Long, List<Long>> {
        val planId = planDao.insertPlan(ActionPlanEntity(title = title, createdAt = now, sourceSessionId = sessionId))
        val rows = steps.mapIndexed { i, (step, why) -> PlanStepEntity(planId = planId, step = step, why = why, position = i) }
        val ids = planDao.insertSteps(rows)
        return planId to ids
    }

    suspend fun gradePlanStep(id: Long, result: String, now: Long): PlanProgress? {
        val item = planDao.getStep(id) ?: return null
        val next = when (result) {
            "done" -> item.copy(doneCount = item.doneCount + 1, streak = item.streak + 1, lastAt = now)
            "skipped" -> item.copy(skippedCount = item.skippedCount + 1, streak = 0, lastAt = now)
            "partial" -> item.copy(lastAt = now)
            else -> return null
        }
        planDao.updateStep(next)
        return planDao.progress(item.planId)
    }

    suspend fun deletePlan(planId: Long) {
        sessionDao.detachPlan(planId)
        planDao.deleteSteps(planId)
        planDao.deletePlan(planId)
    }

    // ───────────────────────── Ловушки ума и факты ─────────────────────────

    suspend fun addTrap(thought: String, antidote: String, skillId: String, why: String, now: Long) {
        val key = Norm.trapKey(thought, antidote)
        if (key.length < 6) return
        val existing = trapDao.byKey(key)
        if (existing != null) {
            trapDao.update(
                existing.copy(
                    times = existing.times + 1, lastAt = now, resolved = false,
                    why = existing.why.ifBlank { why }, skillId = existing.skillId.ifBlank { skillId }
                )
            )
        } else {
            trapDao.insert(
                TrapEntity(
                    keyNorm = key,
                    thought = thought,
                    antidote = antidote,
                    skillId = skillId,
                    why = why,
                    firstAt = now,
                    lastAt = now
                )
            )
        }
    }

    /** @return true, если факт новый. */
    suspend fun addFact(text: String, source: String, now: Long): Boolean {
        val clean = text.trim()
        if (clean.length < 3) return false
        val existing = factDao.recent(80)
        if (existing.any { Norm.similar(it.text, clean) }) return false
        factDao.insert(FactEntity(text = clean, createdAt = now, source = source))
        return true
    }

    suspend fun addJournal(kind: String, text: String, now: Long) {
        if (text.isBlank()) return
        journalDao.insert(JournalEntity(createdAt = now, kind = kind, text = text.trim()))
    }

    // ───────────────────────── Экспорт и сброс ─────────────────────────

    suspend fun exportSessionText(sessionId: Long): String {
        val s = sessionDao.get(sessionId) ?: return ""
        val sb = StringBuilder()
        sb.append("Чат «").append(s.title).append("» — Атлас, психо-эмоциональный союзник\n\n")
        var lastDay = ""
        for (m in messageDao.all(sessionId)) {
            if (m.role == ROLE_INFO) continue
            val day = Timex.humanDay(m.createdAt)
            if (day != lastDay) {
                sb.append("— ").append(day).append(" —\n")
                lastDay = day
            }
            val who = if (m.role == ROLE_USER) "Я" else "Атлас"
            sb.append('[').append(Timex.hhmm(m.createdAt)).append("] ").append(who).append(": ")
            sb.append(Norm.plain(m.text))
            for (a in Attachment.listFromJson(m.attachments)) sb.append(" [").append(a.label()).append(']')
            sb.append("\n")
        }
        return sb.toString()
    }

    suspend fun exportAllJson(): String {
        val root = JSONObject()
        val p = profile()
        root.put(
            "profile",
            JSONObject().put("name", p.name).put("stage", p.stage).put("goal", p.goal).put("interests", p.interests)
                .put("about", p.about).put("tactic", p.tactic).put("xp", p.xp).put("streak", p.streak)
                .put("bestStreak", p.bestStreak).put("lastMood", p.lastMood)
        )
        val anchors = JSONArray()
        for (a in anchorDao.newest(100000)) {
            anchors.put(
                JSONObject().put("phrase", a.phrase).put("why", a.why).put("source", a.source)
                    .put("trigger", a.trigger).put("status", a.status)
                    .put("reinforced", a.reinforced).put("relapses", a.relapses)
            )
        }
        root.put("anchors", anchors)
        val traps = JSONArray()
        for (t in trapDao.top(100000)) {
            traps.put(
                JSONObject().put("thought", t.thought).put("antidote", t.antidote)
                    .put("skill", t.skillId).put("why", t.why).put("times", t.times)
            )
        }
        root.put("traps", traps)
        val skills = JSONObject()
        for (s in skillDao.all()) skills.put(s.skillId, s.mastery)
        root.put("skills", skills)
        val facts = JSONArray()
        for (f in factDao.recent(1000)) facts.put(f.text)
        root.put("facts", facts)
        val plans = JSONArray()
        for (pl in planDao.plans()) {
            val steps = JSONArray()
            for (st in planDao.steps(pl.id)) steps.put(JSONObject().put("step", st.step).put("why", st.why).put("done", st.streak >= 1))
            plans.put(JSONObject().put("title", pl.title).put("steps", steps))
        }
        root.put("plans", plans)
        val sessions = JSONArray()
        for (s in sessionDao.all()) {
            val msgs = JSONArray()
            for (m in messageDao.all(s.id)) {
                if (m.role == ROLE_INFO) continue
                msgs.put(JSONObject().put("role", m.role).put("at", m.createdAt).put("text", m.text))
            }
            sessions.put(JSONObject().put("title", s.title).put("mode", s.mode).put("messages", msgs))
        }
        root.put("sessions", sessions)
        return root.toString(2)
    }

    suspend fun wipeAll() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
        attachments.wipe()
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ATLAS = "atlas"
        const val ROLE_INFO = "info"
    }
}