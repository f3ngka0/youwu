package com.fengkao.timetable.data.xuexitong

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fengkao.timetable.MainActivity
import com.fengkao.timetable.R

/** 学习通新通知的系统通知；点击直接打开首页通知中心抽屉。 */
object XuexitongNotifier {

    const val CHANNEL_ID = "xuexitong_notices"
    const val ACTION_OPEN_NOTICES = "com.fengkao.timetable.OPEN_NOTICES"
    private const val NOTIFICATION_ID = 7101
    private const val REQUEST_CODE = 7102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习通通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "作业、签到与课程公告提醒"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyNew(context: Context, notices: List<XuexitongNotice>) {
        if (notices.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val first = notices.first()
        val title = if (notices.size == 1) {
            "学习通 · 新通知"
        } else {
            "学习通 · ${notices.size} 条新通知"
        }
        val text = first.title.ifBlank { "有新的学习通通知" }
        val bigText = notices.take(3).joinToString("\n") { "· ${it.title}" }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTICES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notice)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).let { manager ->
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}
