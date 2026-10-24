package com.fengkao.timetable.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.CoursePalette
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.paletteFor
import java.time.LocalDate

private val DAY_CHARS = listOf("一", "二", "三", "四", "五", "六", "日")
private val MONTH_EN = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
)

private val RowHeight = 64.dp

/** 供排课面板把课表滚到当前草稿时段用（网格行高 + 表头高度） */
internal val ScheduleRowHeight = RowHeight
private val LeftColumnWidth = 58.dp
/** 横轴（表头）行高：配合表头文字的显式 lineHeight 收紧，由原 60dp 降到 46dp */
private val HeaderHeight = 46.dp
internal val ScheduleHeaderHeight = HeaderHeight

/** 今日强调（示例图的暖红） */
private val TodayAccent: Color
    get() = if (IsDark) Color(0xFFF08A8A) else Color(0xFFD95F5F)

/** 白色网格底 + 浅灰网格线 */
private val GridBg: Color
    get() = if (IsDark) Color(0xFF1B1B20) else Color.White
private val GridLine: Color
    get() = if (IsDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F1F3)

/**
 * 二维网格（对照参考图）：
 * - 交叉角：月份（数字 + 英文缩写），强调色
 * - 横轴：单字星期 + 日期 + 圆点；周六/周日使用强调色；今日为“今” + 暖红点
 * - 纵轴：无底色，大节次号 + 紧凑起止时间
 * - 白色网格底 + 浅灰网格线；课程为马卡龙色块，教师/教室沉底浅色遮罩内居中
 */
@Composable
fun ScheduleGrid(
    weekCourses: List<CourseBlock>,
    weekStart: LocalDate,
    today: LocalDate?,
    periodTimes: List<PeriodTime>,
    paletteMap: Map<String, CoursePalette> = emptyMap(),
    showWeekend: Boolean = true,
    draft: GridDraft? = null,
    onCourseClick: (CourseBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只由设置决定：勾选“显示周六 / 周日”就恒显示 7 列（即使当周末没课），
    // 避免列数随周次忽宽忽窄、也避免开关看起来“失灵”。
    val shownDays = if (showWeekend) 1..7 else 1..5
    val maxPeriod = maxOf(8, weekCourses.maxOfOrNull { it.endPeriod } ?: 8)
    val narrow = shownDays.last > 5
    val headerBg = if (IsDark) Color(0xFF1C1C20) else Color.White
    val weekendColor = BrandGreen

    Column(modifier) {
        // ---- 横轴：交叉角月份（强调色）+ 单字星期 + 日期 + 圆点 ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
                .background(headerBg)
        ) {
            Box(
                Modifier
                    .width(LeftColumnWidth)
                    .fillMaxHeight(),
                // 月份始终居中于两轴交点
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${weekStart.monthValue}",
                        fontSize = 20.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen,
                    )
                    Text(
                        MONTH_EN[weekStart.monthValue - 1],
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 1.sp,
                        color = BrandGreen.copy(alpha = 0.65f),
                    )
                }
            }
            shownDays.forEach { day ->
                val date = weekStart.plusDays((day - 1).toLong())
                val isToday = today != null && today == date
                val isWeekend = day >= 6
                val dayColor = when {
                    isToday -> TodayAccent
                    isWeekend -> weekendColor
                    else -> InkMid
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isToday) "今" else DAY_CHARS[day - 1],
                            // 横轴字号：14.3sp 调小 20% → 11.44sp
                            fontSize = 11.44.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isToday || isWeekend) FontWeight.SemiBold else FontWeight.Medium,
                            color = dayColor,
                        )
                        Text(
                            "${date.dayOfMonth}",
                            // 横轴字号：10.4sp 调小 20% → 8.32sp
                            fontSize = 8.32.sp,
                            lineHeight = 10.sp,
                            color = when {
                                isToday -> TodayAccent
                                isWeekend -> weekendColor.copy(alpha = 0.75f)
                                else -> InkGray
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isToday -> TodayAccent
                                        else -> InkGray.copy(alpha = 0.35f)
                                    }
                                )
                        )
                    }
                }
            }
        }

        // ---- 主体：时间轴（无底色）+ 白色网格 ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(RowHeight * maxPeriod)
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(LeftColumnWidth)
                        .fillMaxHeight()
                ) {
                    for (p in 1..maxPeriod) {
                        val pt = periodTimes.getOrNull(p - 1)
                        Box(
                            Modifier.fillMaxWidth().height(RowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // 节次号：主信息
                                Text(
                                    "$p",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkDark,
                                )
                                Spacer(Modifier.height(1.dp))
                                // 上课 / 下课时间紧凑排列
                                Text(
                                    pt?.startText() ?: " ",
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = InkMid,
                                )
                                Text(
                                    pt?.endText() ?: " ",
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = InkGray,
                                )
                            }
                        }
                    }
                }

                shownDays.forEach { day ->
                    // 竖向网格线
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(GridLine)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(GridBg)
                    ) {
                        // 横向网格线（底层）
                        Column(Modifier.fillMaxSize()) {
                            repeat(maxPeriod) {
                                Box(Modifier.fillMaxWidth().height(RowHeight)) {
                                    HorizontalDivider(thickness = 1.dp, color = GridLine)
                                }
                            }
                        }
                        // 课程卡：位置与高度严格由节次决定
                        weekCourses
                            .filter { it.dayOfWeek == day }
                            .forEach { course ->
                                val top = ((course.startPeriod - 1) * RowHeight.value).dp + 3.dp
                                val cardHeight =
                                    ((course.endPeriod - course.startPeriod + 1) * RowHeight.value).dp - 6.dp
                                CourseCard(
                                    course = course,
                                    narrow = narrow,
                                    paletteMap = paletteMap,
                                    modifier = Modifier
                                        .offset(y = top)
                                        .height(cardHeight)
                                        .padding(horizontal = 2.dp),
                                    onClick = { onCourseClick(course) },
                                )
                            }
                        // 排课草稿：半透明幽灵卡，压在真课程上面才看得见冲突
                        draft?.slots?.forEachIndexed { index, slot ->
                            if (slot.day != day) return@forEachIndexed
                            val top = ((slot.start - 1) * RowHeight.value).dp + 3.dp
                            val cardHeight =
                                ((slot.end - slot.start + 1) * RowHeight.value).dp - 6.dp
                            GhostCard(
                                title = draft.label.ifBlank { "新建课程" },
                                place = draft.place,
                                day = slot.day,
                                start = slot.start,
                                end = slot.end,
                                conflict = index in draft.conflictIndexes,
                                active = index == draft.activeIndex,
                                narrow = narrow,
                                modifier = Modifier
                                    .offset(y = top)
                                    .height(cardHeight)
                                    .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }

            // 手势层：整个网格一块画布，按坐标换算成「周几 + 第几节」
            if (draft != null) {
                val density = LocalDensity.current
                val leftPx = with(density) { LeftColumnWidth.toPx() }
                val rowPx = with(density) { RowHeight.toPx() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(draft, rowPx, leftPx) {
                            fun hit(pos: Offset): Pair<Int, Int>? {
                                val x = pos.x - leftPx
                                if (x < 0f) return null
                                val colW = size.width - leftPx
                                if (colW <= 0f) return null
                                val day = (x / (colW / shownDays.count())).toInt() + 1
                                val period = (pos.y / rowPx).toInt() + 1
                                return day.coerceIn(shownDays.first, shownDays.last) to
                                    period.coerceIn(1, maxPeriod)
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val at = hit(down.position) ?: return@awaitEachGesture
                                // 命中的是不是已有草稿块？靠几何判断，不额外铺热区
                                val y = down.position.y
                                val draftHit = draft.slots.indexOfFirst { s ->
                                    s.day == at.first &&
                                        y >= ((s.start - 1) * rowPx) && y < (s.end * rowPx)
                                }
                                val mode = when {
                                    draftHit < 0 -> ArrangeDragMode.NEW
                                    y - (draft.slots[draftHit].start - 1) * rowPx < rowPx * 0.45f ->
                                        ArrangeDragMode.TOP
                                    (draft.slots[draftHit].end * rowPx) - y < rowPx * 0.45f ->
                                        ArrangeDragMode.BOTTOM
                                    else -> ArrangeDragMode.MOVE
                                }
                                draft.onGesture(
                                    GridGesture.Down(
                                        at.first,
                                        at.second,
                                        draftHit.takeIf { it >= 0 },
                                        mode,
                                    )
                                )
                                var moved = false
                                drag(down.id) { change ->
                                    change.consume()
                                    hit(change.position)?.let {
                                        moved = true
                                        draft.onGesture(GridGesture.Drag(it.first, it.second))
                                    }
                                }
                                if (!moved) {
                                    // 只是点了一下空白格：按一下的范围当两节连堂
                                    draft.onGesture(
                                        GridGesture.Drag(at.first, (at.second + 1).coerceAtMost(maxPeriod))
                                    )
                                }
                                draft.onGesture(GridGesture.Up)
                            }
                        }
                )
            }
        }
    }
}

