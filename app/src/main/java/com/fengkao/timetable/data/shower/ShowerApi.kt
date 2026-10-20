package com.fengkao.timetable.data.shower

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.math.BigDecimal
import com.fengkao.timetable.data.shower.ble.BleSignBuilder
import com.fengkao.timetable.data.shower.ble.DeviceQueryState

data class ShowerAuth(val loginCode: String, val userId: String, val accountId: String, val projectId: String, val phone: String) {
    fun json() = JSONObject().put("loginCode", loginCode).put("userId", userId).put("accountId", accountId).put("projectId", projectId).put("phone", phone)
    fun fields() = mapOf("loginCode" to loginCode, "userId" to userId, "accountId" to accountId,
        "projectId" to projectId, "telephone" to phone, "telPhone" to phone, "phoneSystem" to "android", "version" to "6.5.28")
    companion object {
        fun from(json: JSONObject) = ShowerAuth(json.getString("loginCode"), json.getString("userId"), json.getString("accountId"), json.getString("projectId"), json.getString("phone"))
    }
}

data class ShowerDevice(val sn: String, val name: String, val mac: String, val bluetoothOnly: Boolean = false,
    val deviceId: Int = 0, val bigTypeId: Int = 0, val smallTypeId: Int = 0, val realMac: String = "") {
    val controlMac: String get() = realMac.ifBlank { mac }.replace(":", "").replace("-", "").uppercase()
    fun json() = JSONObject().put("snCode", sn).put("deviceName", name).put("macAddress", mac).put("isBle", bluetoothOnly)
        .put("deviceId", deviceId).put("bigTypeId", bigTypeId).put("smallTypeId", smallTypeId).put("realMac", realMac)
    companion object {
        fun from(json: JSONObject): ShowerDevice {
            val sn = json.optString("snCode").takeUnless { it == "null" }.orEmpty()
            require(sn.isNotBlank()) { "设备信息不完整，请重新扫码" }
            return ShowerDevice(sn, json.optString("deviceName", "浴室热水器").takeUnless { it == "null" }.orEmpty(),
                json.optString("macAddress").takeUnless { it == "null" }.orEmpty(), json.optBoolean("isBle", false),
                json.optInt("deviceId"), json.optInt("bigTypeId"), json.optInt("smallTypeId"),
                json.optString("realMac").takeUnless { it == "null" }.orEmpty())
        }
    }
}
data class ShowerOrder(val active: Boolean, val owner: Boolean, val number: String)
data class ShowerBalance(val amount: String, val source: String)
data class ShowerBleRate(val data: ByteArray, val consumeDate: String, val orderNo: String)
data class ShowerBleUpload(val clData: String)
class ShowerSessionExpired : IOException("洗澡账户登录已失效，请重新登录")

