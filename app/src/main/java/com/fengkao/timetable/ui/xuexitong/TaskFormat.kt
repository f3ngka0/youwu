package com.fengkao.timetable.ui.xuexitong

import com.fengkao.timetable.data.xuexitong.SubmitState
import com.fengkao.timetable.data.xuexitong.TaskItem
import com.fengkao.timetable.data.xuexitong.TaskKind
import com.fengkao.timetable.data.xuexitong.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 待办卡片上的文案：倒计时、截止时间、状态。
 *
 * 全部是纯函数，方便对着真实数据核对——「还剩 2 天 3 小时」这种文案一旦算错，
 * 用户会按错的日子提交作业。
 */
object TaskFormat {

    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    private val monthDay = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
    private val monthDayNoTime = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    fun kindLabel(kind: TaskKind): String = kind.label

    /** 「10月25日 23:00」，估算出来的前面加「约」。 */
    fun deadlineText(task: TaskItem): String {
        val millis = task.deadlineMillis ?: return "无截止时间"
        val text = monthDay.format(Instant.ofEpochMilli(millis).atZone(ZONE))
        return if (task.deadlineEstimated) "约 $text" else text
    }

    /**
     * 倒计时：只剩小时级说「还剩 X 小时」，按天说「还剩 X 天 Y 小时」。
     * 已截止返回空串，由 UI 显示「已截止」。
     */
    fun countdownText(task: TaskItem, now: Long = System.currentTimeMillis()): String {
        val millis = task.deadlineMillis ?: return ""
        val left = millis - now
        if (left <= 0L) return ""
        val days = left / DAY
        val hours = (left % DAY) / HOUR
        val minutes = (left % HOUR) / 60_000L
        val prefix = if (task.deadlineEstimated) "约" else ""
        return when {
            days >= 1L -> "${prefix}还剩 $days 天 $hours 小时"
            hours >= 1L -> "${prefix}还剩 $hours 小时 $minutes 分"
            else -> "${prefix}还剩 $minutes 分钟"
        }
    }

    /** 卡片右上角的状态胶囊文案。 */
    fun statusLabel(task: TaskItem, now: Long = System.currentTimeMillis()): String = when {
        task.status == TaskStatus.CLOSED -> "已截止"
        task.longWindow -> "长期开放"
        task.deadlineMillis == null -> "无期限"
        task.deadlineMillis - now <= DAY -> "即将截止"
        else -> countdownText(task, now).ifBlank { "开放中" }
    }

    /**
     * 千万不要在没有依据时写「未提交」——用户会以为真的没交而不去检查。
     * 只有学习通原文写了「你的作业仍未提交」才显示。
     */
    fun submitHint(task: TaskItem): String? =
        if (task.submitState == SubmitState.NOT_SUBMITTED) "学习通标注仍未提交" else null

    /** 首页卡第三行的元信息：截止时间 + 抓取时间。 */
    fun taskMeta(task: TaskItem): String = buildString {
        append(kindLabel(task.kind))
        append(" · ")
        append(deadlineText(task))
        val countdown = countdownText(task)
        if (countdown.isNotBlank()) {
            append(" · ")
            append(countdown)
        }
    }

    /** 展示用的一行标题：课程名 · 作业名，课程名缺失时只留作业名。 */
    fun titleLine(task: TaskItem): String = task.title

    fun courseLine(task: TaskItem): String = task.courseName

    /** 节次区间 → 「3-5节」；只有单节时「第3节」。中文紧跟在变量后，必须用 ${} 包住。 */
    fun periodText(start: Int?, end: Int?): String {
        if (start == null) return ""
        if (end == null || end == start) return "第${start}节"
        return "${start}-${end}节"
    }

    private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dateText(date: LocalDate?): String {
        if (date == null) return ""
        return "${date.monthValue}月${date.dayOfMonth}日 " + weekdayNames[date.dayOfWeek.value - 1]
    }

    fun relativeFuture(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return ""
        val diff = millis - now
        if (diff <= 0L) return "已过"
        val hours = diff / HOUR
        return if (hours < 24) "${hours}小时后" else "${hours / 24}天后"
    }

    @Suppress("unused")
    private fun shortDate(millis: Long): String =
        monthDayNoTime.format(Instant.ofEpochMilli(millis).atZone(ZONE))
}
