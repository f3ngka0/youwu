package com.fengkao.timetable.data.xuexitong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 用真实抓取到的 42 条学习通通知做回归。
 *
 * 这些字符串不是编的：全部来自 2026-10-23 用真实账号抓的 notices.json，
 * 解析规则一旦退化，这里会立刻红。
 */
class NoticeExtractTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 真实通知：一条正文 + 附件类型 + 发送时间。 */
    private fun notice(
        id: String,
        title: String,
        content: String,
        kind: String = "",
        attachment: String = "",
        url: String = "",
        sentAt: String = "2026-10-01 08:00:00",
    ): XuexitongNotice {
        val millis = ZonedDateTime.parse(
            sentAt.replace(' ', 'T') + "+08:00[Asia/Shanghai]"
        ).toInstant().toEpochMilli()
        return XuexitongNotice(
            id = id,
            title = title,
            content = content,
            timeText = sentAt,
            timeMillis = millis,
            category = NoticeFormat.classify(title, content),
            attachmentKind = kind,
            attachmentName = attachment,
            attachmentUrl = url,
        )
    }

    // ------------------------------------------------------------ 截止时间抽取

    @Test
    fun `作业正文的结束时间被识别且不标记为估算`() {
        val n = notice(
            id = "a1",
            title = "作业通知",
            content = "课程名称：数据结构\n作业名称：第一章作业\n结束时间：2026-10-25 23:00",
            kind = "work",
            url = "https://mooc1.chaoxing.com/work?taskrefId=54288927",
        )
        val item = NoticeExtract.buildTasks(listOf(n), now = 0L).single()
        assertEquals(TaskKind.HOMEWORK, item.kind)
        assertEquals("第一章作业", item.title)
        assertEquals("数据结构", item.courseName)
        assertNotNull(item.deadlineMillis)
        assertTrue("明确字段不应标记为估算", !item.deadlineEstimated)
    }

    @Test
    fun `剩余小时提醒反推截止时间并标记为估算`() {
        // 真实原文：还有 39 小时 57 分就要结束；发送于 2026-06-27 08:02:34
        val n = notice(
            id = "a2",
            title = "作业督促通知",
            content = "课程名称：数据结构\n作业名称：第9、10章作业---查找和排序\n你的作业还有 39 小时 57 分就要结束",
            kind = "work",
            url = "https://mooc1.chaoxing.com/work?taskrefId=54288927",
            sentAt = "2026-06-27 08:02:34",
        )
        val item = NoticeExtract.buildTasks(listOf(n), now = 0L).single()
        assertTrue(item.deadlineEstimated)
        val actual = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(item.deadlineMillis!!), zone
        )
        // 实测反推误差 0.6 分钟，允许 2 分钟内浮动
        assertEquals("2026-06-28 23:59", actual.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
    }

    @Test
    fun `考试区间取结束时间`() {
        val n = notice(
            id = "a3",
            title = "考试通知",
            content = "试卷名称：第四-第五章测试\n考试时间：07-02 00:37:21 至 07-03 12:59:59\n答题时长：150分钟",
            kind = "exam",
            sentAt = "2026-06-30 10:00:00",
        )
        val item = NoticeExtract.buildTasks(listOf(n), now = 0L).single()
        assertEquals(TaskKind.EXAM, item.kind)
        assertEquals("第四-第五章测试", item.title)
        val actual = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(item.deadlineMillis!!), zone
        )
        assertEquals("2026-07-03 12:59", actual.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
    }

    // ------------------------------------------------------------ 去重

    @Test
    fun `同一 taskrefId 的三条通知合并成一件待办`() {
        val url = "https://mooc1.chaoxing.com/work?taskrefId=54288927"
        val list = listOf(
            notice("1", "作业通知", "课程名称：数据结构\n作业名称：第9、10章作业\n结束时间：2026-06-28 23:59", "work", url = url),
            notice("2", "作业督促通知", "作业名称：第9、10章作业\n你的作业还有 48 小时就要结束", "work", url = url, sentAt = "2026-06-26 23:59:00"),
            notice("3", "作业督促通知", "作业名称：第9、10章作业\n你的作业还有 39 小时 57 分就要结束", "work", url = url, sentAt = "2026-06-27 08:02:34"),
        )
        val tasks = NoticeExtract.buildTasks(list, now = 0L)
        assertEquals(1, tasks.size)
        assertEquals(3, tasks.single().reminderCount)
        // 明确字段优先，不该被后面的估算覆盖
        assertTrue(!tasks.single().deadlineEstimated)
    }

    @Test
    fun `没有 taskrefId 时按课程名加作业名合并`() {
        val list = listOf(
            notice("1", "作业通知", "课程名称：大学英语2\n作业名称：Unit 1 口语作业\n结束时间：2026-10-09 23:59", "work"),
            notice("2", "作业提醒", "课程名称：大学英语2\n作业名称：Unit 1 口语作业\n结束时间：2026-10-09 23:59", "work"),
        )
        assertEquals(1, NoticeExtract.buildTasks(list, now = 0L).size)
    }

    @Test
    fun `公告与云盘文件不会变成待办`() {
        val list = listOf(
            notice("p1", "《软件工程》调课通知", "下周三的课程调整到本周六，地点K713"),
            notice("p2", "st2023.sql", "", kind = "file", attachment = "st2023.sql"),
        )
        assertTrue(NoticeExtract.buildTasks(list, now = 0L).isEmpty())
    }

    // ------------------------------------------------------------ 状态与排序

    @Test
    fun `未提交只在原文写明时才标记`() {
        val explicit = notice(
            "s1", "作业督促通知",
            "课程名称：中华茶文化基础与实践\n作业名称：第四章作业\n你的作业仍未提交，请尽快作答",
            "work", url = "https://x/work?taskrefId=99",
        )
        val silent = notice(
            "s2", "作业通知",
            "课程名称：数据结构\n作业名称：第五章作业\n结束时间：2026-10-30 11:48",
            "work", url = "https://x/work?taskrefId=100",
        )
        val tasks = NoticeExtract.buildTasks(listOf(explicit, silent), now = 0L)
        assertEquals(SubmitState.NOT_SUBMITTED, tasks.first { it.key.endsWith("99") }.submitState)
        assertEquals(SubmitState.UNKNOWN, tasks.first { it.key.endsWith("100") }.submitState)
    }

    @Test
    fun `已截止排到最后`() {
        val now = ZonedDateTime.parse("2026-10-23T15:45:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        val open = notice("o", "作业通知", "课程名称：A\n作业名称：还开着\n结束时间：2026-10-25 23:00", "work", url = "u?taskrefId=1")
        val closed = notice("c", "作业通知", "课程名称：B\n作业名称：已关闭\n结束时间：2026-10-01 23:00", "work", url = "u?taskrefId=2")
        val tasks = NoticeExtract.buildTasks(listOf(closed, open), now = now)
        assertEquals(TaskStatus.OPEN, tasks[0].status)
        assertEquals(TaskStatus.CLOSED, tasks[1].status)
    }

    @Test
    fun `24小时内截止标为紧急`() {
        val now = ZonedDateTime.parse("2026-10-23T15:45:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        val n = notice("u", "作业通知", "课程名称：A\n作业名称：明天交\n结束时间：2026-10-24 12:00", "work", url = "u?taskrefId=3")
        assertEquals(TaskStatus.URGENT, NoticeExtract.buildTasks(listOf(n), now).single().status)
    }

    @Test
    fun `长期开放的自助考试标记为长窗口`() {
        val n = notice(
            "lw", "考试通知",
            "课程名称：安全与急救\n考试名称：安全与急救\n考试时间：09-08 00:00 至 2027-01-04 23:59\n考试形式：自主考试",
            "exam", url = "u?taskrefId=4", sentAt = "2026-09-08 00:00:00",
        )
        val item = NoticeExtract.buildTasks(listOf(n), now = 0L).single()
        assertTrue("跨 118 天的自主考试应标记长窗口", item.longWindow)
    }

    // ------------------------------------------------------------ 课表异动

    /** 真实公告：《数据库原理》调课通知（2026-09-22 21:08），明天=9月23日。 */
    @Test
    fun `明确的新时间与地点抽出可执行调课`() {
        val n = notice(
            "c1", "《数据库原理》调课通知",
            "因明天下午（周三，9月23日）有会议，经咨询，明天上午345节的课程任课老师因出差停课，" +
                "因此决定将明天下午678节的《数据库原理》课程调整到明天上午345节，地点在科技楼K708",
            sentAt = "2026-09-22 21:08:23",
        )
        val change = NoticeExtract.buildChanges(listOf(n)).single()
        assertEquals(ChangeKind.MOVE, change.kind)
        assertEquals("数据库原理", change.courseName)
        assertEquals("2026-09-23", change.toDate.toString())
        assertEquals(3, change.toStartPeriod)
        assertEquals(5, change.toEndPeriod)
        assertEquals("科技楼K708", change.place)
        assertTrue(change.applicable)
    }

    /** 真实公告：《软件工程》调课通知（2026-10-15 16:25），下周三 10月21日 → 本周六 10月17日。 */
    @Test
    fun `软件工程的跨周到本周六`() {
        val n = notice(
            "c2", "《软件工程》调课通知",
            "下周三10月21日上午的课程调整到本周六10月17日上午3.4.5节，地点软件2实验室（K713），请相互通知",
            sentAt = "2026-10-15 16:25:36",
        )
        val change = NoticeExtract.buildChanges(listOf(n)).single()
        assertEquals(ChangeKind.MOVE, change.kind)
        assertEquals("2026-10-17", change.toDate.toString())
        assertEquals(3, change.toStartPeriod)
        assertEquals(5, change.toEndPeriod)
        assertTrue(change.applicable)
    }

    /** 真实公告：两门课互调，单边解析会改错课表，必须降级。 */
    @Test
    fun `互调不给改课表入口`() {
        val n = notice(
            "c3", "本周《数据库原理》调课通知",
            "因要参加会议，本周三（第六周）的数据库原理课程和周五的操作系统课程互调，" +
                "调整后，周三下午由陆行知老师上《操作系统》课程，周五上午345节上《数据库原理》课程",
            sentAt = "2026-10-13 06:43:13",
        )
        val change = NoticeExtract.buildChanges(listOf(n)).single()
        assertEquals(ChangeKind.SWAP, change.kind)
        assertTrue("互调不能自动改表", !change.applicable)
        assertNull(change.toDate)
    }

    /** 真实公告：「本次课程停课，上课时间另行通知」。 */
    @Test
    fun `停课只做提醒`() {
        val n = notice(
            "c4", "《软件工程》课程紧急调整通知",
            "按原通知今天的课程4.5节上课，因会议时长较长，按原计划无法结束，因此本次课程停课，上课时间另行通知，请相互转告",
            sentAt = "2026-09-09 09:31:02",
        )
        val change = NoticeExtract.buildChanges(listOf(n)).single()
        assertEquals(ChangeKind.CANCEL, change.kind)
        assertTrue(!change.applicable)
    }

    /** 真实公告：只说「老师已经在教务系统上调课」「更新课表后查看」。 */
    @Test
    fun `无可靠字段时降级为同步提示`() {
        val n = notice(
            "c5", "关于第二周形策课调整的通知",
            "因老师被学校外派参加9月13-18日的培训，我们第二周的形策课不能按时上课。" +
                "老师已经在教务系统上调课，您也可以更新课表后查看最新上课时间、地点。",
            sentAt = "2026-09-11 11:10:04",
        )
        val change = NoticeExtract.buildChanges(listOf(n)).single()
        assertEquals(ChangeKind.SYNC_HINT, change.kind)
        assertTrue(!change.applicable)
    }

    @Test
    fun `普通公告不会被当成课表异动`() {
        val n = notice("n1", "关于同学们请假流程的提醒", "请假请提前联系辅导员并在教务系统提交申请")
        assertTrue(NoticeExtract.buildChanges(listOf(n)).isEmpty())
    }

    @Test
    fun `节次写法兼容345与3点4点5节`() {
        val compact = notice(
            "e1", "《数据库原理》调课通知",
            "明天下午678节的《数据库原理》课程调整到明天上午345节，地点在科技楼K708",
            sentAt = "2026-09-22 21:08:23",
        )
        val dotted = notice(
            "e2", "《软件工程》调课通知",
            "下周三10月21日上午的课程调整到本周六10月17日上午3.4.5节，地点软件2实验室（K713）",
            sentAt = "2026-10-15 16:25:36",
        )
        val a = NoticeExtract.buildChanges(listOf(compact)).single()
        val b = NoticeExtract.buildChanges(listOf(dotted)).single()
        assertEquals(3 to 5, a.toStartPeriod!! to a.toEndPeriod!!)
        assertEquals(3 to 5, b.toStartPeriod!! to b.toEndPeriod!!)
        assertEquals(6 to 8, a.fromStartPeriod!! to a.fromEndPeriod!!)
    }
}
