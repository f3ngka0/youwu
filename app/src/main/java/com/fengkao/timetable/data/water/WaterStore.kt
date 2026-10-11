package com.fengkao.timetable.data.water

import android.content.Context
import com.fengkao.timetable.data.local.Crypto
import org.json.JSONArray
import org.json.JSONObject

/** Stored separately from teaching-system credentials. */
class WaterStore(context: Context) {
    private val prefs = context.getSharedPreferences("water_account", Context.MODE_PRIVATE)
    fun token(): String = prefs.getString("token", null)?.let { runCatching { Crypto.decrypt(it) }.getOrNull() }.orEmpty()
    fun saveLogin(token: String, phone: String) {
        val editor = prefs.edit().putString("token", Crypto.encrypt(token)).putString("phone", phone)
        if (phone != phone()) editor.remove("devices").remove("selected")
        check(editor.commit()) { "登录信息保存失败，请重试" }
    }
    fun markControlAttempt() {
        check(prefs.edit().putLong("last_control_attempt", System.currentTimeMillis()).commit()) { "启动状态保存失败" }
    }
    fun controlCooldownRemaining(): Long =
        (10_000 - (System.currentTimeMillis() - prefs.getLong("last_control_attempt", 0L))).coerceIn(0, 10_000)
    fun phone(): String = prefs.getString("phone", "").orEmpty()
    fun expire() { prefs.edit().remove("token").apply() }
    fun selectedId(): String = prefs.getString("selected", "").orEmpty()
    fun saveDevices(devices: List<WaterDevice>, selected: String) {
        val array = JSONArray()
        devices.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("devices", array.toString()).putString("selected", selected).apply()
    }
    fun devices(): List<WaterDevice> = runCatching {
        val array = JSONArray(prefs.getString("devices", "[]"))
        (0 until array.length()).map { array.getJSONObject(it).let { device -> WaterDevice(device.getString("id"), device.getString("name")) } }
    }.getOrDefault(emptyList())
}
