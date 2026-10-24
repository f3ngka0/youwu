package com.fengkao.timetable.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.BuildConfig
import com.fengkao.timetable.data.update.UpdateInfo
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.BrandGreenSoft
import com.fengkao.timetable.ui.theme.CardWhite
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid

/**
 * 发现新版本时的弹窗。
 *
 * 三个出口：去 GitHub 下载（主）/ 以后再说 / 不再提示这个版本。
 * 「不再提示」记的是具体版本号，所以下个版本放出来仍会提醒，不会一关就永久静音。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("发现新版本 ${info.version}", color = InkDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "当前版本 ${BuildConfig.VERSION_NAME}",
                    color = InkGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (info.notes.isNotBlank()) {
                    // Release 正文是 Markdown 原文，这里按纯文本展示：# 和小节标题原样保留，
                    // 比硬塞一个 Markdown 渲染器稳，字数也不多。
                    Text(cleanNotes(info.notes), color = InkMid, fontSize = 13.sp, lineHeight = 20.sp)
                } else {
                    Text("这个版本带来了一些改进与修复。", color = InkMid, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(
                    if (info.apkUrl != null) "去下载" else "查看发布页",
                    color = BrandGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onLater) { Text("以后再说", color = InkGray, fontSize = 13.sp) }
                TextButton(onClick = onSkipVersion) { Text("不再提示", color = InkGray, fontSize = 13.sp) }
            }
        },
    )
}

/**
 * 关于页里那行「检查更新 / 有新版本」入口。
 */
@Composable
fun UpdateRow(
    checking: Boolean,
    latest: UpdateInfo?,
    hint: String?,
    hintError: Boolean,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    checking -> "正在检查更新…"
                    latest != null -> "发现新版本 ${latest.version}"
                    else -> "检查更新"
                },
                color = if (latest != null) BrandGreen else InkDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            val sub = when {
                latest != null -> "当前 ${BuildConfig.VERSION_NAME} · 点击查看更新内容"
                checking -> "正在连接 GitHub"
                hint != null -> hint
                else -> "当前版本 ${BuildConfig.VERSION_NAME}"
            }
            Text(
                sub,
                color = if (hintError) InkMid else InkGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        TextButton(onClick = onCheck, enabled = !checking) {
            Text(
                if (latest != null) "更新" else "检查",
                color = if (checking) InkGray else BrandGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (checking) BrandGreenSoft.copy(alpha = 0.4f) else BrandGreenSoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 打开发布页 / 直连 APK：优先浏览器打开，交给系统下载器与浏览器处理，不自己实现下载 */
fun openUpdatePage(context: android.content.Context, info: UpdateInfo) {
    val url = info.apkUrl ?: info.pageUrl
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Release 正文是 Markdown 原文，这里只剥离标记符号、保留文字：
 * 标题的 `#`、列表的 `- `、行内 `**粗体**`、`` `代码` ``、`[文字](链接)` 都还原成纯文本，
 * 上下文里塞一个 Markdown 渲染器不值得，字数也不多。
 */
private val mdLink = Regex("""\[([^\]]*)]\(([^)]*)\)""")

internal fun cleanNotes(raw: String): String = raw.lineSequence()
    .map { line ->
        line
            // 列表符号必须先转成「·」，否则下一步会把 `*` 当强调符号吃掉
            .replace(Regex("""^\s*[-*+]\s+"""), "· ")
            .replace(Regex("""^\s*#{1,6}\s*"""), "")
            .let { mdLink.replace(it, "$1") }
            .replace(Regex("""(\*\*|__|\*|`|~~)"""), "")
            .trim()
    }
    .filter { it.isNotBlank() }
    .joinToString("\n")
