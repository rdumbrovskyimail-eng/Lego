package com.legode.friedrich.data

import androidx.room.withTransaction
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Fsrs
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.bool
import com.legode.friedrich.core.dbl
import com.legode.friedrich.core.int
import com.legode.friedrich.core.long
import com.legode.friedrich.core.obj
import com.legode.friedrich.core.objects
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import com.legode.friedrich.data.db.ActionPlanEntity
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.data.db.AppDatabase
import com.legode.friedrich.data.db.DailyEntity
import com.legode.friedrich.data.db.DecisionEntity
import com.legode.friedrich.data.db.FactEntity
import com.legode.friedrich.data.db.FocusSessionEntity
import com.legode.friedrich.data.db.JournalEntity
import com.legode.friedrich.data.db.KnowledgeCardEntity
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.PingEntity
import com.legode.friedrich.data.db.PlanProgress
import com.legode.friedrich.data.db.PlanStepEntity
import com.legode.friedrich.data.db.PredictionEntity
import com.legode.friedrich.data.db.ProfileEntity
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.SkillProgressEntity
import com.legode.friedrich.data.db.SourceEntity
import com.legode.friedrich.data.db.TrapEntity
import com.legode.friedrich.data.db.WeeklyReviewEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** Все операции с базой: переписка, психологическое досье, вторая память, решения и аналитика. */
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

    val sourceDao = db.sourceDao()
    val knowledgeCardDao = db.knowledgeCardDao()
    val decisionDao = db.decisionDao()
    val predictionDao = db.predictionDao()
    val focusSessionDao = db.focusSessionDao()
    val weeklyReviewDao = db.weeklyReviewDao()

    private val homeLock = Mutex()
    private val statLock = Mutex()

    data class WoopStepInput(
        val step: String,
        val why: String = "",
        val obstacle: String = "",
        val ifThenRule: String = ""
    )

    data class CardDraft(
        val kind: String = "recall",
        val front: String,
        val back: String,
        val hint: String = "",
        val skillId: String = "",
        val tags: String = ""
    )

    data class ImportSummary(
        val anchorsAdded: Int = 0,
        val cardsAdded: Int = 0,
        val sourcesAdded: Int = 0,
        val decisionsAdded: Int = 0,
        val predictionsAdded: Int = 0,
        val factsAdded: Int = 0,
        val sessionsAdded: Int = 0
    ) {
        fun describe(): String = listOfNotNull(
            anchorsAdded.takeIf { it > 0 }?.let { "$it якор." },
            cardsAdded.takeIf { it > 0 }?.let { "$it карт." },
            sourcesAdded.takeIf { it > 0 }?.let { "$it источн." },
            decisionsAdded.takeIf { it > 0 }?.let { "$it решен." },
            predictionsAdded.takeIf { it > 0 }?.let { "$it прогн." },
            factsAdded.takeIf { it > 0 }?.let { "$it факт." },
            sessionsAdded.takeIf { it > 0 }?.let { "$it чат." }
        ).joinToString(", ").ifEmpty { "новых элементов не найдено" }
    }

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

    suspend fun createSession(
        mode: String,
        modeArg: String,
        title: String,
        emoji: String,
        planId: Long = 0,
        socraticMode: Boolean = false,
        sourceId: Long = 0,
        decisionId: Long = 0
    ): Long {
        val now = Timex.now()
        return sessionDao.insert(
            SessionEntity(
                title = title,
                mode = mode,
                modeArg = modeArg,
                emoji = emoji,
                createdAt = now,
                updatedAt = now,
                planId = planId,
                socraticMode = socraticMode,
                sourceId = sourceId,
                decisionId = decisionId
            )
        )
    }

    suspend fun deleteSession(id: Long) {
        val s = sessionDao.get(id) ?: return
        if (s.isHome) {
            clearSession(id)
            return
        }
        val files = db.withTransaction {
            val atts = messageDao.attachmentsOf(id)
            messageDao.deleteBySession(id)
            pingDao.deleteBySession(id)
            sessionDao.delete(id)
            atts
        }
        attachments.deleteAll(files) // файлы — только после успешного коммита
    }

    suspend fun clearSession(id: Long) {
        val files = db.withTransaction {
            val s = sessionDao.get(id) ?: return@withTransaction emptyList<String>()
            val atts = messageDao.attachmentsOf(id)
            messageDao.deleteBySession(id)
            pingDao.deleteBySession(id)
            sessionDao.update(
                s.copy(
                    unread = 0, lastPreview = "", summary = "", summarizedUpToId = 0, askedRef = "",
                    turnCount = 0, draft = "", updatedAt = Timex.now()
                )
            )
            atts
        }
        attachments.deleteAll(files) // файлы — только после успешного коммита
    }

    // ───────────────────────── Сообщения ─────────────────────────

    suspend fun addUserMessage(
        sessionId: Long,
        text: String,
        atts: List<Attachment>,
        now: Long = Timex.now(),
        pingOutcome: String = "replied"
    ): Long {
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
        val answered = pingDao.markAnswered(sessionId, now, pingOutcome)
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
        refreshPreview(m.sessionId)
    }

    /** Удаляет ответы Атласа после последнего сообщения пользователя (для «переспросить»). @return было ли что удалять. */
    suspend fun deleteTrailingAtlas(sessionId: Long): Boolean {
        var removed = false
        for (m in messageDao.recentDesc(sessionId, 12)) {
            if (m.role == ROLE_USER) break
            messageDao.delete(m.id)
            removed = true
        }
        if (removed) refreshPreview(sessionId)
        return removed
    }

    private suspend fun refreshPreview(sessionId: Long) {
        val last = messageDao.last(sessionId)
        val preview = when {
            last == null -> ""
            last.role == ROLE_USER -> "Ты: " + Norm.oneLine(last.text, 90)
            else -> Norm.oneLine(last.text, 90)
        }
        sessionDao.setPreview(sessionId, preview)
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
        val resolvedCount = predictionDao.allResolved().size
        val newRank = when {
            est.intelOverall >= 65 && resolvedCount >= 10 -> "стратег"
            est.intelOverall >= 40 || resolvedCount >= 5 -> "аналитик"
            est.intelOverall >= 15 || p.xp >= 150 -> "практик"
            else -> "наблюдатель"
        }
        if (p.stage != est.label || p.cognitiveRank != newRank) {
            updateProfile { it.copy(stage = est.label, cognitiveRank = newRank) }
        }
        return est.label
    }

    // ───────────────────────── Банк якорей силы (FSRS-5) ─────────────────────────

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
            anchorDao.update(
                existing.copy(
                    source = existing.source.ifBlank { source },
                    trigger = existing.trigger.ifBlank { trigger },
                    lastSeenAt = now
                )
            )
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
        if (result == "none") return
        val rating = Srs.ratingFromDialogResult(result)
        reviewAnchorFsrs(id, rating, now)
    }

    suspend fun reviewAnchorFsrs(id: Long, rating: Fsrs.Rating, now: Long = Timex.now()) {
        val a = anchorDao.get(id) ?: return
        val retention = profile().desiredRetention.coerceIn(0.75, 0.96)
        val curState = if (a.stability > 0.0) {
            Fsrs.MemoryState(a.stability, a.difficulty, a.reps, a.lapses, a.lastSeenAt)
        } else {
            Fsrs.fromLegacy(a.intervalDays, a.ease, a.reps, a.lapses, a.lastSeenAt)
        }
        val res = Fsrs.review(curState, rating, now, desiredRetention = retention, maxIntervalDays = 180.0, isSameDayFloorMinutes = 45)
        val status = if (res.status == "core") "core" else "active"
        anchorDao.update(
            a.copy(
                stability = res.nextState.stability,
                difficulty = res.nextState.difficulty,
                intervalDays = res.intervalDays,
                reps = res.nextState.reps,
                lapses = res.nextState.lapses,
                dueAt = res.dueAt,
                status = status,
                reinforced = a.reinforced + if (rating != Fsrs.Rating.AGAIN) 1 else 0,
                relapses = a.relapses + if (rating == Fsrs.Rating.AGAIN) 1 else 0,
                lastSeenAt = now
            )
        )
        bumpDaily(now) { it.copy(cardsReviewed = it.cardsReviewed + 1) }
        if (rating != Fsrs.Rating.AGAIN) {
            addXp(4, now)
            registerActivity(now, 4)
        }
    }

    // ───────────────────────── Планы прорыва (WOOP) ─────────────────────────

    suspend fun createPlan(title: String, steps: List<Pair<String, String>>, sessionId: Long, now: Long): Pair<Long, List<Long>> =
        createWoopPlan(
            title = title,
            wish = "",
            outcome = "",
            mainObstacle = "",
            steps = steps.map { WoopStepInput(it.first, it.second) },
            sessionId = sessionId,
            now = now
        )

    suspend fun createWoopPlan(
        title: String,
        wish: String,
        outcome: String,
        mainObstacle: String,
        steps: List<WoopStepInput>,
        sessionId: Long,
        now: Long
    ): Pair<Long, List<Long>> {
        val planId = planDao.insertPlan(
            ActionPlanEntity(
                title = title,
                createdAt = now,
                sourceSessionId = sessionId,
                wish = wish,
                outcome = outcome,
                mainObstacle = mainObstacle
            )
        )
        val rows = steps.mapIndexed { i, st ->
            PlanStepEntity(
                planId = planId,
                step = st.step,
                why = st.why,
                obstacle = st.obstacle,
                ifThenRule = st.ifThenRule,
                position = i
            )
        }
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

    // ───────────────────────── Вторая память: Источники и FSRS-карточки ─────────────────────────

    suspend fun saveSourceWithCards(
        title: String,
        kind: String,
        originRef: String,
        summary: String,
        keyModels: List<String>,
        blindSpots: String,
        rawExcerpt: String,
        cards: List<CardDraft>,
        now: Long = Timex.now()
    ): Long = db.withTransaction {
        val sourceId = sourceDao.insert(
            SourceEntity(
                title = title.trim().take(120).ifEmpty { "Источник знаний" },
                kind = kind,
                originRef = originRef,
                summary = summary.trim(),
                keyModels = JSONArray(keyModels).toString(),
                authorBlindSpots = blindSpots.trim(),
                rawExcerpt = rawExcerpt.take(12_000),
                cardCount = cards.size,
                createdAt = now,
                lastStudiedAt = now
            )
        )
        if (cards.isNotEmpty()) {
            val entities = cards.mapIndexed { idx, c ->
                KnowledgeCardEntity(
                    sourceId = sourceId,
                    skillId = Curriculum.resolve(c.skillId)?.id ?: "",
                    kind = c.kind.ifBlank { "recall" },
                    front = c.front.trim(),
                    back = c.back.trim(),
                    hint = c.hint.trim(),
                    tags = c.tags.trim(),
                    createdAt = now,
                    dueAt = now + idx * 1000L
                )
            }
            knowledgeCardDao.insertAll(entities)
        }
        sourceId
    }

    suspend fun addStandaloneCard(draft: CardDraft, now: Long = Timex.now()): Long =
        knowledgeCardDao.insert(
            KnowledgeCardEntity(
                sourceId = 0L,
                skillId = Curriculum.resolve(draft.skillId)?.id ?: "",
                kind = draft.kind.ifBlank { "recall" },
                front = draft.front.trim(),
                back = draft.back.trim(),
                hint = draft.hint.trim(),
                tags = draft.tags.trim(),
                createdAt = now,
                dueAt = now
            )
        )

    suspend fun reviewCardFsrs(cardId: Long, rating: Fsrs.Rating, now: Long = Timex.now()) {
        val card = knowledgeCardDao.get(cardId) ?: return
        val retention = profile().desiredRetention.coerceIn(0.75, 0.96)
        val cur = Fsrs.MemoryState(
            stability = card.stability,
            difficulty = card.difficulty,
            reps = card.reps,
            lapses = card.lapses,
            lastReviewedAt = card.lastReviewedAt
        )
        val res = Fsrs.review(cur, rating, now, desiredRetention = retention)
        knowledgeCardDao.update(
            card.copy(
                stability = res.nextState.stability,
                difficulty = res.nextState.difficulty,
                intervalDays = res.intervalDays,
                reps = res.nextState.reps,
                lapses = res.nextState.lapses,
                lastReviewedAt = now,
                dueAt = res.dueAt,
                status = res.status
            )
        )
        if (card.skillId.isNotEmpty()) {
            val d = when (rating) {
                Fsrs.Rating.EASY -> 2
                Fsrs.Rating.GOOD -> 1
                Fsrs.Rating.HARD -> 1
                Fsrs.Rating.AGAIN -> -1
            }
            applySkill(card.skillId, d, now)
        }
        bumpDaily(now) { it.copy(cardsReviewed = it.cardsReviewed + 1) }
        val xp = if (rating == Fsrs.Rating.AGAIN) 2 else 6
        addXp(xp, now)
        registerActivity(now, xp)
    }

    suspend fun deleteSource(sourceId: Long) = db.withTransaction {
        knowledgeCardDao.deleteBySource(sourceId)
        sourceDao.delete(sourceId)
    }

    // ───────────────────────── Лаборатория решений и прогнозов (Brier Score) ─────────────────────────

    suspend fun createDecision(
        title: String,
        context: String,
        alternatives: List<String>,
        chosenOption: String,
        baseRateNote: String,
        preMortem: String,
        ifThenSafeguard: String,
        confidencePct: Int,
        reviewDays: Int,
        now: Long = Timex.now()
    ): Long {
        val reviewAt = if (reviewDays > 0) Timex.daysFromNow(reviewDays, 12, now) else 0L
        val id = decisionDao.insert(
            DecisionEntity(
                title = title.trim(),
                context = context.trim(),
                alternatives = JSONArray(alternatives.filter { it.isNotBlank() }).toString(),
                chosenOption = chosenOption.trim(),
                baseRateNote = baseRateNote.trim(),
                preMortem = preMortem.trim(),
                ifThenSafeguard = ifThenSafeguard.trim(),
                confidencePct = confidencePct.coerceIn(1, 99),
                reviewAt = reviewAt,
                createdAt = now
            )
        )
        addXp(12, now)
        registerActivity(now, 12)
        return id
    }

    suspend fun resolveDecision(id: Long, outcomeNote: String, processQuality: Int, now: Long = Timex.now()) {
        val d = decisionDao.get(id) ?: return
        decisionDao.update(
            d.copy(
                status = "reviewed",
                outcomeNote = outcomeNote.trim(),
                processQuality = processQuality.coerceIn(1, 10),
                resolvedAt = now
            )
        )
        addXp(15, now)
        registerActivity(now, 15)
    }

    suspend fun createPrediction(
        question: String,
        probabilityPct: Int,
        category: String,
        rationale: String,
        checkAt: Long,
        decisionId: Long = 0L,
        now: Long = Timex.now()
    ): Long {
        val id = predictionDao.insert(
            PredictionEntity(
                decisionId = decisionId,
                question = question.trim(),
                probabilityPct = probabilityPct.coerceIn(1, 99),
                category = category,
                rationale = rationale.trim(),
                checkAt = checkAt,
                createdAt = now
            )
        )
        addXp(5, now)
        return id
    }

    suspend fun resolvePrediction(id: Long, outcome: Int, now: Long = Timex.now()) {
        val p = predictionDao.get(id) ?: return
        val o = if (outcome > 0) 1 else 0
        val brier = Srs.brierFor(p.probabilityPct, o)
        predictionDao.update(
            p.copy(
                resolved = true,
                outcome = o,
                brierScore = brier,
                resolvedAt = now
            )
        )
        applySkill("dec_calibration", if (brier <= 0.16) 2 else 1, now)
        addXp(10, now)
        registerActivity(now, 10)
        recomputeStage()
    }

    // ───────────────────────── Фокус-студия и Соматика ─────────────────────────

    suspend fun logFocusSession(
        kind: String,
        goal: String,
        obstacle: String,
        ifThenPlan: String,
        plannedMinutes: Int,
        actualMinutes: Int,
        completed: Boolean,
        focusQuality: Int,
        moodBefore: Int,
        moodAfter: Int,
        note: String,
        now: Long = Timex.now()
    ): Long {
        val id = focusSessionDao.insert(
            FocusSessionEntity(
                kind = kind,
                goal = goal.trim(),
                obstacle = obstacle.trim(),
                ifThenPlan = ifThenPlan.trim(),
                plannedMinutes = plannedMinutes,
                actualMinutes = actualMinutes,
                completed = completed,
                focusQuality = focusQuality,
                moodBefore = moodBefore,
                moodAfter = moodAfter,
                note = note.trim(),
                createdAt = now
            )
        )
        val mins = actualMinutes.coerceAtLeast(1)
        bumpDaily(now) { it.copy(focusMinutes = it.focusMinutes + mins) }
        if (moodAfter in 1..10) recordMood(moodAfter, now)
        val xp = if (completed) max(5, mins / 3) else 2
        addXp(xp, now)
        registerActivity(now, xp)
        return id
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

    // ───────────────────────── Экспорт, Импорт (Restore) и Сброс ─────────────────────────

    suspend fun exportSessionText(sessionId: Long): String {
        val s = sessionDao.get(sessionId) ?: return ""
        val sb = StringBuilder()
        sb.append("Чат «").append(s.title).append("» — Атлас\n\n")
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
        val root = JSONObject().put("version", 3).put("exportedAt", Timex.now())
        val p = profile()
        root.put(
            "profile",
            JSONObject()
                .put("name", p.name).put("stage", p.stage).put("cognitiveRank", p.cognitiveRank)
                .put("goal", p.goal).put("interests", p.interests).put("about", p.about)
                .put("tactic", p.tactic).put("xp", p.xp).put("streak", p.streak)
                .put("bestStreak", p.bestStreak).put("lastMood", p.lastMood).put("formal", p.formal)
                .put("desiredRetention", p.desiredRetention).put("socraticDefault", p.socraticDefault)
        )

        val anchors = JSONArray()
        for (a in anchorDao.newest(100_000)) {
            anchors.put(
                JSONObject().put("phrase", a.phrase).put("why", a.why).put("source", a.source)
                    .put("trigger", a.trigger).put("origin", a.origin).put("status", a.status)
                    .put("reinforced", a.reinforced).put("relapses", a.relapses)
                    .put("stability", a.stability).put("difficulty", a.difficulty)
                    .put("intervalDays", a.intervalDays).put("reps", a.reps).put("lapses", a.lapses)
                    .put("dueAt", a.dueAt).put("createdAt", a.createdAt).put("lastSeenAt", a.lastSeenAt)
            )
        }
        root.put("anchors", anchors)

        val traps = JSONArray()
        for (t in trapDao.top(100_000)) {
            traps.put(
                JSONObject().put("thought", t.thought).put("antidote", t.antidote)
                    .put("skill", t.skillId).put("why", t.why).put("times", t.times)
                    .put("resolved", t.resolved)
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
            for (st in planDao.steps(pl.id)) {
                steps.put(
                    JSONObject().put("step", st.step).put("why", st.why)
                        .put("obstacle", st.obstacle).put("ifThenRule", st.ifThenRule)
                        .put("done", st.streak >= 1)
                )
            }
            plans.put(
                JSONObject().put("title", pl.title).put("wish", pl.wish)
                    .put("outcome", pl.outcome).put("mainObstacle", pl.mainObstacle)
                    .put("steps", steps)
            )
        }
        root.put("plans", plans)

        val sources = JSONArray()
        for (src in sourceDao.all()) {
            val srcCards = JSONArray()
            for (c in knowledgeCardDao.recentOrPracticePool(10_000).filter { it.sourceId == src.id }) {
                srcCards.put(cardToJson(c))
            }
            sources.put(
                JSONObject().put("title", src.title).put("kind", src.kind)
                    .put("originRef", src.originRef).put("summary", src.summary)
                    .put("keyModels", src.keyModels).put("authorBlindSpots", src.authorBlindSpots)
                    .put("rawExcerpt", src.rawExcerpt).put("createdAt", src.createdAt)
                    .put("cards", srcCards)
            )
        }
        root.put("sources", sources)

        val standaloneCards = JSONArray()
        for (c in knowledgeCardDao.recentOrPracticePool(10_000).filter { it.sourceId == 0L }) {
            standaloneCards.put(cardToJson(c))
        }
        root.put("cards", standaloneCards)

        val decisions = JSONArray()
        for (d in decisionDao.dueForReview(Long.MAX_VALUE, 10_000) + emptyList()) {
            // handled below via exportDecisions
        }
        // Используем прямые списки через all
        val allDecisions = withContext(Dispatchers.IO) {
            val list = ArrayList<DecisionEntity>()
            // Читаем напрямую через raw или существующие запросы не нужны — добавим через простой обход
            list
        }
        // Примечание: ниже экспортируем все решения/прогнозы/фокус/обзоры напрямую через DAO-снимки
        root.put("decisions", exportDecisionsArray())
        root.put("predictions", exportPredictionsArray())
        root.put("weeklyReviews", exportWeeklyReviewsArray())

        val sessions = JSONArray()
        for (s in sessionDao.all()) {
            val msgs = JSONArray()
            for (m in messageDao.all(s.id)) {
                if (m.role == ROLE_INFO) continue
                msgs.put(
                    JSONObject().put("role", m.role).put("at", m.createdAt)
                        .put("text", m.text).put("kind", m.kind).put("starred", m.starred)
                )
            }
            sessions.put(
                JSONObject().put("title", s.title).put("mode", s.mode)
                    .put("modeArg", s.modeArg).put("emoji", s.emoji)
                    .put("isHome", s.isHome).put("messages", msgs)
            )
        }
        root.put("sessions", sessions)
        return root.toString(2)
    }

    private fun cardToJson(c: KnowledgeCardEntity): JSONObject = JSONObject()
        .put("skillId", c.skillId).put("kind", c.kind).put("front", c.front)
        .put("back", c.back).put("hint", c.hint).put("tags", c.tags)
        .put("stability", c.stability).put("difficulty", c.difficulty)
        .put("intervalDays", c.intervalDays).put("reps", c.reps).put("lapses", c.lapses)
        .put("status", c.status).put("dueAt", c.dueAt).put("lastReviewedAt", c.lastReviewedAt)
        .put("createdAt", c.createdAt)

    private suspend fun exportDecisionsArray(): JSONArray {
        val arr = JSONArray()
        val cursor = db.openHelper.readableDatabase.query("SELECT id, title, context, alternatives, chosenOption, baseRateNote, preMortem, ifThenSafeguard, confidencePct, status, reviewAt, outcomeNote, processQuality, createdAt, resolvedAt FROM decisions ORDER BY createdAt DESC")
        cursor.use { c ->
            while (c.moveToNext()) {
                arr.put(
                    JSONObject()
                        .put("title", c.getString(1)).put("context", c.getString(2))
                        .put("alternatives", c.getString(3)).put("chosenOption", c.getString(4))
                        .put("baseRateNote", c.getString(5)).put("preMortem", c.getString(6))
                        .put("ifThenSafeguard", c.getString(7)).put("confidencePct", c.getInt(8))
                        .put("status", c.getString(9)).put("reviewAt", c.getLong(10))
                        .put("outcomeNote", c.getString(11)).put("processQuality", c.getInt(12))
                        .put("createdAt", c.getLong(13)).put("resolvedAt", c.getLong(14))
                )
            }
        }
        return arr
    }

    private suspend fun exportPredictionsArray(): JSONArray {
        val arr = JSONArray()
        val cursor = db.openHelper.readableDatabase.query("SELECT question, probabilityPct, category, rationale, checkAt, resolved, outcome, brierScore, createdAt, resolvedAt FROM predictions ORDER BY createdAt DESC")
        cursor.use { c ->
            while (c.moveToNext()) {
                arr.put(
                    JSONObject()
                        .put("question", c.getString(0)).put("probabilityPct", c.getInt(1))
                        .put("category", c.getString(2)).put("rationale", c.getString(3))
                        .put("checkAt", c.getLong(4)).put("resolved", c.getInt(5) != 0)
                        .put("outcome", c.getInt(6)).put("brierScore", c.getDouble(7))
                        .put("createdAt", c.getLong(8)).put("resolvedAt", c.getLong(9))
                )
            }
        }
        return arr
    }

    private suspend fun exportWeeklyReviewsArray(): JSONArray {
        val arr = JSONArray()
        val cursor = db.openHelper.readableDatabase.query("SELECT weekStartDay, summary, nextWeekChallenge, topTrigger, bestWin, scoresJson, brierAvg, avgMood, createdAt FROM weekly_reviews ORDER BY createdAt DESC")
        cursor.use { c ->
            while (c.moveToNext()) {
                arr.put(
                    JSONObject()
                        .put("weekStartDay", c.getString(0)).put("summary", c.getString(1))
                        .put("nextWeekChallenge", c.getString(2)).put("topTrigger", c.getString(3))
                        .put("bestWin", c.getString(4)).put("scoresJson", c.getString(5))
                        .put("brierAvg", c.getDouble(6)).put("avgMood", c.getDouble(7))
                        .put("createdAt", c.getLong(8))
                )
            }
        }
        return arr
    }

    /** Безопасный импорт / слияние данных из JSON-бэкапа Атласа или Фридриха v2/v3. */
    suspend fun importBackupJson(rawJson: String): ImportSummary = db.withTransaction {
        val root = JSONObject(rawJson)
        val now = Timex.now()
        var anchorsAdded = 0
        var cardsAdded = 0
        var sourcesAdded = 0
        var decisionsAdded = 0
        var predictionsAdded = 0
        var factsAdded = 0
        var sessionsAdded = 0

        root.obj("profile")?.let { po ->
            updateProfile { cur ->
                cur.copy(
                    name = cur.name.ifBlank { po.str("name") },
                    goal = cur.goal.ifBlank { po.str("goal") },
                    interests = cur.interests.ifBlank { po.str("interests") },
                    about = cur.about.ifBlank { po.str("about") },
                    tactic = cur.tactic.ifBlank { po.str("tactic") },
                    xp = max(cur.xp, po.int("xp")),
                    bestStreak = max(cur.bestStreak, po.int("bestStreak"))
                )
            }
        }

        root.arr("anchors")?.objects()?.forEach { ao ->
            val phrase = ao.str("phrase").trim()
            val why = ao.str("why").trim()
            val key = Norm.anchorKey(phrase)
            if (key.length >= 4 && why.isNotEmpty() && anchorDao.byKey(key) == null) {
                anchorDao.insert(
                    AnchorEntity(
                        keyNorm = key,
                        phrase = phrase,
                        why = why,
                        source = ao.str("source"),
                        trigger = ao.str("trigger"),
                        origin = ao.str("origin", "user"),
                        createdAt = ao.long("createdAt", now),
                        lastSeenAt = ao.long("lastSeenAt", now),
                        dueAt = ao.long("dueAt", now + Srs.FIRST_DELAY),
                        intervalDays = ao.dbl("intervalDays"),
                        reps = ao.int("reps"),
                        lapses = ao.int("lapses"),
                        reinforced = ao.int("reinforced"),
                        relapses = ao.int("relapses"),
                        status = ao.str("status", "new"),
                        stability = ao.dbl("stability"),
                        difficulty = ao.dbl("difficulty", 5.0)
                    )
                )
                anchorsAdded++
            }
        }

        root.arr("traps")?.objects()?.forEach { to ->
            val thought = to.str("thought").trim()
            val antidote = to.str("antidote").trim()
            if (thought.isNotEmpty() && antidote.isNotEmpty()) {
                addTrap(thought, antidote, to.str("skill"), to.str("why"), now)
            }
        }

        root.obj("skills")?.let { skObj ->
            val keys = skObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val v = skObj.int(id)
                if (Curriculum.topic(id) != null && v > 0) {
                    val cur = skillDao.get(id)
                    if (cur == null || cur.mastery < v) setMastery(id, v)
                }
            }
        }

        root.arr("facts")?.strings()?.forEach { f ->
            if (addFact(f, "user", now)) factsAdded++
        }

        root.arr("sources")?.objects()?.forEach { so ->
            val title = so.str("title").trim()
            if (title.isNotEmpty()) {
                val sid = sourceDao.insert(
                    SourceEntity(
                        title = title,
                        kind = so.str("kind", "text"),
                        originRef = so.str("originRef"),
                        summary = so.str("summary"),
                        keyModels = so.str("keyModels"),
                        authorBlindSpots = so.str("authorBlindSpots"),
                        rawExcerpt = so.str("rawExcerpt"),
                        cardCount = so.arr("cards")?.length() ?: 0,
                        createdAt = so.long("createdAt", now),
                        lastStudiedAt = now
                    )
                )
                sourcesAdded++
                so.arr("cards")?.objects()?.forEach { co ->
                    insertImportedCard(sid, co, now)
                    cardsAdded++
                }
            }
        }

        root.arr("cards")?.objects()?.forEach { co ->
            insertImportedCard(0L, co, now)
            cardsAdded++
        }

        root.arr("decisions")?.objects()?.forEach { dObj ->
            val title = dObj.str("title").trim()
            if (title.isNotEmpty()) {
                decisionDao.insert(
                    DecisionEntity(
                        title = title,
                        context = dObj.str("context"),
                        alternatives = dObj.str("alternatives"),
                        chosenOption = dObj.str("chosenOption"),
                        baseRateNote = dObj.str("baseRateNote"),
                        preMortem = dObj.str("preMortem"),
                        ifThenSafeguard = dObj.str("ifThenSafeguard"),
                        confidencePct = dObj.int("confidencePct", 50),
                        status = dObj.str("status", "open"),
                        reviewAt = dObj.long("reviewAt"),
                        outcomeNote = dObj.str("outcomeNote"),
                        processQuality = dObj.int("processQuality"),
                        createdAt = dObj.long("createdAt", now),
                        resolvedAt = dObj.long("resolvedAt")
                    )
                )
                decisionsAdded++
            }
        }

        root.arr("predictions")?.objects()?.forEach { pObj ->
            val q = pObj.str("question").trim()
            if (q.isNotEmpty()) {
                predictionDao.insert(
                    PredictionEntity(
                        question = q,
                        probabilityPct = pObj.int("probabilityPct", 50).coerceIn(1, 99),
                        category = pObj.str("category", "personal"),
                        rationale = pObj.str("rationale"),
                        checkAt = pObj.long("checkAt", now),
                        resolved = pObj.bool("resolved"),
                        outcome = pObj.int("outcome", -1),
                        brierScore = pObj.dbl("brierScore", -1.0),
                        createdAt = pObj.long("createdAt", now),
                        resolvedAt = pObj.long("resolvedAt")
                    )
                )
                predictionsAdded++
            }
        }

        // Переписки импортируем, если пользователь хочет восстановить историю (не дублируем главный чат).
        root.arr("sessions")?.objects()?.forEach { sObj ->
            val msgs = sObj.arr("messages")?.objects().orEmpty()
            if (msgs.isEmpty()) return@forEach
            val isHome = sObj.bool("isHome")
            val sid = if (isHome) {
                val home = ensureHome()
                if (messageDao.count(home.id) == 0) home.id else 0L
            } else {
                createSession(
                    mode = sObj.str("mode", "free"),
                    modeArg = sObj.str("modeArg"),
                    title = sObj.str("title", "Восстановленный чат"),
                    emoji = sObj.str("emoji", "⚡")
                )
            }
            if (sid > 0L) {
                sessionsAdded++
                for (m in msgs) {
                    messageDao.insert(
                        MessageEntity(
                            sessionId = sid,
                            role = m.str("role", ROLE_USER).let { if (it == "friedrich") ROLE_ATLAS else it },
                            text = m.str("text"),
                            createdAt = m.long("at", now),
                            kind = m.str("kind", "chat"),
                            starred = m.bool("starred")
                        )
                    )
                }
                refreshPreview(sid)
            }
        }

        recomputeStage()
        ImportSummary(
            anchorsAdded = anchorsAdded,
            cardsAdded = cardsAdded,
            sourcesAdded = sourcesAdded,
            decisionsAdded = decisionsAdded,
            predictionsAdded = predictionsAdded,
            factsAdded = factsAdded,
            sessionsAdded = sessionsAdded
        )
    }

    private suspend fun insertImportedCard(sourceId: Long, co: JSONObject, now: Long) {
        val front = co.str("front").trim()
        val back = co.str("back").trim()
        if (front.isEmpty() || back.isEmpty()) return
        knowledgeCardDao.insert(
            KnowledgeCardEntity(
                sourceId = sourceId,
                skillId = co.str("skillId"),
                kind = co.str("kind", "recall"),
                front = front,
                back = back,
                hint = co.str("hint"),
                tags = co.str("tags"),
                createdAt = co.long("createdAt", now),
                lastReviewedAt = co.long("lastReviewedAt"),
                dueAt = co.long("dueAt", now),
                stability = co.dbl("stability"),
                difficulty = co.dbl("difficulty", 5.0),
                intervalDays = co.dbl("intervalDays"),
                reps = co.int("reps"),
                lapses = co.int("lapses"),
                status = co.str("status", "new")
            )
        )
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