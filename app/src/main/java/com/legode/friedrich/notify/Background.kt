package com.legode.friedrich.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.Timex
import com.legode.friedrich.di.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/*
 * Всё, что делает Фридриха «всегда включённым»:
 *  • AlarmScheduler — точный будильник следующего «смс» (срабатывает и в Doze);
 *  • PingAlarmReceiver → PingWorker — пишет сообщение (ИИ или офлайн-банк) и ставит следующий будильник;
 *  • WatchdogWorker — каждые 15 минут проверяет, что расписание живо, и чинит его;
 *  • BootReceiver — восстанавливает расписание после перезагрузки, обновления, смены времени;
 *  • ReplyReceiver → ReplyWorker — ответ ученика прямо из шторки уведомлений;
 *  • KeepAliveService — постоянный сервис, который не даёт системе выгрузить приложение.
 */

// ───────────────────────── Будильник ─────────────────────────

class AlarmScheduler(private val context: Context) {

    private val am: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context, 4242,
        Intent(context, PingAlarmReceiver::class.java).setAction(Notifier.ACTION_PING),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun canExact(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    } catch (_: Throwable) {
        false
    }

    fun arm(at: Long) {
        val pi = pending()
        try {
            if (canExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (se: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (t: Throwable) {
                Logx.w("alarm failed", t)
            }
        } catch (t: Throwable) {
            Logx.w("alarm failed", t)
        }
    }

    fun cancel() {
        try {
            am.cancel(pending())
        } catch (_: Throwable) {
        }
    }
}

// ───────────────────────── Фоновые задания (WorkManager) ─────────────────────────

object Jobs {
    private const val UNIQUE_PING = "friedrich_ping"
    private const val UNIQUE_WATCHDOG = "friedrich_watchdog"
    const val KEY_FORCE = "force"
    const val KEY_KIND = "kind"
    const val KEY_SESSION = "session"
    const val KEY_TEXT = "text"

    fun ping(context: Context, force: Boolean = false, kind: String? = null) {
        try {
            val request = OneTimeWorkRequestBuilder<PingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_FORCE to force, KEY_KIND to (kind ?: "")))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PING, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request
            )
        } catch (t: Throwable) {
            Logx.w("enqueue ping failed", t)
        }
    }

    fun reply(context: Context, sessionId: Long, text: String) {
        try {
            val request = OneTimeWorkRequestBuilder<ReplyWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_SESSION to sessionId, KEY_TEXT to text))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (t: Throwable) {
            Logx.w("enqueue reply failed", t)
        }
    }

    fun watchdog(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, request
            )
        } catch (t: Throwable) {
            Logx.w("enqueue watchdog failed", t)
        }
    }
}

class PingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifier.WORK_ID, applicationContext.graph().notifier().buildService("Friedrich пишет сообщение…"))

    override suspend fun doWork(): Result {
        val graph = applicationContext.graph()
        return try {
            val force = inputData.getBoolean(Jobs.KEY_FORCE, false)
            val kind = inputData.getString(Jobs.KEY_KIND).orEmpty().ifEmpty { null }
            graph.brain().pingCycle(force, kind)
            Result.success()
        } catch (t: Throwable) {
            Logx.e("ping worker failed", t)
            try {
                graph.brain().rearm()
            } catch (_: Throwable) {
            }
            Result.success()
        }
    }
}

class ReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifier.WORK_ID, applicationContext.graph().notifier().buildService("Friedrich отвечает…"))

    override suspend fun doWork(): Result {
        val graph = applicationContext.graph()
        val sessionId = inputData.getLong(Jobs.KEY_SESSION, 0L)
        val text = inputData.getString(Jobs.KEY_TEXT).orEmpty().trim()
        if (sessionId == 0L || text.isEmpty()) return Result.success()
        return try {
            graph.brain().replyFromNotification(sessionId, text)
            Result.success()
        } catch (t: Throwable) {
            Logx.e("reply worker failed", t)
            Result.success()
        }
    }
}

class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = applicationContext.graph()
            graph.brain().ensureScheduled()
            if (graph.settings().value.keepAlive && graph.settings().value.onboarded) {
                KeepAliveService.start(applicationContext)
            }
            Result.success()
        } catch (t: Throwable) {
            Logx.e("watchdog failed", t)
            Result.success()
        }
    }
}

// ───────────────────────── Приёмники ─────────────────────────

class PingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Страховочный будильник на случай, если задание не успеет выполниться; PingWorker заменит его точным.
        try {
            context.applicationContext.graph().alarms().arm(Timex.now() + 25 * 60_000L)
        } catch (t: Throwable) {
            Logx.w("provisional alarm failed", t)
        }
        Jobs.ping(context.applicationContext)
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val sessionId = intent.getLongExtra(Notifier.EXTRA_SESSION, 0L)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString().orEmpty()
        if (sessionId == 0L || text.isBlank()) return
        Jobs.reply(context.applicationContext, sessionId, text)
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val sessionId = intent.getLongExtra(Notifier.EXTRA_SESSION, 0L)
        val action = intent.action ?: return
        val result = goAsync()
        val graph = context.applicationContext.graph()
        graph.scope().launch {
            try {
                when (action) {
                    Notifier.ACTION_SNOOZE -> graph.brain().snooze(60)
                    Notifier.ACTION_READ -> graph.brain().markRead(sessionId)
                }
            } catch (t: Throwable) {
                Logx.w("notification action failed", t)
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val result = goAsync()
        val graph = app.graph()
        graph.scope().launch {
            try {
                if (graph.settings().value.onboarded) {
                    graph.brain().ensureScheduled()
                    Jobs.watchdog(app)
                    if (graph.settings().value.keepAlive) KeepAliveService.start(app)
                }
            } catch (t: Throwable) {
                Logx.w("boot restore failed", t)
            } finally {
                result.finish()
            }
        }
    }
}

// ───────────────────────── Постоянный сервис ─────────────────────────

class KeepAliveService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground("Пишу, проверяю, не отстаю 🙂")
        val graph = applicationContext.graph()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            graph.runtime().nextPingFlow.collectLatest { at ->
                val text = when {
                    !graph.settings().value.pingsEnabled -> "Сообщения выключены в настройках"
                    at <= 0L -> "Жду подходящего момента…"
                    else -> "Следующее сообщение ≈ " + Timex.hhmm(at)
                }
                graph.notifier().updateService(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground("Пишу, проверяю, не отстаю 🙂")
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun goForeground(text: String) {
        try {
            val notification = applicationContext.graph().notifier().buildService(text)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifier.SERVICE_ID, notification)
            }
        } catch (t: Throwable) {
            Logx.w("startForeground failed", t)
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 33) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val ACTION_STOP = "com.legode.friedrich.STOP_SERVICE"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (t: Throwable) {
                // Android 12+: запуск из фона может быть запрещён — тогда сервис поднимется при следующем открытии приложения.
                Logx.w("keep-alive start refused", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}
