package com.fengkao.timetable.ui.xuexitong

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.xuexitong.NoticeCategory
import com.fengkao.timetable.data.xuexitong.NoticeFormat
import com.fengkao.timetable.data.xuexitong.TaskItem
import com.fengkao.timetable.data.xuexitong.TaskStatus
import com.fengkao.timetable.data.xuexitong.XuexitongNotice
import com.fengkao.timetable.ui.glass.GlassElevation
import com.fengkao.timetable.ui.glass.liquidGlassSurface
import com.fengkao.timetable.ui.glass.liquidPress
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.glassRim
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope

data class NextClassUi(
    val statusTitle: String,
    val courseName: String,
    val timeText: String,
    val place: String,
    val startTime: String,
    /** 任课老师；课堂信息行里和教室并排放在两个小胶囊上。 */
    val teacher: String = "",
    /** 「周一 第2-3节」：来自课表格子本身，比纯时间更容易对上课程表。 */
    val dayPeriod: String = "",
    /** 正在上课或距上课 ≤ 30 分钟：自动把课程模式置顶。 */
    val autoFocus: Boolean = false,
)

/** 上下拖动超过该距离（px）才切到另一张卡，避免误触。 */
private const val ModeSwipeThreshold = 56f

/** 切换位移：两张卡在拖拽过程中错开的距离。 */
private val ModeShift = 10.dp

/**
 * 首页双模卡片：通知视图 / 下节课视图。
 *
 * 材质沿用应用统一的 Liquid Glass（与底栏同一套 backdrop 折射）：只靠顶部高光带、
 * 内侧亮描边和左上角高光立起来，不叠外投影，也不在卡下面垫「牌堆露边」——
 * 那两样落在浅灰画布上都会读成一圈脏灰，尤其两个下圆角处像台阶。
 *
 * 切换是跟手的无级动画：上下拖动时两张卡实时错位、缩放、淡入淡出，松手后按拖动距离
 * 与方向弹性吸附到最近一张；点右上角胶囊同样走这段动画。两张卡同层叠放，卡片高度恒为
 * 较高者，切换时不会跳变。
 */
