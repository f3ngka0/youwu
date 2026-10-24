package com.fengkao.timetable.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Release 正文的 Markdown 清洗与版本号比较。 */
class UpdateNotesTest {

    @Test
    fun `cleans markdown markers`() {
        val raw = """
            # 有梧 1.2.0
            这一版新增了通知中心。

            ## 新增：学习通通知中心
            - **待办**：作业考试自动列入
            * `POST_NOTIFICATIONS` 权限
            - 参考 [Xuexitong-mcp](https://github.com/DanMo661/Xuexitong-mcp) 的实现
        """.trimIndent()
        val out = cleanNotes(raw)
        assertFalse(out.contains("**"))
        assertFalse(out.contains("#"))
        assertFalse(out.contains("]("))
        assertFalse(out.contains("`"))
        assertEquals(
            listOf(
                "有梧 1.2.0",
                "这一版新增了通知中心。",
                "新增：学习通通知中心",
                "· 待办：作业考试自动列入",
                "· POST_NOTIFICATIONS 权限",
                "· 参考 Xuexitong-mcp 的实现",
            ),
            out.lines(),
        )
    }

    @Test
    fun `version compare`() {
        assert(com.fengkao.timetable.data.update.UpdateChecker.isNewer("1.3", "1.2.9"))
        assert(com.fengkao.timetable.data.update.UpdateChecker.isNewer("1.2.1", "1.2.0"))
        assert(!com.fengkao.timetable.data.update.UpdateChecker.isNewer("1.2.0", "1.2.0"))
        assertEquals("1.2.0", com.fengkao.timetable.data.update.UpdateChecker.normalizeVersion("v1.2.0"))
    }
}
