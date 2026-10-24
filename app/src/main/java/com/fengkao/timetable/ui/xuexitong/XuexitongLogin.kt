package com.fengkao.timetable.ui.xuexitong

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.data.xuexitong.NoticeFormat
import com.fengkao.timetable.ui.profile.SettingsHint
import com.fengkao.timetable.ui.profile.SettingsItem
import com.fengkao.timetable.ui.profile.SettingsSection
import com.fengkao.timetable.ui.profile.SettingsSwitchItem
import com.fengkao.timetable.ui.profile.SettingsValue
import com.fengkao.timetable.ui.profile.maskAccount
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.water.LifeCard

@Composable
internal fun XuexitongLogin(
    state: XuexitongUiState,
    model: XuexitongViewModel,
    showHeading: Boolean = true,
    submitLabel: String = "登录学习通",
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    LifeCard {
        if (showHeading) {
            Text("连接学习通", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Spacer(Modifier.height(6.dp))
            Text("自动汇总作业、签到与课程公告", color = InkGray, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
        }
        OutlinedTextField(
            value = state.loginAccount,
            onValueChange = model::account,
            label = { Text("学习通手机号 / 账号") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.loginPassword,
            onValueChange = model::password,
            label = { Text("密码") },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (showPassword) "隐藏密码" else "显示密码",
                        tint = InkGray,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = model::login,
            enabled = !state.busy && state.loginAccount.isNotBlank() && state.loginPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color.White),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(submitLabel, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "仅读取通知，不执行签到或提交作业。账号密码经系统密钥加密后保存在本机。",
            color = InkGray,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
internal fun XuexitongNotificationPermissionEffect(model: XuexitongViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) model.setNotifyEnabled(true) else model.notifyPermissionDenied()
    }
    LaunchedEffect(state.loggedIn, state.sessionExpired, state.notifyEnabled) {
        if (!state.loggedIn || state.sessionExpired || !state.notifyEnabled) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
internal fun XuexitongAccountPage(state: XuexitongUiState, model: XuexitongViewModel) {
    if (state.loggedIn && !state.sessionExpired) {
        SettingsSection {
            SettingsValue("账号", maskAccount(state.phone))
            SettingsValue("账户状态", "已登录")
            SettingsValue("最近抓取", NoticeFormat.fetchAge(state.lastFetchAt))
            SettingsItem(
                "立即刷新",
                Icons.Outlined.Refresh,
                value = if (state.busy) "抓取中" else null,
                enabled = !state.busy,
            ) { model.refresh() }
        }
        SettingsSection("通知") {
            SettingsSwitchItem(
                title = "后台通知",
                icon = Icons.Outlined.Notifications,
                subtitle = "每 ${com.fengkao.timetable.data.xuexitong.XuexitongNoticeScheduler.PERIOD_MINUTES} 分钟检查新通知",
                checked = state.notifyEnabled,
                onChange = model::setNotifyEnabled,
            )
        }
        SettingsHint("已读状态仅保存在本机；通知内容只来自学习通通知中心，不含私信。")
        SettingsSection(danger = true) {
            SettingsItem(
                "退出学习通账户",
                Icons.AutoMirrored.Outlined.Logout,
                enabled = !state.busy,
                danger = true,
            ) { model.logout() }
        }
    } else {
        if (state.sessionExpired) {
            SettingsHint("学习通登录已失效，请重新输入密码登录。", error = true)
        }
        XuexitongLogin(
            state = state,
            model = model,
            submitLabel = if (state.sessionExpired) "重新登录学习通" else "登录学习通",
        )
        if (state.loggedIn) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "退出学习通账户",
                    color = NoticeMeta,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(enabled = !state.busy) { model.logout() }
                        .padding(8.dp),
                )
            }
        }
    }
    state.message?.let { SettingsHint(it, state.error) }
}
