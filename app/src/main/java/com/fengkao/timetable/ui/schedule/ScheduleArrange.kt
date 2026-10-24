package com.fengkao.timetable.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.model.CustomCourse
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.GlassControlColor
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.SheetColor

/**
 * 画布排课模式（对照 demo 的方案 A）。
 *
 * 核心思路不是"再做一个填表弹窗"，而是把主课表本身变成编辑器：
 * 直接点空白格 → 原地长出一块半透明色块；拖动色块换天换节；
 * 拖色块上下边缘拉伸起止节次。底部只留一张"课程信息"精简卡片，
 * 因为"上课日 / 节次"这两个最容易填错的东西已经不再需要输入了。
 */

/** 一个待排时段：周几 + 第几节到第几节 */
data class ArrangeSlot(val day: Int, val start: Int, val end: Int)

/** 手势模式：新建涂抹 / 整体搬家 / 拉上边缘 / 拉下边缘 */
enum class ArrangeDragMode { NEW, MOVE, TOP, BOTTOM }

/** 课表画布回传给排课状态的手势事件 */
sealed interface GridGesture {
    data class Down(
        val day: Int,
        val period: Int,
        val index: Int?,
        val mode: ArrangeDragMode,
    ) : GridGesture

    data class Drag(val day: Int, val period: Int) : GridGesture

    data object Up : GridGesture
}

/** 画布要显示什么、手势往哪送 */
class GridDraft(
    val slots: List<ArrangeSlot>,
    val activeIndex: Int,
    val conflictIndexes: Set<Int>,
    val label: String,
    val place: String,
    val onGesture: (GridGesture) -> Unit,
)

private val ArrangeMaxPeriod = 12
private val ArrangeDayMarks = listOf("一", "二", "三", "四", "五", "六", "日")

private fun ArrangeSlot.shortText(): String =
    if (start == end) "周${ArrangeDayMarks[(day - 1).coerceIn(0, 6)]} ${start}节" else
        "周${ArrangeDayMarks[(day - 1).coerceIn(0, 6)]} ${start}-${end}节"

private fun weeksOf(startWeek: Int, endWeek: Int, odd: Boolean, even: Boolean): Set<Int> =
    (startWeek..endWeek).filter {
        if (odd) it % 2 == 1 else if (even) it % 2 == 0 else true
    }.toSet()

private fun overlap(a1: Int, a2: Int, b1: Int, b2: Int): Boolean = !(a2 < b1 || a1 > b2)

/**
 * 排课草稿。手势和表单都只改这一份状态，课表与底部卡片共享同一份数据，
 * 所以拖动色块时下面的时间胶囊会同步跳。
 */
