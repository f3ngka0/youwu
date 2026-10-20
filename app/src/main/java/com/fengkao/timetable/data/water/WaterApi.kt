package com.fengkao.timetable.data.water

import com.fengkao.timetable.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WaterDevice(val id: String, val name: String)

/** 设备状态：link 为链路状态（0 表示离线），gene 为业务状态（99 空闲、98 禁用，其余视为使用中）。 */
data class WaterDeviceStatus(val link: Int, val gene: Int) {
    val offline: Boolean get() = link == 0
    val disabled: Boolean get() = gene == 98
    val available: Boolean get() = link != 0 && gene == 99
}

class WaterSessionExpired(message: String = "喝水登录已过期，请重新登录") : IOException(message)

/** 服务端业务码错误；与网络异常区分，便于决定是否切换支付方式。 */
class WaterApiException(message: String, val code: Int) : IOException(message)

/** Protocol adapted from nocookies111/life-798 (MIT); see THIRD_PARTY_NOTICES.md. */
class WaterApi(
    private val gateway: String = BuildConfig.WATER_API_GATEWAY,
    private val cid: String = BuildConfig.WATER_API_CID,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build(),
) {
    val configured: Boolean get() = gateway.startsWith("https://") && cid.isNotBlank()

    private fun request(path: String, token: String = "", body: JSONObject? = null, app: Boolean = true): Request {
        require(gateway.isNotBlank() && cid.isNotBlank()) { "喝水服务暂未配置，请联系应用维护者" }
        return Request.Builder().url(gateway.trimEnd('/') + path)
            .header("ApplicationType", if (app) "1,1" else "1,5")
            .header("VersionCode", VERSION_CODE)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept-Language", "zh-Hans-CN;q=1")
            .apply {
                if (token.isNotBlank()) header("Authorization", token)
                if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
            }.build()
    }

    private fun json(path: String, token: String = "", body: JSONObject? = null, app: Boolean = true): JSONObject {
        client.newCall(request(path, token, body, app)).execute().use { response ->
            if (response.code == 401) throw WaterSessionExpired()
            if (!response.isSuccessful) throw IOException("服务暂时不可用（${response.code}）")
            val result = JSONObject(response.body?.string() ?: throw IOException("服务返回为空"))
            val code = result.optInt("code", Int.MIN_VALUE)
            if (code == -99) throw WaterSessionExpired()
            if (code == -21) throw WaterSessionExpired("设备登录未被接受，请重新登录")
            if (code != 0) throw WaterApiException(when (code) {
                -88 -> "请先在慧生活798完成代扣签约"
                -87 -> "代扣签约已过期，请在慧生活798重新签约"
                -52 -> "账户欠费，请充值后使用"
                -20 -> "请先在慧生活798绑定一卡通账户"
                -19 -> "设备准备中，请稍后再试"
                -82 -> "请先在慧生活798完成支付"
                else -> result.optString("msg").ifBlank { "请求失败（$code）" }
            }, code)
            return result
        }
    }

    fun captcha(key: String): ByteArray {
        val path = "/captcha/?s=${encode(key)}&r=${System.currentTimeMillis()}"
        client.newCall(request(path)).execute().use {
            if (!it.isSuccessful) throw IOException("图形验证码获取失败")
            return it.body?.bytes() ?: throw IOException("图形验证码为空")
        }
    }

    fun sendSms(phone: String, graph: String, key: String) {
        json("/acc/login/code", body = JSONObject().put("un", phone).put("authCode", graph).put("s", key), app = false)
    }

    fun login(phone: String, code: String): String = json("/acc/login", body = JSONObject()
        .put("openCode", "").put("authCode", code).put("un", phone).put("cid", cid))
        .optJSONObject("data")?.optJSONObject("al")?.optString("token")
        ?.takeIf { it.isNotBlank() && it != "null" } ?: throw IOException("登录未返回有效凭据，请重试")

    fun devices(token: String): List<WaterDevice> {
        // 凭据失效时设备首页仍返回 code=0，但 data 内没有 account；不识别会误显示为「没有设备」。
        val data = json("/ui/app/master", token).optJSONObject("data")?.takeIf { it.opt("account") != null }
            ?: throw WaterSessionExpired()
        val items = data.optJSONArray("favos") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            WaterDevice(id, item.optString("name").takeUnless { it.isBlank() || it == "null" } ?: "饮水设备")
        }.distinctBy { it.id }
    }

    fun bind(token: String, id: String) { json("/dev/favo?did=${encode(id)}&remove=0", token) }

    /** 二维码里的编号可能是扫码流水号，先用服务端换成真实设备 ID；解析不到时保持原编号。 */
    fun resolveQrDevice(token: String, id: String): String = runCatching {
        json("/qr/use?id=${encode(id)}", token)
            .optJSONObject("data")?.optJSONObject("dev")?.optString("id").orEmpty()
    }.getOrDefault("").takeIf { it.isNotBlank() && it != "null" } ?: id

    fun status(token: String, id: String): WaterDeviceStatus {
        val device = json("/ui/app/dev/status?did=${encode(id)}&more=false", token)
            .optJSONObject("data")?.optJSONObject("device") ?: throw IOException("设备状态读取失败")
        return WaterDeviceStatus(device.optInt("status", 1), device.optJSONObject("gene")?.optInt("status", 0) ?: 0)
    }

    /** [ptype] 91 为钱包支付，21 为支付宝免密支付；参数与官方客户端 3.1.9 保持一致。 */
    fun start(token: String, id: String, ptype: Int = 91) {
        json("/dev/start?did=${encode(id)}&upgrade=true&ptype=$ptype&args=&rcp=false&cnt=1", token)
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        /** 与官方客户端一致，旧版本号会被服务端要求升级。 */
        const val VERSION_CODE = "3.1.9"
        const val USER_AGENT = "Android_ilife798_$VERSION_CODE"
    }
}
