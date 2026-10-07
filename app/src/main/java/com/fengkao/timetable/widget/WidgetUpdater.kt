package com.fengkao.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget 刷新入口：读数据 → 渲染推送 → 调度下一分钟闹钟 → 天气异步补刷。
 * 任意线程可安全调用 [requestUpdateAll]。
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestUpdateAll(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { updateAll(app) } }
    }

    suspend fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val snap = WidgetData.load(context)
        val weather = WidgetWeather.cached(context)
        pushAll(context, mgr, snap, weather)
        WidgetAlarm.scheduleNext(context, snap)

        // 天气只在过期时补拉一次；拿到新值后仅重绘角标，不重复计算课表
        if (!WidgetWeather.isFresh(context)) {
            WidgetWeather.refreshAsync(context) { fresh ->
                if (fresh != null) {
                    runCatching {
                        AppWidgetManager.getInstance(context)?.let { pushAll(context, it, snap, fresh) }
                    }
                }
            }
        }
    }

    private fun pushAll(
        context: Context,
        mgr: AppWidgetManager,
        snap: WidgetSnapshot,
        weather: WidgetWeather.Info?,
    ) {
        for ((cls, defaultMedium) in listOf(
            Widget2x2Provider::class.java to false,
            Widget4x2Provider::class.java to true,
        )) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls)) ?: continue
            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minWidth = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
                val maxWidth = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
                val size = WidgetSizing.resolve(minWidth, maxWidth,
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                    context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                    defaultMedium)
                mgr.updateAppWidget(id, WidgetRenderer.render(context, size.medium, snap, weather, size.width, size.height))
            }
        }
    }
}
