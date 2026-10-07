package com.fengkao.timetable.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.data.model.BusyBlock
import com.fengkao.timetable.data.model.CourseMeta
import com.fengkao.timetable.data.model.CourseType

// ============================================================ 设计 Token（发现页 · 统计模块，深浅双套）

// 中性
private val PAGE_CARD: Color get() = if (IsDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
private val TEXT_MAIN: Color get() = if (IsDark) Color(0xFFE8E9EE) else Color(0xFF1F2430)
private val TEXT_SUB: Color get() = if (IsDark) Color(0xFFA9B0BC) else Color(0xFF5B6472)
private val TEXT_WEAK: Color get() = if (IsDark) Color(0xFF828A9A) else Color(0xFF9AA3B2)
private val TRACK: Color get() = if (IsDark) Color(0xFF2A2D36) else Color(0xFFEDF0F5)
private val ZERO_GRAY: Color get() = if (IsDark) Color(0xFF3A3E4A) else Color(0xFFD7DCE5)
private val LEGEND_OFF: Color get() = if (IsDark) Color(0xFF5A6170) else Color(0xFFC2C9D6)

/** 次级浅底（建议条 / 面板内小卡） */
private val SURFACE_ALT: Color get() = if (IsDark) Color(0xFF23252B) else Color(0xFFF6F7F9)

/** 选择器未选中底 */
private val SELECTOR_IDLE: Color get() = if (IsDark) Color(0xFF2A2D36) else Color(0xFFF2F4F8)

// 品牌主色：靛蓝
private val INDIGO = Color(0xFF4E6EF2)
private val INDIGO_SOFT: Color get() = if (IsDark) Color(0xFF232A45) else Color(0xFFECEEFE)
private val INDIGO_DEEP: Color get() = if (IsDark) Color(0xFFA8BDF8) else Color(0xFF3A54D4)

// 语义色（主色双模式通用；浅底/深字随主题切换）
private val GREEN = Color(0xFF2EBE86)
private val GREEN_SOFT: Color get() = if (IsDark) Color(0xFF173428) else Color(0xFFE4F7EF)
private val GREEN_DEEP: Color get() = if (IsDark) Color(0xFF7FDCA8) else Color(0xFF179669)
private val AMBER = Color(0xFFF5A623)
private val AMBER_SOFT: Color get() = if (IsDark) Color(0xFF3A2E14) else Color(0xFFFEF3E0)
private val AMBER_DEEP: Color get() = if (IsDark) Color(0xFFFFC96B) else Color(0xFFB7791F)
private val PURPLE = Color(0xFF8B6CF0)
private val PURPLE_SOFT: Color get() = if (IsDark) Color(0xFF292348) else Color(0xFFF0ECFD)
private val PURPLE_DEEP: Color get() = if (IsDark) Color(0xFFBBA6F5) else Color(0xFF6B4BD4)
private val RED = Color(0xFFF25757)
private val RED_SOFT: Color get() = if (IsDark) Color(0xFF3A1F1F) else Color(0xFFFDECEC)
private val RED_DEEP: Color get() = if (IsDark) Color(0xFFF19994) else Color(0xFFD43C3C)

// 柱状图主色明度序列（低 → 高）
private val BAR_RAMP = listOf(Color(0xFFC9D4FB), Color(0xFF93A8F6), Color(0xFF4E6EF2), Color(0xFF3A54D4))

// 空闲时间轴绿序列
private val FREE_BIG = Color(0xFF2EBE86)
private val FREE_SHORT = Color(0xFF8FDCC0)
private val FREE_FRAG = Color(0xFFF5C451)
private val BUSY_TRACK: Color get() = if (IsDark) Color(0xFF2A2D36) else Color(0xFFE3E7EF)

private val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

/** 统计数字统一等宽数字轴（tnum）：多行数值天然纵向对齐 */
private val NUM_STYLE = TextStyle(fontFeatureSettings = "tnum")

/** 进度类元素的单次入场补间（600ms，只在数据变化时重放；无循环动画） */
@Composable
private fun animateFraction(target: Float): androidx.compose.runtime.State<Float> {
    val frac = remember { Animatable(0f) }
    LaunchedEffect(target) {
        frac.snapTo(0f)
        frac.animateTo(target.coerceIn(0f, 1f), tween(600, easing = FastOutSlowInEasing))
    }
    return frac.asState()
}

/** 程度色阶：0–40 绿 / 40–70 琥珀 / 70–100 红（分数越高越严重时使用） */
private fun degreeColor(score: Int): Color = when {
    score <= 40 -> GREEN
    score <= 70 -> AMBER
    else -> RED
}

/** 反向程度（分数越高越健康）：70+ 绿 / 40–69 琥珀 / <40 红 */
private fun healthColor(score: Int): Color = when {
    score >= 70 -> GREEN
    score >= 40 -> AMBER
    else -> RED
}

private fun deepOf(base: Color): Color = when (base) {
    GREEN -> GREEN_DEEP
    AMBER -> AMBER_DEEP
    RED -> RED_DEEP
    PURPLE -> PURPLE_DEEP
    else -> INDIGO_DEEP
}

private fun softOf(base: Color): Color = when (base) {
    GREEN -> GREEN_SOFT
    AMBER -> AMBER_SOFT
    RED -> RED_SOFT
    PURPLE -> PURPLE_SOFT
    else -> INDIGO_SOFT
}

private val typeColor: (String) -> Color = { t ->
    when (t) {
        CourseType.MAJOR -> INDIGO
        CourseType.PUB -> GREEN
        CourseType.ELE -> PURPLE
        else -> AMBER
    }
}

private val typeDeep: (String) -> Color = { t -> deepOf(typeColor(t)) }

// ============================================================ 入口

/** 统计区：嵌入「发现」页（课程异动置顶之下），滚动与底栏留白由宿主页负责 */
@Composable
fun StatsContent(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val schedule = state.schedule
    val bundle = remember(schedule, state.week, state.settings.termWeeks, state.settings) {
        if (schedule == null) null
        else StatsCalculator.compute(schedule.courses, state.week, state.settings.termWeeks, state.settings.periodTimes())
    }

    var showMetaSheet by remember { mutableStateOf(false) }
    var showBusyDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "课表统计",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            // 小节标题直接坐在页面背景上，深色模式跟随主题（卡片内文字仍用 Token 色）
            color = InkDark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            if (bundle?.hasData == true) "第 ${bundle.week} 周 · 共 ${bundle.weekCourses.size} 门课" else "暂无课表数据",
            fontSize = 12.sp,
            color = TEXT_WEAK,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 3.dp),
        )
        Spacer(Modifier.height(12.dp))

        when {
            schedule == null -> EmptyCard("尚未同步课表\n去「课程表」页登录并同步后查看统计")
            bundle == null || !bundle.hasData -> EmptyCard("本周没有课程\n切到有课的周次再来查看统计")
            else -> {
                val portrait = remember(schedule, state.settings.courseMeta) {
                    StatsCalculator.typePortrait(schedule.courses, state.settings.courseMeta)
                }
                // 学费按教务专业名自动匹配收费标准（专业 / 班级 / 学院文本最长关键词匹配）
                val student = schedule.student
                val tuitionInfo = remember(schedule) {
                    MajorFee.lookup(student.major, student.className, student.department)
                }
                val tuition = tuitionInfo?.second ?: state.settings.tuitionFee
                val progress = remember(schedule, state.week, state.settings.termWeeks) {
                    StatsCalculator.semesterProgress(schedule.courses, state.week, state.settings.termWeeks)
                }
                // 两个趣味指数：统计范围跟随「显示周六/周日」设置
                val statDays = if (state.settings.showWeekend) 7 else 5
                val health = remember(schedule, state.week, state.settings) {
                    StatsCalculator.healthIndex(
                        schedule.courses, state.week, state.settings.periodTimes(), statDays,
                    )
                }
                val prison = remember(schedule, state.week, state.settings) {
                    StatsCalculator.prisonIndex(
                        schedule.courses, state.week, state.settings.periodTimes(), statDays,
                    )
                }
                val freeBlocks = remember(schedule, state.week, state.settings) {
                    StatsCalculator.freeBlockAnalysis(
                        schedule.courses, state.week, state.settings.periodTimes(), statDays,
                        state.settings.busyBlocks,
                    )
                }
                SemesterCard(progress, tuition)
                Spacer(Modifier.height(14.dp))
                TuitionCard(
                    courses = schedule.courses,
                    week = state.week,
                    progress = progress,
                    tuition = tuition,
                )
                Spacer(Modifier.height(14.dp))
                PeriodProfileCard(bundle)
                Spacer(Modifier.height(14.dp))
                TypeCard(portrait) { showMetaSheet = true }
                Spacer(Modifier.height(14.dp))
                WeekDensityCard(bundle)
                Spacer(Modifier.height(14.dp))
                HealthIndexCard(health)
                Spacer(Modifier.height(14.dp))
                PrisonIndexCard(prison)
                Spacer(Modifier.height(14.dp))
                FreeTimeCard(
                    analysis = freeBlocks,
                    busyBlocks = state.settings.busyBlocks,
                    weekDays = statDays,
                    onAdd = { showBusyDialog = true },
                    onRemove = { viewModel.removeBusyBlock(it) },
                )
            }
        }

        Spacer(Modifier.height(80.dp))

        if (showBusyDialog) {
            BusyBlockDialog(
                weekDays = if (state.settings.showWeekend) 7 else 5,
                onConfirm = { title, day, s, e -> viewModel.addBusyBlock(title, day, s, e) },
                onDismiss = { showBusyDialog = false },
            )
        }
        if (showMetaSheet && schedule != null) {
            MetaSheet(
                courses = schedule.courses,
                metaMap = state.settings.courseMeta,
                onSave = { name, meta -> viewModel.setCourseMeta(name, meta) },
                onDismiss = { showMetaSheet = false },
            )
        }
    }
}

