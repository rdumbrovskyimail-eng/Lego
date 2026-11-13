package com.legode.friedrich.brain

import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.db.SessionEntity
import org.json.JSONObject

/**
 * Применяет служебные поля ответа Фридриха к базе знаний об ученике:
 * оценки ответов → интервальные повторения, новые слова → словарь, ошибки → журнал ошибок,
 * практика → освоенность тем и уровень, факты → память, присланный материал → списки для отработки.
 */
class MemoryEngine(private val repo: Repository) {

    data class Delta(
        val result: String = "none",
        val wordsAdded: Int = 0,
        val fixes: Int = 0,
        val listCreated: String = "",
        val listItems: Int = 0,
        val listFinished: String = "",
        val newAskedRef: String = "",
        val newListId: Long = -1L,
        val levelUp: String = ""
    ) {
        /** Компактный JSON для «чипов» под сообщением: что Friedrich запомнил на этом ходу. */
        fun toMeta(): String {
            val o = JSONObject()
            if (result != "none") o.put("result", result)
            if (wordsAdded > 0) o.put("words", wordsAdded)
            if (fixes > 0) o.put("fixes", fixes)
            if (listCreated.isNotEmpty()) o.put("list", listCreated).put("listItems", listItems)
            if (listFinished.isNotEmpty()) o.put("listDone", listFinished)
            if (levelUp.isNotEmpty()) o.put("level", levelUp)
            return if (o.length() == 0) "" else o.toString()
        }
    }

    suspend fun apply(session: SessionEntity, reply: Reply, now: Long): Delta {
        var listId = session.listId
        var listFinished = ""
        val levelBefore = repo.profile().level

        // 1. Оценка ответа на ранее заданный элемент.
        var graded = "none"
        if (session.askedRef.isNotEmpty() && reply.result != "none") {
            val id = session.askedRef.drop(1).toLongOrNull()
            if (id != null) {
                when (session.askedRef.first()) {
                    'V' -> {
                        repo.gradeVocab(id, reply.result, now)
                        graded = reply.result
                    }

                    'L' -> {
                        val progress = repo.gradeListItem(id, reply.result, now)
                        graded = reply.result
                        if (progress != null && progress.total > 0 && progress.mastered >= progress.total &&
                            progress.listId == listId
                        ) {
                            listFinished = repo.listDao.getList(listId)?.title ?: "список"
                        }
                    }
                }
            }
        } else if (reply.result != "none") {
            graded = reply.result
        }
        when (graded) {
            "correct" -> {
                repo.addXp(5, now)
                repo.bumpDaily(now) { it.copy(correct = it.correct + 1) }
            }

            "partial" -> repo.addXp(2, now)
            "wrong" -> repo.bumpDaily(now) { it.copy(wrong = it.wrong + 1) }
        }

        // 2. Новые слова.
        var added = 0
        for (w in reply.words) {
            if (repo.upsertWord(w.de, w.ru, w.plural, w.example, "friedrich", now)) added++
        }

        // 3. Ошибки (и минус соответствующей теме, если модель сама её не отметила).
        val topicIds = reply.topics.map { it.id }.toSet()
        for (f in reply.fixes) {
            repo.addMistake(f.bad, f.good, f.topicId, f.why, now)
            if (f.topicId.isNotEmpty() && f.topicId !in topicIds) repo.applyTopic(f.topicId, -1, now)
        }

        // 4. Практика по темам.
        for (t in reply.topics) repo.applyTopic(t.id, t.d, now)

        // 5. Факты и профиль.
        for (fact in reply.facts) repo.addFact(fact, "friedrich", now)
        if (reply.name.isNotBlank() || reply.goal.isNotBlank() || reply.interests.isNotBlank()) {
            repo.updateProfile { p ->
                p.copy(
                    name = if (reply.name.isNotBlank() && p.name.isBlank()) reply.name else p.name,
                    goal = if (reply.goal.isNotBlank() && p.goal.isBlank()) reply.goal else p.goal,
                    interests = if (reply.interests.isNotBlank() && p.interests.isBlank()) reply.interests else p.interests
                )
            }
        }
        if (reply.level.isNotEmpty() && session.mode == Skills.PLACEMENT) repo.applyLevelFloor(reply.level)

        // 6. Материал ученика → список для отработки.
        var createdTitle = ""
        var createdCount = 0
        var newIds: List<Long> = emptyList()
        val note = reply.list
        if (note != null && note.items.isNotEmpty()) {
            val created = repo.createList(note.title, note.items.map { it.de to it.ru }, session.id, now)
            listId = created.first
            newIds = created.second
            createdTitle = note.title
            createdCount = note.items.size
            listFinished = ""
        }

        // 7. Завершение списка.
        if (reply.listDone && listId != 0L && createdTitle.isEmpty() && listFinished.isEmpty()) {
            listFinished = repo.listDao.getList(listId)?.title ?: "список"
        }
        if (listFinished.isNotEmpty()) {
            listId = 0L
            repo.addXp(20, now)
        }

        // 8. Что Friedrich спросил теперь.
        val asked = resolveAsked(reply.asked, newIds, listId)

        val levelAfter = repo.recomputeLevel()
        return Delta(
            result = graded,
            wordsAdded = added,
            fixes = reply.fixes.size,
            listCreated = createdTitle,
            listItems = createdCount,
            listFinished = listFinished,
            newAskedRef = asked,
            newListId = listId,
            levelUp = if (levelAfter != levelBefore && rank(levelAfter) > rank(levelBefore)) levelAfter else ""
        )
    }

    private fun rank(level: String): Int = when (level) {
        "A0" -> 0
        "A1.1" -> 1
        "A1.2" -> 2
        "A1" -> 3
        else -> 0
    }

    /** Проверяет ссылку на заданный элемент; "N3" превращает в настоящий id элемента нового списка. */
    private suspend fun resolveAsked(raw: String, newIds: List<Long>, listId: Long): String {
        if (raw.isEmpty()) return ""
        val n = raw.drop(1).toLongOrNull() ?: return ""
        return when (raw.first()) {
            'N' -> {
                val idx = (n - 1).toInt()
                if (idx in newIds.indices) "L${newIds[idx]}" else ""
            }

            'V' -> if (repo.vocabDao.get(n) != null) raw else ""
            'L' -> {
                val item = repo.listDao.getItem(n)
                if (item != null && (listId == 0L || item.listId == listId)) raw else ""
            }

            else -> ""
        }
    }
}
