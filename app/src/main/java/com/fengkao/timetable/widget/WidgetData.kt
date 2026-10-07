package com.fengkao.timetable.widget

import android.content.Context
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.data.TermSpec
import com.fengkao.timetable.data.local.AppSettings
import com.fengkao.timetable.data.local.ScheduleCache
import com.fengkao.timetable.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Widget 数据层（每分钟刷新）：
 * 1 今天无课(灰) 2 正在上课(绿) 3 ≤20min 即将上课(蓝) 4 >20min 下一节课(橙)
 * 5 课间空窗=已下课(紫) 6 当天全部结束=课程已结束(暖橙+灰✓)
 */
enum class WidgetTone { NEXT, SOON, ACTIVE, DONE, IDLE }

data class WidgetCourse(
    val key: String,
    /** 清洗后全称（去括号备注） */
    val name: String,
    /** 简称：用户自定义 > 词典 > 全称 */
    val nameShort: String,
    val place: String,
    val start: LocalTime,
    val end: LocalTime,
    val state: Int,
) {
    companion object {
        const val PAST = 0
        const val CURRENT = 1
        const val UPCOMING = 2
    }
}

data class WidgetStatus(
    val kind: Kind,
    val tone: WidgetTone,
    /** 徽章文案：正在上课 / 即将上课 / 下一节课 / 即将开始 / 已下课 / 课程已结束 / 今天无课 / 假期中 */
    val label: String,
    /** 标题行（第一行） */
    val title: String,
    /** 标题简称（阶梯降级用） */
    val titleShort: String? = null,
    /** 第二行（已下课=刚结束的课名；课程已结束=辛苦啦！；今天无课无） */
    val subTitle: String? = null,
    /** 倒计时小标签（即将上课=距离上课 灰 / 即将开始=即将上课 红） */
    val prefix: String? = null,
    /** 大数字（视觉焦点，两位补零；>99 显示 99+） */
    val bigNumber: String? = null,
    val unit: String? = null,
    /** 教室（仅进行中/已下课显示） */
    val place: String? = null,
    /** 底部胶囊：小标签 + 主文案 */
    val cardLabel: String? = null,
    /** 底部胶囊主文案（进行中=剩余分钟数，状态色加粗；其余态为整句） */
    val cardMain: String? = null,
    /** 底部胶囊尾缀（进行中=分钟） */
    val cardPost: String? = null,
    /** 点击跳课程详情 */
    val courseKey: String? = null,
    /** 空状态副文案 */
    val restText: String? = null,
    /** 建议刷新间隔（分钟） */
    val refreshMinutes: Int = 30,
    /** 进度环比例 0~100 */
    val ringProgress: Int = 0,
    /** 下一节课预告框文案 */
    val nextText: String? = null,
) {
    enum class Kind { COUNTDOWN, IN_CLASS, DAY_OVER, NO_TODAY, VACATION, COURSE_GAP, NO_DATA }

    /** 倒计时/上课中需要分钟级刷新 */
    val needsMinuteTick: Boolean get() = kind == Kind.COUNTDOWN || kind == Kind.IN_CLASS
}

data class WidgetSnapshot(
    val status: WidgetStatus,
    val today: List<WidgetCourse>,
    /** 今天没课时右栏「明日课程」 */
    val tomorrow: List<WidgetCourse> = emptyList(),
    val tomorrowLabel: String = "明日课程",
)

object WidgetData {