// ============================================================ 通用组件

/** 统一卡片：白底 16dp 圆角 + 图标标题 */
@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PAGE_CARD)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(22.dp), tint = iconColor)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TEXT_MAIN, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 评价胶囊：浅底 + 同色系深字（可带小图标） */
@Composable
private fun SoftPill(
    text: String,
    soft: Color,
    deep: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(soft)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(11.dp), tint = deep)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = deep)
    }
}

/** 两段式趣味标签：灰色小前缀 + 评价胶囊 */
@Composable
private fun EvalTag(prefix: String, value: String, base: Color) {
    Column {
        Text(prefix, fontSize = 10.sp, color = TEXT_WEAK)
        Spacer(Modifier.height(4.dp))
        SoftPill(value, softOf(base), deepOf(base))
    }
}

/** 数据行：左标签 + 右对齐数值（隐形右轴），解释小字排在数值下方 */
@Composable
private fun StatLine(
    label: String,
    value: String,
    sub: String,
    valueColor: Color = TEXT_MAIN,
    dot: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, fontSize = 13.sp, color = TEXT_SUB, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                style = NUM_STYLE,
            )
            Text(sub, fontSize = 10.sp, color = TEXT_WEAK, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/** 横向条形：轨道 #EDF0F5 / 8dp 高 / 全圆角，填充可带渐变 */
@Composable
private fun HBar(
    fraction: Float,
    fill: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(TRACK)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(fill)
        )
    }
}

