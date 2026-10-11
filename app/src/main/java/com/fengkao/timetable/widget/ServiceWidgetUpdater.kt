package com.fengkao.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.fengkao.timetable.R
import com.fengkao.timetable.data.shower.ShowerStore
import com.fengkao.timetable.data.shower.showerAccountDisplayName
import com.fengkao.timetable.data.water.WaterStore
import com.fengkao.timetable.widget.service.*

abstract class ServiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { ServiceWidgetUpdater.update(context, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        ServiceWidgetUpdater.update(context, id)
    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { ServiceWidgetStore(context).delete(it) }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        // Restored credentials/devices must be chosen explicitly on this installation.
        oldIds.forEach { ServiceWidgetStore(context).delete(it) }
        newIds.forEach { ServiceWidgetStore(context).delete(it); ServiceWidgetUpdater.update(context, it) }
    }
}
class WaterControlWidgetProvider : ServiceWidgetProvider()
class ShowerControlWidgetProvider : ServiceWidgetProvider()

data class ServiceWidgetVisual(val service: WidgetService, val deviceName: String?, val accountLabel: String = "",
    val action: String = "绑定设备", val status: String = "点击选择组件使用的设备")

object ServiceWidgetUpdater {
    fun service(context: Context, id: Int): WidgetService? {
        val cls = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className
        return when (cls) {
            WaterControlWidgetProvider::class.java.name -> WidgetService.WATER
            ShowerControlWidgetProvider::class.java.name -> WidgetService.SHOWER
            else -> null
        }
    }
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(WaterControlWidgetProvider::class.java, ShowerControlWidgetProvider::class.java).forEach { cls ->
            manager.getAppWidgetIds(ComponentName(context, cls)).forEach { update(context, it) }
        }
    }
    fun update(context: Context, id: Int) {
        val service = service(context, id) ?: return
        val store = ServiceWidgetStore(context)
        val binding = store.binding(id)?.takeIf { it.service == service }
        val water = WaterStore(context)
        val shower = ShowerStore(context)
        val device = if (service == WidgetService.WATER) water.devices().firstOrNull { it.id == binding?.deviceId }?.name
            else shower.devices().firstOrNull { it.sn == binding?.deviceId }?.name
        val valid = binding != null && device != null && if (service == WidgetService.WATER)
            water.phone() == binding.accountPhone && water.token().isNotBlank()
            else shower.accounts().any { it.phone == binding.accountPhone }
        val session = shower.bleSession()
        val operation = when {
            service == WidgetService.WATER -> WidgetOperation.START
            shower.pending() -> WidgetOperation.CHECK
            session?.sn == binding?.deviceId && shower.controllingPhone() == binding?.accountPhone -> WidgetOperation.STOP
            else -> WidgetOperation.START
        }
        val label = when (operation) {
            WidgetOperation.CHECK -> "确认状态"
            WidgetOperation.STOP -> "关闭热水"
            WidgetOperation.START -> if (service == WidgetService.SHOWER) "开启热水" else "启动饮水"
        }
        val visual = ServiceWidgetVisual(service, device.takeIf { valid },
            if (valid && service == WidgetService.SHOWER) showerAccountDisplayName(binding.accountPhone, shower.accountNotes()[binding.accountPhone]) else "",
            if (valid) label else "绑定设备",
            if (valid) store.status(id) else if (binding != null) "绑定已失效，点击重新选择" else "点击选择组件使用的设备")
        val views = render(context, visual)
        val click = pending(context, id, !valid, operation)
        views.setOnClickPendingIntent(R.id.service_widget_action, click)
        views.setOnClickPendingIntent(R.id.service_widget_target, pending(context, id, true, operation))
        views.setOnClickPendingIntent(R.id.service_widget_settings, pending(context, id, true, operation))
        AppWidgetManager.getInstance(context).updateAppWidget(id, views)
    }
    fun render(context: Context, visual: ServiceWidgetVisual): RemoteViews {
        val shower = visual.service == WidgetService.SHOWER
        val ink = Color.parseColor(if (shower) "#9B4D2F" else "#174B8E")
        return RemoteViews(context.packageName, R.layout.widget_service_control).apply {
            setInt(R.id.service_widget_root, "setBackgroundResource", if (shower) R.drawable.widget_shower_background else R.drawable.widget_water_background)
            setImageViewResource(R.id.service_widget_icon, if (shower) R.drawable.ic_widget_shower else R.drawable.ic_widget_water)
            setTextViewText(R.id.service_widget_title, if (shower) "洗澡" else "喝水")
            setTextColor(R.id.service_widget_title, ink)
            setTextViewText(R.id.service_widget_target, visual.deviceName ?: "尚未绑定设备")
            setTextViewText(R.id.service_widget_account, visual.accountLabel)
            setViewVisibility(R.id.service_widget_account, if (visual.accountLabel.isBlank()) View.GONE else View.VISIBLE)
            setTextViewText(R.id.service_widget_status, visual.status)
            setViewVisibility(R.id.service_widget_status, if (visual.status.isBlank()) View.GONE else View.VISIBLE)
            setInt(R.id.service_widget_action, "setBackgroundResource", if (shower) R.drawable.widget_shower_action else R.drawable.widget_water_action)
            setTextViewText(R.id.service_widget_action, visual.action)
            setContentDescription(R.id.service_widget_settings, "重新绑定组件设备和账户")
        }
    }
    private fun pending(context: Context, id: Int, bind: Boolean, operation: WidgetOperation): PendingIntent =
        PendingIntent.getActivity(context, id, Intent(context, ServiceWidgetActivity::class.java)
            .setData(Uri.parse("youwu-widget://$id/${if (bind) "bind" else "control"}/${operation.name}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("bind", bind).putExtra("operation", operation.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
