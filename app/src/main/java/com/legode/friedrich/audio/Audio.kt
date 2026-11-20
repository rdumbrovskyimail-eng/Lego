package com.legode.friedrich.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.core.Logx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/** Озвучка сообщений, практик, дыхательных сессий и опорных фраз системным синтезатором речи (ru-RU). */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: PendingSpeech? = null

    @Volatile
    private var lastUtteranceId: String = ""

    private val availableState = MutableStateFlow(true)
    private val speakingState = MutableStateFlow(false)

    /** false, если на устройстве нет русского голоса. */
    val available: StateFlow<Boolean> = availableState.asStateFlow()

    /** true, пока синтезатор произносит сообщение или держит дыхательную паузу. */
    val speaking: StateFlow<Boolean> = speakingState.asStateFlow()

    private data class PendingSpeech(val text: String, val rate: Float, val pitch: Float)

    private sealed class Segment {
        data class Words(val text: String) : Segment()
        data class Pause(val ms: Long) : Segment()
    }

    private fun ensure() {
        if (tts != null) return
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            speakingState.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == lastUtteranceId) {
                                speakingState.value = false
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == lastUtteranceId || utteranceId == null) {
                                speakingState.value = false
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId == lastUtteranceId || utteranceId == null) {
                                speakingState.value = false
                            }
                        }
                    })
                    val result = try {
                        var res = engine?.setLanguage(Locale.forLanguageTag("ru-RU")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            res = engine?.setLanguage(Locale.forLanguageTag("ru")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                        }
                        res
                    } catch (_: Throwable) {
                        TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                    availableState.value = ready
                    val p = pending
                    pending = null
                    if (ready && p != null) speakNow(p.text, p.rate, p.pitch)
                } else {
                    ready = false
                    availableState.value = false
                }
            }
        } catch (t: Throwable) {
            Logx.w("tts init failed", t)
            availableState.value = false
        }
    }

    /**
     * Читает вслух сообщение или практику без разметки и лишних символов.
     * Поддерживает маркеры пауз в тексте: `[пауза]` (2 сек) или `[пауза 4с]` (до 12 сек).
     */
    fun speak(text: String, rate: Float, pitch: Float = 1.0f) {
        if (text.isBlank()) return
        ensure()
        if (ready) {
            speakNow(text, rate, pitch)
        } else {
            pending = PendingSpeech(text, rate, pitch)
        }
    }

    private fun speakNow(rawText: String, rate: Float, pitch: Float) {
        try {
            val engine = tts ?: return
            val segments = buildSegments(rawText)
            if (segments.isEmpty()) return

            engine.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
            engine.setPitch(pitch.coerceIn(0.75f, 1.25f))

            val baseId = "atlas-" + System.nanoTime()
            val finalId = "$baseId-${segments.lastIndex}"
            lastUtteranceId = finalId
            speakingState.value = true

            segments.forEachIndexed { index, seg ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val uid = "$baseId-$index"
                when (seg) {
                    is Segment.Words -> engine.speak(seg.text, queueMode, null, uid)
                    is Segment.Pause -> engine.playSilentUtterance(seg.ms, queueMode, uid)
                }
            }
        } catch (t: Throwable) {
            Logx.w("tts speak failed", t)
            speakingState.value = false
        }
    }

    private fun buildSegments(raw: String): List<Segment> {
        val out = ArrayList<Segment>()
        val matches = PAUSE_TAG.findAll(raw).toList()
        if (matches.isEmpty()) {
            addSpeakableChunks(raw, out)
            return out
        }
        var cursor = 0
        for (m in matches) {
            if (m.range.first > cursor) {
                addSpeakableChunks(raw.substring(cursor, m.range.first), out)
            }
            val sec = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 2
            out.add(Segment.Pause((sec.coerceIn(1, 12)) * 1000L))
            cursor = m.range.last + 1
        }
        if (cursor < raw.length) {
            addSpeakableChunks(raw.substring(cursor), out)
        }
        return out
    }

    private fun addSpeakableChunks(rawPart: String, out: MutableList<Segment>) {
        val clean = Norm.speakable(rawPart)
        if (clean.isBlank()) return
        if (clean.length <= MAX_CHUNK) {
            out.add(Segment.Words(clean))
            return
        }
        var remaining = clean
        while (remaining.length > MAX_CHUNK) {
            val cutArea = remaining.substring(0, MAX_CHUNK)
            var splitAt = cutArea.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
            if (splitAt < MAX_CHUNK / 3) splitAt = cutArea.lastIndexOf(' ')
            if (splitAt <= 0) splitAt = MAX_CHUNK - 1
            val piece = remaining.substring(0, splitAt + 1).trim()
            if (piece.isNotEmpty()) out.add(Segment.Words(piece))
            remaining = remaining.substring(splitAt + 1).trim()
        }
        if (remaining.isNotEmpty()) out.add(Segment.Words(remaining))
    }

    fun stop() {
        pending = null
        speakingState.value = false
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ready = false
    }

    private companion object {
        const val MAX_CHUNK = 3000
        val PAUSE_TAG = Regex("\\[(?:пауза|pause)(?:\\s+(\\d{1,2})\\s*(?:с|сек|s)?)?\\]", RegexOption.IGNORE_CASE)
    }
}

/** Запись голосового сообщения (AAC), которое потом слушает Атлас (Gemini). */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(target: File): Boolean {
        stopInternal()
        return try {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(22050)
            r.setAudioEncodingBitRate(48000)
            r.setMaxDuration(180_000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (t: Throwable) {
            Logx.w("record start failed", t)
            stopInternal()
            try {
                target.delete()
            } catch (_: Throwable) {
            }
            false
        }
    }

    /** Текущая амплитуда микрофона (0..32767) для визуализации записи. */
    fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Throwable) {
        0
    }

    /** @return файл и длительность в мс; null, если запись слишком короткая или не удалась. */
    fun finish(): Pair<File, Long>? {
        val f = file
        val duration = System.currentTimeMillis() - startedAt
        val ok = stopInternal()
        if (f == null) return null
        if (!ok || duration < 600 || !f.exists() || f.length() < 200) {
            try {
                f.delete()
            } catch (_: Throwable) {
            }
            return null
        }
        return f to duration
    }

    fun cancel() {
        val f = file
        stopInternal()
        try {
            f?.delete()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        cancel()
    }

    private fun stopInternal(): Boolean {
        val r = recorder
        recorder = null
        file = null
        if (r == null) return false
        var ok = true
        try {
            r.stop()
        } catch (_: Throwable) {
            ok = false
        }
        try {
            r.release()
        } catch (_: Throwable) {
        }
        return ok
    }
}

/** Проигрывание голосовых сообщений в чате. */
class VoicePlayer {

    private var player: MediaPlayer? = null
    private val playingState = MutableStateFlow("")

    /** Путь файла, который сейчас играет ("" — тишина). */
    val playing: StateFlow<String> = playingState.asStateFlow()

    fun toggle(path: String) {
        if (playingState.value == path) {
            stop()
            return
        }
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(path)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            p.prepare()
            p.start()
            player = p
            playingState.value = path
        } catch (t: Throwable) {
            Logx.w("play failed", t)
            stop()
        }
    }

    fun stop() {
        val p = player
        player = null
        playingState.value = ""
        try {
            p?.stop()
        } catch (_: Throwable) {
        }
        try {
            p?.release()
        } catch (_: Throwable) {
        }
    }

    fun release() = stop()
}