@Composable
private fun EmptyCard(text: String) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PAGE_CARD)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 14.sp, color = TEXT_WEAK, lineHeight = 22.sp)
    }
}

// ============================================================ 学期进度追踪

/** 学期进度追踪卡：进度条 + 满课天数换算 + 考试周倒计时 + 学费回本 */
@Composable
private fun SemesterCard(
    b: StatsCalculator.SemesterProgress,
    tuition: Int,
) {
    val perLesson = if (b.totalLessons > 0) tuition.toDouble() / b.totalLessons else 0.0
    val consumed = perLesson * b.doneLessons
    StatCard(Icons.Outlined.TrendingUp, "学期进度追踪", INDIGO) {
        Spacer(Modifier.height(4.dp))

        // 进度条：与「课程类型画像 · 专业课 vs 水课」同款——双极渐变条 + 白描边滑块
        // 左端 = 开学，右端 = 期末；滑块停在「已上百分比」处（600ms 入场补间）
        val frac = animateFraction(b.percent / 100f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(INDIGO, Color(0xFF8FDCC0))),
                cornerRadius = CornerRadius(4.dp.toPx()),
                size = size,
            )
            val x = (size.width * frac.value).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx())
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, size.height / 2))
            drawCircle(
                TRACK,
                radius = 6.dp.toPx(),
                center = Offset(x, size.height / 2),
                style = Stroke(1.dp.toPx()),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                "已上 ${b.doneLessons} 节 · ${b.percent}%",
                fontSize = 10.sp,
                color = INDIGO_DEEP,
                style = NUM_STYLE,
                modifier = Modifier.weight(1f),
            )
            Text(
                "剩 ${b.remainLessons} 节",
                fontSize = 10.sp,
                color = GREEN_DEEP,
                style = NUM_STYLE,
            )
        }

        Spacer(Modifier.height(8.dp))
        StatLine("相当于全天满课", "${b.daysEquivalent} 天", "按每天 8 节满课换算")
        StatLine("考试周倒计时", "${b.weeksToExam} 周后", "考前还剩 ${b.remainBeforeExam} 节")
        StatLine(
            "学费回本进度",
            "¥${kotlin.math.floor(consumed).toInt()}",
            "每节约 ¥${"%.1f".format(perLesson)}",
            valueColor = AMBER,
        )
    }
}

// ============================================================ 学费换算

/** 学费换算卡：每节课价值大数字 + 本周每日价值排行 + 学费燃烧进度条 + 趣味文案 */
@Composable
private fun TuitionCard(
    courses: List<com.fengkao.timetable.data.model.CourseBlock>,
    week: Int,
    progress: StatsCalculator.SemesterProgress,
    tuition: Int,
) {
    val perLesson = if (progress.totalLessons > 0) tuition.toDouble() / progress.totalLessons else 0.0

    // 本周每日节数（单双周过滤：week in weeks）
    val daily = List(7) { d ->
        courses.filter { it.dayOfWeek == d + 1 && week in it.weeks }
            .sumOf { it.endPeriod - it.startPeriod + 1 }
    }
    val todayIdx = (java.time.LocalDate.now().dayOfWeek.value - 1)
    val todayLessons = daily.getOrElse(todayIdx) { 0 }
    val todayValue = todayLessons * perLesson
    val maxValue = (daily.maxOrNull() ?: 0) * perLesson

    StatCard(Icons.Outlined.AccountBalanceWallet, "学费换算", AMBER) {
        Spacer(Modifier.height(2.dp))

        // 每节课价值（大数字，金额固定琥珀）
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.1f".format(perLesson),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AMBER,
                lineHeight = 44.sp,
                letterSpacing = (-0.5).sp,
                style = NUM_STYLE,
            )
            Text(
                " 元 / 节",
                fontSize = 13.sp,
                color = TEXT_WEAK,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Text(
            "总学费 ¥$tuition ÷ ${progress.totalLessons} 节课",
            fontSize = 10.sp,
            color = TEXT_WEAK,
        )

        Spacer(Modifier.height(12.dp))

        // 本周每日上课价值排行（琥珀序列条形图）
        Text("本周每日上课价值", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TEXT_MAIN)
        Spacer(Modifier.height(8.dp))
        val sorted = daily.indices.filter { daily[it] > 0 }.sortedByDescending { daily[it] }
        sorted.forEach { d ->
            val value = daily[d] * perLesson
            val isToday = d == todayIdx
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "周${DAY_NAMES[d]}" + if (isToday) " ·今天" else "",
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) AMBER_DEEP else TEXT_SUB,
                    modifier = Modifier.width(78.dp),
                )
                HBar(
                    if (maxValue > 0) (value / maxValue).toFloat() else 0f,
                    Brush.horizontalGradient(
                        if (isToday) listOf(Color(0xFFFFD666), Color(0xFFE8890C))
                        else listOf(AMBER.copy(alpha = 0.55f), AMBER)
                    ),
                    Modifier.weight(1f),
                )
                Text(
                    "¥%.0f".format(value),
                    fontSize = 10.sp,
                    color = TEXT_WEAK,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        if (sorted.isEmpty()) {
            Text("本周没有课，一分钱都不用上", fontSize = 11.sp, color = TEXT_WEAK)
        }

        Spacer(Modifier.height(14.dp))

        // 学费燃烧进度条（琥珀渐变 + 火焰图标）
        val consumed = perLesson * progress.doneLessons
        val burn = animateFraction(progress.percent / 100f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                Modifier.size(14.dp),
                tint = AMBER,
            )
            Spacer(Modifier.width(6.dp))
            HBar(burn.value, Brush.horizontalGradient(listOf(Color(0xFFFFD666), Color(0xFFE8890C))), Modifier.weight(1f))
            Text(
                " ${progress.percent}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AMBER_DEEP,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("已燃烧 ¥${kotlin.math.floor(consumed).toInt()} / ¥$tuition")
                append('\n')
                append(
                    when {
                        todayLessons == 0 -> "今天没课，学费一分没烧，纯赚"
                        else -> "今天上了 $todayLessons 节课，赚回 ¥${"%.0f".format(todayValue)} 学费"
                    }
                )
            },
            fontSize = 11.sp,
            color = TEXT_SUB,
            lineHeight = 17.sp,
        )
    }
}

