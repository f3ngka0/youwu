package com.fengkao.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fengkao.timetable.widget.service.WidgetService

/** Uses the real RemoteViews renderer; fictional targets are opt-in and never perform controls. */
class ServiceWidgetPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val demo = intent.getBooleanExtra("demo", false)
        val screenshot = intent.getBooleanExtra("screenshot", false)
        val compact = intent.getBooleanExtra("compact", false)
        val longNames = intent.getBooleanExtra("longNames", false)
        val root = ScrollView(this).apply { setBackgroundColor(0xFFF6F7F9.toInt()) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom); insets
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        root.addView(column); setContentView(root)
        column.addView(TextView(this).apply { text = "生活服务组件"; textSize = 26f; setTextColor(0xFF202124.toInt()); setPadding(0, 0, 0, dp(12)) })
        column.addView(TextView(this).apply { text = "添加后，点击组件绑定设备"; textSize = 15f; setTextColor(0xFF59616C.toInt()); setPadding(0, 0, 0, dp(24)) })
        listOf(WidgetService.WATER, WidgetService.SHOWER).forEach { service ->
            val shower = service == WidgetService.SHOWER
            column.addView(TextView(this).apply { text = "${if (shower) "洗澡" else "喝水"} · 2×2"; textSize = 16f; setTextColor(0xFF202124.toInt()); setPadding(0, dp(8), 0, dp(12)) })
            val slot = FrameLayout(this)
            column.addView(slot, LinearLayout.LayoutParams(dp(if (compact) 144 else 180), dp(if (compact) 144 else 180)))
            val visual = if (demo) ServiceWidgetVisual(service, if (shower) { if (longNames) "热水表-光华宿舍A区-5层-516" else "浴室 · 3号热水器" } else "教学楼一层 · 饮水机",
                if (shower) "宿舍账户" else "", if (shower) "开启热水" else "启动饮水", "")
                else ServiceWidgetVisual(service, null)
            slot.addView(ServiceWidgetUpdater.render(this, visual).apply(this, slot))
            if (!screenshot) column.addView(Button(this).apply {
                text = "添加${if (shower) "洗澡" else "喝水"}组件"
                setOnClickListener {
                    val manager = getSystemService(AppWidgetManager::class.java)
                    if (manager?.isRequestPinAppWidgetSupported == true) manager.requestPinAppWidget(
                        ComponentName(this@ServiceWidgetPreviewActivity, if (shower) ShowerControlWidgetProvider::class.java else WaterControlWidgetProvider::class.java), null, null)
                    else Toast.makeText(this@ServiceWidgetPreviewActivity, "请在桌面的小组件列表中添加", Toast.LENGTH_LONG).show()
                }
            })
            column.addView(Space(this), LinearLayout.LayoutParams(1, dp(20)))
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
