package com.fengkao.timetable.data.xuexitong

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XuexitongApiTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: XuexitongCookieJar
    private lateinit var api: XuexitongApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        jar = XuexitongCookieJar()
        api = XuexitongApi(
            passportBase = server.url("/").toString().trimEnd('/'),
            noticeBase = server.url("/").toString().trimEnd('/'),
            cookieJar = jar,
            client = OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginPostsEncryptedCredentialsAndPersistsCookies() {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "_uid=123; Domain=chaoxing.com; Path=/")
                .setBody("""{"status":true}""")
        )

        val result = api.login("13800138000", "test1234")
        assertTrue(result.ok)

        val request = server.takeRequest()
        assertEquals("/fanyalogin", request.path)
        assertEquals("POST", request.method)
        val params = formParams(request.body.readUtf8())
        assertEquals("-1", params["fid"])
        assertEquals(XuexitongCrypto.encrypt("13800138000"), params["uname"])
        assertEquals(XuexitongCrypto.encrypt("test1234"), params["password"])
        assertEquals("0", params["doubleFactorLogin"])
    }

    private fun formParams(body: String): Map<String, String> =
        body.split("&").filter { it.isNotBlank() }.associate { pair ->
            val parts = pair.split("=", limit = 2)
            java.net.URLDecoder.decode(parts[0], "UTF-8") to
                java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }

    @Test
    fun loginFailureKeepsServerMessage() {
        server.enqueue(MockResponse().setBody("""{"status":false,"msg2":"用户名或密码错误"}"""))
        val result = api.login("13800138000", "wrong")
        assertFalse(result.ok)
        assertEquals("用户名或密码错误", result.message)
    }

    @Test
    fun fetchNoticesParsesRealWorldFields() {
        server.enqueue(
            MockResponse().setBody(
                """
                {"status":true,"notices":{"lastPage":true,"lastGetId":"cursor","list":[
                  {"idCode":"ID-UNREAD","uuid":"UUID-1","title":"作业:《软件工程》第三章作业",
                   "content":"<p>课程名称：软件工程</p><p>结束时间：2026-11-01 23:00</p>",
                   "createrName":"莫知远","completeTime":"2026-10-22 17:15:24","isread":0},
                  {"idCode":"ID-READ","uuid":"UUID-2","title":"《数据库原理》调课通知",
                   "content":"本周三的课程调整到本周六","createrName":"黄本立",
                   "completeTime":"2026-10-21 12:12:14","isread":1}
                ]}}
                """.trimIndent()
            )
        )

        val notices = api.fetchNotices(maxPages = 1)
        assertEquals(2, notices.size)
        val unread = notices.first { it.id == "ID-UNREAD" }
        assertEquals("作业:《软件工程》第三章作业", unread.title)
        assertEquals("课程名称：软件工程\n结束时间：2026-11-01 23:00", unread.content)
        assertEquals("莫知远", unread.sender)
        assertTrue(unread.serverUnread)
        assertEquals(NoticeCategory.HOMEWORK, unread.category)
        assertEquals("2026-11-01 23:00", unread.deadline)
        assertTrue(unread.timeMillis > 0)

        val read = notices.first { it.id == "ID-READ" }
        assertFalse(read.serverUnread)

        val request = server.takeRequest()
        assertEquals("/pc/notice/getNoticeList", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("type=0"))
        assertTrue(body.contains("lastValue="))
    }

    @Test
    fun fetchNoticesUsesUuidWhenIdCodeMissing() {
        server.enqueue(
            MockResponse().setBody(
                """{"status":true,"notices":{"lastPage":true,"list":[
                   {"uuid":"ONLY-UUID","title":"通知","content":"正文","completeTime":"2026-10-22 10:00"}
                ]}}"""
            )
        )
        val notices = api.fetchNotices(maxPages = 1)
        assertEquals("ONLY-UUID", notices.single().id)
    }

    @Test(expected = XuexitongSessionExpired::class)
    fun loginRedirectHtmlBecomesSessionExpired() {
        server.enqueue(MockResponse().setBody("<html><title>用户登录</title></html>"))
        api.fetchNotices(maxPages = 1)
    }

    @Test
    fun fetchNoticesParsesWorkAttachment() {
        server.enqueue(
            MockResponse().setBody(
                """
                {"status":true,"notices":{"lastPage":true,"list":[
                  {"idCode":"WORK-1","title":"作业督促通知","content":"课程名称：软件工程",
                   "createrName":"学习通知","completeTime":"2026-10-22 17:15:24","isread":0,
                   "attachment":"[{\"attachmentType\":25,\"att_web\":{\"title\":\"第三章作业\",\"url\":\"https://example.com/work\",\"examOrWork\":\"work\"}}]"}
                ]}}
                """.trimIndent()
            )
        )
        val notice = api.fetchNotices(maxPages = 1).single()
        assertEquals("第三章作业", notice.attachmentName)
        assertEquals("https://example.com/work", notice.attachmentUrl)
        assertEquals("work", notice.attachmentKind)
    }

    @Test
    fun fetchNoticesParsesCloudDiskAttachment() {
        server.enqueue(
            MockResponse().setBody(
                """
                {"status":true,"notices":{"lastPage":true,"list":[
                  {"idCode":"FILE-1","title":"课件已上传","content":"详见附件",
                   "completeTime":"2026-10-22 10:00","isread":1,
                   "attachment":"[{\"attachmentType\":18,\"att_clouddisk\":{\"name\":\"第四讲.pdf\",\"fileSize\":\"4.2MB\",\"downPath\":\"https://example.com/file\"}}]"}
                ]}}
                """.trimIndent()
            )
        )
        val notice = api.fetchNotices(maxPages = 1).single()
        assertEquals("第四讲.pdf", notice.attachmentName)
        assertEquals("4.2MB", notice.attachmentSize)
        assertEquals("file", notice.attachmentKind)
    }

    @Test
    fun emptyNoticeListIsSuccessful() {
        server.enqueue(
            MockResponse().setBody("""{"status":true,"notices":{"lastPage":true,"list":[]}}""")
        )
        assertTrue(api.fetchNotices(maxPages = 1).isEmpty())
    }
}
