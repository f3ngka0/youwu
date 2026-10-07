package com.fengkao.timetable.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.BuildConfig
import com.fengkao.timetable.R
import com.fengkao.timetable.ui.MainUiState
import com.fengkao.timetable.ui.MainViewModel
import com.fengkao.timetable.ui.shower.*
import com.fengkao.timetable.ui.water.*
import com.fengkao.timetable.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LoadingHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BrandGreen)
        Text(text, color = InkGray, fontSize = 12.sp)
    }
}

@Composable
internal fun SyncSummary(state: MainUiState, onSync: () -> Unit) {
    val busy = state.syncing || state.backgroundSyncing
    val error = state.syncMessage ?: state.backgroundSyncError
    val status = when {
        busy -> "同步中"
        error != null -> "同步失败"
        state.settings.lastSyncAt > 0 -> "已同步"
        else -> "未同步"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BrandGreenSoft).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Sync, null, tint = BrandGreen, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text("当前状态：$status", color = InkDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val at = state.settings.lastSyncAt
            val time = if (at > 0) Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)) else "从未同步"
            Text("上次同步：$time", color = InkGray, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        TextButton(onClick = onSync, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BrandGreen)
            else Text("立即同步", fontSize = 12.sp)
        }
    }
    error?.takeUnless { busy }?.let { SettingsHint(it, error = true) }
}

@Composable
internal fun WaterAccountPage(state: WaterUiState, model: WaterViewModel, confirm: (String) -> Unit) {
    if (state.loggedIn) {
        ServiceAccountDetails(maskAccount(state.phone), state.busy, "喝水", { confirm("water-login") }, { confirm("water") })
    } else WaterLogin(state, model)
    if (state.busy) LoadingHint("正在处理喝水账户…")
    state.message?.let { SettingsHint(it, state.error) }
}

@Composable
internal fun ShowerAccountPage(state: ShowerUiState, model: ShowerViewModel, confirm: (String) -> Unit) {
    if (state.loggedIn) {
        ServiceAccountDetails(maskAccount(state.phone), state.busy, "洗澡", { confirm("shower-login") }, { confirm("shower") })
    } else ShowerLogin(state, model)
    if (state.busy) LoadingHint("正在处理洗澡账户…")
    state.message?.let { SettingsHint(it, state.error) }
}

@Composable
private fun ServiceAccountDetails(account: String, busy: Boolean, name: String, relogin: () -> Unit, logout: () -> Unit) {
    SettingsSection {
        SettingsValue("账号", account); SettingsValue("账户状态", "已登录")
        SettingsItem("重新登录", Icons.AutoMirrored.Outlined.Login, enabled = !busy, onClick = relogin)
    }
    SettingsSection(danger = true) {
        SettingsItem("退出${name}账户", Icons.AutoMirrored.Outlined.Logout, enabled = !busy, danger = true, onClick = logout)
    }
}

@Composable
internal fun AboutPage(openLicenses: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.ic_launcher_foreground), "课程表图标", Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(BrandGreenSoft))
        Text("有梧", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkDark)
        Text("版本 ${BuildConfig.VERSION_NAME}", color = InkGray, fontSize = 13.sp)
    }
    SettingsSection { SettingsItem("开源许可", Icons.Outlined.Description, onClick = openLicenses) }
    SettingsHint("课表数据来自新版正方教务系统。教务凭据仅保存在本机，课表同步直连学校；喝水与洗澡登录分别连接对应校园服务。")
}

@Composable
internal fun LicensesPage(model: ProfileInfoViewModel) {
    val state by model.licenses.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.loadLicenses() }
    if (state.loading) LoadingHint("正在读取开源许可…")
    state.error?.let {
        SettingsHint(it, error = true)
        TextButton(onClick = model::loadLicenses) { Text("重试") }
    }
    if (state.text.isNotBlank()) Text(state.text, fontSize = 12.sp, lineHeight = 19.sp, color = InkMid)
    if (!state.loading && state.error == null && state.text.isBlank()) SettingsHint("暂无许可信息")
}

@Composable
internal fun CourseManagement(state: MainUiState, model: MainViewModel) {
    val courses = state.schedule?.courses.orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
    val custom = state.settings.customCourses
    val hidden = state.settings.hiddenCourses
    var pendingDelete by remember { mutableStateOf<com.fengkao.timetable.data.model.CustomCourse?>(null) }
    SettingsHint("教务课程隐藏仅影响本地显示，不影响同步数据")
    if (hidden.isNotEmpty()) TextButton(onClick = model::restoreAllCourses) { Text("恢复全部隐藏课程") }
    if (courses.isEmpty() && custom.isEmpty()) SettingsHint(if (state.syncing || state.backgroundSyncing) "正在同步课程…" else "暂无课程数据，可先同步或在首页添加课程")
    if (courses.isNotEmpty()) SettingsSection("教务课程 · ${courses.size} 项") {
        courses.forEach { course ->
            val isHidden = course.stableKey in hidden
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(course.name, color = if (isHidden) InkGray else InkDark, fontSize = 14.sp,
                        textDecoration = if (isHidden) TextDecoration.LineThrough else null)
                    val day = listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(course.dayOfWeek - 1) { "?" }
                    Text("周$day 第${course.startPeriod}-${course.endPeriod}节 · ${course.place.ifBlank { "待定" }} · ${course.teacher.ifBlank { "—" }} · ${course.weeksText}",
                        color = InkGray, fontSize = 11.sp, lineHeight = 16.sp)
                }
                TextButton(onClick = { model.toggleCourseHidden(course.stableKey) }) { Text(if (isHidden) "显示" else "隐藏") }
            }
        }
    }
    if (custom.isNotEmpty()) SettingsSection("手动添加 · ${custom.size} 项") {
        custom.forEach { course ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(course.name, color = InkDark, fontSize = 14.sp)
                    Text("第${course.startPeriod}-${course.endPeriod}节 · ${course.startWeek}-${course.endWeek}周", color = InkGray, fontSize = 11.sp)
                }
                TextButton(onClick = { pendingDelete = course }) { Text("删除", color = DangerFg) }
            }
        }
    }
    pendingDelete?.let { course ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("删除手动课程？") },
            text = { Text(course.name) }, confirmButton = { TextButton(onClick = { model.removeCustomCourse(course); pendingDelete = null }) { Text("删除", color = DangerFg) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }, containerColor = CardWhite)
    }
}
