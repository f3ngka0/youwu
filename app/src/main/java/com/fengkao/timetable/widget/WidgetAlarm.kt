package com.fengkao.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fengkao.timetable.widget.Widget2x2Provider

/**
 * 分钟级刷新调度：
 * - 有同日倒计时/上课中 → 每分钟对齐刷新（倒计时文案随真实时间变化）
 * - 其余状态（明天/假期/无数据）→ 只在次日 00:00:30 翻转状态
 * 精确闹钟权限不可用时自动退化为非精确（桌面 Widget 分钟级足够）。
 */
object WidgetAlarm {

    const val ACTION_TICK = "com.fengkao.timetable.widget.ALARM_TICK"
    private const val RC_TICK = 42

    fun scheduleNext(context: Context, snap: WidgetSnapshot) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)

        // 按状态分档：上课中/临近上课 → 分钟级；当天倒计时中 → 数分钟；
        // 其余静态状态（明天/假期/无数据）→ 30 分钟兜底（覆盖深浅切换、跨天、天气过期）
        val minutes = snap.status.refreshMinutes.coerceIn(1, 30)
        val target = System.currentTimeMillis() + minutes * 60_000L
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RC_TICK,
            Intent(context, Widget2x2Provider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
