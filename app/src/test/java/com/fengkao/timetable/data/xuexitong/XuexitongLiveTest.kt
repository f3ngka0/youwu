package com.fengkao.timetable.data.xuexitong

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实账号联调测试。默认跳过；本地验证时通过环境变量提供账号：
 * XUEXITONG_TEST_PHONE / XUEXITONG_TEST_PASSWORD
 */
class XuexitongLiveTest {

    @Test
    fun loginAndFetchRealNotices() {
        val phone = System.getenv("XUEXITONG_TEST_PHONE").orEmpty()
        val password = System.getenv("XUEXITONG_TEST_PASSWORD").orEmpty()
        assumeTrue("未提供真实学习通账号，跳过联调", phone.isNotBlank() && password.isNotBlank())

        val api = XuexitongApi(cookieJar = XuexitongCookieJar())
        val login = api.login(phone, password)
        assertTrue("登录失败: ${login.message}", login.ok)

        val notices = api.fetchNotices(maxPages = 1)
        assertTrue("通知列表为空", notices.isNotEmpty())
        assertTrue("通知缺少稳定 id", notices.all { it.id.isNotBlank() })
        assertTrue("通知时间解析失败", notices.any { it.timeMillis > 0 })
        println(
            "学习通联调：${notices.size} 条通知，未读 ${notices.count { it.serverUnread }} 条，" +
                "首条 id=${notices.first().id} category=${notices.first().category}"
        )
    }
}
