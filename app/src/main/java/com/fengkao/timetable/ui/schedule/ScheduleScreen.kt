package com.fengkao.timetable.ui.schedule

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.fengkao.timetable.data.TermCalculator
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.ui.MainViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.fengkao.timetable.ui.glass.GlassElevation
import com.fengkao.timetable.ui.glass.liquidGlassSurface
import com.fengkao.timetable.ui.GlassBarState
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.GlassControlColor
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.GlassSelectedColor
import com.fengkao.timetable.ui.theme.SheetColor
import com.fengkao.timetable.ui.theme.GlassSurfaceColor
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.appBackground
import com.fengkao.timetable.ui.theme.brandGradient
import com.fengkao.timetable.ui.theme.CoursePalette
import com.fengkao.timetable.ui.theme.paletteFor
import com.fengkao.timetable.ui.theme.paletteMapForCourses
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.collectLatest

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// 学期/周次选框 chip 配色（浅/暗自适应，保证深色下可读）
private val ChipBg get() = if (IsDark) Color(0xFF3A3A42) else Color(0xFFEFF0F3)
private val ChipSelBg get() = if (IsDark) Color(0xFF2E5AAC) else Color(0xFFD8E2FF)
private val ChipText get() = if (IsDark) Color(0xFFE6E6ED) else Color(0xFF3C3C43)
private val ChipSelText get() = if (IsDark) Color(0xFFFFFFFF) else Color(0xFF0B2666)

private fun periodRangeText(pts: List<PeriodTime>, s: Int, e: Int): String {
    val st = pts.getOrNull(s - 1)?.startText() ?: "--:--"
    val en = pts.getOrNull(e - 1)?.endText() ?: "--:--"
    return "$st – $en"
}

