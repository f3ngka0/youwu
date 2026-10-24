package com.fengkao.timetable.ui.xuexitong

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.xuexitong.NoticeCategory
import com.fengkao.timetable.data.xuexitong.NoticeExtract
import com.fengkao.timetable.data.xuexitong.NoticeFormat
import com.fengkao.timetable.data.xuexitong.ScheduleChange
import com.fengkao.timetable.data.xuexitong.TaskItem
import com.fengkao.timetable.ui.glass.liquidPress
import com.fengkao.timetable.data.xuexitong.TaskStatus
import com.fengkao.timetable.data.xuexitong.XuexitongNotice
import com.fengkao.timetable.ui.theme.BrandGreen

private enum class NoticeFilter(val label: String) {
    ALL("全部"),
    UNREAD("未读"),
    HOMEWORK("作业/测验"),
    SIGNIN("签到"),
    ANNOUNCEMENT("课程公告"),
    ARCHIVED("归档"),
}

private enum class SheetTab(val label: String) {
    TODO("待办"),
    CHANGE("课表异动"),
    RAW("通知原文"),
}

/** 关键字匹配：课程名、标题、正文、发送者、附件名任一命中即可（不区分大小写）。 */
private fun XuexitongNotice.matches(keyword: String): Boolean {
    val needle = keyword.removeWhitespace().lowercase()
    if (needle.isEmpty()) return true
    val haystack = listOf(title, content, sender, attachmentName, deadline.orEmpty())
        .joinToString("\n")
        .removeWhitespace()
        .lowercase()
    return haystack.contains(needle)
}

private fun String.removeWhitespace(): String = filterNot { it.isWhitespace() }

/** 待办的关键字匹配：课程名 + 作业名 + 最新一条通知的标题。 */
private fun TaskItem.matches(keyword: String): Boolean {
    val needle = keyword.removeWhitespace().lowercase()
    if (needle.isEmpty()) return true
    return listOf(courseName, title, latest.title, latest.content)
        .joinToString("\n")
        .removeWhitespace()
        .lowercase()
        .contains(needle)
}

/** 课表异动的关键字匹配：课程名 + 原文摘要 + 地点。 */
private fun ScheduleChange.matches(keyword: String): Boolean {
    val needle = keyword.removeWhitespace().lowercase()
    if (needle.isEmpty()) return true
    return listOf(courseName, excerpt, place, notice.title)
        .joinToString("\n")
        .removeWhitespace()
        .lowercase()
        .contains(needle)
}