    suspend fun load(context: Context): WidgetSnapshot {
        val settings = runCatching { SettingsStore(context).first() }.getOrNull() ?: AppSettings()
        val schedule = ScheduleCache(context).load()
        val pts = settings.periodTimes()

        if (schedule == null || schedule.isEmpty()) {
            return WidgetSnapshot(
                status = WidgetStatus(
                    kind = WidgetStatus.Kind.NO_DATA,
                    tone = WidgetTone.IDLE,
                    label = "课程表",
                    title = "打开 App 同步课表",
                    restText = "登录后自动同步",
                ),
                today = emptyList(),
            )
        }

        val today = LocalDate.now()
        val now = LocalTime.now()
        val term = TermSpec(schedule.xnm, schedule.term)
        val termStart = settings.termStartFor(term)
        val week = TermCalculator.weekOf(termStart, today)
        val inTerm = !today.isBefore(termStart) && week <= settings.termWeeks

        fun coursesOfDay(day: LocalDate): List<WidgetCourse> {
            val w = TermCalculator.weekOf(termStart, day)
            if (w < 1 || w > settings.termWeeks) return emptyList()
            return schedule.courses.asSequence()
                .filter { it.dayOfWeek == day.dayOfWeek.value && w in it.weeks && it.stableKey !in settings.hiddenCourses }
                .mapNotNull { c ->
                    val st = pts.getOrNull(c.startPeriod - 1)?.let { LocalTime.of(it.startHour, it.startMinute) }
                        ?: return@mapNotNull null
                    val en = pts.getOrNull(c.endPeriod - 1)?.let { LocalTime.of(it.endHour, it.endMinute) }
                        ?: return@mapNotNull null
                    val state = when {
                        day != today -> WidgetCourse.UPCOMING
                        now < st -> WidgetCourse.UPCOMING
                        now.isAfter(en) -> WidgetCourse.PAST
                        else -> WidgetCourse.CURRENT
                    }
                    val nameFull = WidgetText.cleanName(c.name)
                    WidgetCourse(
                        key = c.stableKey,
                        name = nameFull,
                        nameShort = WidgetText.shortName(nameFull, settings.nameOverrides),
                        place = WidgetText.abbrRoom(c.place),
                        start = st,
                        end = en,
                        state = state,
                    )
                }
                .sortedBy { it.start }
                .toList()
        }

        // 下一个有课的日子（今天起 7 天内）
        var nextDay: LocalDate? = null
        var nextCourses: List<WidgetCourse> = emptyList()
        for (d in 0L..7L) {
            val day = today.plusDays(d)
            val list = coursesOfDay(day)
            if (list.isNotEmpty()) {
                nextDay = day
                nextCourses = list
                break
            }
        }

        val todayCourses = if (inTerm) coursesOfDay(today) else emptyList()
        val current = todayCourses.firstOrNull { it.state == WidgetCourse.CURRENT }
        val next = todayCourses.firstOrNull { it.state == WidgetCourse.UPCOMING }

        val status = when {
            !inTerm -> idleOrVacation(nextDay, nextCourses, today, vacation = true)
            current != null -> inClassStatus(current, now)
            next != null -> countdownOrGap(next, now, todayCourses)
            todayCourses.isNotEmpty() -> dayOverFull(todayCourses, nextDay, nextCourses, today)
            else -> idleOrVacation(nextDay, nextCourses, today, vacation = false)
        }

        val showTomorrow = status.kind == WidgetStatus.Kind.NO_TODAY || status.kind == WidgetStatus.Kind.VACATION
        return WidgetSnapshot(
            status = status,
            today = todayCourses,
            tomorrow = if (showTomorrow) nextCourses else emptyList(),
            tomorrowLabel = "明日课程",
        )
    }

    // ------------------------------------------------------------ 正在上课（绿）

    private fun inClassStatus(c: WidgetCourse, now: LocalTime): WidgetStatus {
        val mins = minutesUntil(now, c.end)
        val total = (Duration.between(c.start, c.end).toMillis() + 59_999) / 60_000
        val elapsed = (total - mins).coerceIn(0L, total)
        return WidgetStatus(
            kind = WidgetStatus.Kind.IN_CLASS,
            tone = WidgetTone.ACTIVE,
            label = "正在上课",
            title = c.name,
            titleShort = c.nameShort,
            place = c.place.ifBlank { null },
            cardLabel = "还剩",
            cardMain = "$mins",
            cardPost = "分钟",
            ringProgress = (elapsed * 100L / total.coerceAtLeast(1L)).toInt().coerceIn(2, 99),
            refreshMinutes = 1,
            courseKey = c.key,
        )
    }

    // ------------------------------------------------------------ 倒计时：≤20 蓝 / >20 课间紫 或 橙

    private fun countdownOrGap(next: WidgetCourse, now: LocalTime, todayCourses: List<WidgetCourse>): WidgetStatus {
        val mins = minutesUntil(now, next.start)
        return when {
            // 原「即将开始(红)」已删除：≤20min 统一由「即将上课(蓝)」承载
            mins <= 20L -> WidgetStatus(
                kind = WidgetStatus.Kind.COUNTDOWN,
                tone = WidgetTone.SOON,
                label = "即将上课",
                bigNumber = "%02d".format(mins),
                unit = "分钟后",
                title = next.name,
                titleShort = next.nameShort,
                place = next.place.ifBlank { null },
                cardLabel = "预计 ${next.start} 上课",
                ringProgress = 22,
                refreshMinutes = 1,
                courseKey = next.key,
            )
            else -> {
                // 课间空窗：刚结束一节且下一节还远 → 已下课（紫）
                // 两行结构：大标题=课程名、小标题=教室；无底部框/进度环
                val lastPast = todayCourses.lastOrNull { it.state == WidgetCourse.PAST }
                if (lastPast != null) {
                    WidgetStatus(
                        kind = WidgetStatus.Kind.COURSE_GAP,
                        tone = WidgetTone.DONE,
                        label = "已下课",
                        title = lastPast.name,
                        titleShort = lastPast.nameShort,
                        place = lastPast.place.ifBlank { null },
                        refreshMinutes = 5,
                        courseKey = lastPast.key,
                    )
                } else {
                    nextOrange(next, mins)
                }
            }
        }
    }