/** 卡片仅顶部两角圆角，左右与底边铺满屏幕 */
private val CardTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showWeekSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showPlusSheet by remember { mutableStateOf(false) }
    var showAddCourse by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<CourseBlock?>(null) }

    val settings = state.settings
    val term = state.term ?: TermCalculator.autoTerm()
    val termStart = viewModel.termStartOf(settings, term)
    // 教务课表 + 用户手动添加课程（本地）合并展示
    val courses = (state.schedule?.courses.orEmpty()) + state.settings.customCourses.map { it.toCourseBlock() }
    val paletteMap = remember(courses) { paletteMapForCourses(courses) }
    val today = TermCalculator.today()

    // 桌面 Widget 深链：点击 Widget 课程 → 打开对应课程详情
    LaunchedEffect(state.widgetCourseKey) {
        val key = state.widgetCourseKey ?: return@LaunchedEffect
        viewModel.consumeWidgetCourseKey()
        courses.firstOrNull { it.stableKey == key }?.let { detailCourse = it }
    }

    // 梧州学院实时天气（Open-Meteo，无Key；失败自动隐藏）：大 emoji 与详情行都由天气决定
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    LaunchedEffect(Unit) { weather = fetchWuzhouWeather() }

    // 卡片内容的折射源（与底栏完全同机制）
    val scheduleBackdrop = rememberLayerBackdrop()

    // 轻量问候（弱化信息）：三字名取后两字，两字名用全名，无名用“同学”
    val rawName = state.schedule?.student?.name?.trim().orEmpty()
    val displayName = if (rawName.length >= 3) rawName.takeLast(2) else rawName.ifEmpty { "同学" }
    val hour = remember { java.time.LocalTime.now().hour }
    val (greetEmoji, greetingText) = when (hour) {
        in 5..8 -> "☀️" to "早安，$displayName。"
        in 9..11 -> "🌞" to "上午好，$displayName。"
        in 12..13 -> "🍃" to "午安，$displayName。"
        in 14..17 -> "🌤️" to "下午好，$displayName。"
        in 18..19 -> "🌆" to "傍晚好，$displayName。"
        in 20..21 -> "🌙" to "晚上好，$displayName。"
        in 22..22 -> "🌃" to "晚安，$displayName。"
        else -> "🌌" to "夜深了，$displayName。"
    }

    // 一级信息：正在上 / 下一节课状态（每 30 秒刷新）
    val nowClock by produceState(initialValue = java.time.LocalTime.now()) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            value = java.time.LocalTime.now()
        }
    }
    val courseStatus = remember(nowClock, courses, settings) {
        computeCourseStatus(
            courses = courses,
            hidden = settings.hiddenCourses,
            today = TermCalculator.today(),
            now = nowClock,
            termStart = termStart,
            termWeeks = settings.termWeeks,
            pts = settings.periodTimes(),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(2.dp))

        // ---- 头部：大 emoji（完全由天气决定）+ 问候 / 天气详情 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                greetEmoji,
                fontSize = 34.sp,
                // ColorOS 等把 emoji 渲染成方形单色字形：强制用系统彩色 emoji 字体
                fontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-emoji", android.graphics.Typeface.NORMAL)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    greetingText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkDark,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    weather?.line ?: "",
                    fontSize = 12.sp,
                    color = InkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassCircleButton(Icons.Filled.Add, "添加", scheduleBackdrop) { showPlusSheet = true }
        }

        // ---- 动态课程状态区（一级信息：正在上/下一节、多久、在哪） ----
        courseStatus?.let { st ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(
                            backdrop = scheduleBackdrop,
                            elevation = GlassElevation.Control,
                            corner = 18.dp,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 状态行（二级强调）
                    Text(
                        st.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandGreen,
                    )
                    Spacer(Modifier.height(2.dp))
                    // 课程名（一级：最大最粗）
                    Text(
                        st.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (st.timeText.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${st.timeText} · ${st.place}".ifBlank { st.place },
                            fontSize = 12.sp,
                            color = InkMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ---- 周次上下文（二级信息） ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "第 ${state.week} 周",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showWeekSheet = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(term.label, fontSize = 11.sp, color = InkGray)
        }

        // ---- 课表卡片：铺满左右与底部，仅顶部大圆角；左右拖拽切换周次 ----
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(scheduleBackdrop)
                    .background(GlassSurfaceColor, CardTopShape)
                    .border(1.dp, GlassEdge, CardTopShape)
                    .clip(CardTopShape) // 子元素背景（表头/网格）不越过顶部圆角
                    .pointerDragWeekSwitch { delta -> viewModel.changeWeek(delta) }
            ) {
                WeekAnimatedGrid(
                    state = state,
                    courses = courses,
                    paletteMap = paletteMap,
                    termStart = termStart,
                    today = today,
                    onCourseClick = { detailCourse = it },
                )
            }
        }
    }

    // ---- "+" 面板 ----
    if (showPlusSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlusSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("更多操作", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                Spacer(Modifier.height(8.dp))
                PlusAction(Icons.Outlined.Checkroom, "添加课程") {
                    showPlusSheet = false
                    showAddCourse = true
                }
                PlusAction(Icons.Filled.Search, "搜索课程") { showPlusSheet = false; showSearch = true }
                PlusAction(Icons.Outlined.CalendarMonth, "切换学期 / 周次") { showPlusSheet = false; showWeekSheet = true }
                PlusAction(Icons.Filled.Refresh, "立即刷新") { showPlusSheet = false; viewModel.refresh() }
                PlusAction(Icons.Filled.Today, "回到本周") { showPlusSheet = false; viewModel.jumpToday() }
                HorizontalDivider(color = Color(0xFFE0E0E0))
                PlusAction(Icons.AutoMirrored.Outlined.Logout, "退出登录", tint = Color(0xFFD64541)) {
                    showPlusSheet = false
                    viewModel.logout()
                }
            }
        }
    }

    // ---- 学期 / 周次选择 ----
    if (showWeekSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWeekSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("学期", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TermCalculator.selectableTerms(term).forEach { t ->
                        FilterChip(
                            selected = t == term,
                            onClick = {
                                viewModel.selectTerm(t)
                                showWeekSheet = false
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ChipBg,
                                selectedContainerColor = ChipSelBg,
                                labelColor = ChipText,
                                selectedLabelColor = ChipSelText,
                            ),
                            label = { Text("${t.xnm}-${(t.xnm + 1) % 100} 第${t.term}学期") },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("周次", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.jumpToday()
                        showWeekSheet = false
                    }) { Text("回到本周", color = BrandGreen) }
                }
                val realWeek = TermCalculator.weekOf(termStart, today)
                if (today.isBefore(termStart) || realWeek > settings.termWeeks) {
                    Text(
                        if (today.isBefore(termStart)) "本学期尚未开始" else "本学期已结束（共 ${settings.termWeeks} 周）",
                        fontSize = 12.sp,
                        color = InkGray,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (w in 1..settings.termWeeks) {
                        FilterChip(
                            selected = w == state.week,
                            onClick = {
                                viewModel.selectWeek(w)
                                showWeekSheet = false
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ChipBg,
                                selectedContainerColor = ChipSelBg,
                                labelColor = ChipText,
                                selectedLabelColor = ChipSelText,
                            ),
                            label = {
                                Text(if (w == realWeek && !today.isBefore(termStart)) "本周($w)" else "$w")
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- 搜索 ----
    if (showSearch) {
        SearchSheet(
            courses = courses,
            paletteMap = paletteMap,
            periodTimes = settings.periodTimes(),
            onDismiss = { showSearch = false },
            onPick = {
                showSearch = false
                detailCourse = it
            },
        )
    }

    // ---- 添加课程 ----
    if (showAddCourse) {
        AddCourseSheet(
            termWeeks = settings.termWeeks,
            onDismiss = { showAddCourse = false },
            onAdd = {
                viewModel.addCustomCourse(it)
                showAddCourse = false
                Toast.makeText(context, "已添加：${it.name}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // ---- 课程详情 ----
    detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
            periodTimes = settings.periodTimes(),
            onDismiss = { detailCourse = null },
        )
    }
}

// ---------------------------------------------------------------- 周视图网格

@Composable
private fun WeekAnimatedGrid(
    state: com.fengkao.timetable.ui.MainUiState,
    courses: List<CourseBlock>,
    paletteMap: Map<String, CoursePalette>,
    termStart: LocalDate,
    today: LocalDate,
    onCourseClick: (CourseBlock) -> Unit,
) {
    val settings = state.settings
    AnimatedContent(
        targetState = state.week,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            val move = tween<IntOffset>(300, easing = FastOutSlowInEasing)
            (slideInHorizontally(move) { it * dir } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(move) { -it * dir } + fadeOut(tween(180)))
        },
        label = "weekSlide",
    ) { week ->
        val ws = TermCalculator.weekStart(termStart, week)
        val wc = courses.filter { it.coversWeek(week) && it.stableKey !in settings.hiddenCourses }
        val we = ws.plusDays(6)
        val dow = today.takeIf { !it.isBefore(ws) && !it.isAfter(we) }
        val listScroll = rememberScrollState()
        SyncBarScrim(listScroll)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(listScroll)
        ) {
            ScheduleGrid(
                weekCourses = wc,
                weekStart = ws,
                today = dow,
                periodTimes = settings.periodTimes(),
                paletteMap = paletteMap,
                showWeekend = settings.showWeekend,
                onCourseClick = onCourseClick,
            )
            Spacer(Modifier.height(116.dp))
        }
    }
}

// ---------------------------------------------------------------- 通用组件

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "glassBtnScale",
    )
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassSurface(backdrop = backdrop, elevation = GlassElevation.Control)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = InkDark)
    }
}

@Composable
private fun PlusAction(
    icon: ImageVector,
    title: String,
    tint: Color = InkDark,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (tint == InkDark) BrandGreen else tint)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, color = tint)
    }
}