@Composable
fun SmartNoticeCard(
    notices: List<XuexitongNotice>,
    locallyReadIds: Set<String>,
    unreadCount: Int,
    loggedIn: Boolean,
    sessionExpired: Boolean,
    lastFetchAt: Long,
    courses: List<CourseBlock>,
    nextClass: NextClassUi?,
    tasks: List<TaskItem>,
    now: Long,
    /** 课表异动条数：通知面第三行的汇总里要报「还有几项调课」。 */
    changesCount: Int = 0,
    backdrop: Backdrop,
    onOpenCenter: () -> Unit,
    onConnect: () -> Unit,
) {
    val autoFocus = nextClass?.autoFocus == true
    val canToggle = nextClass != null
    val scope = rememberCoroutineScope()

    // 牌堆模型：整数位表示「当前谁在最上面」，小数部分表示「最上面这张被抽走了多少」。
    // 只允许向上抽（向下拖会被夹到 0），抽满一张后整数位进一，等效于把抽走的牌塞回
    // 牌堆底部、露出下一张——两张卡就这样循环对调。
    val pos = remember { Animatable(if (autoFocus && canToggle) 1f else 0f) }
    LaunchedEffect(autoFocus, canToggle) {
        if (!canToggle) return@LaunchedEffect
        val target = if (autoFocus) 1f else 0f
        pos.animateTo(target, tween(220))
    }

    val topIndex = floor(pos.value).toInt() and 1
    val pull = (pos.value - floor(pos.value)).coerceIn(0f, 1f)

    var heightPx by remember { mutableFloatStateOf(0f) }

    val unread = notices.firstOrNull { it.serverUnread && it.id !in locallyReadIds }
        ?: notices.firstOrNull()
    val visual = unread?.let { noticeCourseVisual(it, courses) }
    val onBodyClick = if (loggedIn && !sessionExpired) onOpenCenter else onConnect

    /** 松手后吸附：抽过 35% 或甩得够快就进到下一张，否则弹回原位。 */
    fun settle(velocity: Float) {
        if (!canToggle) return
        val base = floor(pos.value)
        val goNext = velocity < -900f || (velocity <= 900f && pull > 0.35f)
        val target = if (goNext) base + 1f else base
        scope.launch {
            pos.animateTo(
                target,
                spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            )
            // 落位后把整数位折回 0/1，避免越累越大；奇偶不变所以画面不会跳
            pos.snapTo(pos.value % 2f)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .clipToBounds()
            .pointerInput(canToggle) {
                if (!canToggle) return@pointerInput
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = {
                        drag = 0f
                        settle(0f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        drag += dragAmount
                        // 只认向上：向下拖产生负增量，夹到 0 后牌堆纹丝不动
                        val travel = -dragAmount / topCardTravel(heightPx)
                        if (travel <= 0f) return@detectVerticalDragGestures
                        val base = floor(pos.value)
                        val next = base + (pos.value - base + travel).coerceAtMost(1f)
                        scope.launch { pos.snapTo(next) }
                    },
                    onDragEnd = { settle(drag * 12f) },
                )
            },
    ) {
    // clipToBounds 让上滑的卡从顶部被切走。
    // 不再在卡下面垫「牌堆露边」：那两截半透明玻璃条落在浅灰画布上会读成
    // 一圈奇怪的灰影，尤其两个下圆角处像楼梯。卡自身就是一块整玻璃。
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(Modifier.fillMaxWidth()) {
            val entering = topIndex xor 1
            // 先画即将露出的那张（在下层），再画被抽走的那张（盖在上面），
            // 于是上滑时就是「上面这张自己走掉，露出底下那张」。
            Box(
                Modifier.graphicsLayer {
                    // 露出面：从 0.94 弹到 1.0，只缩放不平移。
                    // 静止时必须完全不可见——卡面是半透明玻璃，直接叠着会把下面
                    // 那张的文字透上来，看着像重影。
                    val s = 0.94f + 0.06f * pull
                    scaleX = s
                    scaleY = s
                    alpha = (pull * 4f).coerceIn(0f, 1f)
                },
            ) {
                ModeCard(backdrop, entering, unread, visual, unreadCount, loggedIn,
                    sessionExpired, lastFetchAt, nextClass, tasks, now, changesCount, active = pull > 0.5f,
                    onBodyClick, onToggleMode = { advance(scope, pos) }, onOpenCenter)
            }
            Box(
                Modifier.graphicsLayer {
                    // 被抽走那张：完整地向上移出可见区，由 clipToBounds 裁掉，
                    // 不做淡出——半透明玻璃一旦降 alpha，下面那张的文字就会
                    // 透上来，看着像重影。
                    translationY = -pull * topCardTravel(heightPx)
                    val s = 1f - 0.02f * pull
                    scaleX = s
                    scaleY = s
                },
            ) {
                // 被抽走的这张在拖动时要盖住下面那张：玻璃卡面是半透明的，
                // 不垫一层实底就会把下层的文字透上来。静止时这层透明度为 0，
                // 所以玻璃质感不受影响。
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = (pull * 6f).coerceIn(0f, 1f) }
                        .clip(NoticeCardShape)
                        .background(if (IsDark) NoticeCapsule else NoticeCard),
                )
                ModeCard(backdrop, topIndex, unread, visual, unreadCount, loggedIn,
                    sessionExpired, lastFetchAt, nextClass, tasks, now, changesCount, active = pull < 0.5f,
                    onBodyClick, onToggleMode = { advance(scope, pos) }, onOpenCenter)
            }
        }
    }
    // ── 右缘滑动指示点（对照稿的 iOS Smart Stack 风格）：两粒小点常驻卡片右侧，
    // 当前那一面加长成小竖条并染成该面的主题色（下节课=蓝，通知=玫红），
    // 另一粒退成浅灰圆点。它只做提示，不接管点击，所以不吃事件。
    val classFace = topIndex == 1
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 9.dp)
            .width(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackDot(
            active = classFace,
            color = if (IsDark) Color(0xFF7FB2FF) else Color(0xFF2563EB),
        )
        StackDot(
            active = !classFace,
            color = if (IsDark) Color(0xFFFF93A8) else Color(0xFFF43F5E),
        )
    }
    }
}

/** 刻度点：激活时拉成 6×14dp 竖条并上色，否则 6dp 浅灰圆点，尺寸与颜色都做过渡。 */
@Composable
private fun StackDot(active: Boolean, color: Color) {
    val h by animateDpAsState(if (active) 14.dp else 6.dp, tween(260), label = "dot-h")
    val c by animateColorAsState(
        if (active) color else if (IsDark) Color(0xFF3A3F4B) else Color(0xFFCBD5E1),
        tween(260),
        label = "dot-c",
    )
    Box(
        Modifier
            .width(6.dp)
            .height(h)
            .clip(RoundedCornerShape(50))
            .background(c)
    )
}

