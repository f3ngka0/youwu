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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.fengkao.timetable.data.model.CourseNote
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.LocalArrangeMode
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
import com.fengkao.timetable.ui.water.ServiceMessage
import com.fengkao.timetable.ui.xuexitong.NextClassUi
import com.fengkao.timetable.ui.xuexitong.NoticeCenterSheet
import com.fengkao.timetable.ui.xuexitong.SmartNoticeCard
import com.fengkao.timetable.ui.xuexitong.XuexitongLauncher
import com.fengkao.timetable.ui.xuexitong.XuexitongLogin
import com.fengkao.timetable.ui.xuexitong.XuexitongViewModel
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

/** 单节写「第3节」，连堂写「3-5节」：手动加的课常常只有一节，写 3-3 节读起来很怪。 */
private fun periodSpanText(start: Int, end: Int): String =
    if (end <= start) "第${start}节" else "${start}-${end}节"

/** 卡片仅顶部两角圆角，左右与底边铺满屏幕 */
private val CardTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(viewModel: MainViewModel, xuexitongModel: XuexitongViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val xuexitong by xuexitongModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showWeekSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showPlusSheet by remember { mutableStateOf(false) }
    // 画布排课模式：复用主课表本身做编辑器（不是再弹一个填表抽屉）
    var arrange by remember { mutableStateOf<ArrangeState?>(null) }
    // 排课模式下让底栏让位，否则液态玻璃底栏会压住底部表单
    val setArrangeMode = LocalArrangeMode.current
    LaunchedEffect(arrange != null) { setArrangeMode(arrange != null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { setArrangeMode(false) }
    }
    var showNoticeCenter by remember { mutableStateOf(false) }
    var showXuexitongLogin by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<CourseBlock?>(null) }
    // 「改课表」预填：从课表异动卡把新时间/地点带进添加课程表单，用户确认后才落库
    var pendingChange by remember { mutableStateOf<com.fengkao.timetable.data.xuexitong.ScheduleChange?>(null) }

    LaunchedEffect(Unit) { xuexitongModel.enter() }
    LaunchedEffect(state.openNoticesRequested) {
        if (state.openNoticesRequested) {
            showNoticeCenter = true
            viewModel.consumeOpenNotices()
        }
    }
    LaunchedEffect(xuexitong.loggedIn, xuexitong.sessionExpired) {
        if (xuexitong.loggedIn && !xuexitong.sessionExpired) showXuexitongLogin = false
    }

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

    // 笔记提醒通知深链：点通知 → 打开对应课程详情，看笔记
    LaunchedEffect(state.noteCourseRequested) {
        val name = state.noteCourseRequested ?: return@LaunchedEffect
        viewModel.consumeNoteCourseRequest()
        courses.firstOrNull { it.name == name }?.let { detailCourse = it }
    }

    // 课程笔记一次载入常驻：几十条的小文件，进详情页再读会有可见的空白闪一下
    LaunchedEffect(Unit) {
        if (!state.courseNotesLoaded) viewModel.loadCourseNotes()
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
    // 待办倒计时也要跟着走：「还剩 3 小时」不能停在打开页面的那一刻
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = System.currentTimeMillis()
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
    val nextClassUi = courseStatus?.let {
        NextClassUi(
            statusTitle = it.title,
            courseName = it.name,
            timeText = it.timeText,
            place = it.place,
            startTime = it.timeText.substringBefore("–").substringBefore("-").trim(),
            teacher = it.teacher,
            dayPeriod = it.dayPeriod,
            autoFocus = it.autoFocus,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .statusBarsPadding()
    ) {
        if (arrange == null) {
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

        // ---- 智能双模卡片：学习通通知 / 临近上课 ----
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SmartNoticeCard(
                notices = xuexitong.notices,
                locallyReadIds = xuexitong.locallyReadIds,
                unreadCount = xuexitong.unreadCount,
                loggedIn = xuexitong.loggedIn,
                sessionExpired = xuexitong.sessionExpired,
                lastFetchAt = xuexitong.lastFetchAt,
                courses = courses,
                nextClass = nextClassUi,
                tasks = xuexitong.tasks,
                now = nowMillis,
                changesCount = xuexitong.changes.size,
                // 与底栏同一套 backdrop：卡片边缘的折射反射课表内容
                backdrop = scheduleBackdrop,
                onOpenCenter = { showNoticeCenter = true },
                onConnect = { showXuexitongLogin = true },
            )
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
        } else {
            // 排课模式下顶替问候与智能卡的窄条：把可视高度全部让给课表
            ArrangeTopBar(onExit = { arrange = null })
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "第 ${state.week} 周",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkDark,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "点课表空白格开始排课",
                    fontSize = 11.sp,
                    color = InkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    // 排课时不能再左右拖周次：横向手势要让给"拖着色块换天"
                    .then(
                        if (arrange == null) Modifier.pointerDragWeekSwitch { delta -> viewModel.changeWeek(delta) }
                        else Modifier
                    )
            ) {
                WeekAnimatedGrid(
                    state = state,
                    courses = courses,
                    paletteMap = paletteMap,
                    termStart = termStart,
                    today = today,
                    draft = arrange?.let { a ->
                        GridDraft(
                            slots = a.slots,
                            activeIndex = a.activeIndex,
                            conflictIndexes = a.conflictIndexes,
                            label = a.name,
                            place = a.place,
                            onGesture = a::onGesture,
                        )
                    },
                    onCourseClick = { if (arrange == null) detailCourse = it },
                )
            }
        }

        // 底部精简表单：只填"课程信息"，时间已经在上面拖好了
        arrange?.let { a ->
            ArrangePanel(
                state = a,
                periodTimes = settings.periodTimes(),
                onCancel = { arrange = null },
                onSave = {
                    a.build().forEach { viewModel.addCustomCourse(it) }
                    arrange = null
                    val label = a.name.trim()
                    Toast.makeText(context, "已排入课表：$label", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    // ---- 学习通通知中心抽屉 ----
    if (showNoticeCenter) {
        NoticeCenterSheet(
            notices = xuexitong.notices,
            locallyReadIds = xuexitong.locallyReadIds,
            archivedIds = xuexitong.archivedIds,
            doneTaskKeys = xuexitong.doneTaskKeys,
            unreadCount = xuexitong.unreadCount,
            lastFetchAt = xuexitong.lastFetchAt,
            loggedIn = xuexitong.loggedIn,
            sessionExpired = xuexitong.sessionExpired,
            busy = xuexitong.busy,
            courses = courses,
            onDismiss = { showNoticeCenter = false },
            onRefresh = { xuexitongModel.refresh() },
            onMarkAllRead = { xuexitongModel.markAllRead() },
            onMarkRead = xuexitongModel::markRead,
            onArchive = xuexitongModel::archive,
            onUnarchive = xuexitongModel::unarchive,
            onArchiveRead = { xuexitongModel.archiveRead() },
            onTaskDone = { key, done -> xuexitongModel.setTaskDone(key, done) },
            onConnect = {
                showNoticeCenter = false
                showXuexitongLogin = true
            },
            onOpenChaoxing = { XuexitongLauncher.open(context) },
            now = nowMillis,
            onApplyChange = { change ->
                pendingChange = change
                showNoticeCenter = false
                // 直接进画布排课：调课通知的新时间会作为幽灵块涂在课表上
                arrange = buildArrangeState(change, courses, settings.termWeeks, termStart)
                pendingChange = null
            },
        )
    }

    // ---- 学习通登录 ----
    if (showXuexitongLogin) {
        ModalBottomSheet(
            onDismissRequest = { showXuexitongLogin = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                XuexitongLogin(
                    state = xuexitong,
                    model = xuexitongModel,
                    showHeading = true,
                    submitLabel = if (xuexitong.sessionExpired) "重新登录学习通" else "登录学习通",
                )
                ServiceMessage(xuexitong.message, xuexitong.error)
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
                    arrange = buildArrangeState(null, courses, settings.termWeeks, termStart)
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

    // ---- 添加课程（可带「课表异动」预填） ----

    // ---- 课程详情 ----
    detailCourse?.let { course ->
        var noteEditor by remember(course.stableKey) { mutableStateOf<CourseNote?>(null) }
        var noteEditorOpen by remember(course.stableKey) { mutableStateOf(false) }
        // 查看与编辑分两层：点笔记先看（大图/全文），要改再点笔进编辑器
        var noteView by remember(course.stableKey) { mutableStateOf<CourseNote?>(null) }
        val courseNotes = state.courseNotes.filter { it.course == course.name }
        // 详情页在「查看/编辑」开着时收起：两层 BottomSheet 叠在一起，下层卡片会透上来，
        // 看起来像渲染错位；关掉上层（保存/返回）详情页会原样回来
        if (!noteEditorOpen && noteView == null) {
            CourseDetailSheet(
                course = course,
                periodTimes = settings.periodTimes(),
                notes = courseNotes,
                onDismiss = { detailCourse = null },
                onAddNote = {
                    noteEditor = null
                    noteEditorOpen = true
                },
                onOpenNote = { noteView = it },
                onToggleNoteDone = { viewModel.setCourseNoteDone(it.id, !it.done) },
            )
        }
        noteView?.let { viewed ->
            // 笔记可能刚被删除或改了勾选状态，永远按 id 取最新一份
            val live = state.courseNotes.firstOrNull { it.id == viewed.id } ?: viewed
            NoteViewSheet(
                courseName = course.name,
                note = live,
                onDismiss = { noteView = null },
                onEdit = {
                    noteEditor = live
                    noteView = null
                    noteEditorOpen = true
                },
                onDelete = {
                    viewModel.deleteCourseNote(live.id)
                    noteView = null
                },
            )
        }
        if (noteEditorOpen) {
            NoteEditorSheet(
                courseName = course.name,
                existing = noteEditor,
                onDismiss = { noteEditorOpen = false },
                onSave = {
                    viewModel.saveCourseNote(it)
                    noteEditorOpen = false
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    viewModel.deleteCourseNote(it)
                    noteEditorOpen = false
                },
                onImportImage = { bytes -> viewModel.importNoteImage(bytes) },
            )
        }
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
    draft: GridDraft? = null,
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
        // 排课时把草稿时段滚进视野：点「加时段」会挑一段最近的空闲时间（常在 9-11 节），
        // 它可能落在屏幕外，不滚过去用户会以为按钮点了没反应
        val draftTop = draft?.slots?.getOrNull(draft.activeIndex)?.start
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(draftTop) {
            if (draftTop == null) return@LaunchedEffect
            val rowPx = with(density) { ScheduleRowHeight.toPx() }
            // 往上留一行做上下文，别把草稿卡顶到表头下面
            val target = ((draftTop - 2) * rowPx).toInt().coerceAtLeast(0)
            listScroll.animateScrollTo(target)
        }
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
                draft = draft,
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
    notes: List<CourseNote>,
    onDismiss: () -> Unit,
    onAddNote: () -> Unit,
    onOpenNote: (CourseNote) -> Unit,
    onToggleNoteDone: (CourseNote) -> Unit,
) {
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
                // 用课程自己的配色和首字做图标：与课表网格里的色块对得上，
                // 比所有课共用一个蓝色书本更能让人确认「点对了哪门课」
                CourseMonogram(course.name, size = 58.dp, corner = 17.dp, fontSize = 23.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name,
                        fontSize = 19.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${course.teacher.ifBlank { "教师待定" }} · ${course.place.ifBlank { "教室待定" }}",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = InkMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DetailInfoRow(
                Icons.Outlined.Schedule, "时间",
                "${DAY_NAMES[course.dayOfWeek - 1]} ${periodRangeText(periodTimes, course.startPeriod, course.endPeriod)}（${periodSpanText(course.startPeriod, course.endPeriod)}）",
            )
            DetailInfoRow(Icons.Outlined.Notes, "周次", course.weeksText.ifBlank { course.weeks.joinToString("、") })
            DetailInfoRow(Icons.Outlined.LocationOn, "地点", course.place.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.Person, "教师", course.teacher.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.LocationCity, "校区", course.campus.ifBlank { "—" })
            DetailInfoRow(Icons.Outlined.School, "教学班", course.className.ifBlank { "—" })

            Spacer(Modifier.height(18.dp))
            CourseNotesSection(
                notes = notes,
                onAdd = onAddNote,
                onOpen = onOpenNote,
                onToggleDone = onToggleNoteDone,
            )
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
    /** 任课老师：首页卡把它和教室并排做成两个小胶囊。 */
    val teacher: String = "",
    /** 「周一 第2-3节」：直接给课表格子里的坐标，比纯时间更容易对上课程表。 */
    val dayPeriod: String = "",
    /** 正在上课或距上课 ≤ 30 分钟：首页卡片自动切到课程模式。 */
    val autoFocus: Boolean = false,
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

    // 卡片上「周一 第2-3节」这两个信息来自课表格子本身，用户对着课表能立刻定位
    fun dayPeriodOf(day: LocalDate, c: CourseBlock): String {
        val wd = "一二三四五六日"[day.dayOfWeek.value - 1]
        val start = c.startPeriod
        val end = c.endPeriod
        val period = if (end > start) "第${start}-${end}节" else "第${start}节"
        return "周$wd $period"
    }

    if (inTerm) {
        val list = dayCourses(today, realWeek)
        for (c in list) {
            val st = startOf(c.startPeriod)
            val en = endOf(c.endPeriod)
            if (st != null && en != null && !now.isBefore(st) && !now.isAfter(en)) {
                val mins = java.time.Duration.between(now, en).toMinutes().coerceAtLeast(0)
                return CourseStatus(
                    "正在上课 · 还有${durationText(mins)}下课", c.name, "$st–$en", c.place,
                    teacher = c.teacher,
                    dayPeriod = dayPeriodOf(today, c),
                    autoFocus = true,
                )
            }
        }
        for (c in list) {
            val st = startOf(c.startPeriod) ?: continue
            if (now.isBefore(st)) {
                val mins = java.time.Duration.between(now, st).toMinutes()
                return CourseStatus(
                    "下一节课 · ${durationText(mins)}后", c.name, "$st–${endOf(c.endPeriod)}", c.place,
                    teacher = c.teacher,
                    dayPeriod = dayPeriodOf(today, c),
                    autoFocus = mins <= 30,
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
            teacher = first.teacher,
            dayPeriod = dayPeriodOf(day, first),
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



/**
 * 进入画布排课模式：
 * - 从「课表异动」进来时，通知里写明的新时间直接作为幽灵块涂在课表上，用户确认即可；
 *   互调 / 停课 / 待确认的原文没写新时间，就留一个空格让他自己点。
 * - 从「+ 添加课程」进来时，自动找当天最近的一个空位作为起点。
 */
private fun buildArrangeState(
    change: com.fengkao.timetable.data.xuexitong.ScheduleChange?,
    courses: List<CourseBlock>,
    termWeeks: Int,
    termStart: LocalDate,
): ArrangeState {
    val known = change != null && change.applicable
    val day = if (known) change?.toDate?.dayOfWeek?.value else null
    val start = if (known) change?.toStartPeriod else null
    val end = if (known) change?.toEndPeriod else null
    val slot = if (day != null && start != null) {
        ArrangeSlot(day, start, (end ?: start).coerceAtLeast(start))
    } else {
        // 没有指定时间：从今天这一列往下找一段两节的空位
        val todayDay = TermCalculator.today().dayOfWeek.value
        firstFreeSlot(courses, todayDay)
    }
    val week = if (known) {
        TermCalculator.weekOf(termStart, change?.toDate ?: TermCalculator.today())
            .coerceIn(1, termWeeks)
    } else null
    return ArrangeState(
        courses = courses,
        termWeeks = termWeeks,
        prefillName = change?.courseName.orEmpty(),
        prefillPlace = if (known) change?.place.orEmpty() else "",
        prefillSlots = listOf(slot),
        prefillWeek = week,
    )
}

/** 从指定那天开始，找第一段没被占用的两节课 */
private fun firstFreeSlot(courses: List<CourseBlock>, preferredDay: Int): ArrangeSlot {
    val days = listOf(preferredDay, 1, 3, 5, 2, 4, 6, 7).map { ((it - 1) % 7) + 1 }.distinct()
    for (d in days) {
        for (s in 1..11 step 2) {
            val e = s + 1
            val clash = courses.any { c ->
                c.dayOfWeek == d && !(e < c.startPeriod || s > c.endPeriod)
            }
            if (!clash) return ArrangeSlot(d, s, e)
        }
    }
    return ArrangeSlot(preferredDay, 1, 2)
}
