package com.legode.friedrich.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.legode.friedrich.MainActivity
import com.legode.friedrich.R
import com.legode.friedrich.core.Logx

/** Уведомления: «смс» от Фридриха в стиле мессенджера (с ответом прямо из шторки) и служебное уведомление сервиса. */
class Notifier(private val context: Context) {

    data class Line(val text: String, val fromUser: Boolean, val at: Long)

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val avatar: Bitmap by lazy { drawAvatar() }

    private val friedrich: Person by lazy {
        Person.Builder()
            .setName("Friedrich")
            .setKey("friedrich")
            .setImportant(true)
            .setIcon(IconCompat.createWithBitmap(avatar))
            .build()
    }

    private val me: Person by lazy { Person.Builder().setName("Ты").setKey("me").build() }

    fun ensureChannels() {
        try {
            val chat = NotificationChannel(
                CH_CHAT, context.getString(R.string.channel_chat_name), NotificationManager.IMPORTANCE_HIGH
            )
            chat.description = context.getString(R.string.channel_chat_desc)
            chat.enableVibration(true)
            chat.enableLights(true)
            chat.setShowBadge(true)
            chat.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(chat)

            val service = NotificationChannel(
                CH_SERVICE, context.getString(R.string.channel_service_name), NotificationManager.IMPORTANCE_LOW
            )
            service.description = context.getString(R.string.channel_service_desc)
            service.setShowBadge(false)
            service.enableVibration(false)
            service.setSound(null, null)
            manager.createNotificationChannel(service)
        } catch (t: Throwable) {
            Logx.w("channels failed", t)
        }
    }

    fun canPost(): Boolean = try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Throwable) {
        false
    }

    private fun openIntent(sessionId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CHAT)
            .putExtra(EXTRA_SESSION, sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Показывает или обновляет уведомление-переписку для чата. */
    fun showChat(sessionId: Long, title: String, lines: List<Line>, unread: Int, alert: Boolean) {
        if (lines.isEmpty() || !canPost()) return
        try {
            ensureChannels()
            pushShortcut()
            val id = chatId(sessionId)

            val style = NotificationCompat.MessagingStyle(me)
            if (title != "Friedrich") {
                style.conversationTitle = title
                style.isGroupConversation = false
            }
            for (l in lines) {
                style.addMessage(l.text, l.at, if (l.fromUser) null else friedrich)
            }

            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val replyPi = PendingIntent.getBroadcast(
                context, id,
                Intent(context, ReplyReceiver::class.java).setAction(ACTION_REPLY).putExtra(EXTRA_SESSION, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable
            )
            val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Antworten… / Ответить").build()
            val reply = NotificationCompat.Action.Builder(R.drawable.ic_stat_friedrich, "Ответить", replyPi)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()

            val snoozePi = PendingIntent.getBroadcast(
                context, id + 1_000_000,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_SESSION, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val readPi = PendingIntent.getBroadcast(
                context, id + 2_000_000,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_READ)
                    .putExtra(EXTRA_SESSION, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val last = lines.last()
            val builder = NotificationCompat.Builder(context, CH_CHAT)
                .setSmallIcon(R.drawable.ic_stat_friedrich)
                .setLargeIcon(avatar)
                .setColor(ACCENT)
                .setContentTitle(title)
                .setContentText(last.text)
                .setStyle(style)
                .setContentIntent(openIntent(sessionId, id))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setWhen(last.at)
                .setShowWhen(true)
                .setOnlyAlertOnce(!alert)
                .setShortcutId(SHORTCUT_ID)
                .addPerson(friedrich)
                .addAction(reply)
                .addAction(R.drawable.ic_stat_friedrich, "Отложить на час", snoozePi)
                .addAction(R.drawable.ic_stat_friedrich, "Прочитано", readPi)
            if (unread > 0) builder.setNumber(unread)
            if (alert) builder.setDefaults(NotificationCompat.DEFAULT_ALL)

            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (se: SecurityException) {
            Logx.w("no notification permission", se)
        } catch (t: Throwable) {
            Logx.w("showChat failed", t)
        }
    }

    fun cancelChat(sessionId: Long) {
        try {
            manager.cancel(chatId(sessionId))
        } catch (_: Throwable) {
        }
    }

    fun cancelAllChats() {
        try {
            for (sbn in manager.activeNotifications) {
                if (sbn.id != SERVICE_ID && sbn.id != WORK_ID) manager.cancel(sbn.id)
            }
        } catch (_: Throwable) {
        }
    }

    /** Тихое постоянное уведомление сервиса «Friedrich на связи». */
    fun buildService(text: String): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_friedrich)
            .setColor(ACCENT)
            .setContentTitle("Friedrich на связи")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(0L, 7))
            .build()
    }

    fun updateService(text: String) {
        if (!canPost()) return
        try {
            NotificationManagerCompat.from(context).notify(SERVICE_ID, buildService(text))
        } catch (_: Throwable) {
        }
    }

    /** «Разговорный» ярлык: благодаря ему Android показывает уведомления в разделе «Разговоры». */
    private fun pushShortcut() {
        try {
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel("Friedrich")
                .setLongLabel("Чат с Фридрихом")
                .setLongLived(true)
                .setPerson(friedrich)
                .setIcon(IconCompat.createWithBitmap(avatar))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (t: Throwable) {
            Logx.w("shortcut failed", t)
        }
    }

    private fun drawAvatar(): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ACCENT
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFF0B0D12.toInt()
        paint.textSize = size * 0.58f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("F", size / 2f, y, paint)
        return bmp
    }

    companion object {
        const val CH_CHAT = "friedrich_chat_v1"
        const val CH_SERVICE = "friedrich_service_v1"
        const val SERVICE_ID = 77
        const val WORK_ID = 78
        const val SHORTCUT_ID = "friedrich_chat"
        const val KEY_REPLY = "key_reply_text"
        const val EXTRA_SESSION = "session_id"
        const val ACTION_OPEN_CHAT = "com.legode.friedrich.OPEN_CHAT"
        const val ACTION_REPLY = "com.legode.friedrich.REPLY"
        const val ACTION_SNOOZE = "com.legode.friedrich.SNOOZE"
        const val ACTION_READ = "com.legode.friedrich.READ"
        const val ACTION_PING = "com.legode.friedrich.PING"
        val ACCENT: Int = 0xFFFFCE3A.toInt()

        fun chatId(sessionId: Long): Int = 1000 + (sessionId % 500_000L).toInt()
    }
}
