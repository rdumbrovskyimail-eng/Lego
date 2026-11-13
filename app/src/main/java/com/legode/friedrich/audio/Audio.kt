package com.legode.friedrich.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.core.Logx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/** Озвучка немецкого текста системным синтезатором речи (de-DE). */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Float>? = null

    private val availableState = MutableStateFlow(true)

    /** false, если на устройстве нет немецкого голоса. */
    val available: StateFlow<Boolean> = availableState.asStateFlow()

    private fun ensure() {
        if (tts != null) return
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val result = try {
                        engine?.setLanguage(Locale.GERMANY) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    } catch (_: Throwable) {
                        TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                    availableState.value = ready
                    val p = pending
                    pending = null
                    if (ready && p != null) speakNow(p.first, p.second)
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

    /** Читает вслух только немецкие фрагменты смешанного текста. */
    fun speak(text: String, rate: Float) {
        val german = Norm.germanParts(text)
        if (german.isBlank()) return
        ensure()
        if (ready) speakNow(german, rate) else pending = german to rate
    }

    private fun speakNow(text: String, rate: Float) {
        try {
            val engine = tts ?: return
            engine.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
            engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "friedrich-" + System.nanoTime())
        } catch (t: Throwable) {
            Logx.w("tts speak failed", t)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }
}

/** Запись голосового сообщения (AAC), которое потом слушает Gemini. */
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
            r.setMaxDuration(120_000)
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
}