class ArrangeState(
    courses: List<CourseBlock>,
    termWeeks: Int,
    prefillName: String = "",
    prefillPlace: String = "",
    prefillSlots: List<ArrangeSlot> = listOf(ArrangeSlot(1, 1, 2)),
    prefillWeek: Int? = null,
) {
    val allCourses: List<CourseBlock> = courses
    val totalWeeks: Int = termWeeks

    var name by mutableStateOf(prefillName)
    var place by mutableStateOf(prefillPlace)
    var teacher by mutableStateOf("")
    var slots by mutableStateOf(prefillSlots.ifEmpty { listOf(ArrangeSlot(1, 1, 2)) })
    var activeIndex by mutableStateOf(0)
    var startWeek by mutableStateOf(prefillWeek ?: 1)
    var endWeek by mutableStateOf(prefillWeek ?: termWeeks)
    var oddOnly by mutableStateOf(false)
    var evenOnly by mutableStateOf(false)
    var weekAnchor by mutableStateOf<Int?>(null)
    var showWeekPick by mutableStateOf(false)

    // 手势锚点：按下那一刻的格子与色块原始位置，拖动时都以它为基准算偏移
    private var anchorDay by mutableStateOf(1)
    private var anchorPeriod by mutableStateOf(1)
    private var baseDay = 1
    private var baseStart = 1
    private var baseEnd = 2

    val weekSet: Set<Int> get() = weeksOf(startWeek, endWeek, oddOnly, evenOnly)

    val active: ArrangeSlot get() = slots.getOrElse(activeIndex) { ArrangeSlot(1, 1, 2) }

    /** 每个时段撞到哪些课（同名课程不算：从调课通知进来时那正是要挪走的自己） */
    fun conflictsOf(index: Int): List<String> {
        val s = slots.getOrNull(index) ?: return emptyList()
        val hit = LinkedHashSet<String>()
        allCourses.forEach { c ->
            if (c.name != name.trim() && c.dayOfWeek == s.day &&
                (c.weeks.isEmpty() || c.weeks.any { it in weekSet }) &&
                overlap(s.start, s.end, c.startPeriod, c.endPeriod)
            ) {
                hit += c.name
            }
        }
        return hit.toList()
    }

    val conflictIndexes: Set<Int>
        get() = slots.indices.filter { conflictsOf(it).isNotEmpty() }.toSet()

    val conflicts: List<String>
        get() = slots.indices.flatMap { conflictsOf(it) }.distinct()

    fun addSlot() {
        if (slots.size >= 4) return
        val free = nearestFree(baseDay, active.end - active.start + 1, skip = activeIndex)
        slots = slots + free
        activeIndex = slots.lastIndex
    }

    fun removeSlot(index: Int) {
        if (slots.size <= 1) return
        slots = slots.filterIndexed { i, _ -> i != index }
        activeIndex = 0
    }

    /** 从当前时段开始往外找一段没被占用的时间 */
    fun nearestFree(preferredDay: Int, length: Int, skip: Int = -1): ArrangeSlot {
        val days = listOf(preferredDay, 1, 3, 5, 2, 4, 6, 7)
            .map { ((it - 1) % 7) + 1 }.distinct()
        for (d in days) {
            for (s in 1..(ArrangeMaxPeriod - length + 1)) {
                val e = s + length - 1
                val clashCourse = allCourses.any { c ->
                    c.name != name.trim() && c.dayOfWeek == d &&
                        (c.weeks.isEmpty() || c.weeks.any { it in weekSet }) &&
                        overlap(s, e, c.startPeriod, c.endPeriod)
                }
                val clashDraft = slots.filterIndexed { i, _ -> i != skip }.any {
                    it.day == d && overlap(s, e, it.start, it.end)
                }
                if (!clashCourse && !clashDraft) return ArrangeSlot(d, s, e)
            }
        }
        return ArrangeSlot(preferredDay, 1, length)
    }

    fun findFreeForActive() {
        val length = active.end - active.start + 1
        val free = nearestFree(active.day, length, skip = activeIndex)
        slots = slots.toMutableList().also { it[activeIndex.coerceIn(0, it.lastIndex)] = free }
    }

    fun pickWeek(value: Int) {
        val anchor = weekAnchor
        if (anchor == null) {
            weekAnchor = value
            startWeek = value
            endWeek = value
        } else {
            startWeek = minOf(anchor, value)
            endWeek = maxOf(anchor, value)
        }
    }

    fun onGesture(g: GridGesture) {
        when (g) {
            is GridGesture.Down -> {
                anchorDay = g.day
                anchorPeriod = g.period
                hitMode = g.mode
                if (g.mode == ArrangeDragMode.NEW) {
                    activeIndex = activeIndex.coerceIn(0, slots.lastIndex)
                    // 单击空白格先给两节（最常见的连堂），再拖就按涂抹范围改
                    slots = slots.toMutableList().also {
                        it[activeIndex] = ArrangeSlot(
                            g.day,
                            g.period,
                            (g.period + 1).coerceAtMost(ArrangeMaxPeriod),
                        )
                    }
                } else {
                    val idx = g.index?.coerceIn(0, slots.lastIndex) ?: return
                    activeIndex = idx
                    val cur = slots[idx]
                    baseDay = cur.day
                    baseStart = cur.start
                    baseEnd = cur.end
                }
            }

            is GridGesture.Drag -> {
                when (hitMode) {
                    ArrangeDragMode.NEW -> {
                        slots = slots.toMutableList().also {
                            it[activeIndex] = ArrangeSlot(
                                anchorDay,
                                minOf(anchorPeriod, g.period),
                                maxOf(anchorPeriod, g.period),
                            )
                        }
                    }

                    ArrangeDragMode.MOVE -> {
                        val duration = baseEnd - baseStart
                        val newDay = (baseDay + (g.day - anchorDay)).coerceIn(1, 7)
                        val newStart = (baseStart + (g.period - anchorPeriod))
                            .coerceIn(1, ArrangeMaxPeriod - duration)
                        slots = slots.toMutableList().also {
                            it[activeIndex] = ArrangeSlot(newDay, newStart, newStart + duration)
                        }
                    }

                    ArrangeDragMode.TOP -> {
                        val newStart = (baseStart + (g.period - anchorPeriod))
                            .coerceIn(1, baseEnd)
                        slots = slots.toMutableList().also {
                            it[activeIndex] = ArrangeSlot(baseDay, newStart, baseEnd)
                        }
                        markDragged()
                    }

                    ArrangeDragMode.BOTTOM -> {
                        val newEnd = (baseEnd + (g.period - anchorPeriod))
                            .coerceIn(baseStart, ArrangeMaxPeriod)
                        slots = slots.toMutableList().also {
                            it[activeIndex] = ArrangeSlot(baseDay, baseStart, newEnd)
                        }
                        markDragged()
                    }
                }
            }

            GridGesture.Up -> Unit
        }
    }

    /** 当前手势模式（Down 时确定，Drag 时复用同一分支） */
    private var hitMode: ArrangeDragMode = ArrangeDragMode.NEW

    /** 拉伸后让 baseEnd/baseStart 跟着走，否则连续拖会不断叠加同一段位移 */
    private fun markDragged() {
        val cur = slots[activeIndex.coerceIn(0, slots.lastIndex)]
        baseStart = cur.start
        baseEnd = cur.end
        anchorPeriod = cur.start
        anchorDay = cur.day
    }

    fun build(): List<CustomCourse> = slots.map { s ->
        CustomCourse(
            name = name.trim(),
            place = place.trim(),
            teacher = teacher.trim(),
            dayOfWeek = s.day,
            startPeriod = s.start,
            endPeriod = s.end,
            startWeek = startWeek,
            endWeek = endWeek,
            oddOnly = oddOnly,
            evenOnly = evenOnly,
        )
    }
}

