package com.fengkao.timetable.data.xuexitong

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** 学习通通知的纯函数解析：HTML 清洗、分类、截止时间与相对时间。 */
object NoticeFormat {

    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?i)</div\\s*>"), "\n")
            .replace(Regex("(?i)<script[\\s\\S]*?</script>"), " ")
            .replace(Regex("(?i)<style[\\s\\S]*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
        return text
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun classify(title: String, content: String): NoticeCategory {
        val text = "$title\n$content"
        return when {
            Regex("签到|手势签到|位置签到|签到码|扫码签到").containsMatchIn(text) -> NoticeCategory.SIGNIN
            Regex("作业|测验|测试|实验报告|提交|截止|习题|考试|小组任务").containsMatchIn(text) -> NoticeCategory.HOMEWORK
            Regex("公告|通知|调课|停课|补课|讲座|活动|课程安排|课件|资料|附件|安装方法|学习资料").containsMatchIn(text) -> NoticeCategory.ANNOUNCEMENT
            else -> NoticeCategory.OTHER
        }
    }

    private val deadlinePatterns = listOf(
        Regex("""(?:截止|结束)时间[：:\s]*([^\n，。；;]{1,28})"""),
        Regex("""截止(?:时间|日期)?[：:\s]*([^\n，。；;]{1,28})"""),
        Regex("""(?:请)?于\s*([^\n，。；;]{1,24}?)\s*(?:前|之前)"""),
        Regex("""([本今明后]?周[一二三四五六日天]\s*(?:\d{1,2}[:：]\d{2})?)"""),
        Regex("""(\d{1,2}月\d{1,2}日(?:\s*\d{1,2}[:：]\d{2})?)"""),
    )

    fun extractDeadline(title: String, content: String): String? {
        val text = "$title\n$content"
        for (pattern in deadlinePatterns) {
            val raw = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val cleaned = raw.trim(' ', '：', ':', '，', ',', '。', '；', ';')
            if (cleaned.isNotBlank()) return cleaned.take(28)
        }
        return null
    }

    fun parseTime(raw: String, now: ZonedDateTime = ZonedDateTime.now(ZONE)): Long {
        val text = raw.trim()
        if (text.isBlank()) return 0L
        val fullFormats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
        )
        fullFormats.forEach { pattern ->
            runCatching {
                LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.CHINA))
                    .atZone(ZONE)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()?.let { return it }
        }
        val shortFormats = listOf(
            "MM-dd HH:mm",
            "MM/dd HH:mm",
            "M月d日 HH:mm",
            "MM-dd",
            "MM/dd",
            "M月d日",
        )
        shortFormats.forEach { pattern ->
            runCatching {
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern(pattern)
                    .parseDefaulting(ChronoField.YEAR, now.year.toLong())
                    .toFormatter(Locale.CHINA)
                var zoned = LocalDateTime.parse(text, formatter).atZone(ZONE)
                if (zoned.isAfter(now.plusDays(30))) zoned = zoned.minusYears(1)
                zoned.toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
        }
        return 0L
    }

    fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        val diff = now - millis
        if (diff < 0L) return format(millis, "M月d日 HH:mm")
        val minutes = diff / 60_000L
        return when {
            minutes < 1L -> "刚刚"
            minutes < 60L -> "${minutes}分钟前"
            minutes < 24L * 60L -> "${minutes / 60L}小时前"
            minutes < 48L * 60L -> "昨天 " + format(millis, "HH:mm")
            minutes < 7L * 24L * 60L -> "${minutes / (24L * 60L)}天前"
            else -> format(millis, "M月d日")
        }
    }

    fun fetchAge(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return "尚未抓取"
        val minutes = ((now - millis).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1L -> "刚刚抓取"
            minutes < 60L -> "${minutes}分钟前抓取"
            minutes < 24L * 60L -> "${minutes / 60L}小时前抓取"
            else -> "昨天抓取"
        }
    }

    private val bracketTitlePrefix = Regex("""^\s*[\[【]\s*(作业|签到|公告|通知|测验|考试)\s*[\]】]\s*[:：]?\s*""")
    private val colonTitlePrefix = Regex("""^\s*(作业|签到|公告|通知|测验|考试)\s*[:：]\s*""")

    /**
     * 类型标签已在 UI 前置渲染，标题自带的「作业:」「【作业】」前缀属于重复信息；
     * 去掉后可省下 3~4 个汉字的横向空间，也避免窄屏被截断。
     */
    fun cleanTitle(title: String, category: NoticeCategory): String {
        val text = title.trim()
        if (text.isEmpty()) return ""
        val stripped = text
            .replaceFirst(bracketTitlePrefix, "")
            .replaceFirst(colonTitlePrefix, "")
            .trim()
        return stripped.ifBlank { text }
    }

    /** 学习通的作业/签到类通知由系统账号发出，不是老师本人；这类发送者一律不展示。 */
    private val systemSenders = setOf("学习通知", "系统通知", "超星", "学习通", "通知中心", "教务通知")

    /**
     * 通知发送者 → 第三行里的「老师姓名」。
     *
     * 真实数据里作业通知的 creater 是「学习通知」这类系统账号，直接加「老师」会
     * 变成「学习通知老师」；系统账号返回空串，让调用方只保留抓取时间。
     */
    fun senderLabel(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) return ""
        if (name in systemSenders) return ""
        if (name.any { it.isDigit() }) return ""
        if (name.endsWith("老师") || name.endsWith("助教") || name.endsWith("管理员")) return name
        // 太长的多半是课程名或通知标题，不是人名，宁可不显示。
        return if (name.length <= 6) "${name}老师" else ""
    }

    /** 「明科楼713（软件室2）」→「明科楼713」：胶囊里只保留主地点。 */
    fun shortPlace(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val cut = text.indexOfFirst { it == '（' || it == '(' || it == '【' || it == '[' }
        if (cut <= 1) return text
        val head = text.substring(0, cut).trim().trim('·', ',', '，', '、')
        return head.ifBlank { text }
    }

    /**
     * 课程名精简：`WEB应用开发技术（2026春）` → `WEB应用开发技术`。
     * 真实数据里课程名常带年级/班号后缀，胶囊里只需要能认出是哪门课。
     */
    fun shortCourse(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        // 先试括号；没有括号就试「-2026」这类横杠后缀，都不命中就用原名
        val head = shortPlace(text)
        if (head != text) return head
        val m = Regex("""^(.+?)[\s·]*[-—]\s*20\d{2}.*$""").find(text)
        return m?.groupValues?.get(1)?.trim()?.ifBlank { text } ?: text
    }

    /**
     * 正文清洗：
     * - 「课程名称：xxx」一律整行丢弃——左侧马卡龙课程胶囊已经承担了这个信息。
     * - 「作业名称：xxx」在以下任一情况丢弃：标题已含该名，或该名等于附件名（卡片底部
     *   已经渲染 `📎 附件名`）。真实数据 42 条里 20 条带这行，14 条被标题规则吃掉，
     *   剩下 6 条的作业名与附件名逐字符相同，加这条即 100% 覆盖。
     * - 「开始时间：A」/「结束时间：B」是裸字段名各占一行（实测 19.1dp/行），合并成
     *   「2026-10-23 → 2026-10-30」一行，并删掉冒号后的全角空格。
     * 末尾压缩连续空行，让通知卡片更矮。
     */
    fun cleanContent(content: String, title: String = "", attachmentName: String = ""): String {
        if (content.isBlank()) return ""
        val titleKey = title.replace(Regex("\\s"), "")
        val attachKey = attachmentName.replace(Regex("\\s"), "")
        val startRe = Regex("""^\s*开始时间\s*[:：]\s*(.+)$""")
        val endRe = Regex("""^\s*结束时间\s*[:：]\s*(.+)$""")
        val lines = content.lines().map { it.trim() }
        var start = ""
        var end = ""
        val kept = ArrayList<String>(lines.size)
        for (line in lines) {
            if (Regex("""^课程名称\s*[:：]""").containsMatchIn(line)) continue
            val job = Regex("""^作业名称\s*[:：]\s*(.+)$""").find(line)
            if (job != null) {
                val name = job.groupValues[1].replace(Regex("\\s"), "")
                if (name.isNotBlank() &&
                    (titleKey.contains(name) || (attachKey.isNotBlank() && attachKey == name))
                ) continue
            }
            val s = startRe.find(line)
            if (s != null) { start = s.groupValues[1].trim(); continue }
            val e = endRe.find(line)
            if (e != null) { end = e.groupValues[1].trim(); continue }
            kept.add(line)
        }
        val head = when {
            start.isNotBlank() && end.isNotBlank() -> listOf("$start → $end")
            start.isNotBlank() -> listOf(start)
            end.isNotBlank() -> listOf(end)
            else -> emptyList()
        }
        return (head + kept)
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** 在标题与正文中寻找最长命中的课程名；找不到返回空串。 */
    fun matchCourseName(title: String, content: String, courseNames: Collection<String>): String {
        val haystack = (title + " " + content).replace(" ", "").lowercase()
        fun hit(candidate: String) =
            candidate.trim().isNotBlank() &&
                haystack.contains(candidate.replace(" ", "").lowercase())

        courseNames.filter(::hit).maxByOrNull { it.length }?.let { return it }

        // 教务课名常带方向/班级后缀（如「软件工程（卓越班）」），通知里只写主干名。
        return courseNames
            .map { it.substringBefore("（").substringBefore("(").trim() }
            .filter { it.length >= 3 }
            .filter(::hit)
            .maxByOrNull { it.length }
            .orEmpty()
    }

    private fun format(millis: Long, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.CHINA)
            .format(Instant.ofEpochMilli(millis).atZone(ZONE))
}
