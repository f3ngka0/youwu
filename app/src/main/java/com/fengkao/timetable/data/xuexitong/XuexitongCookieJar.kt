package com.fengkao.timetable.data.xuexitong

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * 跨子域持久化 CookieJar。
 *
 * 学习通登录发生在 passport2.chaoxing.com，通知接口在 notice.chaoxing.com，
 * OkHttp 默认不保存 Cookie，因此必须保留 domain/path/expiry 并在请求时匹配。
 */
class XuexitongCookieJar(
    initialJson: String = "",
    private val onPersist: (String) -> Unit = {},
) : CookieJar {

    private val cookies = mutableListOf<Cookie>()

    init {
        load(initialJson)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll {
                it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
            }
            this.cookies.add(incoming)
        }
        onPersist(dump())
    }

    @Synchronized
    fun dump(): String {
        val array = JSONArray()
        cookies.forEach { cookie ->
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly)
            )
        }
        return array.toString()
    }

    @Synchronized
    fun load(json: String) {
        cookies.clear()
        if (json.isBlank()) return
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val value = item.optString("value")
                val domain = item.optString("domain")
                if (name.isBlank() || domain.isBlank()) continue
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(item.optString("path").ifBlank { "/" })
                    .expiresAt(item.optLong("expiresAt", Long.MAX_VALUE))
                if (item.optBoolean("hostOnly", true)) builder.hostOnlyDomain(domain)
                else builder.domain(domain)
                if (item.optBoolean("secure", false)) builder.secure()
                if (item.optBoolean("httpOnly", false)) builder.httpOnly()
                cookies.add(builder.build())
            }
        }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        onPersist("[]")
    }
}