/** 趣智校园协议，参考 MIT 许可的 yehu-imei/linyu。控制请求禁止自动重试。 */
class ShowerApi(private val baseUrl: String = "https://v3-api.china-qzxy.cn",
    private val client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()) {
    private fun call(path: String, fields: Map<String, String>, post: Boolean, auth: ShowerAuth? = null,
        allowUsing: Boolean = false, acceptedCodes: Set<Int> = emptySet()): JSONObject {
        val url = (baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        val all = (auth?.fields().orEmpty() + fields)
        val builder = Request.Builder().header("Accept", "application/json")
        if (auth != null) builder.header("Config-Project", auth.projectId).header("Config-Keys",
            "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config")
        if (post) builder.post(FormBody.Builder().apply { all.forEach { (k, v) -> add(k, v) } }.build())
        else all.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return client.newCall(builder.url(url.build()).build()).execute().use { response ->
            if (auth != null && response.code in listOf(401, 403)) throw ShowerSessionExpired()
            if (!response.isSuccessful) throw IOException("洗澡服务连接失败（${response.code}）")
            val json = JSONObject(response.body?.string() ?: throw IOException("服务返回为空"))
            if (!json.optBoolean("success")) {
                if ((allowUsing && json.optInt("errorCode") == 307) || json.optInt("errorCode") in acceptedCodes) return@use json
                val message = json.optString("errorMessage").takeUnless { it.isBlank() || it == "null" }
                    ?: json.optString("msg", "操作失败").takeUnless { it == "null" } ?: "操作失败"
                if (auth != null && (message.contains("登录") || message.contains("token", true) || message.contains("认证") || message.contains("过期"))) throw ShowerSessionExpired()
                throw IOException(message)
            }
            json
        }
    }
    fun sendSms(phone: String) { call("/user/verification/code/get", mapOf("telephone" to phone, "secret" to smsSecret(phone), "typeId" to "3", "platform" to "1"), false) }
    fun login(phone: String, secret: String, sms: Boolean): ShowerAuth {
        val fields = mapOf("telephone" to phone, "phoneSystem" to "android", "version" to "6.5.28", "type" to if (sms) "5" else "0") +
            if (sms) mapOf("smsCode" to secret) else mapOf("password" to passwordHash(secret))
        val data = call(if (sms) "/user/registerAndLogin" else "/user/login", fields, true).getJSONObject("data")
        val account = data.getJSONObject("userAccount")
        val auth = ShowerAuth(data.optString("loginCode"), data.optString("userId"), account.optString("accountId"), account.optString("projectId"), phone)
        require(listOf(auth.loginCode, auth.userId, auth.accountId, auth.projectId).all { it.isNotBlank() && it != "null" && it != "0" }) { "登录信息不完整，请先在趣智校园完成学校账户绑定" }
        return auth
    }
    fun validate(auth: ShowerAuth) { call("/account/info", emptyMap(), false, auth) }
    fun balance(auth: ShowerAuth): ShowerBalance {
        try {
            val data = call("/settlement/campus/userInfo", emptyMap(), false, auth).optJSONObject("data")
            money(data?.optString("amount"))?.let { return ShowerBalance(it, "一卡通余额") }
        } catch (e: ShowerSessionExpired) { throw e }
        catch (_: IOException) { /* 校园卡未签约或暂不可用时查询真实钱包余额。 */ }
        val data = call("/account/wallet", emptyMap(), false, auth).optJSONObject("data")
        val amount = money(data?.optString("money")) ?: throw IOException("服务未返回余额，请稍后重试")
        return ShowerBalance(amount, "趣智校园钱包余额")
    }
    fun device(auth: ShowerAuth, mac: String) = ShowerDevice.from(call("/device/info/mac", mapOf("macAddress" to mac), false, auth).getJSONObject("data"))
    fun query(auth: ShowerAuth, sn: String): ShowerOrder {
        val json = call("/order/tcpDevice/query/rateOrder/using", mapOf("xfModel" to "0", "snCode" to sn), true, auth, true)
        val data = json.optJSONObject("data")
        val number = data?.optString("orderNo")?.takeUnless { it == "null" }.orEmpty()
        val active = json.optInt("errorCode") == 307 || number.isNotBlank()
        // 缺少归属时不能把他人的用水订单认作本人的。
        return ShowerOrder(active, data?.optBoolean("isOwner", false) == true, number)
    }
    fun start(auth: ShowerAuth, sn: String) { call("/order/tcpDevice/downRate/rateOrder", mapOf("xfModel" to "0", "snCode" to sn), true, auth) }
    fun opened(auth: ShowerAuth, sn: String): String? {
        val data = call("/order/tcpDevice/query/downRateResult", mapOf("snCode" to sn), true, auth).optJSONObject("data") ?: return null
        val number = data.optString("orderNo").takeUnless { it == "null" }.orEmpty()
        return if (number.isNotBlank() || data.optInt("result", -1) == 0 || data.optInt("state", -1) == 0) number else null
    }
    fun stop(auth: ShowerAuth, sn: String, order: String) { call("/order/tcpDevice/closeOrder", mapOf("snCode" to sn, "orderNo" to order), true, auth) }
    fun bleRate(auth: ShowerAuth, device: ShowerDevice, state: DeviceQueryState): ShowerBleRate {
        require(device.deviceId > 0 && device.controlMac.matches(Regex("[0-9A-F]{12}"))) { "设备信息不完整，请重新绑定" }
        val signature = BleSignBuilder.signRateOrder(auth.loginCode, auth.phone, device.deviceId.toString(), "0", state.randomNumber)
            ?: throw IOException("蓝牙请求签名失败")
        val data = call("/order/downRate/bluetooth/rateOrder", mapOf(
            "deviceId" to device.deviceId.toString(), "macAddress" to device.controlMac,
            "macType" to BleSignBuilder.macType(state.devType, state.a1),
            "bigTypeId" to device.bigTypeId.toString(), "smallTypeId" to device.smallTypeId.toString(),
            "protocolType" to state.protocolType, "randomNumber" to state.randomNumber.lowercase(),
            "xfModel" to "0", "signature" to signature), true, auth).optJSONObject("data")
            ?: throw IOException("服务未返回蓝牙费率")
        val raw = com.fengkao.timetable.data.shower.ble.BleFrame.fromHex(data.optString("downData"))
            ?.takeIf { it.isNotEmpty() } ?: throw IOException("服务返回的蓝牙费率无效")
        return ShowerBleRate(raw, data.optString("consumeDate"),
            data.optString("orderNo").takeUnless { it == "null" }.orEmpty().ifBlank { data.optString("liquidOrderNo").takeUnless { it == "null" }.orEmpty() })
    }
    fun bleUpload(auth: ShowerAuth, state: DeviceQueryState, payload: ByteArray): ShowerBleUpload {
        // SDK consumption bodies are at least 42 bytes; device errors are not billing data.
        if (payload.size < 42 || payload[0].toInt() and 0xFF != 0x80)
            throw IOException("设备返回的消费数据无效，请靠近设备后重试")
        val xfData = com.fengkao.timetable.data.shower.ble.BleFrame.toHex(payload).lowercase()
        val signature = BleSignBuilder.signUpload(auth.loginCode, auth.phone, xfData) ?: throw IOException("结算签名失败")
        val data = call("/order/upload/bluetooth/data", mapOf(
            "protocolType" to state.protocolType, "randomNumber" to state.randomNumber.lowercase(),
            "xfData" to xfData, "signature" to signature), true, auth, acceptedCodes = setOf(206, 209))
            .optJSONObject("data") ?: throw IOException("服务未返回结算数据")
        return ShowerBleUpload(data.optString("clData"))
    }
    fun bleFail(auth: ShowerAuth, consumeDate: String) {
        if (consumeDate.isBlank()) return
        val signature = BleSignBuilder.signFail(auth.loginCode, auth.phone, consumeDate) ?: return
        call("/order/upload/bluetooth/fail", mapOf("consumeDate" to consumeDate, "signature" to signature), true, auth)
    }
    companion object {
        private fun money(value: String?): String? = runCatching {
            value?.takeIf { it.isNotBlank() && it != "null" }?.let { BigDecimal(it).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() }
        }.getOrNull()
        private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun passwordHash(password: String) = md5(password).takeLast(10).uppercase()
        fun smsSecret(phone: String): String {
            require(phone.matches(Regex("1\\d{10}"))) { "请输入正确的手机号" }
            return md5(phone.take(3) + phone.takeLast(4) + "klcx")
        }
        fun deviceCode(raw: String): String? {
            val code = raw.trim().substringAfterLast(',').replace(":", "").uppercase()
            return code.takeIf { it.matches(Regex("[0-9A-F]{12}")) }
        }
    }
}
