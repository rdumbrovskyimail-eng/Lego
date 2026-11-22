package com.legode.friedrich.data

import android.content.Context
import android.content.SharedPreferences
import com.legode.friedrich.core.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Пользовательские настройки Атласа. */
data class Settings(
    val onboarded: Boolean = false,
    val hasApiKey: Boolean = false,
    /** ID модели Gemini. По умолчанию — 3.1 Flash-Lite. */
    val model: String = DEFAULT_MODEL,
    /** Уровень «размышлений» модели: auto | minimal | low | medium | high. */
    val thinking: String = "low",
    /** Вкладывать в каждый запрос весь методологический атлас или только релевантные протоколы. */
    val fullReference: Boolean = true,
    /** Напористость Атласа: 0 — бережно, 1 — сбалансированно, 2 — жёсткий прорыв ⚡. */
    val pushiness: Int = 2,
    val pingsEnabled: Boolean = true,
    /** Частота «смс»: 0 редко … 4 плотный режим. См. PingScheduler.INTENSITY. */
    val pingIntensity: Int = 3,
    val quietStartMin: Int = 22 * 60 + 30,
    val quietEndMin: Int = 7 * 60 + 30,
    val dailyPingCap: Int = 60,
    /** В какой чат слать «смс»; 0 — в главный. */
    val pingSessionId: Long = 0,
    /** Постоянный сервис с тихим уведомлением — приложение «всегда включено». */
    val keepAlive: Boolean = true,
    val ttsEnabled: Boolean = true,
    val ttsRate: Float = 0.95f,
    val fontScale: Float = 1.0f,
    /** Паузы «печатает…» между сообщениями, как у живого собеседника. */
    val humanPacing: Boolean = true
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

        /** Запасные модели на случай, если выбранная недоступна (404). */
        val FALLBACK_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-flash-lite",
            "gemini-flash-lite-latest"
        )
    }
}

