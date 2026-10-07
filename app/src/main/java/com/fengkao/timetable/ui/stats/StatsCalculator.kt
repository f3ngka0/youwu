package com.fengkao.timetable.ui.stats

import com.fengkao.timetable.data.model.BusyBlock
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.CourseType
import com.fengkao.timetable.data.model.PeriodTime

/** 梧州学院学费标准：专业关键词（最长匹配）→ 年学费 */
object MajorFee {
    private val TABLE: List<Pair<Int, List<String>>> = listOf(
        3740 to listOf("学前教育", "小学教育"),
        3795 to listOf("茶学"),
        4194 to listOf("哲学"),
        4196 to listOf("商务英语"),
        4200 to listOf("法学", "汉语言文学", "汉语国际教育", "新闻学", "少数民族预科班"),
        4563 to listOf("供应链管理"),
        4578 to listOf("新能源"),
        4590 to listOf("数学与应用数学", "微电子", "人工智能", "计算机科学与技术", "软件工程", "物联网", "数字媒体技术", "数据科学与大数据", "中药学"),
        4593 to listOf("机械电子工程"),
        4594 to listOf("机械设计制造", "材料成型", "电子信息工程", "通信工程", "光电信息", "机器人工程", "制药工程", "资源循环"),
        4596 to listOf("食品科学与工程"),
        4597 to listOf("国际经济与贸易", "电子商务"),
        4600 to listOf("工商管理", "财务管理", "行政管理", "旅游管理", "金融科技", "数字经济"),
    )

    /** 最长关键词匹配：专业名 / 班级名 / 学院名任一包含即可；返回 专业显示名 to 年学费，未识别返回 null */
    fun lookup(vararg texts: String?): Pair<String, Int>? {
        var best: Pair<String, Int>? = null
        var bestLen = 0
        for ((fee, names) in TABLE) for (name in names) for (t in texts) {
            val text = t ?: continue
            if (text.isBlank()) continue
            if (name in text && name.length > bestLen) {
                best = name to fee
                bestLen = name.length
            }
        }
        return best
    }
}

/** 统计口径：按「节」计数；时段按开始节次的上课钟点划分（<12 上午，12-18 下午，≥18 晚间） */
object StatsCalculator {

    const val MORNING = 0
    const val AFTERNOON = 1
    const val EVENING = 2

    data class Bundle(
        val hasData: Boolean,
        val week: Int,
        val weekCourses: List<CourseBlock>,
        // 时段分布（节次）
        val morningLessons: Int,
        val afternoonLessons: Int,
        val eveningLessons: Int,
        // 早八 / 晚课
        val earlyCount: Int,
        val earlyPercent: Int,
        val earlyLevel: String,
        val lateCount: Int,
        val latePercent: Int,
        val lateLevel: String,
        // 连堂
        val maxConsecutive: Int,
        val consecutiveCount: Int,
        // 尴尬空档（间隔恰好 1 节）
        val gapCount: Int,
        val gapMinutes: Int,
        // 每日节数（周一~周日）
        val dailyLessons: List<Int>,
        val deathDay: Int?,   // 0=周一
        val happyDay: Int?,
        // 单双周
        val oddLessons: Int,
        val evenLessons: Int,
        // 周课间总时长（分钟）
        val breakMinutes: Int,
    )

    private fun lessons(c: CourseBlock) = c.endPeriod - c.startPeriod + 1

    private fun periodStartMin(pts: List<PeriodTime>, period: Int): Int {
        val p = pts.getOrNull(period - 1) ?: return 0
        return p.startHour * 60 + p.startMinute
    }

    private fun periodEndMin(pts: List<PeriodTime>, period: Int): Int {
        val p = pts.getOrNull(period - 1) ?: return 0
        return p.endHour * 60 + p.endMinute
    }

    private fun slotOf(pts: List<PeriodTime>, c: CourseBlock): Int {
        val start = periodStartMin(pts, c.startPeriod)
        return when {
            start < 12 * 60 -> MORNING
            start < 18 * 60 -> AFTERNOON
            else -> EVENING
        }
    }

