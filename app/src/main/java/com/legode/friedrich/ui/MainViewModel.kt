package com.legode.friedrich.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legode.friedrich.audio.Speaker
import com.legode.friedrich.brain.Brain
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.DecisionAssistNote
import com.legode.friedrich.brain.Fsrs
import com.legode.friedrich.brain.GeminiClient
import com.legode.friedrich.brain.KnowledgeBase
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.ActionPlanEntity
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.data.db.DailyEntity
import com.legode.friedrich.data.db.DecisionEntity
import com.legode.friedrich.data.db.FactEntity
import com.legode.friedrich.data.db.FocusSessionEntity
import com.legode.friedrich.data.db.JournalEntity
import com.legode.friedrich.data.db.KnowledgeCardEntity
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.PlanProgress
import com.legode.friedrich.data.db.PredictionEntity
import com.legode.friedrich.data.db.ProfileEntity
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.SkillProgressEntity
import com.legode.friedrich.data.db.SourceEntity
import com.legode.friedrich.data.db.TrapEntity
import com.legode.friedrich.data.db.WeeklyReviewEntity
import com.legode.friedrich.notify.Jobs
import com.legode.friedrich.notify.KeepAliveService
import com.legode.friedrich.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class RootTab(val label: String) {
    CHATS("Диалоги"),
    TRAINER("Тренажёр"),
    DECISIONS("Решения"),
    LIBRARY("База"),
    DOSSIER("Досье")
}

sealed class Screen {
    object Sessions : Screen()
    data class Chat(val sessionId: Long) : Screen()
    object Dossier : Screen()
    data class Reference(val topicId: String?) : Screen()
    object Settings : Screen()
}

