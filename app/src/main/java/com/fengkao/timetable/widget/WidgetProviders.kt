package com.fengkao.timetable.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面 Widget Provider 基类。
 * 2×2 = 核心信息；4×2 = 核心信息 + 今日课程概览（同一次数据计算，两种版式）。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshInBackground(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        refreshInBackground(context)
    }

    override fun onEnabled(context: Context) {
        refreshInBackground(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetAlarm.ACTION_TICK) {
            refreshInBackground(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancel(context)
    }

    private fun refreshInBackground(context: Context) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateAll(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        /** 当前桌面上是否有任何本 App 的 Widget */
        fun anyBound(context: Context): Boolean =
            listOf(Widget2x2Provider::class.java, Widget4x2Provider::class.java).any { cls ->
                AppWidgetManager.getInstance(context)
                    ?.getAppWidgetIds(ComponentName(context, cls))?.isNotEmpty() == true
            }
    }
}

/** 2×2：小尺寸核心 Widget */
class Widget2x2Provider : BaseWidgetProvider()

/** 4×2：信息增强 Widget（核心信息 + 今日课程概览） */
class Widget4x2Provider : BaseWidgetProvider()