/**
 * 通知中心半屏抽屉。
 *
 * 学习通给的是一串「通知事件」，用户要的是「还有哪几件事要办」，所以这里分三层：
 * 默认停在「待办」（同一作业的 3 条提醒合并成 1 张卡），「课表异动」看调课停课，
 * 「通知原文」保留逐条流水的原始视图——不丢信息，只是默认折起来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeCenterSheet(
    notices: List<XuexitongNotice>,
    locallyReadIds: Set<String>,
    archivedIds: Set<String>,
    doneTaskKeys: Set<String>,
    unreadCount: Int,
    lastFetchAt: Long,
    loggedIn: Boolean,
    sessionExpired: Boolean,
    busy: Boolean,
    courses: List<CourseBlock>,
    now: Long,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkRead: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onArchiveRead: () -> Unit,
    onTaskDone: (String, Boolean) -> Unit,
    onConnect: () -> Unit,
    onOpenChaoxing: () -> Unit,
    onApplyChange: (ScheduleChange) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableStateOf(SheetTab.TODO) }
    var filter by rememberSaveable { mutableStateOf(NoticeFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    fun isUnread(notice: XuexitongNotice) =
        notice.serverUnread && notice.id !in locallyReadIds

    val keyword = query.trim()

    // 归档是「收起来」，不是「删掉」：默认三层视图都看不到，只有「归档」筛选能翻出来。
    val activeNotices = remember(notices, archivedIds) {
        notices.filter { it.id !in archivedIds }
    }
    val archivedNotices = remember(notices, archivedIds) {
        notices.filter { it.id in archivedIds }
    }

    // 派生数据只算一次：42 条通知的抽取在真机上约 1~2ms，但不能跟着搜索框每次重算
    val tasks = remember(activeNotices, now) { NoticeExtract.buildTasks(activeNotices, now) }
    val changes = remember(activeNotices) { NoticeExtract.buildChanges(activeNotices) }
    val visibleTasks = remember(tasks, keyword, doneTaskKeys) {
        val matched = if (keyword.isEmpty()) tasks else tasks.filter { it.matches(keyword) }
        // 已完成的沉到最后：未完成的那几件才是这一页要回答的问题
        matched.sortedBy { if (it.key in doneTaskKeys) 1 else 0 }
    }
    val visibleChanges = remember(changes, keyword) {
        if (keyword.isEmpty()) changes else changes.filter { it.matches(keyword) }
    }

    val filtered = remember(activeNotices, archivedNotices, filter, locallyReadIds, keyword) {
        val byCategory = when (filter) {
            NoticeFilter.ALL -> activeNotices
            NoticeFilter.UNREAD -> activeNotices.filter { it.serverUnread && it.id !in locallyReadIds }
            NoticeFilter.HOMEWORK -> activeNotices.filter { it.category == NoticeCategory.HOMEWORK }
            NoticeFilter.SIGNIN -> activeNotices.filter { it.category == NoticeCategory.SIGNIN }
            NoticeFilter.ANNOUNCEMENT -> activeNotices.filter { it.category == NoticeCategory.ANNOUNCEMENT }
            NoticeFilter.ARCHIVED -> archivedNotices
        }
        if (keyword.isEmpty()) byCategory else byCategory.filter { it.matches(keyword) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NoticeBg,
        shape = NoticeSheetShape,
        scrimColor = Color(0x591A202C),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(NoticeFaint.copy(alpha = 0.45f))
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.80f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "通知中心",
                    color = NoticeInk,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    // 只给标题权重：若与 Spacer 平分剩余宽度，360dp 屏上标题会被裁掉一半。
                    modifier = Modifier.weight(1f),
                )
                if (unreadCount > 0) {
                    // 圆形徽标：靠 padding 撑出来的圆在数字位数变化时会变椭圆，
                    // 直接钉死 18dp 方形 + 居中，1~2 位数都是正圆。
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(NoticeBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$unreadCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            // 行盒必须显式给：继承 bodyLarge 的 24sp 会被 18dp 的方框裁掉
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // 抓取时间：11.5px，紧贴「全部已读」左侧，永不参与折行
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .liquidPress(enabled = loggedIn && !busy, onClick = onRefresh)
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (busy) "抓取中…" else NoticeFormat.fetchAge(lastFetchAt),
                        color = NoticeMeta,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Outlined.Refresh,
                        "刷新通知",
                        tint = NoticeMeta,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 关键字搜索：在分类之上再过滤一次（课程名 / 标题 / 正文 / 发送者 / 附件名）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NoticeCard)
                    .border(1.dp, NoticeHairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = NoticeFaint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                // 自绘输入框：M3 TextField 有 56dp 最小高度，会被 40dp 容器裁掉文字
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        // #A0AEC0 在 #FFFFFF 上只有 2.12:1，占位符也是正文级信息。
                        Text("搜索课程、作业或关键字", color = NoticeMeta, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NoticeInk, fontSize = 12.5.sp, lineHeight = 17.sp),
                        cursorBrush = SolidColor(NoticeBlue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        "清空搜索",
                        tint = NoticeFaint,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .liquidPress(pressedScale = 0.9f) { query = "" },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 三层视图切换：默认落在「待办」——用户要办的事，而不是通知流水
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NoticeCard)
                    .border(1.dp, NoticeHairline, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SheetTab.entries.forEach { item ->
                    val selected = tab == item
                    val count = when (item) {
                        SheetTab.TODO -> visibleTasks.count { it.status != TaskStatus.CLOSED }
                        SheetTab.CHANGE -> visibleChanges.size
                        SheetTab.RAW -> filtered.size
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) NoticeBlueSoft else Color.Transparent)
                            // 分段控件属于玻璃体系：只要颜色切换，不要 MD 的灰 state layer
                            .liquidPress(pressedScale = 1f) { tab = item }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (count > 0) "${item.label} $count" else item.label,
                            color = if (selected) NoticeBlue else NoticeMeta,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 分类筛选只在「通知原文」有意义：待办与异动已经按类型组织过了
            if (tab == SheetTab.RAW) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NoticeFilter.entries.forEach { item ->
                        FilterPill(
                            label = if (item == NoticeFilter.UNREAD) "未读 $unreadCount" else item.label,
                            selected = filter == item,
                            onClick = { filter = item },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                !loggedIn -> Box(Modifier.fillMaxWidth().weight(1f)) { ConnectState(onConnect = onConnect) }
                tab == SheetTab.TODO -> if (visibleTasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        EmptyState(
                            text = if (keyword.isNotEmpty()) "没有匹配「$keyword」的待办" else "没有待办",
                            hint = if (keyword.isNotEmpty()) {
                                "换个关键字，或点搜索框右侧 × 清空"
                            } else {
                                "学习通里有作业、考试时会自动汇总到这里"
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (sessionExpired) {
                            item { SessionExpiredBanner(onConnect = onConnect) }
                        }
                        items(visibleTasks, key = { it.key }) { task ->
                            val done = task.key in doneTaskKeys
                            TaskCard(
                                task = task,
                                courses = courses,
                                now = now,
                                done = done,
                                onOpenChaoxing = onOpenChaoxing,
                                onToggleDone = { onTaskDone(task.key, !done) },
                            )
                        }
                        item {
                            SheetFooter(
                                if (doneTaskKeys.isEmpty()) {
                                    "已合并同一份作业的多条提醒 · 截止时间来自学习通原文"
                                } else {
                                    "已完成 ${doneTaskKeys.size} 件会沉到列表底部 · 仅保存在本机"
                                }
                            )
                        }
                    }
                }

                tab == SheetTab.CHANGE -> if (visibleChanges.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        EmptyState(
                            text = "没有课表异动",
                            hint = "调课、停课、补课类公告会汇总到这里",
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (sessionExpired) {
                            item { SessionExpiredBanner(onConnect = onConnect) }
                        }
                        items(visibleChanges, key = { "change-${it.noticeId}" }) { change ->
                            ChangeCard(
                                change = change,
                                onApply = { onApplyChange(change) },
                                onOpenChaoxing = onOpenChaoxing,
                            )
                        }
                        item {
                            SheetFooter(
                                "只有明确写清「改到哪天第几节」的公告才提供改课表；" +
                                    "互调与停课需自行确认"
                            )
                        }
                    }
                }

                filtered.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmptyState(
                        text = when {
                            keyword.isNotEmpty() -> "没有匹配「$keyword」的通知"
                            notices.isEmpty() -> "暂无学习通通知"
                            else -> "当前分类暂无通知"
                        },
                        hint = when {
                            keyword.isNotEmpty() -> "换个关键字，或点搜索框右侧 × 清空"
                            notices.isEmpty() -> "有新的作业、签到或公告时会自动出现在这里"
                            else -> null
                        },
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (sessionExpired) {
                        item { SessionExpiredBanner(onConnect = onConnect) }
                    }
                    items(filtered, key = { it.id }) { notice ->
                        val archived = notice.id in archivedIds
                        NoticeItemCard(
                            notice = notice,
                            courses = courses,
                            unread = isUnread(notice),
                            expanded = expandedId == notice.id,
                            archived = archived,
                            onToggle = {
                                if (expandedId == notice.id) {
                                    expandedId = null
                                } else {
                                    expandedId = notice.id
                                    if (isUnread(notice)) onMarkRead(notice.id)
                                }
                            },
                            onMarkRead = { onMarkRead(notice.id) },
                            onArchive = { onArchive(notice.id) },
                            onUnarchive = { onUnarchive(notice.id) },
                            onOpenChaoxing = onOpenChaoxing,
                        )
                    }
                    item {
                        // 归档是「把读完的收起来」，不删数据：还原入口就在「归档」筛选里。
                        NoticeChipButton(
                            label = "归档全部已读",
                            onClick = onArchiveRead,
                        )
                    }
                    item { SheetFooter("已读与归档仅保存在本机 · 通知内容来自学习通通知中心") }
                }
            }
        }
    }
}

@Composable
private fun SessionExpiredBanner(onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoticeDangerBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "学习通登录已失效，通知可能不是最新的。",
            color = NoticeDangerFg,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "重新登录",
            color = NoticeBlue,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .liquidPress(pressedScale = 0.94f, onClick = onConnect)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SheetFooter(text: String) {
    Text(
        text,
        color = NoticeMeta,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (selected) Color.White else NoticeSub,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) NoticeBlue else NoticeCard)
            .border(1.dp, if (selected) NoticeBlue else NoticeHairline, shape)
            .liquidPress(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun ConnectState(onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Text("还没有连接学习通", color = NoticeInk, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "登录后自动汇总作业、签到与课程公告，课表页也会显示最新通知。",
                color = NoticeMeta,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color.White),
            ) {
                Text("连接学习通", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, hint: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = NoticeSub, fontSize = 14.sp, lineHeight = 20.sp)
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(hint, color = NoticeMeta, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun NoticeItemCard(
    notice: XuexitongNotice,
    courses: List<CourseBlock>,
    unread: Boolean,
    expanded: Boolean,
    archived: Boolean,
    onToggle: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onOpenChaoxing: () -> Unit,
) {
    val visual = noticeCourseVisual(notice, courses)
    val shape = RoundedCornerShape(18.dp)
    val content = remember(notice.content, notice.title, notice.attachmentName) {
        NoticeFormat.cleanContent(notice.content, notice.title, notice.attachmentName)
    }
    val title = remember(notice.title, notice.category) {
        NoticeFormat.cleanTitle(notice.title, notice.category).ifBlank { "学习通通知" }
    }
        Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NoticeCard)
            .animateContentSize()
            .liquidPress(pressedScale = 0.985f, onClick = onToggle)
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(visual.base)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoursePill(visual)
                Spacer(Modifier.width(8.dp))
                val sender = NoticeFormat.senderLabel(notice.sender).ifBlank { "学习通" }
                val time = NoticeFormat.relativeTime(notice.timeMillis)
                Text(
                    listOf(sender, time).filter { it.isNotBlank() }.joinToString(" · "),
                    color = NoticeMeta,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(NoticeBlue))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                title,
                color = NoticeInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    content,
                    color = NoticeSub,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = expanded &&
                    notice.category == NoticeCategory.HOMEWORK &&
                    !notice.deadline.isNullOrBlank(),
                enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(220)) + fadeIn(),
                exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(180)) + fadeOut(),
            ) {
                Box(Modifier.padding(top = 8.dp)) { DeadlineChip(notice.deadline.orEmpty()) }
            }
            // 底部同行：左附件 / 右操作按钮，避免多占一行高度
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val attachment = notice.attachmentName.takeIf { it.isNotBlank() }
                if (attachment != null) {
                    Text(
                        buildString {
                            append("📎 ")
                            append(attachment)
                            if (notice.attachmentSize.isNotBlank()) append("（${notice.attachmentSize}）")
                        },
                        color = NoticeBlue,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Spacer(Modifier.width(0.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 归档 / 已读是「整理」动作，用描边次级按钮；
                    // 去学习通是主操作，保持浅蓝实底，视线自然落在最右。
                    NoticeChipButton(
                        label = if (archived) "还原" else "归档",
                        onClick = if (archived) onUnarchive else onArchive,
                    )
                    if (unread) {
                        NoticeChipButton(label = "已读", onClick = onMarkRead)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeChipButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = NoticeSub,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NoticeCard)
            .border(1.dp, NoticeHairline, RoundedCornerShape(50))
            .liquidPress(pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CoursePill(visual: NoticeCourseVisual) {
    Text(
        visual.courseName.ifBlank { "学习通" },
        color = visual.pillText,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(visual.pillBg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun DeadlineChip(deadline: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NoticeDangerBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏰", fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            "截止：$deadline",
            color = NoticeDangerFg,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
