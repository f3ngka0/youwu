package com.fengkao.timetable.widget.service

import android.content.Context
import org.json.JSONObject

enum class WidgetService { WATER, SHOWER }
enum class WidgetOperation { START, STOP, CHECK }
data class ServiceWidgetBinding(val service: WidgetService, val deviceId: String, val accountPhone: String) {
    internal fun json() = JSONObject().put("service", service.name).put("device", deviceId).put("phone", accountPhone)
    companion object {
        internal fun parse(raw: String): ServiceWidgetBinding? = runCatching {
            val json = JSONObject(raw)
            ServiceWidgetBinding(WidgetService.valueOf(json.getString("service")), json.getString("device"), json.getString("phone"))
                .also { require(it.deviceId.isNotBlank() && it.accountPhone.isNotBlank()) }
        }.getOrNull()
    }
}

/** Widget instances only store an explicit target, never tokens or a global default. */
class ServiceWidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("service_widgets", Context.MODE_PRIVATE)
    fun binding(id: Int): ServiceWidgetBinding? = runCatching {
        ServiceWidgetBinding.parse(prefs.getString("binding_$id", null) ?: return null)
    }.getOrNull()
    fun save(id: Int, binding: ServiceWidgetBinding) {
        require(id > 0 && binding.deviceId.isNotBlank() && binding.accountPhone.isNotBlank())
        val json = binding.json()
        check(prefs.edit().putString("binding_$id", json.toString()).remove("status_$id").commit()) { "组件绑定保存失败" }
    }
    fun delete(id: Int) { prefs.edit().remove("binding_$id").remove("status_$id").apply() }
    fun status(id: Int): String = prefs.getString("status_$id", "").orEmpty()
    fun status(id: Int, value: String) { prefs.edit().putString("status_$id", value.take(80)).apply() }
}

/** An old stop/check click must never turn into an open-valve request. */
internal fun widgetOperationAllowed(operation: WidgetOperation, active: Boolean, owner: Boolean, unknown: Boolean): Boolean =
    when (operation) {
        WidgetOperation.START -> !active && !unknown
        WidgetOperation.STOP -> active && owner && !unknown
        WidgetOperation.CHECK -> true
    }

internal fun widgetMatchesPendingTarget(phone: String, deviceId: String, controllingPhone: String,
    sessionDeviceId: String?, selectedDeviceId: String): Boolean =
    phone == controllingPhone && deviceId == (sessionDeviceId ?: selectedDeviceId)
