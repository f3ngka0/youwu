package com.fengkao.timetable.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.data.model.ChangeType
import com.fengkao.timetable.data.model.CourseChange
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.stats.StatsContent
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.appBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 卡片底色：与统计区包裹框一致 */
private val CARD: Color get() = if (IsDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
private val TEXT_MAIN: Color get() = if (IsDark) Color(0xFFE8E9EE) else Color(0xFF1F2430)
private val TEXT_SUB: Color get() = if (IsDark) Color(0xFFA9B0BC) else Color(0xFF5B6472)
private val TEXT_WEAK: Color get() = if (IsDark) Color(0xFF828A9A) else Color(0xFF9AA3B2)
private val INDIGO = Color(0xFF4E6EF2)
private val GREEN_SOFT: Color get() = if (IsDark) Color(0xFF173428) else Color(0xFFE4F7EF)
private val GREEN_DEEP: Color get() = if (IsDark) Color(0xFF7FDCA8) else Color(0xFF179669)
private val AMBER_SOFT: Color get() = if (IsDark) Color(0xFF3A2E14) else Color(0xFFFEF3E0)
private val AMBER_DEEP: Color get() = if (IsDark) Color(0xFFFFC96B) else Color(0xFFB7791F)
private val RED_SOFT: Color get() = if (IsDark) Color(0xFF3A1F1F) else Color(0xFFFDECEC)
private val RED_DEEP: Color get() = if (IsDark) Color(0xFFF19994) else Color(0xFFD43C3C)

/**
 * 课程表洞察：课程异动（置顶）+ 课表统计，由“我的”进入。
 * 整页 LazyColumn 滚动；课程异动与统计卡片统一使用统计区的 CARD 包裹框风格。
 */
@Composable
fun DiscoverScreen(viewModel: MainViewModel, onBack: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val changes = state.courseChanges
    val loaded = state.courseChangesLoaded

    // 进入页面 / 一次同步结束后重新读取
    LaunchedEffect(state.syncing, state.backgroundSyncing, state.settings.lastSyncAt) {
        if (!state.syncing && !state.backgroundSyncing) {
            viewModel.loadCourseChanges()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .statusBarsPadding()
    ) {
        item {
            onBack?.let { TextButton(onClick = it) { Text("返回", color = INDIGO) } }
            Spacer(Modifier.height(4.dp))
            Text(
                "课程表洞察",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = InkDark,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "课程异动 · 课表统计",
                fontSize = 12.sp,
                color = TEXT_WEAK,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 3.dp),
            )
            Spacer(Modifier.height(14.dp))
        }

        // ---- 课程异动（置顶，CARD 包裹框）----
        item {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CARD)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        Modifier.size(22.dp),
                        tint = INDIGO,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("课程异动", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TEXT_MAIN)
                }
                Text(
                    "课表同步时自动对比新旧课表，记录每处变化",
                    fontSize = 11.sp,
                    color = TEXT_WEAK,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(12.dp))
                when {
                    state.syncing -> ChangeEmptyLine("正在同步课表…")
                    changes.isEmpty() -> ChangeEmptyLine(if (loaded) "暂无课程异动" else "正在读取…")
                    else -> {
                        Text(
                            "最近一次同步 · ${changes.size} 条变化",
                            fontSize = 11.sp,
                            color = TEXT_WEAK,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        changes.forEachIndexed { i, change ->
                            ChangeRow(change)
                            if (i != changes.lastIndex) {
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        Text(
                            "仅保留最近一次同步检测到的变化；重新同步将自动更新",
                            fontSize = 10.sp,
                            color = TEXT_WEAK,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        // ---- 课表统计（迁移自原「统计」页）----
        item {
            Spacer(Modifier.height(14.dp))
            StatsContent(viewModel)
        }
    }
}

/** 异动卡内的空态行 */
@Composable
private fun ChangeEmptyLine(text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.SwapHoriz,
            null,
            Modifier.size(18.dp),
            tint = InkGray,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = InkGray)
    }
}

/** 异动条目行：不带自身卡片底，由外层 CARD 包裹 */
@Composable
private fun ChangeRow(change: CourseChange) {
    val (badgeBg, badgeColor, badgeText) = when (change.type) {
        ChangeType.ADDED -> Triple(GREEN_SOFT, GREEN_DEEP, "新增")
        ChangeType.CHANGED -> Triple(AMBER_SOFT, AMBER_DEEP, "调整")
        ChangeType.REMOVED -> Triple(RED_SOFT, RED_DEEP, "取消")
        else -> Triple(Color(0xFFEFF0F3), TEXT_SUB, "变化")
    }
    val timeText = remember(change.detectedAt) {
        SimpleDateFormat("M.d HH:mm", Locale.CHINA).format(Date(change.detectedAt))
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(badgeBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = badgeColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                change.course,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                change.detail,
                fontSize = 12.sp,
                color = TEXT_SUB,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                timeText,
                fontSize = 10.sp,
                color = TEXT_WEAK,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