/** 上滑多远算抽走一张：取卡高的一定比例，保证手感一致。 */
private fun topCardTravel(heightPx: Float): Float =
    // 整卡高 + 一点余量：抽满时上层卡恰好完全移出裁切区，
    // 下一帧两者交换角色时才不会出现半张卡的跳变。
    if (heightPx > 0f) heightPx * 1.02f else 240f

/** 胶囊点击：永远朝同一个方向抽下一张，与上滑同一条动画。 */
private fun advance(scope: CoroutineScope, pos: Animatable<Float, AnimationVector1D>) {
    scope.launch {
        pos.animateTo(
            floor(pos.value) + 1f,
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        )
        pos.snapTo(pos.value % 2f)
    }
}

/** 按当前排位渲染对应模式的内容。 */
@Composable
private fun ModeCard(
    backdrop: Backdrop,
    index: Int,
    notice: XuexitongNotice?,
    visual: NoticeCourseVisual?,
    unreadCount: Int,
    loggedIn: Boolean,
    sessionExpired: Boolean,
    lastFetchAt: Long,
    nextClass: NextClassUi?,
    tasks: List<TaskItem>,
    now: Long,
    changesCount: Int,
    active: Boolean,
    onBodyClick: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenCenter: () -> Unit,
) {
    if (index == 1 && nextClass != null) {
        ClassMode(
            backdrop = backdrop,
            nextClass = nextClass,
            unreadCount = unreadCount,
            active = active,
            onBodyClick = onOpenCenter,
            onToggleMode = onToggleMode,
        )
    } else {
        NoticeMode(
            backdrop = backdrop,
            notice = notice,
            visual = visual,
            unreadCount = unreadCount,
            loggedIn = loggedIn,
            sessionExpired = sessionExpired,
            lastFetchAt = lastFetchAt,
            tasks = tasks,
            now = now,
            changesCount = changesCount,
            nextClass = nextClass,
            active = active,
            onBodyClick = onBodyClick,
            onToggleMode = onToggleMode,
            onOpenCenter = onOpenCenter,
        )
    }
}

/**
 * 液态玻璃卡面。
 *
 * 浅色：三段式高光描边（glassRim），让玻璃的立体边缘看得出来。
 * 深色：**只留一圈淡描边**。深色画布本来就暗，Highlight.Default 的整圈亮白
 * 加上顶缘光带、左上角径向高光在深色下会被读成霓虹灯箱，卡片一大就非常夸张。
 */
@Composable
private fun GlassModeCard(backdrop: Backdrop, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                backdrop = backdrop,
                elevation = GlassElevation.Elevated,
                lensDp = 18.dp,
                chromatic = true,
                corner = 20.dp,
                // 底栏那套没有外投影：玻璃浮在画布上靠高光边和折射立起来。
                // 这里原本的 0.16 灰投影落在浅灰画布上，会在下方两个圆角外
                // 晕出一圈脏灰，正是「奇怪阴影」的来源。
                shadowAlpha = 0f,
                // 深色下把材质自带的受光面关掉，只靠下面那道描边立边。
                highlightAlpha = if (IsDark) 0f else 0.85f,
                // 深色下也不走内置的 GlassEdge 描边（深色下它本身是白 18%，
                // 加在近黑画布上偏亮），改成 drawBehind 里自己画那道白 10%。
                edgeColor = if (IsDark) Color.Transparent else GlassEdge,
            )
            .clip(NoticeCardShape)
            // 高光必须画在内容之下。之前用 drawWithContent { drawContent(); ... }
            // 让这圈白色径向渐变盖在文字上，左上角「学习通」被洗到 2.24:1
            // （AA 门槛 4.5:1）。玻璃的受光面本来就在材质里、不在字前面。
            .drawBehind {
                if (IsDark) {
                    // 深色：一道 insets 1dp 的淡描边，不叠亮白内线、不做顶缘光带、
                    // 不做左上角径向高光——这些在近黑画布上都会过曝。
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(19.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    return@drawBehind
                }
                // 注：clip 会把描边外半裁掉，所以描边整体向内收 1dp、宽度 2dp，
                // 这样才有一整条完整的边线。白色卡片落在浅灰背景上，必须先压
                // 一道冷灰外缘，玻璃的边才立得起来，再叠一道亮白内线形成折边。
                // 外缘一道极淡的玻璃边（底栏同款 GlassEdge）：只用来给白卡
                // 在浅灰背景上留一圈轮廓，不能像之前那样压一圈冷灰粗线，
                // 否则看起来就是 MD 的硬边阴影。
                drawRoundRect(
                    color = GlassEdge,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(19.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.85f),
                        ),
                    ),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    cornerRadius = CornerRadius(17.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
                // 顶缘高光带：玻璃受光面
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        startY = 0f,
                        endY = 7.dp.toPx(),
                    ),
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width - 4.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = CornerRadius(17.dp.toPx()),
                )
                // 左上角集中高光
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(34.dp.toPx(), 8.dp.toPx()),
                        radius = 58.dp.toPx(),
                    ),
                    radius = 58.dp.toPx(),
                    center = Offset(34.dp.toPx(), 8.dp.toPx()),
                )
            },
    ) {
        content()
    }
}

