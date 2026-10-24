package com.fengkao.timetable.data.xuexitong

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.UUID

/** 待办的类型；只有学习通真正挂了作业/考试附件的通知才算待办。 */
enum class TaskKind(val label: String) {
    HOMEWORK("作业"),
    EXAM("考试"),
}

enum class TaskStatus { URGENT, OPEN, CLOSED, UNDATED }

/**
 * 学习通的提交状态。
 *
 * 只有原文自己写了「你的作业仍未提交」才敢标 NOT_SUBMITTED；
 * 没写就是 UNKNOWN——猜「未提交」会让用户以为真的没交。
 */
enum class SubmitState { UNKNOWN, NOT_SUBMITTED }

/**
 * 由同一 taskrefId 的多条通知合并出的一件「待办事项」。
 * 学习通会为同一份作业发「发布 + 48 小时提醒 + 2 小时提醒」多条通知，
 * 用户要办的是 1 件事，不是 3 条通知。
 */
data class TaskItem(
    val key: String,
    val kind: TaskKind,
    val courseName: String,
    val title: String,
    /** 截止时间；null 表示原文没有给出任何可用的截止信息。 */
    val deadlineMillis: Long?,
    /** true 表示截止时间是从「还有 N 小时就要结束」反推出来的，UI 上要加「约」。 */
    val deadlineEstimated: Boolean,
    val status: TaskStatus,
    val submitState: SubmitState,
    /** 同一件事收到了几条通知。 */
    val reminderCount: Int,
    val latest: XuexitongNotice,
    /** 开放窗口超过 30 天（如自主考试）：不宜霸占「最近要交」的首屏。 */
    val longWindow: Boolean,
)

enum class ChangeKind {
    /** 明确的新时间地点：「明天下午678节调到上午345节，地点K708」。 */
    MOVE,

    /** 两门课互调，单边解析会改错课表。 */
    SWAP,

    /** 只有「停课」，没有改到哪。 */
    CANCEL,

    /** 只说「去教务系统看」，抽不出任何可用字段。 */
    SYNC_HINT,
}

/**
 * 从课程公告里抽出的课表异动。
 *
 * 识别失败不可怕，怕的是「识别错却当成对的」。所以凡是拿不准的一律降级：
 * 只有同时确定了目标日期、目标节次，才允许给出「改课表」入口。
 */
data class ScheduleChange(
    val noticeId: String,
    val courseName: String,
    val kind: ChangeKind,
    /** 目标日期；SWAP / CANCEL / SYNC_HINT 时为 null。 */
    val toDate: LocalDate?,
    val toStartPeriod: Int?,
    val toEndPeriod: Int?,
    val fromStartPeriod: Int?,
    val fromEndPeriod: Int?,
    val place: String,
    /** 命中原文的一句摘要，让用户能自己核对。 */
    val excerpt: String,
    val notice: XuexitongNotice,
) {
    /** 能否安全地预填「改课表」表单：必须知道改到哪天、第几节。 */
    val applicable: Boolean
        get() = kind == ChangeKind.MOVE &&
            toDate != null &&
            toStartPeriod != null &&
            toEndPeriod != null
}

/**
 * 学习通通知的二次抽取：把 42 条通知压成 6 件要办的事 + 几条课表异动。
 *
 * 全部是纯函数，输入「通知列表 + 当前时间」，方便用真实抓取数据做单元测试。
 */
object NoticeExtract {

    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private val courseLine = Regex("""^课程名称\s*[:：]\s*(.+)$""", RegexOption.MULTILINE)
    private val homeworkLine = Regex("""^(?:作业名称|作业标题)\s*[:：]\s*(.+)$""", RegexOption.MULTILINE)
    private val examLine = Regex("""^(?:考试名称|试卷名称)\s*[:：]\s*(.+)$""", RegexOption.MULTILINE)

    private val explicitEnd = Regex(
        """(?:结束时间|截止时间|截止日期)\s*[:：]\s*(\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}(?::\d{2})?)"""
    )
    /** 考试区间：「07-02 00:37 至 07-03 12:59」，两侧可能一侧带年份（真实数据里有 09-08 → 2027-01-04）。 */
    private val examRange = Regex(
        """考试时间\s*[:：]\s*((?:\d{4}-)?\d{2}-\d{2}\s+\d{1,2}:\d{2}(?::\d{2})?)\s*(?:至|到|—|－|-)\s*((?:\d{4}-)?\d{2}-\d{2}\s+\d{1,2}:\d{2}(?::\d{2})?)"""
    )
    private val reminderLeft = Regex(
        """还有\s*(?:(\d{1,3})\s*个?\s*小时)?\s*(?:(\d{1,2})\s*分(?:钟)?)?\s*就要结束"""
    )

