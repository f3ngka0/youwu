package com.fengkao.timetable.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fengkao.timetable.TimetableApp
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.data.local.DEFAULT_BASE_URL
import com.fengkao.timetable.data.model.*
import com.fengkao.timetable.data.shower.ShowerBalance
import com.fengkao.timetable.data.shower.ShowerDevice
import com.fengkao.timetable.data.water.WaterDevice
import com.fengkao.timetable.ui.*
import com.fengkao.timetable.ui.login.LoginScreen
import com.fengkao.timetable.ui.theme.TimetableTheme
import com.fengkao.timetable.ui.water.*
import com.fengkao.timetable.ui.shower.*
import com.fengkao.timetable.ui.welcome.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Only compiled into the offline .demo app. Uses the production screens with fictional state. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val container = (application as TimetableApp).container
            val term = TermCalculator.autoTerm()
            val start = TermCalculator.today().minusWeeks(4).with(java.time.DayOfWeek.MONDAY)
            val courses = listOf(
                course("高等数学", "教学楼 A203", "示例教师甲", 1, 1, 2),
                course("大学英语", "教学楼 B106", "示例教师乙", 1, 6, 7),
                course("程序设计基础", "实验楼 C305", "示例教师丙", 2, 3, 5),
                course("离散数学", "教学楼 A205", "示例教师甲", 3, 1, 2),
                course("程序设计实验", "实验楼 C302", "示例教师丙", 3, 6, 7),
                course("数据结构", "教学楼 B208", "示例教师丁", 4, 3, 5),
                course("大学体育", "运动场", "示例教师戊", 4, 8, 9),
                course("计算机网络", "教学楼 A301", "示例教师丁", 5, 1, 2),
                course("通识选修", "教学楼 B201", "示例教师乙", 5, 6, 7),
            )
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                container.settings.setAutoSync(false)
                container.settings.saveCredentials("DEMO-STUDENT", null, DEFAULT_BASE_URL, false)
                container.settings.completeWelcome()
                container.settings.setTermStartEpochDay(start.toEpochDay(), term)
                container.settings.setLastSync(term.xnm, term.term, now)
                container.cache.save(CachedSchedule(term.xnm, term.term, now,
                    StudentInfo("演示同学", "DEMO-STUDENT", "演示班级", "示例学院", "示例专业", "2026"), courses))
                container.changes.save(listOf(
                    CourseChange(ChangeType.CHANGED, "程序设计基础", "周二 3–5 节 · 教学楼 A203 → 实验楼 C305", now),
                    CourseChange(ChangeType.ADDED, "通识选修", "周五 6–7 节 · 教学楼 B201", now),
                ))
                container.settings.setCourseMeta("程序设计基础", CourseMeta(CourseType.MAJOR, 4.0, true))
                container.settings.setCourseMeta("程序设计实验", CourseMeta(CourseType.EXP, 2.0))
                container.settings.setBusyBlocks(listOf(BusyBlock(1, "示例社团例会", 2, 1140, 1230)))
            }
            val provider = ViewModelProvider(this@DemoActivity)
            val main = provider[MainViewModel::class.java]
            val water = provider[WaterViewModel::class.java]
            val shower = provider[ShowerViewModel::class.java]
            main.state.first { it.bootstrapped }
            val screen = intent.getStringExtra("screen").orEmpty()
            setState(water, WaterUiState(loggedIn = screen != "water-login", configured = true,
                devices = listOf(WaterDevice("DEMO-WATER-01", "示例饮水机 · 教学楼一层"), WaterDevice("DEMO-WATER-02", "示例饮水机 · 图书馆")),
                selectedId = "DEMO-WATER-01"))
            setState(shower, ShowerUiState(loggedIn = screen != "shower-login",
                devices = listOf(ShowerDevice("DEMO-SHOWER-01", "示例热水器 · 演示浴室", "000000000001", true),
                    ShowerDevice("DEMO-SHOWER-02", "示例热水器 · 备用设备", "000000000002", true)),
                selected = "DEMO-SHOWER-01", balance = ShowerBalance("20.00", "演示余额")))
            main.setTab(when (screen) { "water", "water-login" -> 1; "shower", "shower-login" -> 2; "profile" -> 3; else -> 0 })
            if (screen == "welcome") {
                container.settings.restartWelcome()
                container.settings.setWelcomeStep(intent.getIntExtra("step", 0))
                val settings = container.settings.first()
                setState(main, main.state.value.copy(settings = settings))
            }
            setContent {
                TimetableTheme {
                    when (screen) {
                        "welcome" -> WelcomeScreen(main)
                        "login" -> LoginScreen(main)
                        else -> MainScaffold(main)
                    }
                }
            }
        }
    }

    private fun course(name: String, place: String, teacher: String, day: Int, first: Int, last: Int) =
        CourseBlock(name, place, teacher, "示例校区", day, first, last, (1..16).toList(), "1–16 周", "演示班级")

    // Fixture injection lives exclusively in this build type; release contains no demo hooks.
    @Suppress("UNCHECKED_CAST")
    private fun <T> setState(model: Any, value: T) {
        val field = model.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        (field.get(model) as MutableStateFlow<T>).value = value
    }
}