/** 排课草稿卡：半透明底 + 虚线感描边，与真课程明显区分；撞课转红 */
@Composable
private fun GhostCard(
    title: String,
    place: String,
    day: Int,
    start: Int,
    end: Int,
    conflict: Boolean,
    active: Boolean,
    narrow: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (conflict) Color(0xFFFFE4E6) else BrandGreen
    val ink = if (conflict) Color(0xFFBE123C) else BrandGreen
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (active) 0.96f else 0.80f)
            .clip(RoundedCornerShape(5.dp))
            .background(base.copy(alpha = if (conflict) 0.92f else 0.14f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = ink.copy(alpha = if (active) 0.95f else 0.45f),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (conflict) "⚠ 撞课" else title,
            fontSize = if (narrow) 9.sp else 10.5.sp,
            lineHeight = if (narrow) 11.sp else 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "周${DAY_CHARS[(day - 1).coerceIn(0, 6)]} $start-${end}节",
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink.copy(alpha = 0.85f),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (place.isNotBlank()) {
            Text(
                place,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 马卡龙课程卡：淡色底 + 同色系深色文字；名称顶部居中，教师/教室沉入底部浅色遮罩并居中 */
@Composable
private fun CourseCard(
    course: CourseBlock,
    narrow: Boolean,
    paletteMap: Map<String, CoursePalette>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pal = paletteMap[course.name] ?: paletteFor(course.name)
    Column(
        modifier
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(5.dp), clip = false)
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(pal.container)
            .clickable(onClick = onClick)
    ) {
        // 课程名（一级，顶部居中）
        Text(
            course.name,
            fontSize = if (narrow) 10.sp else 12.sp,
            lineHeight = if (narrow) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = pal.title,
            maxLines = if (narrow) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f).heightIn(min = 2.dp))
        // 底部浅色遮罩：教师 + 教室（居中）
        if (course.teacher.isNotBlank() || course.place.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (course.teacher.isNotBlank()) {
                    Text(
                        course.teacher,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = pal.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (course.place.isNotBlank()) {
                    Text(
                        course.place,
                        fontSize = if (narrow) 8.sp else 9.sp,
                        lineHeight = if (narrow) 9.sp else 11.sp,
                        color = pal.body.copy(alpha = 0.85f),
                        maxLines = if (narrow) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
