package com.legode.friedrich.data

import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.AppDatabase
import com.legode.friedrich.data.db.DailyEntity
import com.legode.friedrich.data.db.FactEntity
import com.legode.friedrich.data.db.JournalEntity
import com.legode.friedrich.data.db.ListItemEntity
import com.legode.friedrich.data.db.ListProgress
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.MistakeEntity
import com.legode.friedrich.data.db.PingEntity
import com.legode.friedrich.data.db.ProfileEntity
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.StudyListEntity
import com.legode.friedrich.data.db.TopicProgressEntity
import com.legode.friedrich.data.db.VocabEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** Все операции с базой: переписка и досье ученика. */
class Repository(private val db: AppDatabase, private val attachments: AttachmentStore) {

    val sessionDao = db.sessionDao()
    val messageDao = db.messageDao()
    val pingDao = db.pingDao()
    val profileDao = db.profileDao()
    val vocabDao = db.vocabDao()
    val listDao = db.listDao()
    val mistakeDao = db.mistakeDao()
    val topicDao = db.topicDao()
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
                title = "Friedrich", mode = "free", emoji = "🇩🇪", createdAt = now, updatedAt = now,
                pinned = true, isHome = true, titleLocked = true
            )
        )
        sessionDao.get(id) ?: SessionEntity(id = id, title = "Friedrich", createdAt = now, updatedAt = now, isHome = true)
    }

    suspend fun createSession(mode: String, modeArg: String, title: String, emoji: String, listId: Long = 0): Long {
        val now = Timex.now()
        return sessionDao.insert(
            SessionEntity(
                title = title, mode = mode, modeArg = modeArg, emoji = emoji,
                createdAt = now, updatedAt = now, listId = listId
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
        bumpDaily(now) { it.copy(userMsgs = it.userMsgs + 1, pingsAnswered = it.pingsAnswered + answered, xp = it.xp + 2) }
        registerActivity(now, 2)
        return id
    }

    suspend fun addFriedrichMessage(
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
                sessionId = sessionId, role = ROLE_FRIEDRICH, text = text, createdAt = now,
                kind = kind, quickReplies = quickJson, meta = meta
            )
        )
        sessionDao.touch(sessionId, now, Norm.oneLine(text, 90))
        if (countUnread) sessionDao.addUnread(sessionId, 1)
        bumpDaily(now) { it.copy(friedrichMsgs = it.friedrichMsgs + 1) }
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

    /** Удаляет ответы Фридриха после последнего сообщения ученика (для «переспросить»). @return было ли что удалять. */
    suspend fun deleteTrailingFriedrich(sessionId: Long): Boolean {
        var removed = false
        for (m in messageDao.recentDesc(sessionId, 12)) {
            if (m.role == ROLE_USER) break
            messageDao.delete(m.id)
            removed = true
        }
        return removed
    }

    suspend fun recordPing(sessionId: Long, messageId: Long, kind: String, source: String, now: Long) {
        pingDao.insert(PingEntity(firedAt = now, kind = kind, source = source, sessionId = sessionId, messageId = messageId))
        bumpDaily(now) { it.copy(pings = it.pings + 1) }
    }

    // ───────────────────────── Профиль и статистика ─────────────────────────

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

    /** Учитывает активность: серия дней подряд и очки опыта. */
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

    // ───────────────────────── Темы и уровень ─────────────────────────

    suspend fun masteryMap(): Map<String, Int> = topicDao.all().associate { it.topicId to it.mastery }

    suspend fun applyTopic(topicId: String, d: Int, now: Long) {
        if (Curriculum.topic(topicId) == null || d == 0) return
        val cur = topicDao.get(topicId) ?: TopicProgressEntity(topicId = topicId)
        topicDao.upsert(
            cur.copy(
                mastery = Srs.masteryAfter(cur.mastery, d),
                exposures = cur.exposures + 1,
                correct = cur.correct + if (d > 0) 1 else 0,
                wrong = cur.wrong + if (d < 0) 1 else 0,
                lastAt = now
            )
        )
    }

    suspend fun setMastery(topicId: String, mastery: Int) {
        val cur = topicDao.get(topicId) ?: TopicProgressEntity(topicId = topicId)
        topicDao.upsert(cur.copy(mastery = mastery.coerceIn(0, 100), lastAt = Timex.now()))
    }

    /** Стартовая расстановка по самооценке (не понижает уже набранное). */
    suspend fun seedMastery(seed: Map<String, Int>) {
        val existing = topicDao.all().associateBy { it.topicId }
        val rows = seed.mapNotNull { (id, value) ->
            val cur = existing[id]
            when {
                cur == null -> if (value > 0) TopicProgressEntity(topicId = id, mastery = value) else null
                cur.mastery < value -> cur.copy(mastery = value)
                else -> null
            }
        }
        if (rows.isNotEmpty()) topicDao.upsertAll(rows)
        recomputeLevel()
    }

    /** Результат теста уровня: поднимает «пол» освоенности тем ниже определённого уровня. */
    suspend fun applyLevelFloor(level: String) {
        val floors: Map<String, Int> = when (level) {
            "A1.1" -> mapOf("A0" to 60)
            "A1.2" -> mapOf("A0" to 75, "A1.1" to 60)
            "A1" -> mapOf("A0" to 85, "A1.1" to 75, "A1.2" to 60)
            else -> emptyMap()
        }
        if (floors.isEmpty()) return
        val seed = HashMap<String, Int>()
        for (t in Curriculum.topics) floors[t.level]?.let { seed[t.id] = it }
        seedMastery(seed)
    }

    suspend fun recomputeLevel(): String {
        val est = Curriculum.estimate(masteryMap())
        val p = profile()
        if (p.level != est.label) updateProfile { it.copy(level = est.label) }
        return est.label
    }

    // ───────────────────────── Словарь ─────────────────────────

    /** @return true, если слово новое. */
    suspend fun upsertWord(de: String, ru: String, plural: String, example: String, source: String, now: Long): Boolean {
        val key = Norm.vocabKey(de)
        if (key.length < 2 || key.length > 70 || ru.isBlank()) return false
        val existing = vocabDao.byKey(key)
        if (existing != null) {
            val patched = existing.copy(
                plural = existing.plural.ifBlank { plural },
                example = existing.example.ifBlank { example },
                de = if (Norm.articleOf(existing.de).isEmpty() && Norm.articleOf(de).isNotEmpty()) de.trim() else existing.de,
                lastSeenAt = now
            )
            vocabDao.update(patched)
            return false
        }
        val rowId = vocabDao.insert(
            VocabEntity(
                keyNorm = key, de = de.trim(), ru = ru.trim(), plural = plural.trim(), example = example.trim(),
                source = source, createdAt = now, lastSeenAt = now, dueAt = now + Srs.FIRST_DELAY
            )
        )
        if (rowId <= 0L) return false
        bumpDaily(now) { it.copy(wordsAdded = it.wordsAdded + 1) }
        return true
    }

    suspend fun gradeVocab(id: Long, result: String, now: Long) {
        val v = vocabDao.get(id) ?: return
        val state = Srs.State(v.intervalDays, v.ease, v.reps, v.lapses)
        val next = when (result) {
            "correct" -> {
                val step = Srs.onCorrect(state, now)
                v.copy(
                    intervalDays = step.state.intervalDays, ease = step.state.ease, reps = step.state.reps,
                    lapses = step.state.lapses, dueAt = step.dueAt, status = step.status,
                    correct = v.correct + 1, lastSeenAt = now
                )
            }

            "wrong" -> {
                val step = Srs.onWrong(state, now)
                v.copy(
                    intervalDays = step.state.intervalDays, ease = step.state.ease, reps = step.state.reps,
                    lapses = step.state.lapses, dueAt = step.dueAt, status = step.status,
                    wrong = v.wrong + 1, lastSeenAt = now
                )
            }

            "partial" -> v.copy(dueAt = now + 30 * Srs.MINUTE, status = "learning", lastSeenAt = now)
            else -> return
        }
        vocabDao.update(next)
    }

    // ───────────────────────── Списки ─────────────────────────

    /** Создаёт список и возвращает id элементов в порядке вставки. */
    suspend fun createList(title: String, items: List<Pair<String, String>>, sessionId: Long, now: Long): Pair<Long, List<Long>> {
        val listId = listDao.insertList(StudyListEntity(title = title, createdAt = now, sourceSessionId = sessionId))
        val rows = items.mapIndexed { i, (de, ru) -> ListItemEntity(listId = listId, de = de, ru = ru, position = i) }
        val ids = listDao.insertItems(rows)
        for ((de, ru) in items) {
            if (ru.isNotBlank() && de.trim().split(' ').size <= 5) upsertWord(de, ru, "", "", "list", now)
        }
        return listId to ids
    }

    suspend fun gradeListItem(id: Long, result: String, now: Long): ListProgress? {
        val item = listDao.getItem(id) ?: return null
        val next = when (result) {
            "correct" -> item.copy(correct = item.correct + 1, streak = item.streak + 1, lastAt = now)
            "wrong" -> item.copy(wrong = item.wrong + 1, streak = 0, lastAt = now)
            "partial" -> item.copy(lastAt = now)
            else -> return null
        }
        listDao.updateItem(next)
        vocabDao.byKey(Norm.vocabKey(item.de))?.let { gradeVocab(it.id, result, now) }
        return listDao.progress(item.listId)
    }

    suspend fun deleteList(listId: Long) {
        sessionDao.detachList(listId)
        listDao.deleteItems(listId)
        listDao.deleteList(listId)
    }

    // ───────────────────────── Ошибки и факты ─────────────────────────

    suspend fun addMistake(bad: String, good: String, topicId: String, why: String, now: Long) {
        val key = (Norm.looseKey(bad) + ">" + Norm.looseKey(good)).take(240)
        if (key.length < 5) return
        val existing = mistakeDao.byKey(key)
        if (existing != null) {
            mistakeDao.update(
                existing.copy(
                    times = existing.times + 1, lastAt = now, resolved = false,
                    why = existing.why.ifBlank { why }, topicId = existing.topicId.ifBlank { topicId }
                )
            )
        } else {
            mistakeDao.insert(
                MistakeEntity(keyNorm = key, bad = bad, good = good, topicId = topicId, why = why, firstAt = now, lastAt = now)
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
        sb.append("Чат «").append(s.title).append("» — Friedrich, наставник немецкого A0–A1\n\n")
        var lastDay = ""
        for (m in messageDao.all(sessionId)) {
            if (m.role == ROLE_INFO) continue
            val day = Timex.humanDay(m.createdAt)
            if (day != lastDay) {
                sb.append("— ").append(day).append(" —\n")
                lastDay = day
            }
            val who = if (m.role == ROLE_USER) "Я" else "Friedrich"
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
            JSONObject().put("name", p.name).put("level", p.level).put("goal", p.goal).put("interests", p.interests)
                .put("about", p.about).put("plan", p.plan).put("xp", p.xp).put("streak", p.streak)
                .put("bestStreak", p.bestStreak)
        )
        val vocab = JSONArray()
        for (v in vocabDao.newest(100000)) {
            vocab.put(
                JSONObject().put("de", v.de).put("ru", v.ru).put("plural", v.plural).put("example", v.example)
                    .put("status", v.status).put("correct", v.correct).put("wrong", v.wrong)
            )
        }
        root.put("vocab", vocab)
        val mistakes = JSONArray()
        for (m in mistakeDao.top(100000)) {
            mistakes.put(
                JSONObject().put("bad", m.bad).put("good", m.good).put("topic", m.topicId).put("why", m.why).put("times", m.times)
            )
        }
        root.put("mistakes", mistakes)
        val topics = JSONObject()
        for (t in topicDao.all()) topics.put(t.topicId, t.mastery)
        root.put("topics", topics)
        val facts = JSONArray()
        for (f in factDao.recent(1000)) facts.put(f.text)
        root.put("facts", facts)
        val lists = JSONArray()
        for (l in listDao.lists()) {
            val items = JSONArray()
            for (i in listDao.items(l.id)) items.put(JSONObject().put("de", i.de).put("ru", i.ru).put("streak", i.streak))
            lists.put(JSONObject().put("title", l.title).put("items", items))
        }
        root.put("lists", lists)
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
        const val ROLE_FRIEDRICH = "friedrich"
        const val ROLE_INFO = "info"
    }
}
