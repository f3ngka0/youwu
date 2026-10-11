package com.fengkao.timetable.data.shower

import android.content.Context
import com.fengkao.timetable.data.local.Crypto
import org.json.JSONArray
import org.json.JSONObject
import com.fengkao.timetable.data.shower.ble.BleSession

class ShowerStore(context: Context) {
    private val prefs = context.getSharedPreferences("shower_account", Context.MODE_PRIVATE)
    private fun readAuth(key: String) = runCatching {
        prefs.getString(key, null)?.let { ShowerAuth.from(JSONObject(Crypto.decrypt(it))) }
    }.getOrNull()
    fun auth() = readAuth("auth")
    fun secondaryAuth() = readAuth("secondary_auth")
    fun phone() = prefs.getString("phone", "").orEmpty()
    fun secondaryPhone() = prefs.getString("secondary_phone", "").orEmpty()
    fun accounts() = listOfNotNull(auth(), secondaryAuth()).distinctBy { it.phone }
    fun accountNotes(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString("account_notes", "{}").orEmpty())
        json.keys().asSequence().associateWith { json.optString(it) }
    }.getOrDefault(emptyMap())
    fun saveAccountNote(phone: String, note: String) {
        require(accounts().any { it.phone == phone }) { "账户尚未登录" }
        val notes = JSONObject(prefs.getString("account_notes", "{}").orEmpty())
        val normalized = normalizeShowerAccountNote(note)
        if (normalized.isBlank()) notes.remove(phone) else notes.put(phone, normalized)
        check(prefs.edit().putString("account_notes", notes.toString()).commit()) { "备注保存失败" }
    }
    fun controllingPhone(): String = prefs.getString("control_phone", null)
        ?: bleSession()?.accountPhone?.takeIf { it.isNotBlank() } ?: phone()
    fun activePhone(): String = if (pending() || bleSession() != null) controllingPhone()
        else prefs.getString("active_phone", null) ?: phone()
    fun activeAuth() = resolveShowerAccount(auth(), secondaryAuth(), activePhone(),
        controllingPhone().takeIf { pending() || bleSession() != null })
    fun selectAccount(phone: String) {
        require(accounts().any { it.phone == phone }) { "账户尚未登录" }
        check(!(pending() || bleSession() != null) || controllingPhone() == phone) { "请先确认当前用水状态" }
        check(prefs.edit().putString("active_phone", phone).commit()) { "账户选择保存失败" }
    }
    fun login(auth: ShowerAuth) {
        check(!(pending() || bleSession() != null) || controllingPhone() == auth.phone) { "请使用当前用水账户登录" }
        if (auth.phone == secondaryPhone() && auth.phone != phone()) {
            loginSecondary(auth)
            selectAccount(auth.phone)
            return
        }
        val editor = prefs.edit().putString("auth", Crypto.encrypt(auth.json().toString()))
            .putString("phone", auth.phone).putString("active_phone", auth.phone)
        if (phone() != auth.phone) editor.remove("devices").remove("selected").remove("pending")
            .remove("ble_session").remove("control_phone").remove("secondary_auth").remove("secondary_phone").remove("account_notes")
        check(editor.commit()) { "登录信息保存失败" }
    }
    fun loginSecondary(auth: ShowerAuth) {
        require(auth.phone != phone()) { "请填写另一个手机号" }
        check(!(pending() || bleSession() != null) || controllingPhone() == auth.phone) { "请先确认当前用水状态" }
        val editor = prefs.edit().putString("secondary_auth", Crypto.encrypt(auth.json().toString()))
            .putString("secondary_phone", auth.phone)
        if (secondaryPhone() != auth.phone) {
            val notes = JSONObject(prefs.getString("account_notes", "{}").orEmpty()).apply { remove(secondaryPhone()) }
            editor.putString("account_notes", notes.toString())
        }
        if (activePhone() == secondaryPhone() && secondaryPhone() != auth.phone) {
            editor.putString("active_phone", phone())
        }
        check(editor.commit()) { "第二账户保存失败" }
    }
    fun expireAccount(phone: String) {
        val key = if (phone == secondaryPhone()) "secondary_auth" else "auth"
        check(prefs.edit().remove(key).commit()) { "账户状态保存失败" }
    }
    fun expire() {
        check(prefs.edit().remove("auth").remove("secondary_auth").remove("secondary_phone")
            .remove("active_phone").remove("account_notes").commit()) { "退出账户失败" }
    }
    fun devices(): List<ShowerDevice> = runCatching {
        val array = JSONArray(prefs.getString("devices", "[]"))
        (0 until array.length()).map { ShowerDevice.from(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun selected() = prefs.getString("selected", "").orEmpty()
    fun saveDevices(devices: List<ShowerDevice>, selected: String) {
        val array = JSONArray(); devices.forEach { array.put(it.json()) }
        check(prefs.edit().putString("devices", array.toString()).putString("selected", selected).commit()) { "设备保存失败" }
    }
    // 控制请求发出前落盘；超时或应用重启后先查询，避免重复开水。
    fun pending() = prefs.getBoolean("pending", false)
    fun pending(value: Boolean, accountPhone: String? = null) {
        val editor = prefs.edit().putBoolean("pending", value)
        if (value && accountPhone != null) editor.putString("control_phone", accountPhone)
        if (!value && bleSession() == null) editor.remove("control_phone")
        check(editor.commit()) { "用水状态保存失败" }
    }
    fun bleSession(): BleSession? = runCatching {
        prefs.getString("ble_session", null)?.let { BleSession.from(JSONObject(Crypto.decrypt(it))) }
    }.getOrNull()
    fun bleSession(value: BleSession?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove("ble_session")
            if (!pending()) editor.remove("control_phone")
        }
        else {
            editor.putString("ble_session", Crypto.encrypt(value.json().toString()))
            if (value.accountPhone.isNotBlank()) editor.putString("control_phone", value.accountPhone)
        }
        check(editor.commit()) { "蓝牙用水状态保存失败" }
    }
}
