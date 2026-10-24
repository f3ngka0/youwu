package com.fengkao.timetable.ui.schedule

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fengkao.timetable.data.model.CourseNote
import com.fengkao.timetable.data.model.NoteKind
import com.fengkao.timetable.ui.glass.liquidPress
import com.fengkao.timetable.ui.theme.GlassControlColor
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.SheetColor

/**
 * 课程笔记的「查看页」。
 *
 * 之前点列表直接进编辑器，图片只能看到 72dp 的缩略图、正文被输入框挤成三行，
 * 记录变成「自己的东西都看不全」。查看页只负责读：大图、全文、提醒时间，
 * 想改再点右上角的笔。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewSheet(
    courseName: String,
    note: CourseNote,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var viewerPath by remember(note.id) { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourseMonogram(courseName, size = 34.dp, corner = 11.dp, fontSize = 15.sp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteKindChip(note.kind)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            courseName,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = InkGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (note.done) "已完成" else "未完成",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (note.done) InkGray else InkDark,
                    )
                }
                Icon(
                    Icons.Outlined.EditNote,
                    "编辑",
                    tint = InkDark,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .liquidPress(pressedScale = 0.9f, onClick = onEdit)
                        .padding(8.dp),
                )
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "删除",
                    tint = InkGray,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .liquidPress(pressedScale = 0.9f, onClick = onDelete)
                        .padding(8.dp),
                )
            }

            if (note.remindAt != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassControlColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(15.dp), tint = InkMid)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "提醒 ${formatRemind(note.remindAt)}" +
                            note.remindText.takeIf { it.isNotBlank() && it != "自定义" }?.let { " · $it" }.orEmpty(),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = InkDark,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                note.content.ifBlank { if (note.images.isNotEmpty()) "（只有图片）" else "（空）" },
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = if (note.done) InkMid else InkDark,
            )

            if (note.images.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    note.images.forEach { path ->
                        NoteBigImage(path) { viewerPath = path }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("点图片看大图", fontSize = 11.sp, lineHeight = 15.sp, color = InkGray)
            }
        }
    }
    viewerPath?.let { path ->
        FullScreenImage(path, onDismiss = { viewerPath = null })
    }
}

/**
 * 图片预览：铺满宽度，高度封顶 220dp。
 *
 * 长截图（手机竖屏整页）按原比例铺满后能占满两三屏，一条笔记要滑很久；
 * 缩到高度上限又会只剩窄窄一条、右边一大片空白。所以长图统一「铺满宽 + 从顶部裁」，
 * 看起来像一页纸的开头，想看全就点开全屏。
 */
@Composable
private fun NoteBigImage(path: String, onClick: () -> Unit) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    if (bitmap == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GlassControlColor)
                .border(1.dp, GlassEdge, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Image, null, Modifier.size(22.dp), tint = InkGray)
        }
        return
    }
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cap = 220.dp
        val natural = maxWidth / ratio
        val tall = natural > cap
        val h = if (tall) cap else natural
        Box(
            Modifier
                .fillMaxWidth()
                .height(h)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.04f))
                .border(1.dp, GlassEdge, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "笔记图片",
                modifier = Modifier.fillMaxSize(),
                // 长图从顶部裁：笔记重点通常在第一行
                contentScale = if (tall) ContentScale.Crop else ContentScale.Fit,
                alignment = Alignment.TopCenter,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenImage(path: String, onDismiss: () -> Unit) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "笔记图片",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Icon(
                Icons.Outlined.Close,
                "关闭",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .liquidPress(pressedScale = 0.9f, onClick = onDismiss)
                    .padding(9.dp),
            )
        }
    }
}
