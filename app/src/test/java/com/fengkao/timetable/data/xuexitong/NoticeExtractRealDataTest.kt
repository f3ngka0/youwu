package com.fengkao.timetable.data.xuexitong

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 用「真实抓取的 42 条通知」做端到端回归：`xuexitong-notices.json` 是 2026-10-23
 * 用真实账号从学习通通知中心抓下来的原文，结构与措辞保持原样，
 * 仅把发送者与正文里出现的真实教师姓名替换为同长度的化名。
 *
 * 这里锁的是产品承诺本身：42 条通知 → 6 件仍要办的事。
 * 只要解析规则或去重逻辑退化，这组数字就会变，测试立刻红。
 */
class NoticeExtractRealDataTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.parse("2026-10-23T15:45:00+08:00[Asia/Shanghai]")
        .toInstant().toEpochMilli()

    private fun load(): List<XuexitongNotice> {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("xuexitong-notices.json")) {
            "缺少测试资源 xuexitong-notices.json"
        }.bufferedReader().readText()
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            val title = o.optString("title")
            val content = o.optString("content")
            XuexitongNotice(
                id = o.optString("id"),
                title = title,
                content = content,
                sender = o.optString("sender"),
                timeText = o.optString("timeText"),
                timeMillis = runCatching {
                    ZonedDateTime.parse(
                        o.optString("timeText").replace(' ', 'T') + "+08:00[Asia/Shanghai]"
                    ).toInstant().toEpochMilli()
                }.getOrDefault(0L),
                category = NoticeFormat.classify(title, content),
                attachmentKind = o.optString("attachmentKind"),
                attachmentName = o.optString("attachmentName"),
                attachmentUrl = o.optString("attachmentUrl"),
            )
        }
    }

    @Test
    fun `42条通知压成21件作业考试任务`() {
        val notices = load()
        assertEquals(42, notices.size)
        val tasks = NoticeExtract.buildTasks(notices, now)
        // 27 条作业/考试通知 → 15 组作业 + 6 场考试；其余 15 条公告不产生待办
        assertEquals(21, tasks.size)
        assertEquals(15, tasks.count { it.kind == TaskKind.HOMEWORK })
    }

    @Test
    fun `当前仍可做的作业是4件`() {
        val tasks = NoticeExtract.buildTasks(load(), now)
        val open = tasks.filter {
            it.kind == TaskKind.HOMEWORK &&
                (it.status == TaskStatus.OPEN || it.status == TaskStatus.URGENT) &&
                !it.longWindow
        }
        assertEquals(
            listOf("第一章作业-2026.09", "上机实验作业2", "第五章作业", "第二章作业-2026.09"),
            open.map { it.title },
        )
    }

    @Test
    fun `两门自主考试被识别为长窗口`() {
        val tasks = NoticeExtract.buildTasks(load(), now)
        val long = tasks.filter { it.longWindow && it.status != TaskStatus.CLOSED }
        assertEquals(setOf("安全与急救", "毒品危害与防范"), long.map { it.title }.toSet())
    }

    @Test
    fun `重复提醒合并后不再重复出现`() {
        val tasks = NoticeExtract.buildTasks(load(), now)
        val titles = tasks.filter { it.kind == TaskKind.HOMEWORK }.map { it.title }
        assertEquals("作业标题不应重复", titles.size, titles.toSet().size)
        assertTrue("应存在被合并的多条提醒", tasks.any { it.reminderCount >= 2 })
    }

    @Test
    fun `真实公告里能抽出5条课表异动`() {
        val changes = NoticeExtract.buildChanges(load())
        // 5 条 = 2 条可执行调课 + 1 条互调 + 1 条停课 + 1 条「去教务系统看」
        assertEquals(5, changes.size)
        assertEquals(2, changes.count { it.applicable })
        assertEquals(1, changes.count { it.kind == ChangeKind.SWAP })
        assertEquals(1, changes.count { it.kind == ChangeKind.CANCEL })
    }

    @Test
    fun `抽出的课表异动与人工核对结果一致`() {
        val changes = NoticeExtract.buildChanges(load()).associateBy { it.noticeId }

        val db = changes["6CEC5B6F0E1DF17B5CBA9BE3212C6F40"]!!
        assertEquals(ChangeKind.MOVE, db.kind)
        assertEquals("数据库原理", db.courseName)
        assertEquals("2026-09-23", db.toDate.toString())
        assertEquals(3, db.toStartPeriod)
        assertEquals(5, db.toEndPeriod)
        assertEquals("科技楼K708", db.place)

        val se = changes["936FA1F9C6541B98C025BCF82F289BF7"]!!
        assertEquals(ChangeKind.MOVE, se.kind)
        assertEquals("软件工程", se.courseName)
        assertEquals("2026-10-17", se.toDate.toString())

        val swap = changes["FBF11BAC007A6C91C69ADA2FB71E444E"]!!
        assertEquals(ChangeKind.SWAP, swap.kind)
        assertTrue(!swap.applicable)

        val cancel = changes["351DF7CC4FEC6C38353F11E8A3A36A08"]!!
        assertEquals(ChangeKind.CANCEL, cancel.kind)
        assertTrue(!cancel.applicable)
    }
}
