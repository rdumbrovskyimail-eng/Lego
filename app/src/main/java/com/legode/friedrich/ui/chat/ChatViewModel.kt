@file:OptIn(ExperimentalCoroutinesApi::class)

package com.legode.friedrich.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legode.friedrich.audio.Speaker
import com.legode.friedrich.audio.VoicePlayer
import com.legode.friedrich.audio.VoiceRecorder
import com.legode.friedrich.brain.Brain
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.data.db.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val repo: Repository,
    private val brain: Brain,
    val settings: SettingsStore,
    private val attachments: AttachmentStore,
    private val speaker: Speaker
) : ViewModel() {

    private val sessionId = MutableStateFlow(0L)
    val id: Long get() = sessionId.value

    val session: StateFlow<SessionEntity?> = sessionId
        .flatMapLatest { if (it == 0L) flowOf(null) else repo.sessionDao.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<MessageEntity>> = sessionId
        .flatMapLatest { if (it == 0L) flowOf(emptyList()) else repo.messageDao.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val typing: StateFlow<Boolean> = combine(brain.typing, sessionId) { set, sid -> sid in set }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val streamingMessages: StateFlow<List<String>> = combine(brain.streaming, sessionId) { map, sid ->
        map[sid] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val speaking: StateFlow<Boolean> = speaker.speaking

    val pending = mutableStateListOf<Attachment>()

    var importing by mutableStateOf(false)
        private set

    var recording by mutableStateOf(false)
        private set
    var recordStartedAt by mutableStateOf(0L)
        private set

    private val toastFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = toastFlow.asSharedFlow()

    private val recorder = VoiceRecorder(app)
    val player = VoicePlayer()

    fun bind(sid: Long) {
        if (sessionId.value != sid) {
            sessionId.value = sid
            pending.clear()
        }
    }

    // ───────────────────────── Видимость и черновик ─────────────────────────

    fun onVisible(visible: Boolean) {
        val sid = sessionId.value
        if (sid == 0L) return
        if (visible) {
            brain.visibleSession.value = sid
            viewModelScope.launch { brain.markRead(sid) }
        } else if (brain.visibleSession.value == sid) {
            brain.visibleSession.value = 0L
        }
    }

    fun saveDraft(text: String) {
        val sid = sessionId.value
        if (sid == 0L) return
        viewModelScope.launch { repo.sessionDao.setDraft(sid, text) }
    }

    // ───────────────────────── Отправка и режимы ─────────────────────────

    fun send(text: String) {
        val sid = sessionId.value
        val body = text.trim()
        val atts = pending.toList()
        if (sid == 0L || (body.isEmpty() && atts.isEmpty())) return
        pending.clear()
        viewModelScope.launch {
            repo.addUserMessage(sid, body, atts)
            repo.sessionDao.setDraft(sid, "")
            brain.requestRespond(sid)
        }
    }

    fun retry(messageId: Long) {
        val sid = sessionId.value
        if (sid == 0L) return
        brain.requestRetry(sid, messageId)
    }

    fun regenerate() {
        val sid = sessionId.value
        viewModelScope.launch {
            if (repo.deleteTrailingAtlas(sid)) brain.requestRegenerate(sid)
        }
    }

    fun toggleSocratic() {
        val s = session.value ?: return
        viewModelScope.launch {
            val next = !s.socraticMode
            repo.sessionDao.setSocratic(s.id, next)
            toastFlow.tryEmit(
                if (next) "Режим тренера (анти-оффлоадинг) включён"
                else "Режим тренера выключен"
            )
        }
    }

    fun delete(messageId: Long) = viewModelScope.launch { repo.deleteMessage(messageId) }

    fun toggleStar(m: MessageEntity) = viewModelScope.launch { repo.messageDao.setStar(m.id, !m.starred) }

    fun saveToAnchor(rawText: String) {
        val clean = Norm.plain(rawText).trim()
        if (clean.length < 4) return
        viewModelScope.launch {
            val added = repo.upsertAnchor(
                phrase = Norm.truncateWords(clean, 210),
                why = "Опорная мысль, сохранённая вручную из диалога",
                source = "Чат с Атласом",
                trigger = "",
                origin = "user",
                now = Timex.now()
            )
            toastFlow.tryEmit(if (added) "Сохранено в Якоря силы ⚓" else "Такой якорь уже есть")
        }
    }

    fun saveToCard(front: String, back: String) {
        if (front.isBlank() || back.isBlank()) return
        viewModelScope.launch {
            repo.addStandaloneCard(Repository.CardDraft(kind = "recall", front = front, back = back))
            toastFlow.tryEmit("Карточка добавлена в Тренажёр FSRS 🧬")
        }
    }

    fun speak(text: String) {
        val s = settings.value
        if (!s.ttsEnabled) {
            toastFlow.tryEmit("Озвучка выключена в настройках")
            return
        }
        if (!speaker.available.value) {
            toastFlow.tryEmit("На устройстве нет русского голоса для синтеза речи. Установи его в настройках TTS.")
            return
        }
        val isBreathScript = text.contains("[пауза", ignoreCase = true) || text.contains("[pause", ignoreCase = true)
        val rate = if (isBreathScript) (s.ttsRate * 0.90f).coerceIn(0.6f, 1.3f) else s.ttsRate
        val pitch = if (isBreathScript) 0.94f else 1.0f
        speaker.speak(text, rate, pitch)
    }

    fun stopSpeaking() {
        speaker.stop()
    }

    // ───────────────────────── Вложения ─────────────────────────

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing = true
            for (uri in uris.take(8)) {
                if (pending.size >= MAX_PENDING) {
                    toastFlow.tryEmit("Не больше $MAX_PENDING вложений за раз")
                    break
                }
                when (val r = attachments.importUri(uri)) {
                    is AttachmentStore.ImportResult.Ok -> pending.add(r.attachment)
                    is AttachmentStore.ImportResult.Rejected -> toastFlow.tryEmit(r.reason)
                }
            }
            importing = false
        }
    }

    fun addCameraFile(file: File) {
        viewModelScope.launch {
            importing = true
            val a = attachments.adoptCameraFile(file)
            if (a != null) pending.add(a) else toastFlow.tryEmit("Не получилось сохранить снимок")
            importing = false
        }
    }

    fun newCameraTarget(): Pair<File, Uri> = attachments.newCameraTarget()

    fun removePending(a: Attachment) {
        pending.remove(a)
        attachments.delete(a)
    }

    // ───────────────────────── Голосовые ─────────────────────────

    fun startRecording(): Boolean {
        player.stop()
        speaker.stop()
        val ok = recorder.start(attachments.newVoiceFile())
        if (ok) {
            recording = true
            recordStartedAt = System.currentTimeMillis()
        } else {
            toastFlow.tryEmit("Не удалось включить микрофон")
        }
        return ok
    }

    fun finishRecording(send: Boolean) {
        if (!recording) return
        recording = false
        if (!send) {
            recorder.cancel()
            return
        }
        val result = recorder.finish()
        if (result == null) {
            toastFlow.tryEmit("Запись слишком короткая")
            return
        }
        val sid = sessionId.value
        val att = attachments.voiceAttachment(result.first, result.second)
        viewModelScope.launch {
            repo.addUserMessage(sid, "", listOf(att))
            brain.requestRespond(sid)
        }
    }

    // ───────────────────────── Меню чата ─────────────────────────

    fun rename(title: String) {
        val sid = sessionId.value
        if (title.isBlank()) return
        viewModelScope.launch { repo.sessionDao.rename(sid, title.trim().take(60), true) }
    }

    fun clear() {
        val sid = sessionId.value
        viewModelScope.launch { repo.clearSession(sid) }
    }

    fun detachPlan() {
        val sid = sessionId.value
        viewModelScope.launch { repo.sessionDao.setPlan(sid, 0L) }
    }

    suspend fun exportText(): String = repo.exportSessionText(sessionId.value)

    override fun onCleared() {
        recorder.release()
        player.release()
        speaker.stop()
        super.onCleared()
    }

    private companion object {
        const val MAX_PENDING = 6
    }
}