// ---------------------------------------------------------------- 顶部状态条

/** 排课模式下顶替"问候 + 智能卡"的窄条：明示当前处于画布编辑态 */
@Composable
fun ArrangeTopBar(onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BrandGreen)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFF9AE6B4))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "画布排课模式 · 点空白格或拖色块",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "完成 / 退出",
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.20f))
                .clickable(onClick = onExit)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ---------------------------------------------------------------- 底部精简表单

@Composable
fun ArrangePanel(
    state: ArrangeState,
    periodTimes: List<PeriodTime>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val active = state.active
    val valid = state.name.isNotBlank()
    val startTime = periodTimes.getOrNull(active.start - 1)?.startText().orEmpty()
    val endTime = periodTimes.getOrNull(active.end - 1)?.endText().orEmpty()
    val range = if (startTime.isNotBlank() && endTime.isNotBlank()) " ($startTime-$endTime)" else ""
    val conflicts = state.conflicts

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(SheetColor)
            .border(1.dp, GlassEdge, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .padding(horizontal = 14.dp)
            // 底栏在排课模式下隐藏了，这里得自己让开系统导航条，
            // 否则「取消 / 排入课表」会压在手势条下面点不到
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        // 手势条
        Box(
            Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(InkGray.copy(alpha = 0.28f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))

        // 实时状态：当前时段 + 加时段
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandGreen.copy(alpha = 0.08f))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Text(
                    "周${ArrangeDayMarks[(active.day - 1).coerceIn(0, 6)]} · 第 ${active.start}-${active.end} 节$range",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandGreen.copy(alpha = 0.10f))
                    .clickable { state.addSlot() }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = BrandGreen, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    "加时段",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandGreen,
                )
            }
        }

        // 多时段时给一排可切换/删除的胶囊
        if (state.slots.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.slots.forEachIndexed { index, slot ->
                    val on = index == state.activeIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) BrandGreen else GlassControlColor)
                            .clickable { state.activeIndex = index }
                            .padding(start = 8.dp, end = 3.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            slot.shortText(),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) Color.White else InkMid,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { state.removeSlot(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = if (on) Color.White.copy(alpha = 0.85f) else InkGray,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        }

        if (conflicts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF5F5))
                    .border(1.dp, Color(0xFFFED7D7), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Text(
                    "⚠ 与《${conflicts.first()}》时间重叠",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFC53030),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "一键找空位",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC53030),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { state.findFreeForActive() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 课程信息：只有这三项还需要打字
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrangeField(
                value = state.name,
                onChange = { state.name = it.take(24) },
                placeholder = "课程名称 *",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ArrangeField(
                value = state.place,
                onChange = { state.place = it.take(20) },
                placeholder = "教室",
                modifier = Modifier.weight(1f),
            )
            ArrangeField(
                value = state.teacher,
                onChange = { state.teacher = it.take(12) },
                placeholder = "教师",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(7.dp))

        // 周次：三个常用预设 + 可展开逐周涂抹
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrangeChip(
                text = "1-${state.totalWeeks}周",
                on = state.startWeek == 1 && state.endWeek == state.totalWeeks && !state.oddOnly && !state.evenOnly,
            ) {
                state.startWeek = 1
                state.endWeek = state.totalWeeks
                state.oddOnly = false
                state.evenOnly = false
                state.weekAnchor = null
            }
            Spacer(Modifier.width(6.dp))
            ArrangeChip(text = "单周", on = state.oddOnly) {
                state.oddOnly = true
                state.evenOnly = false
            }
            Spacer(Modifier.width(6.dp))
            ArrangeChip(text = "双周", on = state.evenOnly) {
                state.evenOnly = true
                state.oddOnly = false
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.startWeek == state.endWeek) "第 ${state.startWeek} 周"
                else "第 ${state.startWeek}-${state.endWeek} 周",
                fontSize = 11.sp,
                color = InkGray,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { state.showWeekPick = !state.showWeekPick }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        if (state.showWeekPick) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 96.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val perRow = 8
                val weeks = (1..state.totalWeeks).toList()
                weeks.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { w ->
                            val on = w in state.startWeek..state.endWeek
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (on) BrandGreen else GlassControlColor)
                                    .clickable { state.pickWeek(w) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$w",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else InkGray,
                                )
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "取消",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkMid,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassControlColor)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (valid) BrandGreen else GlassControlColor)
                    .clickable(enabled = valid) { onSave() }
                    .padding(vertical = 10.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    tint = if (valid) Color.White else InkGray,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (valid) "排入课表 (${state.slots.joinToString("、") { it.shortText() }})"
                    else "请先填写课程名称",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (valid) Color.White else InkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArrangeField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            // 表单在白色抽屉上：GlassControlColor 是半透明白，压在白底上等于没有边界，
            // 所以输入框改用实心浅灰底 + 略深的描边，让人一眼看出这里可以打字
            .background(if (IsDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF1F5F9))
            .border(1.dp, if (IsDark) GlassEdge else Color(0xFFE2E8F0), RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 12.sp, color = InkGray, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = InkDark,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(BrandGreen),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ArrangeChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (on) Color.White else InkMid,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) BrandGreen else GlassControlColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