    private fun nextOrange(next: WidgetCourse, mins: Long): WidgetStatus = WidgetStatus(
        kind = WidgetStatus.Kind.COUNTDOWN,
        tone = WidgetTone.NEXT,
        label = "下一节课",
        bigNumber = if (mins > 99L) "99+" else "$mins",
        unit = "分钟后",
        title = next.name,
        titleShort = next.nameShort,
        place = next.place.ifBlank { null },
        cardLabel = "预计 ${next.start} 上课",
        ringProgress = 14,
        refreshMinutes = if (mins > 60L) 15 else 5,
        courseKey = next.key,
    )

    // ------------------------------------------------------------ 今天全部结束（暖橙 + 灰✓）

    private fun dayOverFull(
        todayCourses: List<WidgetCourse>,
        nextDay: LocalDate?,
        nextCourses: List<WidgetCourse>,
        today: LocalDate,
    ): WidgetStatus {
        val first = nextCourses.firstOrNull()
        return WidgetStatus(
            kind = WidgetStatus.Kind.DAY_OVER,
            tone = WidgetTone.NEXT,
            label = "课程已结束",
            title = "今天课程已结束",
            subTitle = "辛苦啦！",
            nextText = first?.let { nextInfoText(today, nextDay, it) },
            refreshMinutes = 30,
        )
    }

    // ------------------------------------------------------------ 今天无课 / 假期（灰）

    private fun idleOrVacation(
        nextDay: LocalDate?,
        nextCourses: List<WidgetCourse>,
        today: LocalDate,
        vacation: Boolean,
    ): WidgetStatus {
        val first = nextCourses.firstOrNull()
        return WidgetStatus(
            kind = if (vacation) WidgetStatus.Kind.VACATION else WidgetStatus.Kind.NO_TODAY,
            tone = WidgetTone.IDLE,
            label = if (vacation) "假期中" else "今天无课",
            title = if (vacation) "假期中" else "今天没有课程",
            restText = if (vacation && first == null) "下学期见" else "好好休息一下吧",
            nextText = first?.let { nextInfoText(today, nextDay, it) },
            refreshMinutes = 30,
        )
    }

    // ------------------------------------------------------------ 演示模式（真机预览各状态，adb: am start -e demo active|soon|next|gap|noclass|over）

    private fun demoCourse(name: String, place: String, start: String, end: String, state: Int): WidgetCourse {
        val cleaned = WidgetText.cleanName(name)
        return WidgetCourse(
        key = "demo_$cleaned",
        name = cleaned,
        nameShort = WidgetText.shortName(cleaned),
        place = WidgetText.abbrRoom(place),
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        state = state,
        )
    }

