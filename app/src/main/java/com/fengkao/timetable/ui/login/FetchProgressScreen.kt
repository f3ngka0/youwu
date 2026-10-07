package com.fengkao.timetable.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.CardWhite
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.SageBg

/**
 * 登录后的首次课表获取进度页：进度条 + 实时日志，获取完成才进入主界面。
 * 失败时停留本页，提供「重试」与「跳过（用缓存进入）」。
 */
@Composable
fun FetchProgressScreen(
    logs: List<String>,
    failed: Boolean,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    // 日志区自动滚到最底部（最新一条始终可见）
    val logScroll = rememberScrollState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logScroll.animateScrollTo(logScroll.maxValue)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SageBg)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFDECEC)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFD64545),
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("课表获取失败", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkDark)
                Text("请检查网络与账号密码后重试", fontSize = 13.sp, color = InkMid)
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(com.fengkao.timetable.ui.theme.BrandGreenSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = BrandGreen,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("正在获取课表", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkDark)
                Text("正在从教务系统同步最新课表，请稍候", fontSize = 13.sp, color = InkMid)
            }

            Spacer(Modifier.height(18.dp))
            if (!failed) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandGreen,
                    trackColor = Color(0xFFE8ECDF),
                )
            } else {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFD64545),
                    trackColor = Color(0xFFE8ECDF),
                )
            }

            // 实时日志（最新在底部，自动滚动）
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF6F8F1))
                    .verticalScroll(logScroll)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .height(132.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val shown = remember(logs) { logs.takeLast(8) }
                shown.forEachIndexed { i, line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (i == shown.lastIndex) InkDark else InkGray,
                        fontWeight = if (i == shown.lastIndex) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }

            if (failed) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("跳过进入", color = InkMid)
                    }
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    ) {
                        Text("重试")
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = BrandGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
