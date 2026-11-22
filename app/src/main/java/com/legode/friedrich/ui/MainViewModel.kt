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
import com.legode.friedrich.brain.GeminiClient
import com.legode.friedrich.brain.KnowledgeBase
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.Settings
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.ActionPlanEntity
import com.legode.friedrich.data.db.AnchorEntity
import com.legode.friedrich.data.db.DailyEntity
import com.legode.friedrich.data.db.FactEntity
import com.legode.friedrich.data.db.JournalEntity
import com.legode.friedrich.data.db.PlanProgress
import com.legode.friedrich.data.db.ProfileEntity
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.data.db.SkillProgressEntity
import com.legode.friedrich.data.db.TrapEntity
import com.legode.friedrich.notify.Jobs
import com.legode.friedrich.notify.KeepAliveService
import com.legode.friedrich.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class Screen {
    object Sessions : Screen()
    data class Chat(val sessionId: Long) : Screen()
    object Dossier : Screen()
    data class Reference(val topicId: String?) : Screen()
    object Settings : Screen()
}

/** Текст или файлы, которыми поделились с приложением извне. */
data class SharedPayload(val text: String, val uris: List<Uri>)

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

    /** Текст или файлы из системного «Поделиться» — забирает экран чата. */
    var shared by mutableStateOf<SharedPayload?>(null)

    fun open(screen: Screen) {
        if (stack.lastOrNull() == screen) return
        if (screen is Screen.Chat) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openChat(sessionId: Long) = open(Screen.Chat(sessionId))

    fun openHomeChat() {
        viewModelScope.launch { openChat(repo.ensureHome().id) }
    }

    /** Переход из уведомления: sessionId = 0 означает «главный чат». */
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

    // ───────────────────────── Запуск и знакомство ─────────────────────────

    /** Вызывается каждый раз, когда приложение выходит на передний план. */
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
            stack.add(Screen.Chat(home.id))
            brain.requestKickoff(home.id)
            brain.rearm()
        }
    }

    // ───────────────────────── Сессии ─────────────────────────

    fun newSession(mode: String, arg: String = "", planId: Long = 0L) {
        viewModelScope.launch {
            val id = repo.createSession(mode, arg, Skills.defaultTitle(mode, arg), Skills.emoji(mode, arg), planId)
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

    // ───────────────────────── Досье ─────────────────────────

    fun addAnchor(phrase: String, why: String, source: String = "Личный якорь", trigger: String = "") =
        viewModelScope.launch {
            repo.upsertAnchor(phrase, why, source, trigger, "user", Timex.now())
        }

    fun deleteAnchor(id: Long) = viewModelScope.launch { repo.anchorDao.delete(id) }

    fun resolveTrap(id: Long, resolved: Boolean) = viewModelScope.launch { repo.trapDao.setResolved(id, resolved) }

    fun deleteTrap(id: Long) = viewModelScope.launch { repo.trapDao.delete(id) }

    fun addFact(text: String) = viewModelScope.launch { repo.addFact(text, "user", Timex.now()) }

    fun deleteFact(id: Long) = viewModelScope.launch { repo.factDao.delete(id) }

    fun deleteJournal(id: Long) = viewModelScope.launch { repo.journalDao.delete(id) }

    fun setMastery(skillId: String, value: Int) = viewModelScope.launch {
        repo.setMastery(skillId, value)
        repo.recomputeStage()
    }

    /** План из текста: каждая строка — «шаг — зачем/условие» (разделители: —, -, =, ;, таб). */
    fun createPlanFromText(title: String, text: String) = viewModelScope.launch {
        val steps = text.lines().mapNotNull { raw ->
            val line = raw.trim().trimStart('•', '-', '*', ' ').trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = line.split(" — ", " – ", " - ", "\t", "=", ";", limit = 2)
            parts[0].trim().take(220) to parts.getOrElse(1) { "" }.trim().take(220)
        }.filter { it.first.isNotEmpty() }.take(100)
        if (steps.isNotEmpty()) {
            repo.createPlan(title.ifBlank { "План прорыва" }.take(60), steps, 0L, Timex.now())
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

    fun saveProfile(name: String, goal: String, interests: String, about: String, formal: Boolean) =
        viewModelScope.launch {
            repo.updateProfile {
                it.copy(name = name.trim(), goal = goal.trim(), interests = interests.trim(), about = about.trim(), formal = formal)
            }
        }

    // ───────────────────────── Настройки ─────────────────────────

    fun update(block: (Settings) -> Settings) = settings.update(block)

    /** Изменение настроек «смс»: сразу пересчитываем расписание. */
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
            keyCheck = "Ключ удалён. Атлас перейдёт на офлайн-банк практик и цитат."
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

    // ───────────────────────── Данные ─────────────────────────

    /** Экспорт всего досье и переписки в JSON-файл через системное «Поделиться». */
    fun exportAll(context: Context) = viewModelScope.launch {
        try {
            val json = repo.exportAllJson()
            val uri = withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "shared")
                dir.mkdirs()
                val file = File(dir, "atlas-export-${Timex.today()}.json")
                file.writeText(json, Charsets.UTF_8)
                FileProvider.getUriForFile(app, app.packageName + ".files", file)
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Экспорт данных Атласа"))
        } catch (_: Throwable) {
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
        stack.clear()
        stack.add(Screen.Sessions)
    }
}