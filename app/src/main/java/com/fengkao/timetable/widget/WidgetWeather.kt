package com.fengkao.timetable.widget

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Widget 天气：与课程信息解耦的辅助模块（icon + 23°），15 分钟缓存；
 * 拉取失败静默隐藏，绝不阻塞主渲染。
 */
object WidgetWeather {

    data class Info(
        /** Open-Meteo weather_code（渲染器据此选 Material Symbol 图标） */
        val code: Int,
        val temp: Int,
    )

    private const val PREFS = "widget_weather_cache"
    private const val KEY_JSON = "json"
    private const val KEY_TS = "ts"
    private const val FRESH_MS = 15 * 60 * 1000L

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    }

    fun cached(context: Context): Info? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_JSON, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun isFresh(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return System.currentTimeMillis() - sp.getLong(KEY_TS, 0L) < FRESH_MS
    }

    private fun store(context: Context, info: Info) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, toJson(info).toString())
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply()
        }
    }

    /** 后台线程拉取最新天气，回调带回新值（失败回调 null，不影响现有显示） */
    fun refreshAsync(context: Context, onDone: (Info?) -> Unit) {
        val app = context.applicationContext
        Thread {
            val fresh = fetch()
            if (fresh != null) store(app, fresh)
            runCatching { onDone(fresh) }
        }.start()
    }

    private fun toJson(i: Info) = JSONObject().put("c", i.code).put("t", i.temp)

    private fun fromJson(o: JSONObject) =
        Info(o.optInt("c", 3), o.optInt("t", 0))

    private fun fetch(): Info? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=23.873&longitude=111.266" +
            "&current=temperature_2m,weather_code" +
            "&minutely_15=precipitation&forecast_minutely_15=8" +
            "&timezone=Asia%2FShanghai"
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string() ?: return null
            val root = JSONObject(body)
            val cur = root.optJSONObject("current") ?: return null
            val temp = cur.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return null
            Info(cur.optInt("weather_code", 3), kotlin.math.round(temp).toInt())
        }
    }.getOrNull()
}
