package com.fengkao.timetable.data.xuexitong

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XuexitongCookieJarTest {

    private val passport = "https://passport2.chaoxing.com/".toHttpUrl()
    private val notice = "https://notice.chaoxing.com/pc/notice/myNotice".toHttpUrl()

    @Test
    fun domainCookieIsSentToSiblingSubdomainAndSurvivesReload() {
        val jar = XuexitongCookieJar()
        jar.saveFromResponse(
            passport,
            listOf(
                Cookie.Builder()
                    .name("_uid")
                    .value("123")
                    .domain("chaoxing.com")
                    .path("/")
                    .build()
            ),
        )
        assertEquals("123", jar.loadForRequest(notice).single().value)

        val restored = XuexitongCookieJar(jar.dump())
        assertEquals("123", restored.loadForRequest(notice).single().value)
    }

    @Test
    fun hostOnlyCookieDoesNotLeakToSiblingSubdomain() {
        val jar = XuexitongCookieJar()
        jar.saveFromResponse(
            passport,
            listOf(
                Cookie.Builder()
                    .name("JSESSIONID")
                    .value("abc")
                    .hostOnlyDomain("passport2.chaoxing.com")
                    .path("/")
                    .build()
            ),
        )
        assertTrue(jar.loadForRequest(passport).isNotEmpty())
        assertTrue(jar.loadForRequest(notice).isEmpty())
    }

    @Test
    fun expiredCookiesAreDropped() {
        val jar = XuexitongCookieJar()
        jar.saveFromResponse(
            passport,
            listOf(
                Cookie.Builder()
                    .name("old")
                    .value("1")
                    .domain("chaoxing.com")
                    .path("/")
                    .expiresAt(System.currentTimeMillis() - 1_000L)
                    .build()
            ),
        )
        assertTrue(jar.loadForRequest(notice).isEmpty())
    }
}