    private fun taskRef(notice: XuexitongNotice): String {
        val url = notice.attachmentUrl
        val idx = url.indexOf("taskrefId=")
        if (idx < 0) return ""
        val rest = url.substring(idx + "taskrefId=".length)
        return rest.substringBefore('&').substringBefore('#').trim()
    }

    private fun kindOf(notice: XuexitongNotice): TaskKind? = when (notice.attachmentKind) {
        "work" -> TaskKind.HOMEWORK
        "exam" -> TaskKind.EXAM
        else -> null
    }

    private fun firstGroup(regex: Regex, text: String): String =
        regex.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    /** 抽取一件任务的截止毫秒；估出来的第二个返回值置 true。 */
    private fun deadlineOf(
        notices: List<XuexitongNotice>,
    ): Pair<Long?, Boolean> {
        // 1) 明确写死的结束时间优先级最高
        for (notice in notices) {
            explicitEnd.find(notice.content)?.groupValues?.getOrNull(1)?.let { raw ->
                parseDateTime(raw)?.let { return it to false }
            }
            examRange.find(notice.content)?.groupValues?.getOrNull(2)?.let { raw ->
                parseDateTime(raw, notice.timeMillis)?.let { return it to false }
            }
        }
        // 2) 「还有 39 小时 57 分就要结束」+ 通知发出时间 反推（实测误差 1 分钟内）
        for (notice in notices) {
            val m = reminderLeft.find(notice.content) ?: continue
            val hours = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            val minutes = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hours == 0 && minutes == 0) continue
            val base = notice.timeMillis
            if (base <= 0L) continue
            return (base + hours * 3_600_000L + minutes * 60_000L) to true
        }
        return null to false
    }

    private fun parseDateTime(raw: String, refMillis: Long = 0L): Long? {
        val text = raw.trim()
        val full = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")
        for (pattern in full) {
            runCatching {
                LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.CHINA))
                    .atZone(ZONE).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
        }
        // 短格式「07-02 19:30」没有年份：以通知发出时间的年份为基准，跨年时补一年
        val year = if (refMillis > 0L) {
            ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(refMillis), ZONE).year
        } else {
            ZonedDateTime.now(ZONE).year
        }
        for (pattern in listOf("MM-dd HH:mm:ss", "MM-dd HH:mm")) {
            runCatching {
                // 短格式没有年份：必须用 parseDefaulting 补 YEAR，
                // 事后 withYear 救不了——缺 YEAR 字段在 parse 阶段就抛异常了。
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern(pattern)
                    .parseDefaulting(ChronoField.YEAR, year.toLong())
                    .toFormatter(Locale.CHINA)
                LocalDateTime.parse(text, formatter).atZone(ZONE).toInstant().toEpochMilli()
            }.getOrNull()?.let { millis ->
                // 结果比参照时间早 60 天以上，说明是跨年场景
                return if (refMillis > 0L && millis < refMillis - 60L * 86_400_000L) {
                    ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZONE)
                        .plusYears(1).toInstant().toEpochMilli()
                } else {
                    millis
                }
            }
        }
        return null
    }

    fun statusOf(deadline: Long?, now: Long): TaskStatus = when {
        deadline == null -> TaskStatus.UNDATED
        deadline < now -> TaskStatus.CLOSED
        deadline - now <= 24L * 3_600_000L -> TaskStatus.URGENT
        else -> TaskStatus.OPEN
    }

    /**
     * 把通知列表压成待办列表。
     * 排序：紧急 > 开放（按截止升序）> 长期窗口 > 无期限 > 已截止。
     */
    fun buildTasks(
        notices: List<XuexitongNotice>,
        now: Long = System.currentTimeMillis(),
    ): List<TaskItem> {
        val groups = LinkedHashMap<String, MutableList<XuexitongNotice>>()
        notices.forEach { notice ->
            val kind = kindOf(notice) ?: return@forEach
            val taskRef = taskRef(notice)
            val course = firstGroup(courseLine, notice.content)
            val name = firstGroup(homeworkLine, notice.content)
                .ifBlank { firstGroup(examLine, notice.content) }
                .ifBlank { notice.attachmentName }
            val key = when {
                taskRef.isNotBlank() -> "t:$taskRef"
                course.isNotBlank() && name.isNotBlank() -> "c:$course|$name"
                else -> "n:${notice.id}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(notice)
        }

        return groups.map { (key, list) ->
            val sorted = list.sortedByDescending { it.timeMillis }
            val latest = sorted.first()
            val kind = kindOf(latest) ?: TaskKind.HOMEWORK
            val course = sorted.asSequence()
                .map { firstGroup(courseLine, it.content) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val name = sorted.asSequence()
                .map {
                    firstGroup(homeworkLine, it.content)
                        .ifBlank { firstGroup(examLine, it.content) }
                }
                .firstOrNull { it.isNotBlank() }
                ?: latest.attachmentName.takeIf { it.isNotBlank() }
                ?: NoticeFormat.cleanTitle(latest.title, latest.category).ifBlank { latest.title }
            val (deadline, estimated) = deadlineOf(sorted)
            val notSubmitted = sorted.any { it.content.contains("仍未提交") || it.content.contains("未提交") }
            val windowDays = if (deadline != null && latest.timeMillis > 0L) {
                (deadline - latest.timeMillis) / 86_400_000L
            } else {
                0L
            }
            TaskItem(
                key = key,
                kind = kind,
                courseName = course,
                title = name.trim(),
                deadlineMillis = deadline,
                deadlineEstimated = estimated && deadline != null,
                status = statusOf(deadline, now),
                submitState = if (notSubmitted) SubmitState.NOT_SUBMITTED else SubmitState.UNKNOWN,
                reminderCount = list.size,
                latest = latest,
                longWindow = windowDays > 30L,
            )
        }.sortedWith(
            compareBy<TaskItem>(
                { statusRank(it.status) },
                { if (it.longWindow) 1 else 0 },
                { it.deadlineMillis ?: Long.MAX_VALUE },
                { -it.latest.timeMillis },
            )
        )
    }

    private fun statusRank(status: TaskStatus): Int = when (status) {
        TaskStatus.URGENT -> 0
        TaskStatus.OPEN -> 1
        TaskStatus.CLOSED -> 4
        TaskStatus.UNDATED -> 3
    }

    // ---------------------------------------------------------------- 课表异动

    private val weekdayRegex = Regex("""(本周|这周|下周|周|星期|礼拜)\s*([一二三四五六日天])""")
    private val monthDayRegex = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
    private val periodRegex = Regex("""([0-9][0-9.、,，\-]*)\s*节""")

    private fun weekdayValue(ch: Char): Int = when (ch) {
        '一' -> 1
        '二' -> 2
        '三' -> 3
        '四' -> 4
        '五' -> 5
        '六' -> 6
        else -> 7
    }

    /** 「345节」→[3,4,5]；「3.4.5节」→[3,4,5]；「第6-8节」→[6,7,8]。 */
    private fun periodsOf(raw: String): Pair<Int, Int>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        if (text.any { it == '-' || it == '、' || it == ',' || it == '，' || it == '.' }) {
            val parts = text.split('-', '、', ',', '，', '.').mapNotNull { it.trim().toIntOrNull() }
            if (parts.size >= 2) return parts.min() to parts.max()
            if (parts.size == 1) return parts[0] to parts[0]
        }
        if (digits.length >= 2) {
            // 「345节」这种把节次连写的形式，按单个数字拆开
            val nums = digits.mapNotNull { it.digitToIntOrNull() }.filter { it in 1..12 }
            if (nums.size >= 2) return nums.min() to nums.max()
        }
        val single = digits.toIntOrNull() ?: return null
        return if (single in 1..12) single to single else null
    }

    private fun dateOf(text: String, ref: ZonedDateTime): LocalDate? {
        monthDayRegex.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            var date = runCatching { LocalDate.of(ref.year, month, day) }.getOrNull() ?: return@let
            // 通知是 12 月发的「1月3日」多半指下一年
            if (date.isBefore(ref.toLocalDate().minusDays(180))) date = date.plusYears(1)
            return date
        }
        val today = ref.toLocalDate()
        when {
            "今天" in text || "今日" in text -> return today
            "明天" in text || "明日" in text -> return today.plusDays(1)
            "后天" in text -> return today.plusDays(2)
        }
        weekdayRegex.find(text)?.let { m ->
            val prefix = m.groupValues[1]
            val target = weekdayValue(m.groupValues[2][0])
            val current = today.dayOfWeek.value
            return when {
                prefix == "下周" -> {
                    val delta = ((target - current + 7) % 7)
                    today.plusDays(if (delta == 0) 7L else delta.toLong() + 7L)
                }
                else -> today.plusDays(((target - current + 7) % 7).toLong())
            }
        }
        return null
    }

    /**
     * 从公告里抽课表异动。
     *
     * 真实样本里 10 条公告只有 4 条能抽出「周几 + 节次」，所以这里宁缺毋滥：
     * 抽不出关键字段就降级成 SWAP / CANCEL / SYNC_HINT，只做提醒不做改表。
     */
    fun buildChanges(
        notices: List<XuexitongNotice>,
        now: ZonedDateTime = ZonedDateTime.now(ZONE),
    ): List<ScheduleChange> {
        val result = mutableListOf<ScheduleChange>()
        notices.forEach { notice ->
            val text = notice.content
            if (text.isBlank()) return@forEach
            val isChange = Regex("调课|停课|补课|互调|课程调整|上课时间调整|调整到").containsMatchIn(text) ||
                Regex("调课|停课|补课|调整").containsMatchIn(notice.title)
            if (!isChange) return@forEach

            val ref = if (notice.timeMillis > 0L) {
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(notice.timeMillis), ZONE)
            } else {
                now
            }
            val course = firstGroup(courseLine, text)
                .ifBlank { Regex("""《([^》]{2,20})》""").find(notice.title)?.groupValues?.getOrNull(1).orEmpty() }
                .ifBlank { Regex("""《([^》]{2,20})》""").find(text)?.groupValues?.getOrNull(1).orEmpty() }
            // 「地点在科技楼K708」「地点：软件2实验室（K713）」——长分支必须排在短分支前面，
            // 否则「地点」先命中，会把后面的「在」当成地点首字吃进去。
            val place = Regex("""(?:地点在|教室在|地点为|地点|教室|在)\s*([^\s，。；,;]{2,20}?(?:楼|室|馆|中心)[A-Za-z]?\d{0,4})""")
                .find(text)?.groupValues?.getOrNull(1)
                ?.trim()
                ?: Regex("""([A-Za-z]{0,3}\d{2,4}\s*实验室)""").find(text)?.groupValues?.getOrNull(1).orEmpty()

            val excerpt = text.lines().firstOrNull { line ->
                Regex("调课|停课|调整|互调").containsMatchIn(line)
            }?.trim()?.take(60).orEmpty()

            val swap = "互调" in text
            val cancel = !swap && Regex("停课|不上课|暂停上课|取消").containsMatchIn(text) &&
                !Regex("调整到|调到|改为").containsMatchIn(text)
            if (swap) {
                result += ScheduleChange(
                    noticeId = notice.id, courseName = course, kind = ChangeKind.SWAP,
                    toDate = null, toStartPeriod = null, toEndPeriod = null,
                    fromStartPeriod = null, fromEndPeriod = null, place = place,
                    excerpt = excerpt, notice = notice,
                )
                return@forEach
            }
            if (cancel) {
                result += ScheduleChange(
                    noticeId = notice.id, courseName = course, kind = ChangeKind.CANCEL,
                    toDate = null, toStartPeriod = null, toEndPeriod = null,
                    fromStartPeriod = null, fromEndPeriod = null, place = place,
                    excerpt = excerpt, notice = notice,
                )
                return@forEach
            }

            // 「调整到 / 调到 / 改到」之后的片段才是目标
            val movePart = Regex("""(?:调整到|调到|改到|改为|改在|移至|换到)(.*)""")
                .find(text)?.groupValues?.getOrNull(1).orEmpty()
            val target = movePart.ifBlank { text }
            val targetDate = dateOf(target, ref) ?: dateOf(text, ref)
            // 目标节次：优先取「调整到」之后那段；「上午345节」这种带午别的也认
            val targetPeriods = periodRegex.find(target)?.groupValues?.getOrNull(1)?.let(::periodsOf)
                ?: Regex("""(?:上午|下午|晚上)\s*([0-9][0-9.、,，\-]*)\s*节""")
                    .find(target)?.groupValues?.getOrNull(1)?.let(::periodsOf)

            val before = text.substringBefore("调整到").ifBlank { text }
            val fromPeriods = Regex("""(?:上午|下午|晚上)\s*([0-9][0-9.、,，\-]*)\s*节""")
                .find(before)?.groupValues?.getOrNull(1)?.let(::periodsOf)

            val kind = if (targetDate != null && targetPeriods != null) ChangeKind.MOVE else ChangeKind.SYNC_HINT
            result += ScheduleChange(
                noticeId = notice.id,
                courseName = course,
                kind = kind,
                toDate = if (kind == ChangeKind.MOVE) targetDate else null,
                toStartPeriod = if (kind == ChangeKind.MOVE) targetPeriods?.first else null,
                toEndPeriod = if (kind == ChangeKind.MOVE) targetPeriods?.second else null,
                fromStartPeriod = fromPeriods?.first,
                fromEndPeriod = fromPeriods?.second,
                place = place,
                excerpt = excerpt,
                notice = notice,
            )
        }
        return result.sortedByDescending { it.notice.timeMillis }
    }
}
