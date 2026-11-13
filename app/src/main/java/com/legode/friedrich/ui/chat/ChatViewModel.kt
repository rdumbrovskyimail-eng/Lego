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

    /** Вложения, прикреплённые к ещё не отправленному сообщению. */
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

    // ───────────────────────── Видимость ─────────────────────────

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

    // ───────────────────────── Отправка ─────────────────────────

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
        viewModelScope.launch {
            repo.messageDao.setStatus(messageId, "ok", "")
            brain.requestRespond(sid)
        }
    }

    /** Удаляет последний ответ Фридриха и просит ответить заново. */
    fun regenerate() {
        val sid = sessionId.value
        viewModelScope.launch {
            if (repo.deleteTrailingFriedrich(sid)) brain.requestRegenerate(sid)
        }
    }

    fun delete(messageId: Long) = viewModelScope.launch { repo.deleteMessage(messageId) }

    fun toggleStar(m: MessageEntity) = viewModelScope.launch { repo.messageDao.setStar(m.id, !m.starred) }

    fun speak(text: String) {
        val s = settings.value
        if (!s.ttsEnabled) {
            toastFlow.tryEmit("Озвучка выключена в настройках")
            return
        }
        if (!speaker.available.value) {
            toastFlow.tryEmit("На устройстве нет немецкого голоса. Установи его в настройках синтеза речи.")
            return
        }
        speaker.speak(text, s.ttsRate)
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

    fun detachList() {
        val sid = sessionId.value
        viewModelScope.launch { repo.sessionDao.setList(sid, 0L) }
    }

    suspend fun exportText(): String = repo.exportSessionText(sessionId.value)

    override fun onCleared() {
        recorder.cancel()
        player.stop()
        super.onCleared()
    }

    private companion object {
        const val MAX_PENDING = 6
    }
}