// ============================================================ 时段分布画像

/** 时段分布画像卡：趣味标签 → 饼图 → 连堂/空档 */
@Composable
private fun PeriodProfileCard(b: StatsCalculator.Bundle) {
    val total = b.morningLessons + b.afternoonLessons + b.eveningLessons
    StatCard(Icons.Outlined.WbTwilight, "时段分布画像", INDIGO) {
        // 趣味标签（评价胶囊）
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvalTag("早八受害者指数 ${b.earlyPercent}%", b.earlyLevel, degreeColor(b.earlyPercent))
            Spacer(Modifier.width(8.dp))
            EvalTag("熬夜课等级", b.lateLevel, if ("从不" in b.lateLevel) GREEN else PURPLE)
        }
        Spacer(Modifier.height(14.dp))

        // 饼图 + 图例（0 值扇区不绘制，图例置灰）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 24.dp.toPx()
                    val inset = stroke / 2
                    val rect = Size(size.width - inset * 2, size.height - inset * 2)
                    val segs = listOf(
                        b.morningLessons to INDIGO,
                        b.afternoonLessons to AMBER,
                        b.eveningLessons to PURPLE,
                    )
                    var startAngle = -90f
                    segs.forEach { (n, color) ->
                        val sweep = n * 360f / total.coerceAtLeast(1)
                        if (sweep > 0.5f) {
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = rect,
                                style = Stroke(stroke),
                            )
                        }
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$total", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TEXT_MAIN)
                    Text("节 / 周", fontSize = 9.sp, color = TEXT_WEAK)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                LegendRow(INDIGO, "上午", b.morningLessons, total)
                LegendRow(AMBER, "下午", b.afternoonLessons, total)
                LegendRow(PURPLE, "晚间", b.eveningLessons, total)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 连堂 / 空档
        StatLine("最长连堂", "${b.maxConsecutive} 节", "出现 ${b.consecutiveCount} 次连堂")
        StatLine("尴尬空档", "${b.gapCount} 次", "累计 ${b.gapMinutes} 分钟")
    }
}

/** 图例行：8dp 圆角方块 + 名称，百分比右对齐；0 值整行置灰 */
@Composable
private fun LegendRow(color: Color, label: String, n: Int, total: Int) {
    val off = n == 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (off) LEGEND_OFF else color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$label $n 节",
            fontSize = 12.sp,
            color = if (off) LEGEND_OFF else TEXT_MAIN,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (total > 0) "${(n * 100 + total / 2) / total}%" else "0%",
            fontSize = 11.sp,
            color = if (off) LEGEND_OFF else TEXT_WEAK,
        )
    }
}

// ============================================================ 课程类型画像