data class SharedPayload(val text: String, val uris: List<Uri>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    val settings: SettingsStore,
    val runtime: RuntimeState,
    val repo: Repository,
    val brain: Brain,
    val attachments: AttachmentStore,
    val kb: KnowledgeBase,
    val speaker: Speaker,
    private val notifier: Notifier
) : ViewModel() {

    // ───────────────────────── Навигация ─────────────────────────

    val stack = mutableStateListOf<Screen>(Screen.Sessions)
    var rootTab by mutableStateOf(RootTab.CHATS)
        private set
    var shared by mutableStateOf<SharedPayload?>(null)

    fun selectTab(tab: RootTab) {
        rootTab = tab
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun open(screen: Screen) {
        when (screen) {
            is Screen.Dossier -> {
                selectTab(RootTab.DOSSIER)
                return
            }
            is Screen.Reference -> if (screen.topicId == null) {
                selectTab(RootTab.LIBRARY)
                return
            }
            else -> Unit
        }
        if (stack.lastOrNull() == screen) return
        if (screen is Screen.Chat) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (rootTab != RootTab.CHATS) {
            rootTab = RootTab.CHATS
            return true
        }
        return false
    }

    fun openChat(sessionId: Long) = open(Screen.Chat(sessionId))

    fun openHomeChat() {
        viewModelScope.launch { openChat(repo.ensureHome().id) }
    }

    fun openFromNotification(sessionId: Long) {
        viewModelScope.launch {
            val id = if (sessionId != 0L && repo.sessionDao.get(sessionId) != null) sessionId else repo.ensureHome().id
            openChat(id)
        }
    }

    fun acceptShare(text: String, uris: List<Uri>) {
        if (text.isBlank() && uris.isEmpty()) return
        shared = SharedPayload(text, uris)
        if (settings.value.onboarded) openHomeChat()
    }

    // ───────────────────────── Потоки данных ─────────────────────────

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settingsFlow: StateFlow<Settings> = settings.flow
    val sessions: StateFlow<List<SessionEntity>> = repo.sessionDao.observeAll().state(emptyList())
    val profile: StateFlow<ProfileEntity?> = repo.profileDao.observe().state(null)
    val anchors: StateFlow<List<AnchorEntity>> = repo.anchorDao.observeAll().state(emptyList())
    val traps: StateFlow<List<TrapEntity>> = repo.trapDao.observeAll().state(emptyList())
    val skills: StateFlow<List<SkillProgressEntity>> = repo.skillDao.observeAll().state(emptyList())
    val plans: StateFlow<List<ActionPlanEntity>> = repo.planDao.observePlans().state(emptyList())
    val planProgress: StateFlow<List<PlanProgress>> = repo.planDao.observeProgress().state(emptyList())
    val facts: StateFlow<List<FactEntity>> = repo.factDao.observeAll().state(emptyList())
    val journal: StateFlow<List<JournalEntity>> = repo.journalDao.observeAll().state(emptyList())
    val daily: StateFlow<List<DailyEntity>> = repo.dailyDao.observeRecent(14).state(emptyList())
    val pingCount: StateFlow<Int> = repo.pingDao.observeCount().state(0)
    val pingAnswered: StateFlow<Int> = repo.pingDao.observeAnswered().state(0)
    val nextPing: StateFlow<Long> = runtime.nextPingFlow
    val speaking: StateFlow<Boolean> = speaker.speaking

    val sources: StateFlow<List<SourceEntity>> = repo.sourceDao.observeAll().state(emptyList())
    val knowledgeCards: StateFlow<List<KnowledgeCardEntity>> = repo.knowledgeCardDao.observeAllActive().state(emptyList())
    val decisions: StateFlow<List<DecisionEntity>> = repo.decisionDao.observeAll().state(emptyList())
    val predictions: StateFlow<List<PredictionEntity>> = repo.predictionDao.observeAll().state(emptyList())
    val focusSessions: StateFlow<List<FocusSessionEntity>> = repo.focusSessionDao.observeRecent().state(emptyList())
    val weeklyReviews: StateFlow<List<WeeklyReviewEntity>> = repo.weeklyReviewDao.observeAll().state(emptyList())
    val starredMessages: StateFlow<List<MessageEntity>> = repo.messageDao.observeStarred().state(emptyList())

    val activityGrid: StateFlow<Array<IntArray>> = sessions.mapLatest {
        val msgs = repo.messageDao.since(Timex.now() - 14 * Srs.DAY).filter { it.role == Repository.ROLE_USER }
        val grid = Array(7) { IntArray(4) }
        for (m in msgs) {
            val d = Timex.dayOfWeekIndex(m.createdAt)
            val h = Timex.hourOfDay(m.createdAt)
            val p = when (h) {
                in 5..11 -> 0
                in 12..17 -> 1
                in 18..22 -> 2
                else -> 3
            }
            grid[d][p]++
        }
        grid
    }.state(Array(7) { IntArray(4) })

    // ───────────────────────── Жизненный цикл и онбординг ─────────────────────────

    fun onForeground() {
        viewModelScope.launch(Dispatchers.IO) { kb.ensureLoaded() }
        if (!settings.value.onboarded) return
        notifier.ensureChannels()
        if (settings.value.keepAlive) KeepAliveService.start(app)
        Jobs.watchdog(app)
        viewModelScope.launch {
            repo.ensureHome()
            brain.ensureScheduled()
        }
    }

    fun completeOnboarding(name: String, selfState: Int, goal: String, intensity: Int, pushiness: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { kb.ensureLoaded() }
            repo.updateProfile { it.copy(name = name.trim(), goal = goal.trim()) }
            repo.seedMastery(Curriculum.seedMastery(selfState))
            settings.update {
                it.copy(
                    onboarded = true,
                    pingIntensity = intensity.coerceIn(0, 4),
                    pushiness = pushiness.coerceIn(0, 2)
                )
            }
            val home = repo.ensureHome()
            notifier.ensureChannels()
            if (settings.value.keepAlive) KeepAliveService.start(app)
            Jobs.watchdog(app)
            stack.clear()
            stack.add(Screen.Sessions)
            rootTab = RootTab.CHATS
            stack.add(Screen.Chat(home.id))
            brain.requestKickoff(home.id)
            brain.rearm()
        }
    }

    // ───────────────────────── Сессии ─────────────────────────

    fun newSession(mode: String, arg: String = "", planId: Long = 0L, sourceId: Long = 0L, decisionId: Long = 0L) {
        viewModelScope.launch {
            val socratic = settings.value.socraticByDefault || Skills.mode(mode).defaultSocratic
            val id = repo.createSession(
                mode = mode,
                modeArg = arg,
                title = Skills.defaultTitle(mode, arg),
                emoji = Skills.emoji(mode, arg),
                planId = planId,
                socraticMode = socratic,
                sourceId = sourceId,
                decisionId = decisionId
            )
            openChat(id)
            brain.requestKickoff(id)
        }
    }

    fun openSourceChat(src: SourceEntity, teachMode: Boolean = false) {
        viewModelScope.launch {
            val mode = if (teachMode) Skills.TEACH else Skills.FREE
            val prefix = if (teachMode) "Фейнман: " else "Разбор: "
            val id = repo.createSession(
                mode = mode,
                modeArg = src.title,
                title = (prefix + src.title).take(40),
                emoji = if (teachMode) "🎓" else "📚",
                socraticMode = teachMode,
                sourceId = src.id
            )
            openChat(id)
            brain.requestKickoff(id)
        }
    }

    fun openDecisionChat(d: DecisionEntity) {
        viewModelScope.launch {
            val id = repo.createSession(
                mode = Skills.STEELMAN,
                modeArg = d.title,
                title = ("Спарринг: " + d.title).take(40),
                emoji = "⚖️",
                socraticMode = true,
                decisionId = d.id
            )
            openChat(id)
            brain.requestKickoff(id)
        }
    }

    fun startPlanDrill(plan: ActionPlanEntity) {
        viewModelScope.launch {
            val existing = repo.sessionDao.all().firstOrNull { it.planId == plan.id }
            if (existing != null) {
                openChat(existing.id)
            } else {
                val id = repo.createSession(Skills.DRILL, "", plan.title.take(40), "📋", plan.id)
                openChat(id)
                brain.requestKickoff(id)
            }
        }
    }

    fun deleteSession(id: Long) = viewModelScope.launch {
        notifier.cancelChat(id)
        if (settings.value.pingSessionId == id) settings.update { it.copy(pingSessionId = 0L) }
        repo.deleteSession(id)
    }

    fun clearSession(id: Long) = viewModelScope.launch {
        notifier.cancelChat(id)
        repo.clearSession(id)
    }

    fun renameSession(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repo.sessionDao.rename(id, title.trim().take(60), true)
    }

    fun pinSession(id: Long, pinned: Boolean) = viewModelScope.launch { repo.sessionDao.setPinned(id, pinned) }

    // ───────────────────────── Вторая память (Источники + Карточки FSRS) ─────────────────────────

    var extractingSource by mutableStateOf(false)
        private set

    fun extractSourceText(titleHint: String, kind: String, contentOrUrl: String, onDone: (Result<Long>) -> Unit) {
        if (extractingSource) return
        extractingSource = true
        viewModelScope.launch {
            val res = brain.extractAndSaveSource(titleHint, kind, contentOrUrl, null)
            extractingSource = false
            onDone(res)
        }
    }

    fun extractSourceFromUri(uri: Uri, titleHint: String, onDone: (Result<Long>) -> Unit) {
        if (extractingSource) return
        extractingSource = true
        viewModelScope.launch {
            when (val imp = attachments.importUri(uri)) {
                is AttachmentStore.ImportResult.Rejected -> {
                    extractingSource = false
                    onDone(Result.failure(IllegalArgumentException(imp.reason)))
                }
                is AttachmentStore.ImportResult.Ok -> {
                    val a = imp.attachment
                    val kind = when {
                        a.isPdf -> "pdf"
                        a.isImage -> "image"
                        a.isAudio -> "audio"
                        else -> "text"
                    }
                    val res = brain.extractAndSaveSource(
                        titleHint = titleHint.ifBlank { a.name.substringBeforeLast('.') },
                        kind = kind,
                        contentOrUrl = a.name,
                        attachment = a
                    )
                    extractingSource = false
                    onDone(res)
                }
            }
        }
    }

    fun reviewCard(cardId: Long, rating: Fsrs.Rating) = viewModelScope.launch {
        repo.reviewCardFsrs(cardId, rating)
    }

    fun reviewAnchor(anchorId: Long, rating: Fsrs.Rating) = viewModelScope.launch {
        repo.reviewAnchorFsrs(anchorId, rating)
    }

    fun addManualCard(front: String, back: String, kind: String = "recall", skillId: String = "") = viewModelScope.launch {
        if (front.isNotBlank() && back.isNotBlank()) {
            repo.addStandaloneCard(Repository.CardDraft(kind = kind, front = front, back = back, skillId = skillId))
        }
    }

    fun deleteCard(id: Long) = viewModelScope.launch { repo.knowledgeCardDao.delete(id) }

    fun deleteSource(id: Long) = viewModelScope.launch { repo.deleteSource(id) }

    // ───────────────────────── Решения, Прогнозы и Калибровка ─────────────────────────

    var assistingDecision by mutableStateOf(false)
        private set

    fun assistDecision(
        title: String,
        contextText: String,
        alternatives: List<String>,
        chosenOption: String,
        confidencePct: Int,
        onDone: (Result<DecisionAssistNote>) -> Unit
    ) {
        if (assistingDecision) return
        assistingDecision = true
        viewModelScope.launch {
            val res = brain.assistDecision(title, contextText, alternatives, chosenOption, confidencePct)
            assistingDecision = false
            onDone(res)
        }
    }

    fun createDecision(
        title: String,
        contextText: String,
        alternatives: List<String>,
        chosenOption: String,
        baseRateNote: String,
        preMortem: String,
        ifThenSafeguard: String,
        confidencePct: Int,
        reviewDays: Int
    ) = viewModelScope.launch {
        val did = repo.createDecision(
            title, contextText, alternatives, chosenOption,
            baseRateNote, preMortem, ifThenSafeguard, confidencePct, reviewDays
        )
        // Автоматически создаём связанный вероятностный прогноз для тренировки Brier Score!
        if (reviewDays > 0) {
            repo.createPrediction(
                question = "Решение сработает успешно: $title",
                probabilityPct = confidencePct,
                category = "personal",
                rationale = baseRateNote.ifBlank { preMortem },
                checkAt = Timex.daysFromNow(reviewDays, 12),
                decisionId = did
            )
        }
    }

    fun resolveDecision(id: Long, outcomeNote: String, processQuality: Int) = viewModelScope.launch {
        repo.resolveDecision(id, outcomeNote, processQuality)
    }

    fun deleteDecision(id: Long) = viewModelScope.launch { repo.decisionDao.delete(id) }

    fun createPrediction(question: String, pct: Int, daysUntilCheck: Int, rationale: String = "", category: String = "personal") =
        viewModelScope.launch {
            val checkAt = if (daysUntilCheck <= 0) Timex.now() else Timex.daysFromNow(daysUntilCheck, 12)
            repo.createPrediction(question, pct, category, rationale, checkAt)
        }

    fun resolvePrediction(id: Long, outcome: Int) = viewModelScope.launch {
        repo.resolvePrediction(id, outcome)
    }

    fun deletePrediction(id: Long) = viewModelScope.launch { repo.predictionDao.delete(id) }

    var generatingQuiz by mutableStateOf(false)
        private set

    fun generateCalibrationQuiz(topicHint: String, onDone: (Result<Brain.CalibrationQuiz>) -> Unit) {
        if (generatingQuiz) return
        generatingQuiz = true
        viewModelScope.launch {
            val res = brain.generateCalibrationQuiz(topicHint)
            generatingQuiz = false
            onDone(res)
        }
    }

    fun recordQuizResult(question: String, guessedPct: Int, actualOutcome: Int, explanation: String) =
        viewModelScope.launch {
            val now = Timex.now()
            val id = repo.createPrediction(
                question = question,
                probabilityPct = guessedPct,
                category = "calibration_quiz",
                rationale = explanation,
                checkAt = now,
                now = now
            )
            repo.resolvePrediction(id, actualOutcome, now)
        }

    // ───────────────────────── Фокус, Соматика и Компас эмоций ─────────────────────────

    fun logFocusSession(
        kind: String,
        goal: String,
        obstacle: String,
        ifThenPlan: String,
        plannedMin: Int,
        actualMin: Int,
        completed: Boolean,
        focusQuality: Int,
        moodBefore: Int,
        moodAfter: Int,
        note: String
    ) = viewModelScope.launch {
        repo.logFocusSession(
            kind, goal, obstacle, ifThenPlan, plannedMin, actualMin,
            completed, focusQuality, moodBefore, moodAfter, note
        )
    }

    fun logEmotionCheck(emotionLabel: String, moodScore: Int, note: String) = viewModelScope.launch {
        val now = Timex.now()
        repo.recordMood(moodScore, now)
        val txt = if (note.isBlank()) "Эмоция: $emotionLabel ($moodScore/10)" else "Эмоция: $emotionLabel ($moodScore/10) — $note"
        repo.addJournal("emotion", txt, now)
        repo.addXp(3, now)
        repo.registerActivity(now, 3)
    }

    // ───────────────────────── Досье, Избранное и Еженедельный синтез ─────────────────────────

    fun addAnchor(phrase: String, why: String, source: String = "Личный якорь", trigger: String = "") =
        viewModelScope.launch { repo.upsertAnchor(phrase, why, source, trigger, "user", Timex.now()) }

    fun deleteAnchor(id: Long) = viewModelScope.launch { repo.anchorDao.delete(id) }

    fun resolveTrap(id: Long, resolved: Boolean) = viewModelScope.launch { repo.trapDao.setResolved(id, resolved) }

    fun deleteTrap(id: Long) = viewModelScope.launch { repo.trapDao.delete(id) }

    fun addFact(text: String) = viewModelScope.launch { repo.addFact(text, "user", Timex.now()) }

    fun deleteFact(id: Long) = viewModelScope.launch { repo.factDao.delete(id) }

    fun deleteJournal(id: Long) = viewModelScope.launch { repo.journalDao.delete(id) }

    fun deleteWeeklyReview(id: Long) = viewModelScope.launch { repo.weeklyReviewDao.delete(id) }

    fun unstarMessage(id: Long) = viewModelScope.launch { repo.messageDao.setStar(id, false) }

    fun setMastery(skillId: String, value: Int) = viewModelScope.launch {
        repo.setMastery(skillId, value)
        repo.recomputeStage()
    }

    fun createPlanFromText(title: String, text: String) = viewModelScope.launch {
        val steps = text.lines().mapNotNull { raw ->
            val line = raw.trim().trimStart('•', '-', '*', ' ').trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = line.split(" — ", " – ", " - ", "\t", "=", ";", limit = 2)
            Repository.WoopStepInput(
                step = parts[0].trim().take(220),
                why = parts.getOrElse(1) { "" }.trim().take(220)
            )
        }.filter { it.step.isNotEmpty() }.take(100)
        if (steps.isNotEmpty()) {
            repo.createWoopPlan(title.ifBlank { "WOOP-план" }.take(60), "", "", "", steps, 0L, Timex.now())
        }
    }

    fun deletePlan(id: Long) = viewModelScope.launch { repo.deletePlan(id) }

    fun resetPlan(id: Long) = viewModelScope.launch { repo.planDao.resetSteps(id) }

    var reflecting by mutableStateOf(false)
        private set

    fun reflectNow(onDone: (Boolean) -> Unit) {
        if (reflecting) return
        reflecting = true
        viewModelScope.launch {
            val ok = brain.reflect(force = true)
            reflecting = false
            onDone(ok)
        }
    }

    var generatingWeekly by mutableStateOf(false)
        private set

    fun generateWeeklyReviewNow(onDone: (Boolean) -> Unit) {
        if (generatingWeekly) return
        generatingWeekly = true
        viewModelScope.launch {
            val ok = brain.generateWeeklyReview(force = true)
            generatingWeekly = false
            onDone(ok)
        }
    }

    fun saveProfile(name: String, goal: String, interests: String, about: String, formal: Boolean) =
        viewModelScope.launch {
            repo.updateProfile {
                it.copy(name = name.trim(), goal = goal.trim(), interests = interests.trim(), about = about.trim(), formal = formal)
            }
        }

    fun stopSpeaking() = speaker.stop()

    // ───────────────────────── Настройки ─────────────────────────

    fun update(block: (Settings) -> Settings) {
        val next = block(settings.value)
        settings.update { next }
        viewModelScope.launch {
            repo.updateProfile {
                it.copy(
                    desiredRetention = next.desiredRetention.toDouble(),
                    socraticDefault = next.socraticByDefault
                )
            }
        }
    }

    fun updatePings(block: (Settings) -> Settings) {
        settings.update(block)
        viewModelScope.launch { brain.rearm() }
    }

    fun setKeepAlive(on: Boolean) {
        settings.update { it.copy(keepAlive = on) }
        if (on) KeepAliveService.start(app) else KeepAliveService.stop(app)
    }

    fun pingNow() = Jobs.ping(app, force = true)

    fun cancelSnooze() = viewModelScope.launch { brain.cancelSnooze() }

    var keyCheck by mutableStateOf("")
        private set
    var keyChecking by mutableStateOf(false)
        private set

    fun saveAndTestKey(key: String, onResult: (Boolean) -> Unit = {}) {
        val clean = key.trim()
        settings.setApiKey(clean)
        if (clean.isEmpty()) {
            keyCheck = "Ключ удалён. Атлас перешёл на офлайн-банк практик и цитат."
            onResult(false)
            return
        }
        keyChecking = true
        keyCheck = "Проверяю…"
        viewModelScope.launch {
            val result = brain.testKey(clean, settings.value.model)
            keyChecking = false
            when (result) {
                is GeminiClient.Result.Ok -> {
                    keyCheck = "✅ Ключ работает. Модель: ${result.model}"
                    onResult(true)
                }

                is GeminiClient.Result.Fail -> {
                    keyCheck = "⚠️ " + result.human()
                    onResult(false)
                }
            }
        }
    }

    // ───────────────────────── Экспорт, Импорт (Backup) и Сброс ─────────────────────────

    fun exportAll(context: Context) = viewModelScope.launch {
        try {
            val json = repo.exportAllJson()
            val uri = withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "shared")
                dir.mkdirs()
                val file = File(dir, "atlas-backup-${Timex.today()}.json")
                file.writeText(json, Charsets.UTF_8)
                FileProvider.getUriForFile(app, app.packageName + ".files", file)
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Экспорт бэкапа Атласа"))
        } catch (_: Throwable) {
        }
    }

    fun importBackupUri(uri: Uri, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
                if (json.isBlank()) {
                    onDone(Result.failure(IllegalArgumentException("Файл бэкапа пустой")))
                    return@launch
                }
                val summary = repo.importBackupJson(json)
                onDone(Result.success("Восстановлено: ${summary.describe()}"))
            } catch (t: Throwable) {
                onDone(Result.failure(IllegalArgumentException("Не удалось прочитать бэкап: ${t.message}")))
            }
        }
    }

    fun wipeAll() = viewModelScope.launch {
        notifier.cancelAllChats()
        repo.wipeAll()
        runtime.reset()
        val key = settings.apiKey()
        settings.resetAll()
        settings.setApiKey(key)
        KeepAliveService.stop(app)
        brain.rearm()
        rootTab = RootTab.CHATS
        stack.clear()
        stack.add(Screen.Sessions)
    }
}