package com.fengkao.timetable.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fengkao.timetable.MainActivity
import com.fengkao.timetable.R
import com.fengkao.timetable.data.local.CourseNoteStore
import com.fengkao.timetable.data.model.CourseNote

/**
 * 课程笔记的提醒闹钟。
 *
 * 不用 WorkManager：它的最小周期是 15 分钟，而笔记提醒是「今晚 20:00」这种分钟级时刻，
 * 只能靠 AlarmManager 精确闹钟。每次笔记增删改都整批重排，只留最近一条未来提醒，
 * 到点后由 [NoteReminderReceiver] 发通知并把下一条排上。
 */
object NoteReminders {

    const val CHANNEL_ID = "note_reminders"
    const val ACTION_OPEN_NOTES = "com.fengkao.timetable.OPEN_COURSE_NOTES"
    const val EXTRA_COURSE = "com.fengkao.timetable.EXTRA_NOTE_COURSE"

    private const val RC_ALARM = 7301
    private const val NOTIFY_ID = 7302
    private const val RC_NOTIFY = 7303

    /** 取消旧闹钟，按「未完成 + 未到点」的最近一条重排。 */
    fun reschedule(context: Context, notes: List<CourseNote>? = null) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmIntent(app)
        am.cancel(pi)
        val list = notes ?: CourseNoteStore(app).all()
        val now = System.currentTimeMillis()
        val next = list
            .filter { !it.done && it.remindAt != null && it.remindAt > now }
            .minByOrNull { it.remindAt ?: Long.MAX_VALUE } ?: return
        val target = next.remindAt ?: return
        runCatching {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(alarmIntent(app))
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "课程笔记提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "作业、考点与课堂笔记自己设定的提醒时间"
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

    /** 到点发通知：单条给足正文，多条合并成一句。 */
    fun notifyDue(context: Context, due: List<CourseNote>) {
        if (due.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val first = due.first()
        val title = if (due.size == 1) {
            "${first.course} · ${first.kind.label}提醒"
        } else {
            "${due.size} 条课程提醒"
        }
        val text = first.content.ifBlank { first.kind.label }.replace('\n', ' ')
        val bigText = due.take(3).joinToString("\n") { note ->
            "· ${note.course}：${note.content.ifBlank { note.kind.label }.replace('\n', ' ')}"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTES
            putExtra(EXTRA_COURSE, first.course)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            RC_NOTIFY,
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
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification) }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RC_ALARM,
            Intent(context, NoteReminderReceiver::class.java)
                .setAction(NoteReminderReceiver.ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** 闹钟到点：先发现在该提醒的笔记，再把下一条排上。 */
class NoteReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TICK = "com.fengkao.timetable.NOTE_REMINDER_TICK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = CourseNoteStore(app)
        val notes = store.all()
        if (intent.action != ACTION_TICK) {
            // 开机 / 改时间 / 换时区：系统会清掉已排的闹钟，整体重排一遍
            NoteReminders.reschedule(app, notes)
            return
        }
        val now = System.currentTimeMillis()
        // 给 60 秒余量：手机可能延迟唤醒，稍有偏差的也不能漏掉。
        val due = notes.filter { !it.done && it.remindAt != null && (it.remindAt ?: 0L) <= now + 60_000L }
        if (due.isNotEmpty()) NoteReminders.notifyDue(app, due)
        NoteReminders.reschedule(app, notes)
    }
}