/** 课程类型画像卡：双饼图（课时/学分）+ 专业课 vs 水课滑块 + 学分含金量 */
@Composable
private fun TypeCard(
    p: StatsCalculator.TypePortrait,
    onEdit: () -> Unit,
) {
    StatCard(
        Icons.Outlined.MenuBook,
        "课程类型画像",
        INDIGO,
        trailing = {
            Text(
                "调整标签",
                fontSize = 11.sp,
                color = INDIGO,
                modifier = Modifier.clickable(onClick = onEdit).padding(2.dp),
            )
        },
    ) {
        Spacer(Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TypePie(p, by = "lessons", title = "课时占比")
            Spacer(Modifier.width(14.dp))
            TypePie(p, by = "credits", title = "学分占比")
        }
        Spacer(Modifier.height(12.dp))

        p.items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(typeColor(item.type)))
                Spacer(Modifier.width(8.dp))
                Text(
                    com.fengkao.timetable.data.model.CourseType.label(item.type),
                    fontSize = 12.sp,
                    color = TEXT_MAIN,
                    modifier = Modifier.weight(1f),
                )
                Text("${item.lessons} 节", fontSize = 11.sp, color = TEXT_SUB)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (item.credits > 0.0) "${"%.1f".format(item.credits)} 学分" else "学分未设",
                    fontSize = 11.sp,
                    color = TEXT_WEAK,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        val total = (p.majorLessons + p.softLessons).coerceAtLeast(1)
        val majorPct = p.majorLessons * 100 / total
        Text("专业课 vs 水课", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TEXT_MAIN)
        Spacer(Modifier.height(8.dp))

        // 双极渐变条 + 白描边滑块
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(INDIGO, Color(0xFF8FDCC0))),
                cornerRadius = CornerRadius(4.dp.toPx()),
                size = size,
            )
            val x = (size.width * majorPct / 100f).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx())
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, size.height / 2))
            drawCircle(
                TRACK,
                radius = 6.dp.toPx(),
                center = Offset(x, size.height / 2),
                style = Stroke(1.dp.toPx()),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                "专业课 $majorPct%",
                fontSize = 10.sp,
                color = INDIGO_DEEP,
                modifier = Modifier.weight(1f),
            )
            Text("水课 ${100 - majorPct}%", fontSize = 10.sp, color = GREEN_DEEP)
        }

        Spacer(Modifier.height(10.dp))
        val review = when {
            p.creditsGold >= 22.0 -> "地狱修罗场，学分含金量拉满"
            p.creditsGold >= 18.0 -> "标准浓度，学分扎实"
            p.creditsGold >= 12.0 -> "还算轻松，稳步毕业"
            else -> "学分近乎白送，悠着点玩"
        }
        StatLine("学分含金量", "每学分 ≈ ${"%.1f".format(p.creditsGold)} 节课", review)
    }
}

@Composable
private fun TypePie(
    p: StatsCalculator.TypePortrait,
    by: String,
    title: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(86.dp)) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val rect = Size(size.width - inset * 2, size.height - inset * 2)
                val total = when (by) {
                    "credits" -> p.totalCredits.toFloat()
                    else -> p.totalLessons.toFloat()
                }.coerceAtLeast(0.001f)
                var startAngle = -90f
                p.items.forEach { item ->
                    val v = when (by) {
                        "credits" -> item.credits.toFloat()
                        else -> item.lessons.toFloat()
                    }
                    val sweep = v * 360f / total
                    if (sweep > 0.5f) {
                        drawArc(
                            color = typeColor(item.type),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = rect,
                            style = Stroke(stroke),
                        )
                    }
                    startAngle += sweep
                }
            }
        }
        Text(title, fontSize = 10.sp, color = TEXT_WEAK, modifier = Modifier.padding(top = 4.dp))
    }
}

// ============================================================ 周度密度

/** 周度密度卡：柱状图 + 死亡/快乐日 + 单双周对比 + 课间换算 */
@Composable
private fun WeekDensityCard(b: StatsCalculator.Bundle) {
    StatCard(Icons.Outlined.Equalizer, "周度密度", INDIGO) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.deathDay?.let { SoftPill("死亡星期${DAY_NAMES[it]}", RED_SOFT, RED_DEEP) }
            b.happyDay?.let { SoftPill("快乐星期${DAY_NAMES[it]}", GREEN_SOFT, GREEN_DEEP) }
        }
        Spacer(Modifier.height(8.dp))

        // 柱状图：蓝色明度序列编码节数；最大值红 / 最小值绿；0 值灰点
        // 顶部预留 20dp 给数值标签，避免与上方胶囊重叠
        val maxV = (b.dailyLessons.maxOrNull() ?: 0).coerceAtLeast(1)
        val minV = b.dailyLessons.filter { it > 0 }.minOrNull() ?: -1
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val gap = 14.dp.toPx()
            val plotH = size.height - 20.dp.toPx()
            val barW = (size.width - gap * 6) / 7
            for (d in 0 until 7) {
                val v = b.dailyLessons[d]
                val x = d * (barW + gap)
                val color = when {
                    v == 0 -> ZERO_GRAY
                    v == maxV -> RED
                    v == minV -> GREEN
                    else -> when {
                        v * 100 / maxV <= 25 -> BAR_RAMP[0]
                        v * 100 / maxV <= 50 -> BAR_RAMP[1]
                        v * 100 / maxV <= 75 -> BAR_RAMP[2]
                        else -> BAR_RAMP[3]
                    }
                }
                if (v == 0) {
                    // 0 值：基线短虚线小圆点 + 「0」
                    drawRoundRect(
                        color = ZERO_GRAY,
                        topLeft = Offset(x + barW / 2 - 4.dp.toPx() / 2, size.height - 3.dp.toPx()),
                        size = Size(4.dp.toPx(), 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "0", x + barW / 2, size.height - 8.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(android.graphics.Color.argb(255, 0xC2, 0xC9, 0xD6))
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 10.sp.toPx()
                            isAntiAlias = true
                        },
                    )
                } else {
                    val h = plotH * v / maxV
                    // 顶部 4dp 圆角（底部平齐：矩形下延后裁切）
                    clipRect(left = x, top = 0f, right = x + barW, bottom = size.height) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, size.height - h),
                            size = Size(barW, h + 4.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "$v", x + barW / 2, size.height - h - 8.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(android.graphics.Color.argb(255, 0x1F, 0x24, 0x30))
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 10.sp.toPx()
                            isAntiAlias = true
                        },
                    )
                }
                // 星期标签（与柱同 x，保证对齐）
                drawContext.canvas.nativeCanvas.drawText(
                    DAY_NAMES[d], x + barW / 2, size.height + 12.sp.toPx(),
                    android.graphics.Paint().apply {
                        setColor(android.graphics.Color.argb(255, 0x9A, 0xA3, 0xB2))
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                    },
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        // 单双周对比
        StatLine(
            "单周 / 双周",
            "${b.oddLessons} 节 / ${b.evenLessons} 节",
            when {
                b.oddLessons == b.evenLessons -> "单双周一样多"
                else -> {
                    val diff = b.oddLessons - b.evenLessons
                    val base = b.evenLessons.coerceAtLeast(1)
                    val pct = (kotlin.math.abs(diff) * 100 + base / 2) / base
                    (if (diff > 0) "单周多 " else "双周多 ") + "$pct%"
                }
            },
        )

        // 课间换算
        StatLine(
            "周课间累计",
            "${b.breakMinutes} 分钟",
            "≈ 短视频 ${b.breakMinutes * 60 / 15} 条 · 课间餐 ${b.breakMinutes / 20} 份",
        )
    }
}

