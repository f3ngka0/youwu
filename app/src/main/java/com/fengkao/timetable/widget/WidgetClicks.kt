package com.fengkao.timetable.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fengkao.timetable.MainActivity

/** Widget 点击链路：根区域打开 App；课程区打开课程详情（deep link extras） */
object WidgetClicks {

    const val ACTION_OPEN_COURSE = "com.fengkao.timetable.widget.OPEN_COURSE"
    const val EXTRA_COURSE_KEY = "com.fengkao.timetable.widget.EXTRA_COURSE_KEY"

    private const val RC_OPEN_APP = 1001
    private const val RC_OPEN_COURSE_BASE = 2000

    private val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** 打开课程表 App（默认即定位当前日期） */
    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_OPEN_APP,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            FLAGS,
        )

    /** 打开指定课程详情 */
    fun openCourse(context: Context, courseKey: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_OPEN_COURSE_BASE + (courseKey.hashCode().and(0x7FFFFFFF) % 100000),
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_COURSE)
                .putExtra(EXTRA_COURSE_KEY, courseKey)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            FLAGS,
        )
}