    fun compute(
        courses: List<CourseBlock>,
        week: Int,
        termWeeks: Int,
        pts: List<PeriodTime>,
    ): Bundle {
        val weekCourses = courses.filter { week in it.weeks }
        if (weekCourses.isEmpty()) {
            return Bundle(
                hasData = false, week = week, weekCourses = emptyList(),
                morningLessons = 0, afternoonLessons = 0, eveningLessons = 0,
                earlyCount = 0, earlyPercent = 0, earlyLevel = "无早八",
                lateCount = 0, latePercent = 0, lateLevel = "无晚课",
                maxConsecutive = 0, consecutiveCount = 0,
                gapCount = 0, gapMinutes = 0,
                dailyLessons = List(7) { 0 }, deathDay = null, happyDay = null,
                oddLessons = 0, evenLessons = 0, breakMinutes = 0,
            )
        }

        // 时段 / 早八 / 晚课
        var morning = 0; var afternoon = 0; var evening = 0
        var early = 0; var late = 0
        var totalLessons = 0
        for (c in weekCourses) {
            val n = lessons(c)
            totalLessons += n
            when (slotOf(pts, c)) {
                MORNING -> morning += n
                AFTERNOON -> afternoon += n
                else -> evening += n
            }
            if (c.startPeriod == 1) early++
            if (periodStartMin(pts, c.startPeriod) >= 18 * 60) late++
        }
        fun pct(part: Int) = if (totalLessons == 0) 0 else (part * 100 + totalLessons / 2) / totalLessons
        val earlyPercent = pct(early)
        val earlyLevel = when {
            earlyPercent >= 40 -> "重度受害者"
            earlyPercent >= 20 -> "中度受害者"
            else -> "轻度受害者"
        }
        val latePercent = pct(late)
        val lateLevel = when {
            latePercent >= 25 -> "昼夜颠倒型"
            latePercent >= 10 -> "资深夜猫型"
            latePercent > 0 -> "偶尔熬夜型"
            else -> "从不熬夜"
        }

        // 连堂：单日同课程区间合并后的连续长度
        var maxConsec = 0
        var consecCount = 0
        weekCourses.groupBy { it.dayOfWeek to it.name }.forEach { (_, list) ->
            var curStart = 0; var curEnd = -1
            list.sortedBy { it.startPeriod }.forEach { c ->
                if (curEnd > 0 && c.startPeriod > curEnd) {
                    val len = curEnd - curStart + 1
                    if (len >= 2) consecCount++
                    if (len > maxConsec) maxConsec = len
                    curStart = c.startPeriod
                } else if (curEnd == -1) {
                    curStart = c.startPeriod
                }
                curEnd = maxOf(curEnd, c.endPeriod)
            }
            val len = curEnd - curStart + 1
            if (len >= 2) consecCount++
            if (len > maxConsec) maxConsec = len
        }

        // 尴尬空档：同一天相邻两课间隔恰好 1 节
        var gapCount = 0
        var gapMinutes = 0
        weekCourses.groupBy { it.dayOfWeek }.forEach { (_, list) ->
            val sorted = list.sortedBy { it.startPeriod }
            for (i in 0 until sorted.size - 1) {
                val gap = sorted[i + 1].startPeriod - sorted[i].endPeriod - 1
                if (gap == 1) {
                    gapCount++
                    gapMinutes += periodEndMin(pts, sorted[i].endPeriod + 1) -
                        periodStartMin(pts, sorted[i].endPeriod + 1)
                }
            }
        }

        // 每日节数
        val daily = List(7) { d -> weekCourses.filter { it.dayOfWeek == d + 1 }.sumOf { lessons(it) } }
        val deathDay = daily.indices.filter { daily[it] > 0 }.maxByOrNull { daily[it] }
        val happyDay = if (daily.any { it > 0 }) daily.indices.filter { daily[it] > 0 }.minByOrNull { daily[it] } else null

        // 单双周课时
        fun lessonsInWeek(w: Int): Int = courses.filter { w in it.weeks }.sumOf { lessons(it) }
        var odd = 0; var even = 0
        for (w in 1..termWeeks) {
            val n = lessonsInWeek(w)
            if (w % 2 == 1) odd += n else even += n
        }

        // 周课间累计（相邻课间实际钟点间隔 > 0 分钟）
        var breakMin = 0
        weekCourses.groupBy { it.dayOfWeek }.forEach { (_, list) ->
            val sorted = list.sortedBy { it.startPeriod }
            for (i in 0 until sorted.size - 1) {
                val m = periodStartMin(pts, sorted[i + 1].startPeriod) -
                    periodEndMin(pts, sorted[i].endPeriod)
                if (m > 0) breakMin += m
            }
        }

        return Bundle(
            hasData = true,
            week = week,
            weekCourses = weekCourses,
            morningLessons = morning,
            afternoonLessons = afternoon,
            eveningLessons = evening,
            earlyCount = early,
            earlyPercent = earlyPercent,
            earlyLevel = earlyLevel,
            lateCount = late,
            latePercent = latePercent,
            lateLevel = lateLevel,
            maxConsecutive = maxConsec,
            consecutiveCount = consecCount,
            gapCount = gapCount,
            gapMinutes = gapMinutes,
            dailyLessons = daily,
            deathDay = deathDay,
            happyDay = happyDay,
            oddLessons = odd,
            evenLessons = even,
            breakMinutes = breakMin,
        )
    }

