package com.fengkao.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fengkao.timetable.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget 真机预览（QA 用）：用与桌面完全相同的渲染管线输出 2×2 / 4×2 实际 RemoteViews，
 * 便于在 App 内快速核对明暗模式与各动态状态，无需反复钉到桌面。
 *
 * 演示模式：adb shell am start -n com.fengkao.timetable/.widget.WidgetPreviewActivity
 *          -e demo active|soon|urgent|next|gap|over|noclass（重复调用走 onNewIntent 即时切换）
 */
class WidgetPreviewActivity : ComponentActivity() {

    private lateinit var slotSmall: LinearLayout
    private lateinit var slotMedium: LinearLayout
    private var mediumWidthDp = 360

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val root = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@WidgetPreviewActivity, R.color.window_background))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        root.addView(column)
        setContentView(root)

        // 一键钉到桌面（requestPinAppWidget；桌面弹确认框）
        val pinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun pinButton(text: String, cls: Class<*>) = Button(this).apply {
            this.text = text
            setOnClickListener {
                getSystemService(AppWidgetManager::class.java)
                    ?.requestPinAppWidget(ComponentName(this@WidgetPreviewActivity, cls), null, null)
            }
        }
        pinRow.addView(pinButton("添加 2×2 到桌面", Widget2x2Provider::class.java))
        pinRow.addView(pinButton("添加 4×2 到桌面", Widget4x2Provider::class.java))
        column.addView(Button(this).apply {
            text = "喝水与洗澡 · 桌面组件"
            setOnClickListener { startActivity(Intent(this@WidgetPreviewActivity, ServiceWidgetPreviewActivity::class.java)) }
        }.apply { if (intent.getBooleanExtra("screenshot", false)) visibility = View.GONE })
        column.addView(pinRow)
        if (intent.getBooleanExtra("screenshot", false)) pinRow.visibility = View.GONE

        fun label(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, dp(18), 0, dp(8))
        }
        // 固定标准比例：2×2 正方形、4×2 横长条（2:1）
        column.addView(label("2×2 · 小尺寸核心"))
        slotSmall = LinearLayout(this).apply { id = View.generateViewId() }
        column.addView(slotSmall, LinearLayout.LayoutParams(dp(180), dp(180)))
        column.addView(label("4×2 · 信息增强"))
        slotMedium = LinearLayout(this).apply { id = View.generateViewId() }
        mediumWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 32
        column.addView(
            slotMedium,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(mediumWidthDp / 2)),
        )

        renderDemo(intent?.getStringExtra("demo"))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderDemo(intent?.getStringExtra("demo"))
    }

    /** 用与桌面一致的渲染管线输出指定（或真实）状态的 2×2 / 4×2 */
    private fun renderDemo(demoKind: String?) {
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val snap = if (demoKind != null) {
                WidgetData.demoSnapshot(demoKind)
            } else {
                WidgetData.load(app)
            }
            val weather = WidgetWeather.cached(app)
            val small = WidgetRenderer.render(this@WidgetPreviewActivity, medium = false, snap, weather, 180)
            val medium = WidgetRenderer.render(this@WidgetPreviewActivity, medium = true, snap, weather, mediumWidthDp)
            runOnUiThread {
                slotSmall.removeAllViews()
                slotMedium.removeAllViews()
                slotSmall.addView(small.apply(this@WidgetPreviewActivity, slotSmall))
                slotMedium.addView(medium.apply(this@WidgetPreviewActivity, slotMedium))
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
