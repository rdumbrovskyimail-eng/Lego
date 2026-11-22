package com.legode.friedrich.brain

import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.db.SessionEntity
import org.json.JSONObject

/**
 * Применяет служебную аналитику из ответа Атласа к базе знаний пользователя:
 *  • балл настроения (mood) и эмоция → дневная траектория и профиль;
 *  • оценка выполнения шага / якоря → интервальное подкрепление (SRS) и прогресс плана;
 *  • anchors → личный банк якорей силы;
 *  • traps → журнал когнитивных ловушек ума;
 *  • skills → освоенность психологических навыков;
 *  • facts / profile / plan → долговременная память и планы прорыва.
 */
class MemoryEngine(private val repo: Repository) {

    data class Delta(
        val mood: Int = 0,
        val emotion: String = "",
        val result: String = "none",
        val anchorsAdded: Int = 0,
        val trapsLogged: Int = 0,
        val planCreated: String = "",
        val planSteps: Int = 0,
        val planFinished: String = "",
        val newAskedRef: String = "",
        val newPlanId: Long = -1L,
        val stageUp: String = ""
    ) {
        /** Компактный JSON для «чипов» под сообщением в чате. */
        fun toMeta(): String {
            val o = JSONObject()
            if (mood > 0) o.put("mood", mood)
            if (emotion.isNotEmpty()) o.put("emotion", emotion)
            if (result != "none") o.put("result", result)
            if (anchorsAdded > 0) o.put("anchors", anchorsAdded)
            if (trapsLogged > 0) o.put("traps", trapsLogged)
            if (planCreated.isNotEmpty()) o.put("plan", planCreated).put("planSteps", planSteps)
            if (planFinished.isNotEmpty()) o.put("planDone", planFinished)
            if (stageUp.isNotEmpty()) o.put("stage", stageUp)
            return if (o.length() == 0) "" else o.toString()
        }
    }

    suspend fun apply(session: SessionEntity, reply: Reply, now: Long): Delta {
        var planId = session.planId
        var planFinished = ""
        val stageBefore = repo.profile().stage

        // 1. Фиксируем состояние и эмоцию.
        if (reply.mood in 1..10) {
            repo.recordMood(reply.mood, now)
        }

        // 2. Оценка ответа / выполнения по последнему заданному элементу.
        var graded = "none"
        if (session.askedRef.isNotEmpty() && reply.result != "none") {
            val id = session.askedRef.drop(1).toLongOrNull()
            if (id != null) {
                when (session.askedRef.first()) {
                    'A' -> {
                        repo.gradeAnchor(id, reply.result, now)
                        graded = reply.result
                    }

                    'S' -> {
                        val progress = repo.gradePlanStep(id, reply.result, now)
                        graded = reply.result
                        if (progress != null && progress.total > 0 && progress.done >= progress.total &&
                            progress.planId == planId
                        ) {
                            planFinished = repo.planDao.getPlan(planId)?.title ?: "план"
                        }
                    }
                }
            }
        } else if (reply.result != "none") {
            graded = reply.result
        }

        when (graded) {
            "done" -> {
                repo.addXp(10, now)
                repo.bumpDaily(now) { it.copy(stepsDone = it.stepsDone + 1) }
            }

            "partial" -> repo.addXp(4, now)
            "skipped" -> repo.bumpDaily(now) { it.copy(slips = it.slips + 1) }
        }

        // 3. Новые якоря силы.
        var addedAnchors = 0
        for (a in reply.anchors) {
            if (repo.upsertAnchor(a.phrase, a.why, a.source, a.trigger, "atlas", now)) addedAnchors++
        }

        // 4. Когнитивные ловушки (и лёгкий штраф навыку, если модель сама не отметила его).
        val skillIds = reply.skills.map { it.id }.toSet()
        for (t in reply.traps) {
            repo.addTrap(t.thought, t.antidote, t.skillId, t.why, now)
            if (t.skillId.isNotEmpty() && t.skillId !in skillIds) repo.applySkill(t.skillId, -1, now)
        }

        // 5. Практика навыков устойчивости.
        for (s in reply.skills) repo.applySkill(s.id, s.d, now)

        // 6. Факты и профиль.
        for (fact in reply.facts) repo.addFact(fact, "atlas", now)
        if (reply.name.isNotBlank() || reply.goal.isNotBlank() || reply.interests.isNotBlank()) {
            repo.updateProfile { p ->
                p.copy(
                    name = if (reply.name.isNotBlank() && p.name.isBlank()) reply.name else p.name,
                    goal = if (reply.goal.isNotBlank() && p.goal.isBlank()) reply.goal else p.goal,
                    interests = if (reply.interests.isNotBlank() && p.interests.isBlank()) reply.interests else p.interests
                )
            }
        }
        if (reply.stage.isNotEmpty() && session.mode == Skills.DIAGNOSTIC) repo.applyStageFloor(reply.stage)

        // 7. Новый план прорыва.
        var createdTitle = ""
        var createdCount = 0
        var newIds: List<Long> = emptyList()
        val plan = reply.plan
        if (plan != null && plan.steps.isNotEmpty()) {
            val created = repo.createPlan(plan.title, plan.steps.map { it.step to it.why }, session.id, now)
            planId = created.first
            newIds = created.second
            createdTitle = plan.title
            createdCount = plan.steps.size
            planFinished = ""
        }

        // 8. Завершение активного плана.
        if (reply.planDone && planId != 0L && createdTitle.isEmpty() && planFinished.isEmpty()) {
            planFinished = repo.planDao.getPlan(planId)?.title ?: "план"
        }
        if (planFinished.isNotEmpty()) {
            planId = 0L
            repo.addXp(25, now)
        }

        // 9. Ссылка на новый шаг / якорь.
        val asked = resolveAsked(reply.asked, newIds, planId)

        val stageAfter = repo.recomputeStage()
        return Delta(
            mood = reply.mood,
            emotion = reply.emotion,
            result = graded,
            anchorsAdded = addedAnchors,
            trapsLogged = reply.traps.size,
            planCreated = createdTitle,
            planSteps = createdCount,
            planFinished = planFinished,
            newAskedRef = asked,
            newPlanId = planId,
            stageUp = if (stageAfter != stageBefore && rank(stageAfter) > rank(stageBefore)) stageAfter else ""
        )
    }

    private fun rank(stage: String): Int = when (stage) {
        "истощение" -> 0
        "спад" -> 1
        "стабильность" -> 2
        "сила" -> 3
        else -> 0
    }

    /** Проверяет ссылку на шаг или якорь; "N3" превращает в реальный id шага нового плана. */
    private suspend fun resolveAsked(raw: String, newIds: List<Long>, planId: Long): String {
        if (raw.isEmpty()) return ""
        val n = raw.drop(1).toLongOrNull() ?: return ""
        return when (raw.first()) {
            'N' -> {
                val idx = (n - 1).toInt()
                if (idx in newIds.indices) "S${newIds[idx]}" else ""
            }

            'A' -> if (repo.anchorDao.get(n) != null) raw else ""
            'S' -> {
                val step = repo.planDao.getStep(n)
                if (step != null && (planId == 0L || step.planId == planId)) raw else ""
            }

            else -> ""
        }
    }
}