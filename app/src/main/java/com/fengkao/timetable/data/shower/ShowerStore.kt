package com.fengkao.timetable.data.shower

import android.content.Context
import com.fengkao.timetable.data.local.Crypto
import org.json.JSONArray
import org.json.JSONObject
import com.fengkao.timetable.data.shower.ble.BleSession

class ShowerStore(context: Context) {
    private val prefs = context.getSharedPreferences("shower_account", Context.MODE_PRIVATE)
    fun auth() = runCatching { prefs.getString("auth", null)?.let { ShowerAuth.from(JSONObject(Crypto.decrypt(it))) } }.getOrNull()
    fun phone() = prefs.getString("phone", "").orEmpty()
    fun login(auth: ShowerAuth) {
        val editor = prefs.edit().putString("auth", Crypto.encrypt(auth.json().toString())).putString("phone", auth.phone)
        if (phone() != auth.phone) editor.remove("devices").remove("selected").remove("pending").remove("ble_session")
        check(editor.commit()) { "登录信息保存失败" }
    }
    fun expire() { prefs.edit().remove("auth").apply() }
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
    fun pending(value: Boolean) { check(prefs.edit().putBoolean("pending", value).commit()) { "用水状态保存失败" } }
    fun bleSession(): BleSession? = runCatching {
        prefs.getString("ble_session", null)?.let { BleSession.from(JSONObject(Crypto.decrypt(it))) }
    }.getOrNull()
    fun bleSession(value: BleSession?) {
        val editor = prefs.edit()
        if (value == null) editor.remove("ble_session") else editor.putString("ble_session", Crypto.encrypt(value.json().toString()))
        check(editor.commit()) { "蓝牙用水状态保存失败" }
    }
}