// ============================================================ 学期进度追踪（完整版见 SemesterCard）

// ============================================================ 作息健康指数

private fun nightTone(level: Int): Color = when (level) {
    0 -> GREEN
    1 -> AMBER
    else -> PURPLE
}

/** 作息健康指数卡：健康评分环 + 早餐缺失率 / 熬夜风险 / 喘息半天 + 个性化建议 */
@Composable
private fun HealthIndexCard(h: StatsCalculator.HealthIndex) {
    val color = healthColor(h.score)
    StatCard(Icons.Outlined.Eco, "作息健康指数", GREEN) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(h.score, color, "健康分")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                SoftPill(h.level, softOf(color), deepOf(color))
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftPill(h.breakfastTag, GREEN_SOFT, GREEN_DEEP)
                    SoftPill("熬夜${h.nightRisk}", softOf(nightTone(h.nightRiskLevel)), deepOf(nightTone(h.nightRiskLevel)))
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "一周 ${h.weekDays} 天里，有 ${h.breathHalfDays} 个半天能喘口气",
                    fontSize = 11.sp,
                    color = TEXT_WEAK,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        StatLine(
            "早餐缺失率",
            "${h.breakfastMissRate}%",
            if (h.earlyDays == 0) "本周没有早八，早餐自由"
            else "${h.earlyDays} 天有早八 · 最早 ${h.earliestStartText}",
        )
        StatLine(
            "最晚下课",
            h.latestEndText,
            if (h.latestDay != null) "周${DAY_NAMES[h.latestDay]} · 熬夜${h.nightRisk}"
            else "本周无课，完全不熬夜",
        )
        StatLine(
            "喘息半天",
            "${h.breathHalfDays} 个",
            "共 ${h.totalHalfDays} 个半天 · 空闲 ${h.breathHalfDays * 100 / h.totalHalfDays.coerceAtLeast(1)}%",
        )

        Spacer(Modifier.height(10.dp))
        AdviceBlock(h.advice)
    }
}

/** 环形评分：轨道 #EDF0F5 + 进度弧 + 中心大数字（深色系字） */
@Composable
private fun ScoreRing(score: Int, color: Color, label: String) {
    val sweepState = animateFraction(score / 100f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val rect = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = TRACK,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = rect,
                style = Stroke(stroke),
            )
            val sweep = 360f * sweepState.value
            if (sweep > 0.5f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = rect,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = deepOf(color),
                lineHeight = 32.sp,
                style = NUM_STYLE,
            )
            Text(label, fontSize = 9.sp, color = TEXT_WEAK)
        }
    }
}

/** 卡片底部的建议 / 吐槽块：#F6F7F9 浅灰圆角提示条 */
@Composable
private fun AdviceBlock(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SURFACE_ALT)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = TEXT_SUB, lineHeight = 17.sp)
    }
}

// ============================================================ 课程坐牢指数

/** 课程坐牢指数卡：指数大数字 + 一周坐牢日历 + 分类统计 + 连堂明细 + 吐槽文案 */
@Composable
private fun PrisonIndexCard(p: StatsCalculator.PrisonIndex) {
    val color = degreeColor(p.score)
    StatCard(Icons.Outlined.EventSeat, "课程坐牢指数", RED) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${p.score}",
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                style = NUM_STYLE,
            )
            Text(" 分", fontSize = 13.sp, color = TEXT_WEAK, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            SoftPill(p.level, softOf(color), deepOf(color))
        }
        Spacer(Modifier.height(10.dp))

        // 坐牢程度条（程度色阶）
        val prisonFill = animateFraction(p.score / 100f)
        HBar(prisonFill.value, Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))

        Spacer(Modifier.height(14.dp))

        // 一周坐牢日历条（上午=靛蓝 / 下午=琥珀 / 全天=红）
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            p.dayFlags.forEachIndexed { d, flag ->
                val (bg, fg) = when (flag) {
                    StatsCalculator.DAY_FULL -> RED to Color.White
                    StatsCalculator.DAY_AM -> INDIGO to Color.White
                    StatsCalculator.DAY_PM -> AMBER to Color.White
                    StatsCalculator.DAY_BUSY -> BUSY_TRACK to TEXT_SUB
                    else -> Color(0xFFF2F4F8) to TEXT_WEAK
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(DAY_NAMES[d], fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            LegendDot(INDIGO, "上午坐牢 ${p.amPrisonDays} 天")
            LegendDot(AMBER, "下午坐牢 ${p.pmPrisonDays} 天")
            LegendDot(RED, "全天坐牢 ${p.fullPrisonDays} 天")
        }

        Spacer(Modifier.height(10.dp))
        StatLine(
            "4 节连堂",
            "${p.chain4Count} 次",
            if (p.chain4Count == 0) "没有连着 4 节的课，非常自由"
            else "一周有 ${p.chain4Count} 段连着 4 节以上",
        )
        StatLine(
            "最长连续上课",
            p.maxContinuousText,
            if (p.maxContinuousDay != null) "周${DAY_NAMES[p.maxContinuousDay]} · 连着 ${p.maxContinuousPeriods} 节（已扣午休）"
            else "本周无课",
        )

        Spacer(Modifier.height(10.dp))
        AdviceBlock(p.roast)
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = TEXT_WEAK)
    }
}