    // ---------------------------------------------------------- 学期进度追踪

    data class SemesterProgress(
        val totalLessons: Int,
        val doneLessons: Int,
        val remainLessons: Int,
        val percent: Int,
        /** 已上课时 ÷ 8 节/天 = 相当于多少天全天满课 */
        val daysEquivalent: Int,
        /** 距考试周（学期最后一周）剩余周数 */
        val weeksToExam: Int,
        /** 考试周前的剩余课时 */
        val remainBeforeExam: Int,
    )

    /** 学期总课时天然包含单双周（逐周累加），已上按 currentWeek 截断 */
    fun semesterProgress(
        courses: List<CourseBlock>,
        currentWeek: Int,
        termWeeks: Int,
    ): SemesterProgress {
        val weekly = (1..termWeeks).map { w -> courses.filter { w in it.weeks }.sumOf { lessons(it) } }
        val total = weekly.sum()
        val cw = currentWeek.coerceIn(1, termWeeks)
        val done = weekly.take(cw).sum()
        val remain = (total - done).coerceAtLeast(0)
        val percent = if (total == 0) 0 else (done * 100 + total / 2) / total
        return SemesterProgress(
            totalLessons = total,
            doneLessons = done,
            remainLessons = remain,
            percent = percent.coerceIn(0, 100),
            daysEquivalent = kotlin.math.floor(done.toFloat() / 8f + 0.5f).toInt(),
            weeksToExam = (termWeeks - cw).coerceAtLeast(0),
            remainBeforeExam = weekly.drop(cw).sum(),
        )
    }

    // ---------------------------------------------------------- 课程类型画像

    data class TypeStat(val type: String, val lessons: Int, val credits: Double)

    data class TypePortrait(
        val items: List<TypeStat>,
        val totalLessons: Int,
        val totalCredits: Double,
        /** 专业课课时（type == MAJOR） */
        val majorLessons: Int,
        /** 水课课时 = 专业课以外的所有课 */
        val softLessons: Int,
        /** 每学分对应课时数 = 总课时 / 总学分，越高学分越难拿 */
        val creditsGold: Double,
    )

    /** 按课名聚合：类型/学分（未设置学分时默认=每周节数，即国内「周学时≈学分」惯例） */
    fun typePortrait(
        courses: List<CourseBlock>,
        overrides: Map<String, com.fengkao.timetable.data.model.CourseMeta> = emptyMap(),
    ): TypePortrait {
        data class Agg(val lessonsPerWeek: Int, val weeks: Int, val type: String, val credits: Double)

        val byName = LinkedHashMap<String, Agg>()
        for (c in courses) {
            val meta = overrides[c.name]
            val perWeek = lessons(c)
            val cur = byName[c.name]
            byName[c.name] = when {
                cur == null -> Agg(
                    perWeek, c.weeks.size,
                    meta?.type ?: CourseType.infer(c.name),
                    meta?.credits?.takeIf { it > 0.0 } ?: perWeek.toDouble(),
                )
                else -> Agg(
                    cur.lessonsPerWeek + perWeek, maxOf(cur.weeks, c.weeks.size),
                    meta?.type ?: cur.type,
                    meta?.credits?.takeIf { it > 0.0 } ?: (cur.credits + perWeek.toDouble()),
                )
            }
        }
        val items = mutableListOf<TypeStat>()
        var totalLessons = 0
        var totalCredits = 0.0
        var major = 0
        var soft = 0
        for ((_, a) in byName) {
            val total = a.lessonsPerWeek * a.weeks
            val idx = items.indexOfFirst { it.type == a.type }
            if (idx < 0) items += TypeStat(a.type, total, a.credits)
            else items[idx] = TypeStat(a.type, items[idx].lessons + total, items[idx].credits + a.credits)
            totalLessons += total
            totalCredits += a.credits
            // 专业课 vs 水课：专业课 = MAJOR；水课 = 专业课以外的所有课
            if (a.type == CourseType.MAJOR) major += total else soft += total
        }
        return TypePortrait(
            items = items.sortedByDescending { it.lessons },
            totalLessons = totalLessons,
            totalCredits = totalCredits,
            majorLessons = major,
            softLessons = soft,
            creditsGold = if (totalCredits > 0.0) totalLessons / totalCredits else 0.0,
        )
    }