/** Хранилище настроек на SharedPreferences с реактивным StateFlow. API-ключ зашифрован через Android Keystore. */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("atlas_settings", Context.MODE_PRIVATE)

    @Volatile
    private var cachedKey: String? = null

    init {
        migrateLegacyKeyIfNeeded()
    }

    private val state = MutableStateFlow(load())
    val flow: StateFlow<Settings> = state.asStateFlow()
    val value: Settings get() = state.value

    private fun migrateLegacyKeyIfNeeded() {
        if (!prefs.getString(K_API_KEY, "").isNullOrEmpty()) return
        try {
            val legacy = appContext.getSharedPreferences("friedrich_settings", Context.MODE_PRIVATE)
            val sealed = legacy.getString(K_API_KEY, "") ?: ""
            if (sealed.isNotEmpty()) {
                prefs.edit().putString(K_API_KEY, sealed).apply()
            }
        } catch (_: Throwable) {
        }
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            onboarded = prefs.getBoolean(K_ONBOARDED, d.onboarded),
            hasApiKey = !prefs.getString(K_API_KEY, "").isNullOrEmpty(),
            model = prefs.getString(K_MODEL, d.model) ?: d.model,
            thinking = prefs.getString(K_THINKING, d.thinking) ?: d.thinking,
            fullReference = prefs.getBoolean(K_FULL_REF, d.fullReference),
            pushiness = prefs.getInt(K_PUSHINESS, d.pushiness),
            pingsEnabled = prefs.getBoolean(K_PINGS, d.pingsEnabled),
            pingIntensity = prefs.getInt(K_INTENSITY, d.pingIntensity),
            quietStartMin = prefs.getInt(K_QUIET_START, d.quietStartMin),
            quietEndMin = prefs.getInt(K_QUIET_END, d.quietEndMin),
            dailyPingCap = prefs.getInt(K_DAILY_CAP, d.dailyPingCap),
            pingSessionId = prefs.getLong(K_PING_SESSION, d.pingSessionId),
            keepAlive = prefs.getBoolean(K_KEEP_ALIVE, d.keepAlive),
            ttsEnabled = prefs.getBoolean(K_TTS, d.ttsEnabled),
            ttsRate = prefs.getFloat(K_TTS_RATE, d.ttsRate),
            fontScale = prefs.getFloat(K_FONT, d.fontScale),
            humanPacing = prefs.getBoolean(K_PACING, d.humanPacing)
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putBoolean(K_ONBOARDED, s.onboarded)
            .putString(K_MODEL, s.model)
            .putString(K_THINKING, s.thinking)
            .putBoolean(K_FULL_REF, s.fullReference)
            .putInt(K_PUSHINESS, s.pushiness)
            .putBoolean(K_PINGS, s.pingsEnabled)
            .putInt(K_INTENSITY, s.pingIntensity)
            .putInt(K_QUIET_START, s.quietStartMin)
            .putInt(K_QUIET_END, s.quietEndMin)
            .putInt(K_DAILY_CAP, s.dailyPingCap)
            .putLong(K_PING_SESSION, s.pingSessionId)
            .putBoolean(K_KEEP_ALIVE, s.keepAlive)
            .putBoolean(K_TTS, s.ttsEnabled)
            .putFloat(K_TTS_RATE, s.ttsRate)
            .putFloat(K_FONT, s.fontScale)
            .putBoolean(K_PACING, s.humanPacing)
            .apply()
    }

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        val next = block(state.value).copy(hasApiKey = state.value.hasApiKey)
        save(next)
        state.value = next
    }

    fun apiKey(): String {
        cachedKey?.let { return it }
        val opened = SecretBox.open(prefs.getString(K_API_KEY, "") ?: "")
        cachedKey = opened
        return opened
    }

    @Synchronized
    fun setApiKey(key: String) {
        val clean = key.trim()
        cachedKey = clean
        prefs.edit().putString(K_API_KEY, if (clean.isEmpty()) "" else SecretBox.seal(clean)).apply()
        state.value = state.value.copy(hasApiKey = clean.isNotEmpty())
    }

    @Synchronized
    fun resetAll() {
        prefs.edit().clear().apply()
        cachedKey = null
        state.value = load()
    }

    private companion object {
        const val K_ONBOARDED = "onboarded"
        const val K_API_KEY = "api_key_sealed"
        const val K_MODEL = "model"
        const val K_THINKING = "thinking"
        const val K_FULL_REF = "full_reference"
        const val K_PUSHINESS = "pushiness"
        const val K_PINGS = "pings_enabled"
        const val K_INTENSITY = "ping_intensity"
        const val K_QUIET_START = "quiet_start"
        const val K_QUIET_END = "quiet_end"
        const val K_DAILY_CAP = "daily_cap"
        const val K_PING_SESSION = "ping_session"
        const val K_KEEP_ALIVE = "keep_alive"
        const val K_TTS = "tts"
        const val K_TTS_RATE = "tts_rate"
        const val K_FONT = "font_scale"
        const val K_PACING = "human_pacing"
    }
}

/**
 * Служебное состояние планировщика (не настройки): когда следующий «пинг»,
 * до какого времени отложено, что уже сделано сегодня.
 */
class RuntimeState(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("atlas_runtime", Context.MODE_PRIVATE)

    private val nextPing = MutableStateFlow(prefs.getLong("next_ping_at", 0L))
    val nextPingFlow: StateFlow<Long> = nextPing.asStateFlow()

    var nextPingAt: Long
        get() = nextPing.value
        set(v) {
            prefs.edit().putLong("next_ping_at", v).apply()
            nextPing.value = v
        }

    var snoozeUntil: Long
        get() = prefs.getLong("snooze_until", 0L)
        set(v) = prefs.edit().putLong("snooze_until", v).apply()

    /** День (yyyy-MM-dd), когда Атлас уже отправил утренний заряд. */
    var morningDay: String
        get() = prefs.getString("morning_day", "") ?: ""
        set(v) = prefs.edit().putString("morning_day", v).apply()

    /** День, когда уже была вечерняя разгрузка. */
    var eveningDay: String
        get() = prefs.getString("evening_day", "") ?: ""
        set(v) = prefs.edit().putString("evening_day", v).apply()

    var lastPingAt: Long
        get() = prefs.getLong("last_ping_at", 0L)
        set(v) = prefs.edit().putLong("last_ping_at", v).apply()

    var lastError: String
        get() = prefs.getString("last_error", "") ?: ""
        set(v) = prefs.edit().putString("last_error", v).apply()

    fun reset() {
        prefs.edit().clear().apply()
        nextPing.value = 0L
    }
}