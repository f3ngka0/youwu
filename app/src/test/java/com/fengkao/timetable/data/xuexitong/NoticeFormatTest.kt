package com.fengkao.timetable.data.xuexitong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NoticeFormatTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun stripHtmlKeepsReadableParagraphs() {
        val raw = "<p>课程名称：软件工程</p><p>作业名称：第三章<br>请于本周五前提交</p>"
        assertEquals(
            "课程名称：软件工程\n作业名称：第三章\n请于本周五前提交",
            NoticeFormat.stripHtml(raw),
        )
    }

    @Test
    fun stripHtmlNormalizesCarriageReturns() {
        assertEquals("课程名称：软件工程\n作业名称：第四章作业", NoticeFormat.stripHtml("课程名称：软件工程\r作业名称：第四章作业"))
    }

    @Test
    fun classifiesHomeworkSigninAndAnnouncement() {
        assertEquals(
            NoticeCategory.HOMEWORK,
            NoticeFormat.classify("作业:《软件工程》第三章", "结束时间：2026-11-01 23:00"),
        )
        assertEquals(
            NoticeCategory.SIGNIN,
            NoticeFormat.classify("签到通知", "请打开学习通完成手势签到"),
        )
        assertEquals(
            NoticeCategory.ANNOUNCEMENT,
            NoticeFormat.classify("《软件工程》调课通知", "下周三的课程调整到本周六"),
        )
        assertEquals(
            NoticeCategory.ANNOUNCEMENT,
            NoticeFormat.classify("课程教学使用的 OpenGauss 安装方法", "详见附件，请同学们按建议方法安装"),
        )
    }

    @Test
    fun extractsDeadlineFromCommonTemplates() {
        assertEquals("本周五 23:59", NoticeFormat.extractDeadline("作业通知", "截止：本周五 23:59"))
        assertEquals("本周五", NoticeFormat.extractDeadline("作业通知", "请于本周五前提交第三章报告"))
        assertEquals("2026-11-01 23:00", NoticeFormat.extractDeadline("作业", "结束时间：2026-11-01 23:00"))
        assertNull(NoticeFormat.extractDeadline("普通通知", "请同学们互相转告"))
    }

    @Test
    fun parsesRealCompleteTime() {
        val millis = NoticeFormat.parseTime("2026-10-22 17:15:24")
        val text = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).toString()
        assertEquals("2026-10-22T17:15:24+08:00[Asia/Shanghai]", text)
    }

    @Test
    fun parsesShortTimeWithCurrentYear() {
        val now = ZonedDateTime.of(2026, 10, 22, 20, 0, 0, 0, zone)
        val millis = NoticeFormat.parseTime("10-21 12:12", now)
        val text = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).toString()
        assertEquals("2026-10-21T12:12+08:00[Asia/Shanghai]", text)
    }

    @Test
    fun matchesLongestCourseName() {
        val name = NoticeFormat.matchCourseName(
            "作业:《WEB应用开发》第四讲",
            "课程名称：WEB应用开发\n作业名称：实验报告",
            listOf("软件工程", "WEB应用开发", "计算机网络"),
        )
        assertEquals("WEB应用开发", name)
    }

    @Test
    fun relativeTimeIsHumanReadable() {
        val now = 1_800_000_000_000L
        assertTrue(NoticeFormat.relativeTime(now - 30_000L, now).endsWith("刚刚"))
        assertEquals("10分钟前", NoticeFormat.relativeTime(now - 10 * 60_000L, now))
        assertEquals("2小时前", NoticeFormat.relativeTime(now - 2 * 60 * 60_000L, now))
    }
}