    // ---------------------------------------------------------- 作息健康指数

    /** 早八判定阈值：开始钟点早于 08:30 即算「早八」（兼容用户自定义作息表） */
    private const val EARLY_LIMIT = 8 * 60 + 30

    private val DAY_CN = listOf("一", "二", "三", "四", "五", "六", "日")

    private fun clockText(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private fun durationText(min: Int): String = when {
        min <= 0 -> "0 分钟"
        min < 60 -> "$min 分钟"
        else -> {
            val h = min / 60
            val m = min % 60
            if (m == 0) "$h 小时" else "$h 小时 $m 分"
        }
    }

    /** 单天的坐牢分类 */
    const val DAY_FREE = 0   // 无课
    const val DAY_BUSY = 1   // 有课但不构成坐牢
    const val DAY_AM = 2     // 上午坐牢
    const val DAY_PM = 3     // 下午坐牢
    const val DAY_FULL = 4   // 全天坐牢

    data class HealthIndex(
        /** 早餐缺失率 = 有早八课的天数 / 周总天数 × 100% */
        val breakfastMissRate: Int,
        /** 有早八课的天数 */
        val earlyDays: Int,
        /** 参与统计的总天数（5 或 7，跟随「显示周末」设置） */
        val weekDays: Int,
        /** 早餐达人 / 偶尔赶时间 / 早餐绝缘体 */
        val breakfastTag: String,
        /** 本周最晚下课时间（分钟） */
        val latestEndMin: Int?,
        val latestEndText: String,
        /** 最晚那天的星期（0=周一） */
        val latestDay: Int?,
        /** 低风险 / 中风险 / 高风险 */
        val nightRisk: String,
        /** 0 低 1 中 2 高 */
        val nightRiskLevel: Int,
        /** 本周最早上课时间 */
        val earliestStartMin: Int?,
        val earliestStartText: String,
        /** 喘息半天：完整上午或下午无课的数量 */
        val breathHalfDays: Int,
        val totalHalfDays: Int,
        /** 综合健康评分 0-100 */
        val score: Int,
        val level: String,
        val advice: String,
    )

    /**
     * 作息健康指数：早餐缺失率 + 熬夜风险等级 + 喘息半天 → 0-100 健康评分。
     * 评分权重：早餐 35 分、熬夜 35 分、喘息 30 分。
     */
    fun healthIndex(
        courses: List<CourseBlock>,
        week: Int,
        pts: List<PeriodTime>,
        weekDays: Int = 7,
    ): HealthIndex {
        val weekCourses = courses.filter { week in it.weeks }
        val days = weekDays.coerceIn(1, 7)
        val totalHalf = days * 2

        if (weekCourses.isEmpty()) {
            return HealthIndex(
                breakfastMissRate = 0, earlyDays = 0, weekDays = days,
                breakfastTag = "早餐自由", latestEndMin = null, latestEndText = "--:--",
                latestDay = null, nightRisk = "无风险", nightRiskLevel = 0,
                earliestStartMin = null, earliestStartText = "--:--",
                breathHalfDays = totalHalf, totalHalfDays = totalHalf,
                score = 100, level = "假期模式",
                advice = "本周没课，睡到自然醒就是最好的养生。",
            )
        }

        // ---- 早餐缺失率：有早八课的天数 / 周总天数 ----
        val earlyDaySet = weekCourses
            .filter { it.dayOfWeek <= days && periodStartMin(pts, it.startPeriod) < EARLY_LIMIT }
            .map { it.dayOfWeek }
            .toSet()
        val earlyDays = earlyDaySet.size
        val missRate = (earlyDays * 100 + days / 2) / days
        val breakfastTag = when {
            missRate <= 20 -> "早餐达人"
            missRate <= 50 -> "偶尔赶时间"
            else -> "早餐绝缘体"
        }

        // ---- 最晚下课 / 最早开课 ----
        var latestEnd = -1
        var latestDay: Int? = null
        var earliestStart = Int.MAX_VALUE
        for (c in weekCourses) {
            if (c.dayOfWeek > days) continue
            val e = periodEndMin(pts, c.endPeriod)
            if (e > latestEnd) {
                latestEnd = e
                latestDay = c.dayOfWeek - 1
            }
            val s = periodStartMin(pts, c.startPeriod)
            if (s < earliestStart) earliestStart = s
        }
        if (earliestStart == Int.MAX_VALUE) earliestStart = -1

        // 20:00 前低风险；20:00-21:00 中风险；21:00 及以后高风险（含 22:00 后）
        val nightRiskLevel = when {
            latestEnd < 20 * 60 -> 0
            latestEnd < 21 * 60 -> 1
            else -> 2
        }
        val nightRisk = when (nightRiskLevel) {
            0 -> "低风险"
            1 -> "中风险"
            else -> "高风险"
        }

        // ---- 喘息半天：完整上午 / 下午无课 ----
        var breath = 0
        for (d in 1..days) {
            val dayCourses = weekCourses.filter { it.dayOfWeek == d }
            val amHas = dayCourses.any { periodStartMin(pts, it.startPeriod) < 12 * 60 }
            val pmHas = dayCourses.any {
                periodStartMin(pts, it.startPeriod) in (12 * 60) until (18 * 60)
            }
            if (!amHas) breath++
            if (!pmHas) breath++
        }

        // ---- 三项打分 ----
        val breakfastScore = when {
            missRate <= 20 -> 35.0
            missRate <= 50 -> 35.0 - (missRate - 20) * 17.0 / 30.0
            else -> (18.0 - (missRate - 50).coerceAtMost(50) * 18.0 / 50.0).coerceAtLeast(0.0)
        }
        val nightScore = when (nightRiskLevel) {
            0 -> 35.0
            1 -> 26.0
            else -> (18.0 - (latestEnd - 21 * 60).coerceIn(0, 120) * 10.0 / 120.0).coerceAtLeast(6.0)
        }
        // 空闲半天占比达到 50% 即拿满 30 分
        val breathScore = (breath * 60.0 / totalHalf).coerceAtMost(30.0)
        val score = (breakfastScore + nightScore + breathScore).toInt().coerceIn(0, 100)

        val level = when {
            score >= 82 -> "作息标兵"
            score >= 66 -> "还算养生"
            score >= 50 -> "勉强活着"
            score >= 34 -> "亚健康预警"
            else -> "修仙预备役"
        }

        // 找出最拉胯的一项，建议优先补它
        val weakest = when {
            breakfastScore / 35.0 <= nightScore / 35.0 && breakfastScore / 35.0 <= breathScore / 30.0 -> "早餐"
            nightScore / 35.0 <= breathScore / 30.0 -> "晚课收得太晚"
            else -> "几乎没有喘息的半天"
        }
        val advice = when {
            nightRiskLevel == 2 && missRate > 50 ->
                "又早起又晚归，身体不是这么造的。晚上那节课下课后别再刷手机，明早那顿饭真别省。"
            nightRiskLevel == 2 ->
                "最晚要熬到 ${clockText(latestEnd)} 才下课，回寝室早点躺下，把觉补回来比什么都强。"
            missRate > 50 ->
                "一周 $earlyDays 天早八（最早 ${clockText(earliestStart)}），前一晚把早餐备好，边走边啃也比空着强。"
            breath <= days / 2 ->
                "半天几乎排满，一周只有 $breath 个半天能喘气。找个小半天出去透口气，别把自己焊在教室里。"
            score >= 82 ->
                "堪称养生范本：早饭吃得上、${clockText(latestEnd)} 就能收工、还有 $breath 个半天自由支配。"
            else -> "整体还行，主要短板是「$weakest」，稍微挪一挪就更舒服了。"
        }

        return HealthIndex(
            breakfastMissRate = missRate,
            earlyDays = earlyDays,
            weekDays = days,
            breakfastTag = breakfastTag,
            latestEndMin = latestEnd.takeIf { it >= 0 },
            latestEndText = if (latestEnd >= 0) clockText(latestEnd) else "--:--",
            latestDay = latestDay,
            nightRisk = nightRisk,
            nightRiskLevel = nightRiskLevel,
            earliestStartMin = earliestStart.takeIf { it >= 0 },
            earliestStartText = if (earliestStart >= 0) clockText(earliestStart) else "--:--",
            breathHalfDays = breath,
            totalHalfDays = totalHalf,
            score = score,
            level = level,
            advice = advice,
        )
    }

    // ---------------------------------------------------------- 课程坐牢指数

    data class PrisonIndex(
        /** 每周 4 节连堂及以上的段数 */
        val chain4Count: Int,
        /** 单天最长连续上课时长（分钟，已扣除午休） */
        val maxContinuousMinutes: Int,
        val maxContinuousText: String,
        /** 最长那天的星期（0=周一） */
        val maxContinuousDay: Int?,
        /** 最长那一段覆盖的节数 */
        val maxContinuousPeriods: Int,
        /** 上午连续 ≥4 节的天数 */
        val amPrisonDays: Int,
        /** 下午连续 ≥4 节的天数 */
        val pmPrisonDays: Int,
        /** 上午 + 下午各 ≥4 节的天数 */
        val fullPrisonDays: Int,
        /** 逐天坐牢分类，取值见 DAY_* */
        val dayFlags: List<Int>,
        /** 综合坐牢指数 0-100 */
        val score: Int,
        val level: String,
        val roast: String,
    )

    /**
     * 课程坐牢指数：连堂段数 + 最长连续时长 + 坐牢天数 → 0-100。
     * 同一天内重叠或紧邻的课会合并成一段（跨课程也算，人反正坐着没走）。
     * 时长按实际钟点计算，并扣除午休窗口（上午最后一节下课 → 下午第一节上课）。
     */
    fun prisonIndex(
        courses: List<CourseBlock>,
        week: Int,
        pts: List<PeriodTime>,
        weekDays: Int = 7,
    ): PrisonIndex {
        val weekCourses = courses.filter { week in it.weeks }
        val days = weekDays.coerceIn(1, 7)

        if (weekCourses.isEmpty()) {
            return PrisonIndex(
                chain4Count = 0, maxContinuousMinutes = 0, maxContinuousText = "0 分钟",
                maxContinuousDay = null, maxContinuousPeriods = 0,
                amPrisonDays = 0, pmPrisonDays = 0, fullPrisonDays = 0,
                dayFlags = List(days) { DAY_FREE },
                score = 0, level = "自由人",
                roast = "本周没课，铁窗也关不住你，尽情享受放风时光。",
            )
        }

        // 午休窗口由作息表反推，用户改了作息表也能自适应
        val amEnds = pts.filter { it.startHour * 60 + it.startMinute < 12 * 60 }
            .map { it.endHour * 60 + it.endMinute }
        val pmStarts = pts.filter { it.startHour * 60 + it.startMinute in (12 * 60) until (18 * 60) }
            .map { it.startHour * 60 + it.startMinute }
        val lunchStart = amEnds.maxOrNull() ?: (12 * 60)
        val lunchEnd = pmStarts.minOrNull() ?: (14 * 60)
        val hasLunch = (lunchEnd - lunchStart) >= 30

        var chain4 = 0
        var maxMin = 0
        var maxPeriods = 0
        var maxDay: Int? = null
        var amDays = 0
        var pmDays = 0
        var fullDays = 0
        val flags = ArrayList<Int>(days)

        for (d in 1..days) {
            val list = weekCourses.filter { it.dayOfWeek == d }.sortedBy { it.startPeriod }
            if (list.isEmpty()) {
                flags += DAY_FREE
                continue
            }

            // 合并重叠 / 紧邻的区间
            val segs = ArrayList<Pair<Int, Int>>()
            var cs = list[0].startPeriod
            var ce = list[0].endPeriod
            for (i in 1 until list.size) {
                val c = list[i]
                if (c.startPeriod <= ce + 1) {
                    ce = maxOf(ce, c.endPeriod)
                } else {
                    segs += cs to ce
                    cs = c.startPeriod
                    ce = c.endPeriod
                }
            }
            segs += cs to ce

            var dayMaxMin = 0
            var dayMaxPeriods = 0
            segs.forEach { (s, e) ->
                val n = e - s + 1
                if (n >= 4) chain4++
                val start = periodStartMin(pts, s)
                val end = periodEndMin(pts, e)
                var dur = (end - start).coerceAtLeast(0)
                if (hasLunch) {
                    val overlap = minOf(end, lunchEnd) - maxOf(start, lunchStart)
                    if (overlap > 0) dur -= overlap
                }
                if (dur > dayMaxMin) {
                    dayMaxMin = dur
                    dayMaxPeriods = n
                }
            }
            if (dayMaxMin > maxMin) {
                maxMin = dayMaxMin
                maxPeriods = dayMaxPeriods
                maxDay = d - 1
            }

            // 半天内的最长连续节数
            fun longestRunInSlot(from: Int, to: Int): Int {
                var best = 0
                segs.forEach { (s, e) ->
                    var run = 0
                    for (p in s..e) {
                        if (periodStartMin(pts, p) in from until to) {
                            run++
                            if (run > best) best = run
                        } else {
                            run = 0
                        }
                    }
                }
                return best
            }
            val amLock = longestRunInSlot(0, 12 * 60) >= 4
            val pmLock = longestRunInSlot(12 * 60, 18 * 60) >= 4

            if (amLock) amDays++
            if (pmLock) pmDays++
            if (amLock && pmLock) fullDays++
            flags += when {
                amLock && pmLock -> DAY_FULL
                amLock -> DAY_AM
                pmLock -> DAY_PM
                else -> DAY_BUSY
            }
        }

        // 连堂 40% + 时长 35% + 坐牢天数 25%（全天坐牢在天数项计 2 次，权重更高）
        val chainScore = (chain4 * 100.0 / 6.0).coerceAtMost(100.0)
        val durScore = ((maxMin - 90.0) / 150.0 * 100.0).coerceIn(0.0, 100.0)
        val dayScore = ((amDays + pmDays) * 100.0 / 6.0).coerceAtMost(100.0)
        val score = (chainScore * 0.40 + durScore * 0.35 + dayScore * 0.25).toInt().coerceIn(0, 100)

        val level = when {
            score >= 85 -> "终身监禁"
            score >= 70 -> "重刑犯"
            score >= 52 -> "有期徒刑"
            score >= 32 -> "取保候审"
            score >= 15 -> "缓刑观察"
            else -> "自由人"
        }

        val dayText = maxDay?.let { "周${DAY_CN[it]}" } ?: "本周"
        val roast = when {
            score >= 85 ->
                "这已经不是上课，是服刑。$dayText 连着 $maxPeriods 节不让走，建议直接在教室搭个铺。"
            score >= 70 ->
                "$dayText 一天坐穿 $maxPeriods 节（${durationText(maxMin)}），铁窗泪提前流干了。"
            fullDays > 0 ->
                "有 $fullDays 天被上午下午两头封死，记得提前囤好干粮和水，放风时间另算。"
            score >= 52 ->
                "上午下午轮着坐，一周 $chain4 段连堂，屁股和椅子之间已经产生感情了。"
            amDays > 0 && pmDays == 0 ->
                "$amDays 天上午直接焊死在座位上，好在下午还能放放风。"
            pmDays > 0 && amDays == 0 ->
                "$pmDays 天下午连轴转，上午倒是可以睡个懒觉，血赚。"
            score >= 15 ->
                "一周就 $chain4 段连堂，这叫劳逸结合，谈不上坐牢。"
            else ->
                "几乎没有连堂，你自由得像风，教室的门根本关不住你。"
        }

        return PrisonIndex(
            chain4Count = chain4,
            maxContinuousMinutes = maxMin,
            maxContinuousText = durationText(maxMin),
            maxContinuousDay = maxDay,
            maxContinuousPeriods = maxPeriods,
            amPrisonDays = amDays,
            pmPrisonDays = pmDays,
            fullPrisonDays = fullDays,
            dayFlags = flags,
            score = score,
            level = level,
            roast = roast,
        )
    }

    // ---------------------------------------------------------- 空闲时间块分析

    /** ≤30 分钟 */
    const val FRAG = 0
    /** 30 分钟 – 2 小时 */
    const val SHORT = 1
    /** 2 小时以上 */
    const val BIG = 2

    data class FreeBlock(
        val dayOfWeek: Int,   // 1=周一
        val startMin: Int,
        val endMin: Int,
        val category: Int,
        /** 自动标注的推荐用途（大段 / 覆盖中午的块），无标注为 null */
        val tag: String?,
    ) {
        val minutes: Int get() = endMin - startMin
    }

    data class FreeBlockAnalysis(
        /** 分析窗口：全周最早上课钟点 – 最晚下课钟点 */
        val dayWindowStart: Int,
        val dayWindowEnd: Int,
        val blocks: List<FreeBlock>,
        val totalFreeMinutes: Int,
        val avgFreeMinutes: Int,
        val bigCount: Int, val bigMinutes: Int,
        val shortCount: Int, val shortMinutes: Int,
        val fragCount: Int, val fragMinutes: Int,
    )

    /**
     * 空闲时间块分析：
     * 窗口 = 作息表第 1 节开始 → 最末节结束；窗口内除「上课 + 用户事务」外的连续段即空闲块。
     * 分类：≤30 分钟碎片化；30 分钟–2 小时短空闲；2 小时以上完整大段。
     * 推荐标注：与 12–14 点相交 → 适合午休；上午大段 → 适合自习；下午/晚上大段 → 适合外出/社团。
     */
    fun freeBlockAnalysis(
        courses: List<CourseBlock>,
        week: Int,
        pts: List<PeriodTime>,
        weekDays: Int,
        busyBlocks: List<BusyBlock> = emptyList(),
    ): FreeBlockAnalysis {
        val empty = FreeBlockAnalysis(0, 0, emptyList(), 0, 0, 0, 0, 0, 0, 0, 0)
        if (pts.isEmpty()) return empty

        val windowStart = pts.minOf { it.startHour * 60 + it.startMinute }
        val windowEnd = pts.maxOf { it.endHour * 60 + it.endMinute }
        if (windowEnd <= windowStart) return empty
        val days = weekDays.coerceIn(1, 7)
        val weekCourses = courses.filter { week in it.weeks }

        // 某天的占用区间（课程按节换算成钟点 + 用户事务），合并重叠/紧邻
        fun mergedBusy(d: Int): List<Pair<Int, Int>> {
            val intervals = weekCourses
                .filter { it.dayOfWeek == d }
                .map { periodStartMin(pts, it.startPeriod) to periodEndMin(pts, it.endPeriod) } +
                busyBlocks.filter { it.dayOfWeek == d }.map { it.startMin to it.endMin }
            val sorted = intervals
                .map { (s, e) -> maxOf(s, windowStart) to minOf(e, windowEnd) }
                .filter { (s, e) -> e > s }
                .sortedBy { it.first }
            val out = mutableListOf<Pair<Int, Int>>()
            for ((s, e) in sorted) {
                val last = out.lastOrNull()
                if (last != null && s <= last.second) {
                    out[out.size - 1] = last.first to maxOf(last.second, e)
                } else {
                    out += s to e
                }
            }
            return out
        }

        val blocks = mutableListOf<FreeBlock>()
        for (d in 1..days) {
            var cursor = windowStart
            for ((s, e) in mergedBusy(d)) {
                if (s > cursor) blocks += mkBlock(d, cursor, s)
                cursor = maxOf(cursor, e)
                if (cursor >= windowEnd) break
            }
            if (cursor < windowEnd) blocks += mkBlock(d, cursor, windowEnd)
        }

        val total = blocks.sumOf { it.minutes }
        var bigC = 0; var bigM = 0
        var shortC = 0; var shortM = 0
        var fragC = 0; var fragM = 0
        blocks.forEach { b ->
            when (b.category) {
                BIG -> { bigC++; bigM += b.minutes }
                SHORT -> { shortC++; shortM += b.minutes }
                else -> { fragC++; fragM += b.minutes }
            }
        }
        return FreeBlockAnalysis(
            dayWindowStart = windowStart,
            dayWindowEnd = windowEnd,
            blocks = blocks,
            totalFreeMinutes = total,
            avgFreeMinutes = total / days,
            bigCount = bigC, bigMinutes = bigM,
            shortCount = shortC, shortMinutes = shortM,
            fragCount = fragC, fragMinutes = fragM,
        )
    }

    /** 分类 + 推荐标注 */
    private fun mkBlock(day: Int, startMin: Int, endMin: Int): FreeBlock {
        val minutes = endMin - startMin
        val category = when {
            minutes > 120 -> BIG
            minutes > 30 -> SHORT
            else -> FRAG
        }
        val noonStart = 12 * 60
        val noonEnd = 14 * 60
        val tag = when {
            // 与中午 12–14 点相交（优先级最高，即使是大段也先提示午休）
            startMin < noonEnd && endMin > noonStart -> "适合午休"
            category == BIG && endMin <= noonStart -> "适合自习"
            category == BIG && startMin >= noonStart -> "适合外出"
            else -> null
        }
        return FreeBlock(day, startMin, endMin, category, tag)
    }
}
