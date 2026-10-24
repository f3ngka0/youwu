package com.fengkao.timetable.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一个 GitHub Release 里我们关心的字段。 */
data class UpdateInfo(
    /** 去掉前缀 v 的版本号，如 1.2.0 */
    val version: String,
    /** Release 标题，取不到就退回 tag */
    val title: String,
    /** Release 说明正文（Markdown 原文） */
    val notes: String,
    /** 浏览器可打开的发布页 */
    val pageUrl: String,
    /** 直连 APK（有这个就优先给直接下载） */
    val apkUrl: String?,
    /** 是否比当前安装的版本新 */
    val newer: Boolean,
)

/**
 * 检查更新：读 GitHub 的 releases/latest。
 *
 * 用 `/releases/latest` 而不是列表接口 —— 它本身就排除了 draft 和 prerelease，
 * 不会把测试版推给普通用户。
 */
class UpdateChecker(
    private val apiBase: String = "https://api.github.com",
    private val owner: String = "f3ngka0",
    private val repo: String = "youwu",
    private val currentVersion: String,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$apiBase/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "youwu-android/$currentVersion")
                .build()
            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        when (response.code) {
                            403, 429 -> "请求太频繁，稍后再试"
                            404 -> "还没有发布过版本"
                            else -> "服务器返回 ${response.code}"
                        }
                    )
                }
                response.body?.string().orEmpty()
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val version = normalizeVersion(tag)
            if (version.isBlank()) throw IOException("版本号无法识别")
            UpdateInfo(
                version = version,
                title = json.optString("name").ifBlank { "有梧 $version" },
                notes = json.optString("body").trim(),
                pageUrl = json.optString("html_url").ifBlank { "https://github.com/$owner/$repo/releases" },
                apkUrl = json.optJSONArray("assets")
                    ?.let { assets ->
                        (0 until assets.length())
                            .map { assets.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                            ?.optString("browser_download_url")
                            ?.takeIf { it.isNotBlank() }
                    },
                newer = isNewer(version, currentVersion),
            )
        }
    }

    companion object {
        /** "v1.2.0" / "1.2.0-beta" -> "1.2.0" */
        fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V")
                .takeWhile { it.isDigit() || it == '.' }
                .trimEnd('.')

        /**
         * 语义化比较：逐段比数字，缺的段按 0 算，所以 1.3 比 1.2.9 新。
         * 解析不出来时退回 false（宁可漏报，也不给用户推一个假更新）。
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: return false }
            val b = current.split('.').map { it.toIntOrNull() ?: return false }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