/** 课程绿：下节课卡的主色，与课表格子的绿色胶囊呼应。 */
private val ClassAccent: Color get() = if (IsDark) Color(0xFF7FD9BC) else Color(0xFF11644B)
private val ClassSoft: Color get() = if (IsDark) Color(0xFF12332B) else Color(0xFFE6FAF3)

/** 中性小胶囊的底色：和白卡只差一点点，用来把「标签」和「正文」分开。 */
private val ChipNeutral: Color get() = if (IsDark) Color(0xFF2A2D36) else Color(0xFFF2F5F9)
private val ChipNeutralText: Color get() = if (IsDark) Color(0xFFC3C8D2) else Color(0xFF4A5568)

/**
 * 通知视图（学习通那一面）。
 *
 * 按方案 B 重组：右上角不再挂「下节课 08:45 明科楼713 >」这种把另一张卡塞进来的长胶囊，
 * 而是固定成三行——顶部「类型标 + 课程名 … 倒计时」，中间大标题，底部「还有 N 项待办」
 * 加一句轻提示。两张卡的行数、行高完全一致，上滑切换时视觉锚点不动。
 */
@Composable
private fun NoticeMode(
    backdrop: Backdrop,
    notice: XuexitongNotice?,
    visual: NoticeCourseVisual?,
    unreadCount: Int,
    loggedIn: Boolean,
    sessionExpired: Boolean,
    lastFetchAt: Long,
    nextClass: NextClassUi?,
    tasks: List<TaskItem>,
    now: Long,
    changesCount: Int,
    active: Boolean,
    onBodyClick: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenCenter: () -> Unit,
) {
    // 这一面说的是「最要紧的那件事」：优先当前通知所属的待办，找不到才退回最紧急的一件。
    // 否则会出现「标题写 A 作业、倒计时写 B 作业」的错位。
    val rowTask = remember(tasks, notice?.id) {
        tasks.firstOrNull { t -> t.latest.id == notice?.id }
            ?: tasks.firstOrNull {
                !it.longWindow && (it.status == TaskStatus.URGENT || it.status == TaskStatus.OPEN)
            }
    }
    val openCount = remember(tasks) {
        tasks.count { !it.longWindow && (it.status == TaskStatus.URGENT || it.status == TaskStatus.OPEN) }
    }
    val headTitle = when {
        notice != null -> NoticeFormat.cleanTitle(notice.title, notice.category).ifBlank { "学习通通知" }
        loggedIn -> "暂无新通知"
        else -> "连接学习通，自动聚合作业与课程通知"
    }
    val countdown = rowTask?.let { TaskFormat.countdownText(it, now) }.orEmpty()
    val urgent = rowTask?.status == TaskStatus.URGENT
    val statusLabel = when {
        !loggedIn -> "未连接"
        sessionExpired -> "登录已失效"
        unreadCount > 0 -> "学习通 · ${unreadCount} 条未读"
        else -> "学习通"
    }

    GlassModeCard(backdrop = backdrop) {
        Box(Modifier.fillMaxWidth().then(if (active) Modifier.liquidPress { onBodyClick() } else Modifier)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 30.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // ── 第一行：类型标 + 课程名（左）｜ 倒计时（右）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (notice != null) {
                        CategoryPill(notice.category)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        (rowTask?.courseName?.let { NoticeFormat.shortCourse(it) })
                            ?: visual?.courseName?.ifBlank { null }
                            ?: statusLabel,
                        color = ChipNeutralText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (countdown.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        SmallPill(
                            text = countdown,
                            fg = if (urgent) NoticeDangerFg else NoticeBlue,
                            bg = if (urgent) NoticeDangerBg else NoticeBlueSoft,
                            bold = true,
                            round = true,
                        )
                    }
                }

                // ── 第二行：大标题。单行截断（对照稿的 truncate），两张卡因此等高。
                Text(
                    headTitle,
                    color = if (rowTask?.status == TaskStatus.CLOSED) NoticeMeta else NoticeInk,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── 第三行：左侧汇总小胶囊，右侧切到另一张卡的轻提示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val summary = buildString {
                        if (openCount > 0) append("$openCount 项待办")
                        if (changesCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$changesCount 项调课")
                        }
                        if (isEmpty()) {
                            append(notice?.let { NoticeFormat.fetchAge(lastFetchAt) } ?: "暂无待办")
                        }
                    }
                    // 不给汇总胶囊加权：加权会把它压到整行的一半，`4 项待办 · 5 项调课`
                    // 这种稍长的文案就被截成 `5 项…`。让它先按内容取宽，右侧提示占余量。
                    MetaChip(summary)
                    Spacer(Modifier.weight(1f))
                    if (nextClass != null) {
                        SwitchHint("上滑看下节课 ↑", active, onToggleMode)
                    } else {
                        Text(
                            "展开中心 ›",
                            color = NoticeBlue,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .then(if (active) Modifier.liquidPress(pressedScale = 0.94f, onClick = onOpenCenter) else Modifier)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 下节课视图。
 *
 * 与通知视图严格同构：同样三行、同样行高。教室和老师各占一个小胶囊放在底部一行——
 * 赶路时扫一眼就知道去哪间教室，不用在灰色小字里找。
 */
@Composable
private fun ClassMode(
    backdrop: Backdrop,
    nextClass: NextClassUi,
    unreadCount: Int,
    active: Boolean,
    onBodyClick: () -> Unit,
    onToggleMode: () -> Unit,
) {
    GlassModeCard(backdrop = backdrop) {
        Box(Modifier.fillMaxWidth().then(if (active) Modifier.liquidPress { onBodyClick() } else Modifier)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 30.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // ── 第一行：状态标 + 星期节次（左）｜ 开始时间（右）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallPill(
                        text = if (nextClass.statusTitle.startsWith("正在上课")) "正在上课" else "下一节课",
                        fg = ClassAccent,
                        bg = ClassSoft,
                        bold = true,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        nextClass.dayPeriod.ifBlank { nextClass.statusTitle },
                        color = ChipNeutralText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (nextClass.startTime.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        SmallPill(
                            text = "${nextClass.startTime} 开始",
                            fg = ClassAccent,
                            bg = ClassSoft,
                            bold = true,
                            round = true,
                        )
                    }
                }

                // ── 第二行：课程名（单行截断，与通知卡等高）
                Text(
                    nextClass.courseName.ifBlank { "暂无课程安排" },
                    color = NoticeInk,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── 第三行：教室 + 老师两个小胶囊，右侧切回通知
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val place = NoticeFormat.shortPlace(nextClass.place)
                    if (place.isNotBlank()) {
                        MetaChip("📍 $place")
                    }
                    if (nextClass.teacher.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        MetaChip("👤 ${nextClass.teacher}")
                    }
                    Spacer(Modifier.weight(1f))
                    SwitchHint(
                        if (unreadCount > 0) "上滑看通知 ($unreadCount) ↑" else "上滑看通知 ↑",
                        active,
                        onToggleMode,
                    )
                }
            }
        }
    }
}

/** 顶部/底部的小标签胶囊：11.5sp，行盒 15sp，垂直内边距 4dp → 固定 23dp 高。 */
@Composable
private fun SmallPill(text: String, fg: Color, bg: Color, bold: Boolean = false, round: Boolean = false) {
    Text(
        text,
        color = fg,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(if (round) RoundedCornerShape(50) else RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 底部信息胶囊：地点、老师、汇总数字，统一中性底，不抢标题。 */
@Composable
private fun MetaChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = ChipNeutralText,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(ChipNeutral)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 两张卡底部的「上滑看另一面」轻提示——方案 B 用来替代右上角胶囊的切换隐喻。 */
@Composable
private fun SwitchHint(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = NoticeFaint,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (active) Modifier.liquidPress(pressedScale = 0.92f, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
internal fun CategoryPill(category: NoticeCategory) {
    val style = categoryStyle(category)
    val shape = RoundedCornerShape(5.dp)
    Text(
        category.label,
        color = style.fg,
        fontSize = 11.sp,
        // 不写 lineHeight 会继承 M3 bodyLarge 的 24sp 行盒，chip 被撑到 27.4dp，
        // 两张卡高度对不上，切换时整卡跳动。
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(style.bg)
            .border(1.dp, style.border, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