    fun demoSnapshot(kind: String): WidgetSnapshot {
        val c1 = demoCourse("高等数学", "A栋 203", "10:25", "11:50", WidgetCourse.UPCOMING)
        val c2 = demoCourse("大学英语", "B栋 106", "13:00", "13:45", WidgetCourse.UPCOMING)
        return when (kind) {
            "active" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.IN_CLASS, tone = WidgetTone.ACTIVE, label = "正在上课",
                    title = "高等数学", place = "A栋 203",
                    cardLabel = "还剩", cardMain = "38", cardPost = "分钟",
                    ringProgress = 72, refreshMinutes = 1, courseKey = c1.key,
                ),
                listOf(
                    c1.copy(state = WidgetCourse.CURRENT),
                    demoCourse("大学英语", "B栋 106", "13:00", "13:45", WidgetCourse.UPCOMING),
                    demoCourse("程序设计基础", "C栋 305", "15:00", "16:35", WidgetCourse.UPCOMING),
                    demoCourse("体育", "操场", "17:10", "17:50", WidgetCourse.UPCOMING),
                ),
            )
            "soon" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.COUNTDOWN, tone = WidgetTone.SOON, label = "即将上课",
                    bigNumber = "08", unit = "分钟后",
                    title = "高等数学", place = "A栋 203",
                    cardLabel = "预计 10:25 上课", ringProgress = 22,
                    refreshMinutes = 1, courseKey = c1.key,
                ),
                listOf(
                    c1,
                    c2,
                    demoCourse("程序设计基础", "C栋 305", "15:00", "16:35", WidgetCourse.UPCOMING),
                    demoCourse("体育", "操场", "17:10", "17:50", WidgetCourse.UPCOMING),
                ),
            )
            "next" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.COUNTDOWN, tone = WidgetTone.NEXT, label = "下一节课",
                    bigNumber = "42", unit = "分钟后",
                    title = "大学英语", place = "B栋 106",
                    cardLabel = "预计 10:10 上课", ringProgress = 14,
                    refreshMinutes = 5, courseKey = c2.key,
                ),
                listOf(
                    c1.copy(state = WidgetCourse.PAST),
                    c2,
                    demoCourse("程序设计基础", "C栋 305", "15:00", "16:35", WidgetCourse.UPCOMING),
                    demoCourse("体育", "操场", "17:10", "17:50", WidgetCourse.UPCOMING),
                ),
            )
            "gap" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.COURSE_GAP, tone = WidgetTone.DONE, label = "已下课",
                    title = "高等数学", place = "A栋 203",
                    refreshMinutes = 5, courseKey = c1.copy(state = WidgetCourse.PAST).key,
                ),
                listOf(
                    c1.copy(state = WidgetCourse.PAST),
                    c2,
                    demoCourse("程序设计基础", "C栋 305", "15:00", "16:35", WidgetCourse.UPCOMING),
                ),
            )
            "over" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.DAY_OVER, tone = WidgetTone.NEXT, label = "课程已结束",
                    title = "今天课程已结束", subTitle = "辛苦啦！",
                    nextText = "明天 08:00 · 大学英语", refreshMinutes = 30,
                ),
                listOf(
                    c1.copy(state = WidgetCourse.PAST),
                    c2.copy(state = WidgetCourse.PAST),
                    demoCourse("程序设计基础", "C栋 305", "15:00", "16:35", WidgetCourse.PAST),
                    demoCourse("体育", "操场", "17:10", "17:50", WidgetCourse.PAST),
                ),
            )
            "cases" -> {
                val rows = listOf(
                    demoCourse("离散数学（A班·张老师）", "明科楼1309", "08:00", "09:35", WidgetCourse.PAST),
                    demoCourse("铸造工艺学", "明理楼S-205", "10:10", "10:45", WidgetCourse.PAST),
                    demoCourse("程序设计基础", "C栋305", "14:00", "15:35", WidgetCourse.UPCOMING),
                    demoCourse("马克思主义基本原理", "第3教学楼301", "16:00", "16:45", WidgetCourse.UPCOMING),
                )
                WidgetSnapshot(
                    WidgetStatus(
                        kind = WidgetStatus.Kind.COUNTDOWN, tone = WidgetTone.NEXT, label = "下一节课",
                        bigNumber = "42", unit = "分钟后",
                        title = "马克思主义基本原理", titleShort = "马原", place = "第3教学楼301",
                        cardLabel = "预计 16:00 上课", ringProgress = 14, refreshMinutes = 5,
                        courseKey = rows[3].key,
                    ),
                    rows,
                )
            }
            "noclass" -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.NO_TODAY, tone = WidgetTone.IDLE, label = "今天无课",
                    title = "今天没有课程", restText = "好好休息一下吧",
                    nextText = "明天 08:00 · 大学英语", refreshMinutes = 30,
                ),
                emptyList(),
                listOf(c1, c2),
            )
            else -> WidgetSnapshot(
                WidgetStatus(
                    kind = WidgetStatus.Kind.COUNTDOWN, tone = WidgetTone.NEXT, label = "下一节课",
                    bigNumber = "42", unit = "分钟后", title = "高等数学", place = "A栋 203",
                    cardLabel = "预计 10:25 上课", ringProgress = 14, courseKey = c1.key,
                ),
                listOf(c1, c2),
            )
        }
    }

    // ------------------------------------------------------------ 工具

    private fun minutesUntil(now: LocalTime, target: LocalTime): Long =
        (Duration.between(now, target).toMillis() + 59_999) / 60_000

    private fun nextInfoText(today: LocalDate, nextDay: LocalDate?, first: WidgetCourse): String =
        "${dayLabel(today, nextDay!!)} ${first.start} · ${first.name}"

    private fun dayLabel(today: LocalDate, day: LocalDate): String =
        when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "今天"
            1L -> "明天"
            2L -> "后天"
            else -> "周${"一二三四五六日"[day.dayOfWeek.value - 1]}"
        }
}