// ============================================================ 空闲时间块分析

private val TIME_RE = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

private fun fmtDur(min: Int): String = when {
    min <= 0 -> "0 分钟"
    min < 60 -> "$min 分钟"
    else -> {
        val h = min / 60
        val m = min % 60
        if (m == 0) "$h 小时" else "$h 小时 $m 分"
    }
}

private fun fmtClock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** 推荐用途胶囊配色 + 图标：午休=琥珀+月亮 / 自习=靛蓝+书本 / 外出=紫+脚步 */
private fun tagStyle(tag: String): Triple<Color, Color, androidx.compose.ui.graphics.vector.ImageVector> = when {
    "午休" in tag -> Triple(AMBER_SOFT, AMBER_DEEP, Icons.Outlined.NightsStay)
    "自习" in tag -> Triple(INDIGO_SOFT, INDIGO_DEEP, Icons.Outlined.MenuBook)
    else -> Triple(PURPLE_SOFT, PURPLE_DEEP, Icons.Outlined.DirectionsWalk)
}

/**
 * 空闲时间块分析卡：周总/日均空闲 + 每日时间轴（绿序列空闲、灰占用）
 * + 三档分类统计 + 大段明细（自动标注推荐用途）+ 用户事务占用管理。
 */
@Composable
private fun FreeTimeCard(
    analysis: StatsCalculator.FreeBlockAnalysis,
    busyBlocks: List<BusyBlock>,
    weekDays: Int,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    StatCard(Icons.Outlined.HourglassEmpty, "空闲时间块分析", GREEN) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "本周空闲 ${fmtDur(analysis.totalFreeMinutes)} · 日均 ${fmtDur(analysis.avgFreeMinutes)}",
                fontSize = 11.sp,
                color = TEXT_WEAK,
                modifier = Modifier.weight(1f),
            )
            Text(
                "添加事务",
                fontSize = 10.sp,
                color = INDIGO,
                modifier = Modifier.clickable(onClick = onAdd).padding(2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // 每日时间轴：窗口 [dayWindowStart, dayWindowEnd]，灰色 = 占用（上课 + 事务）
        val span = (analysis.dayWindowEnd - analysis.dayWindowStart).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..weekDays) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("周${DAY_NAMES[d - 1]}", fontSize = 10.sp, color = TEXT_WEAK, modifier = Modifier.width(26.dp))
                    Canvas(Modifier.weight(1f).height(16.dp)) {
                        drawRoundRect(
                            color = BUSY_TRACK,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                        analysis.blocks.filter { it.dayOfWeek == d }.forEach { b ->
                            val x = (b.startMin - analysis.dayWindowStart) * size.width / span
                            val w = b.minutes * size.width / span
                            when (b.category) {
                                StatsCalculator.BIG -> drawRoundRect(
                                    color = FREE_BIG,
                                    topLeft = Offset(x, 0f),
                                    size = Size(w.coerceAtLeast(2f), size.height),
                                    cornerRadius = CornerRadius(8.dp.toPx()),
                                )
                                StatsCalculator.SHORT -> drawRoundRect(
                                    color = FREE_SHORT,
                                    topLeft = Offset(x, 0f),
                                    size = Size(w.coerceAtLeast(2f), size.height),
                                    cornerRadius = CornerRadius(8.dp.toPx()),
                                )
                                else -> drawRoundRect(
                                    color = FREE_FRAG,
                                    topLeft = Offset(x, (size.height - 4.dp.toPx()) / 2),
                                    size = Size(w.coerceAtLeast(4.dp.toPx()), 4.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "${fmtClock(analysis.dayWindowStart)} – ${fmtClock(analysis.dayWindowEnd)} · 窗口跟随作息表",
            fontSize = 9.sp,
            color = TEXT_WEAK,
            modifier = Modifier.padding(top = 4.dp).align(Alignment.End),
        )
        Spacer(Modifier.height(10.dp))

        // 三档分类统计（前缀圆点与时间轴颜色一一对应）
        StatLine(
            "完整大段空闲",
            "${analysis.bigCount} 段",
            "共 ${fmtDur(analysis.bigMinutes)} · 2 小时以上",
            dot = FREE_BIG,
        )
        StatLine(
            "短空闲",
            "${analysis.shortCount} 段",
            "共 ${fmtDur(analysis.shortMinutes)} · 30 分钟 – 2 小时",
            dot = FREE_SHORT,
        )
        StatLine(
            "碎片化时间",
            "${analysis.fragCount} 段",
            "共 ${fmtDur(analysis.fragMinutes)} · 30 分钟以内",
            dot = FREE_FRAG,
        )

        // 大段明细（含推荐标注）；超过 4 段时默认折叠，点「展开」查看全部
        val bigs = analysis.blocks.filter { it.category == StatsCalculator.BIG }
        if (bigs.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            Text("大段空闲推荐", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TEXT_MAIN)
            Spacer(Modifier.height(4.dp))
            (if (expanded) bigs else bigs.take(4)).forEach { b ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "周${DAY_NAMES[b.dayOfWeek - 1]} ${fmtClock(b.startMin)}–${fmtClock(b.endMin)}",
                        fontSize = 11.sp,
                        color = TEXT_SUB,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmtDur(b.minutes), fontSize = 10.sp, color = TEXT_WEAK)
                    Spacer(Modifier.width(8.dp))
                    b.tag?.let {
                        val (soft, deep, icon) = tagStyle(it)
                        SoftPill(it, soft, deep, icon)
                    }
                }
            }
            if (bigs.size > 4) {
                Text(
                    if (expanded) "收起" else "展开全部 ${bigs.size} 段空闲时间",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = INDIGO,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }

        // 已添加的事务占用
        if (busyBlocks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("已添加事务（每周重复）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TEXT_MAIN)
            Spacer(Modifier.height(4.dp))
            busyBlocks.forEach { blk ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(BUSY_TRACK))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        blk.title,
                        fontSize = 11.sp,
                        color = TEXT_MAIN,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "周${DAY_NAMES[blk.dayOfWeek - 1]} ${fmtClock(blk.startMin)}–${fmtClock(blk.endMin)}",
                        fontSize = 10.sp,
                        color = TEXT_WEAK,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        "删除",
                        fontSize = 10.sp,
                        color = RED_DEEP,
                        modifier = Modifier
                            .clickable { onRemove(blk.id) }
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** 添加事务占用对话框：名称 + 星期 + 起止时间（HH:mm，每周重复） */
@Composable
private fun BusyBlockDialog(
    weekDays: Int,
    onConfirm: (title: String, dayOfWeek: Int, startMin: Int, endMin: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(1) }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun toMin(t: String): Int? {
        val m = TIME_RE.matchEntire(t.trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加事务占用", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TEXT_MAIN) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事务名称（如：社团例会）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Text("星期（每周重复）", fontSize = 12.sp, color = TEXT_WEAK)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in 1..weekDays) {
                        val selected = d == day
                        Text(
                            DAY_NAMES[d - 1],
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else TEXT_SUB,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (selected) INDIGO else SELECTOR_IDLE)
                                .clickable { day = d }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("开始 HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("结束 HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = RED_DEEP)
                }
            }
        },
        confirmButton = {
            Text(
                "添加",
                fontWeight = FontWeight.Bold,
                color = INDIGO,
                modifier = Modifier.clickable {
                    val s = toMin(startText)
                    val e = toMin(endText)
                    when {
                        title.isBlank() -> error = "请填写事务名称"
                        s == null || e == null -> error = "时间格式应为 HH:mm，如 14:30"
                        e <= s -> error = "结束时间需晚于开始时间"
                        else -> {
                            onConfirm(title, day, s, e)
                            onDismiss()
                        }
                    }
                }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        dismissButton = {
            Text("取消", color = TEXT_WEAK, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
        },
    )
}

/** 课程类型标签编辑面板（改动即时保存） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MetaSheet(
    courses: List<com.fengkao.timetable.data.model.CourseBlock>,
    metaMap: Map<String, CourseMeta>,
    onSave: (String, CourseMeta) -> Unit,
    onDismiss: () -> Unit,
) {
    val distinct = courses.distinctBy { it.name }.sortedBy { it.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
            Text("调整课程类型标签", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TEXT_MAIN)
            Text(
                "类型影响画像饼图；学分默认按周学时估算",
                fontSize = 11.sp,
                color = TEXT_WEAK,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(distinct.size) { i ->
                    val c = distinct[i]
                    val init = metaMap[c.name]
                        ?: CourseMeta(
                            type = CourseType.infer(c.name),
                            credits = (c.endPeriod - c.startPeriod + 1).toDouble(),
                        )
                    var type by remember { mutableStateOf(init.type) }
                    var creditsText by remember {
                        mutableStateOf(if (init.credits > 0.0) "%.1f".format(init.credits) else "")
                    }

                    // isHard 已废弃（专业课 vs 水课按类型自动划分），提交时保留原值兼容旧数据
                    fun commit(t: String = type, cr: Double = creditsText.toDoubleOrNull() ?: 0.0) {
                        onSave(c.name, CourseMeta(t, cr, init.isHard))
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SURFACE_ALT)
                            .padding(10.dp)
                    ) {
                        Text(
                            c.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TEXT_MAIN,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CourseType.ALL.forEach { t ->
                                val selected = t == type
                                Text(
                                    CourseType.label(t),
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else TEXT_SUB,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(if (selected) INDIGO else SELECTOR_IDLE)
                                        .clickable {
                                            type = t
                                            commit(t = t)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("学分", fontSize = 11.sp, color = TEXT_WEAK)
                            OutlinedTextField(
                                value = creditsText,
                                onValueChange = {
                                    creditsText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5)
                                    creditsText.toDoubleOrNull()?.let { v -> commit(cr = v) }
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = TEXT_MAIN,
                                ),
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(50.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
            }
        }
    }
}