/** 课表滚动 → 底栏遮罩联动（滚动时加深，静止回透） */
@Composable
private fun SyncBarScrim(scrollState: androidx.compose.foundation.ScrollState) {
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collectLatest { scrolling ->
                animate(
                    initialValue = GlassBarState.scrimBoost,
                    targetValue = if (scrolling) 1f else 0f,
                    animationSpec = tween(400),
                ) { v, _ -> GlassBarState.scrimBoost = v }
            }
    }
}

/** 左右拖拽切换上一周/下一周 */
private fun Modifier.pointerDragWeekSwitch(onSwitch: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        var acc = 0f
        detectHorizontalDragGestures(
            onDragStart = { acc = 0f },
            onDragEnd = {
                if (acc < -90f) onSwitch(1) else if (acc > 90f) onSwitch(-1)
            },
        ) { change, dragAmount ->
            acc += dragAmount
            change.consume()
        }
    }

// ---------------------------------------------------------------- 课程详情

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailSheet(
    course: CourseBlock,
    periodTimes: List<PeriodTime>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brandGradient()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MenuBook, null, Modifier.size(26.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        course.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkDark,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${course.teacher.ifBlank { "教师待定" }} · ${course.place.ifBlank { "教室待定" }}",
                        fontSize = 13.sp,
                        color = InkMid,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DetailInfoRow(
                Icons.Outlined.Schedule, "时间",
                "${DAY_NAMES[course.dayOfWeek - 1]} ${periodRangeText(periodTimes, course.startPeriod, course.endPeriod)}（${course.startPeriod}-${course.endPeriod}节）",
            )
            DetailInfoRow(Icons.Outlined.Notes, "周次", course.weeksText.ifBlank { course.weeks.joinToString("、") })
            DetailInfoRow(Icons.Outlined.LocationOn, "地点", course.place.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.Person, "教师", course.teacher.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.LocationCity, "校区", course.campus.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.School, "教学班", course.className.ifBlank { "—" })

            Spacer(Modifier.height(18.dp))
            // ---- 课堂笔记（占位） ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassControlColor)
                    .border(1.dp, GlassEdge, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("课堂笔记", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "全部 >",
                        fontSize = 12.sp,
                        color = BrandGreen,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "课堂笔记功能开发中", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "暂无课堂笔记，点击下方按钮记录本节课的要点",
                    fontSize = 12.sp,
                    color = InkGray,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable {
                            Toast.makeText(context, "课堂笔记功能开发中", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = InkDark)
                        Spacer(Modifier.width(6.dp))
                        Text("添加课堂笔记", fontSize = 13.sp, color = InkDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = InkGray)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = InkGray, modifier = Modifier.width(44.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = InkDark,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------- 搜索

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    courses: List<CourseBlock>,
    paletteMap: Map<String, CoursePalette>,
    periodTimes: List<PeriodTime>,
    onDismiss: () -> Unit,
    onPick: (CourseBlock) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索课程 / 教师 / 教室") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = InkGray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            val trimmed = query.trim()
            val results = if (trimmed.isEmpty()) emptyList() else courses.filter {
                it.name.contains(trimmed, true) ||
                    it.teacher.contains(trimmed, true) ||
                    it.place.contains(trimmed, true)
            }
            if (results.isEmpty()) {
                Text(
                    if (trimmed.isEmpty()) "输入关键词查找本学期课程" else "没有匹配的课程",
                    fontSize = 13.sp,
                    color = InkGray,
                    modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    results.forEach { c ->
                        SearchResultRow(c, periodTimes, paletteMap, onClick = { onPick(c) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    course: CourseBlock,
    periodTimes: List<PeriodTime>,
    paletteMap: Map<String, CoursePalette>,
    onClick: () -> Unit,
) {
    val pal = paletteMap[course.name] ?: paletteFor(course.name)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(pal.container))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(course.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
            Text(
                "${periodRangeText(periodTimes, course.startPeriod, course.endPeriod)} · ${course.place}",
                fontSize = 12.sp,
                color = InkMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(DAY_NAMES[course.dayOfWeek - 1], fontSize = 12.sp, color = InkGray)
        Spacer(Modifier.width(8.dp))
        Text("第${course.startPeriod}-${course.endPeriod}节", fontSize = 12.sp, color = InkGray)
    }
}



// ---------------------------------------------------------------- 动态课程状态

private data class CourseStatus(
    val title: String,
    val name: String,
    val timeText: String,
    val place: String,
)

private fun durationText(minutes: Long): String =
    if (minutes < 60) "${minutes}分钟"
    else "${minutes / 60}小时" + (if (minutes % 60 > 0) "${minutes % 60}分钟" else "")

private fun computeCourseStatus(
    courses: List<CourseBlock>,
    hidden: Set<String>,
    today: LocalDate,
    now: LocalTime,
    termStart: LocalDate,
    termWeeks: Int,
    pts: List<PeriodTime>,
): CourseStatus? {
    if (courses.isEmpty() || pts.isEmpty()) return null
    val realWeek = TermCalculator.weekOf(termStart, today)
    val inTerm = !today.isBefore(termStart) && realWeek <= termWeeks

    fun startOf(p: Int) = pts.getOrNull(p - 1)?.let { LocalTime.of(it.startHour, it.startMinute) }
    fun endOf(p: Int) = pts.getOrNull(p - 1)?.let { LocalTime.of(it.endHour, it.endMinute) }
    fun dayCourses(day: LocalDate, week: Int): List<CourseBlock> =
        courses.filter {
            it.dayOfWeek == day.dayOfWeek.value && week in it.weeks && it.stableKey !in hidden
        }.sortedBy { it.startPeriod }

    if (inTerm) {
        val list = dayCourses(today, realWeek)
        for (c in list) {
            val st = startOf(c.startPeriod)
            val en = endOf(c.endPeriod)
            if (st != null && en != null && !now.isBefore(st) && !now.isAfter(en)) {
                val mins = java.time.Duration.between(now, en).toMinutes().coerceAtLeast(0)
                return CourseStatus(
                    "正在上课 · 还有${durationText(mins)}下课", c.name, "$st–$en", c.place,
                )
            }
        }
        for (c in list) {
            val st = startOf(c.startPeriod) ?: continue
            if (now.isBefore(st)) {
                val mins = java.time.Duration.between(now, st).toMinutes()
                return CourseStatus(
                    "下一节课 · ${durationText(mins)}后", c.name, "$st–${endOf(c.endPeriod)}", c.place,
                )
            }
        }
    }
    for (d in 1..7L) {
        val day = today.plusDays(d)
        val week = TermCalculator.weekOf(termStart, day)
        if (week < 1 || week > termWeeks) continue
        val first = dayCourses(day, week).firstOrNull() ?: continue
        val st = startOf(first.startPeriod) ?: continue
        val dayLabel = if (d == 1L) "明天" else "周${"一二三四五六日"[day.dayOfWeek.value - 1]}"
        return CourseStatus(
            "下一节课 · $dayLabel $st", first.name, "$st–${endOf(first.endPeriod)}", first.place,
        )
    }
    return if (inTerm) CourseStatus("今日课程已结束", "近期无更多课程", "", "")
    else CourseStatus("假期中", "暂无课程安排", "", "")
}

// ---------------------------------------------------------------- 梧州学院天气

@Serializable
private data class WuzhouWeatherResp(
    val current: Current? = null,
    @Suppress("PropertyName") val minutely_15: Minutely? = null,
)

@Serializable
private data class Current(val temperature_2m: Double = 0.0, val weather_code: Int = 0)

@Serializable
private data class Minutely(@Suppress("PropertyName") val precipitation: List<Double> = emptyList())

private object WeatherHttp {
    val client = OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
}

/** 拉取梧州学院（梧州，桂东）当前天气与未来两小时降水；任何失败返回 null 并隐藏天气行 */
private data class WeatherNow(val emoji: String, val line: String)

private suspend fun fetchWuzhouWeather(): WeatherNow? = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=23.873&longitude=111.266" +
            "&current=temperature_2m,weather_code" +
            "&minutely_15=precipitation&forecast_minutely_15=8" +
            "&timezone=Asia%2FShanghai"
        WeatherHttp.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext null
            val w = Json { ignoreUnknownKeys = true }.decodeFromString(WuzhouWeatherResp.serializer(), body)
            val cur = w.current ?: return@withContext null
            val (emoji, desc) = when (cur.weather_code) {
                0 -> "☀️" to "晴"
                1 -> "🌤️" to "晴间多云"
                2 -> "⛅" to "多云"
                3 -> "☁️" to "阴"
                45, 48 -> "🌫️" to "雾"
                in 51..57 -> "🌦️" to "毛毛雨"
                in 61..67, in 80..82 -> "🌧️" to "雨"
                in 71..77, in 85..86 -> "❄️" to "雪"
                in 95..99 -> "⛈️" to "雷雨"
                else -> "⛅" to "多云"
            }
            val slots = w.minutely_15?.precipitation.orEmpty().take(8)
            val firstRain = slots.indexOfFirst { it > 0.05 }
            val advice = when {
                firstRain == 0 -> "记得带伞"
                firstRain > 0 -> "约${firstRain * 15}分钟后有雨"
                else -> ""
            }
            val line = listOf("$emoji ${cur.temperature_2m.roundToInt()}°", desc, advice)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            WeatherNow(emoji, line)
        }
    }.getOrNull()
}


// ---------------------------------------------------------------- 添加课程

/** 手动添加课程：本地保存、与教务课表合并显示，同步不影响 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddCourseSheet(
    termWeeks: Int,
    onDismiss: () -> Unit,
    onAdd: (com.fengkao.timetable.data.model.CustomCourse) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(1) }
    var start by remember { mutableStateOf(1) }
    var count by remember { mutableStateOf(2) }
    var startWeek by remember { mutableStateOf(1) }
    var endWeek by remember { mutableStateOf(termWeeks) }
    var oddOnly by remember { mutableStateOf(false) }
    var evenOnly by remember { mutableStateOf(false) }

    fun commitOdd(o: Boolean) {
        oddOnly = o
        if (o) evenOnly = false
    }
    fun commitEven(e: Boolean) {
        evenOnly = e
        if (e) oddOnly = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("添加课程", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Text(
                "保存在本机，与教务课表合并显示；教务同步不会覆盖",
                fontSize = 11.sp,
                color = InkGray,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("课程名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it.take(20) },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it.take(12) },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("星期", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, d ->
                    val selected = day == i + 1
                    Text(
                        d,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else InkMid,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) BrandGreen else GlassControlColor)
                            .clickable { day = i + 1 }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "节次：第 $start 节 起，共 $count 节（第 ${start + count - 1} 节止）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkDark,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperButton("−") { if (start > 1) start-- }
                Text("$start", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkDark)
                StepperButton("+") { if (start < 12) start++ }
                Spacer(Modifier.width(10.dp))
                StepperButton("−") { if (count > 1) count-- }
                Text("$count", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkDark)
                StepperButton("+") { if (start + count - 1 < 12) count++ }
            }

            Spacer(Modifier.height(14.dp))
            Text("周次：$startWeek - $endWeek 周", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperButton("−") { if (startWeek > 1) startWeek-- }
                Text("$startWeek", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkDark)
                StepperButton("+") { if (startWeek < endWeek) startWeek++ }
                Spacer(Modifier.width(10.dp))
                StepperButton("−") { if (endWeek > startWeek) endWeek-- }
                Text("$endWeek", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkDark)
                StepperButton("+") { if (endWeek < termWeeks) endWeek++ }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekToggle("单周", oddOnly) { commitOdd(!oddOnly) }
                WeekToggle("双周", evenOnly) { commitEven(!evenOnly) }
                WeekToggle("每周", !oddOnly && !evenOnly) { commitOdd(false); commitEven(false) }
            }

            Spacer(Modifier.height(16.dp))
            val valid = name.isNotBlank()
            Text(
                if (valid) "添加课程" else "请填写课程名称",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (valid) Color.White else InkGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (valid) BrandGreen else GlassControlColor)
                    .clickable(enabled = valid) {
                        onAdd(
                            com.fengkao.timetable.data.model.CustomCourse(
                                name = name.trim(),
                                place = place.trim(),
                                teacher = teacher.trim(),
                                dayOfWeek = day,
                                startPeriod = start,
                                endPeriod = start + count - 1,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                oddOnly = oddOnly,
                                evenOnly = evenOnly,
                            )
                        )
                    }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Text(
        symbol,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = InkDark,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(GlassControlColor)
            .clickable(onClick = onClick)
            .wrapContentSize(Alignment.Center),
    )
}

@Composable
private fun WeekToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = if (selected) Color.White else InkMid,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) BrandGreen else GlassControlColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
