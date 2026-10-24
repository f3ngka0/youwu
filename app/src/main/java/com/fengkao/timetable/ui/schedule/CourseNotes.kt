package com.fengkao.timetable.ui.schedule

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import java.time.LocalDate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.data.model.CourseNote
import com.fengkao.timetable.data.model.NoteKind
import com.fengkao.timetable.ui.glass.liquidPress
import com.fengkao.timetable.ui.theme.GlassControlColor
import com.fengkao.timetable.ui.theme.GlassEdge
import com.fengkao.timetable.ui.theme.InkDark
import com.fengkao.timetable.ui.theme.InkGray
import com.fengkao.timetable.ui.theme.InkMid
import com.fengkao.timetable.ui.theme.SheetColor
import com.fengkao.timetable.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * 课程图标：用课程名首字 + 课程自身的马卡龙底色。
 *
 * 为什么不用统一的书本图标：课表网格里每门课已经是按颜色区分的，
 * 详情页却用同一个蓝色书本，颜色线索在这里断掉，用户没法把「这个色块」
 * 和「课表里那个色块」对上。
 */
@Composable
fun CourseMonogram(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    corner: androidx.compose.ui.unit.Dp = 16.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
) {
    val palette = remember(name) { paletteFor(name) }
    // 先尝试「这门课是什么学科」的矢量图标（高数=计算器、英语=翻译、体育=哑铃……），
    // 学科库覆盖不到的冷门课（比如某一门校本选修）再退回首字，保证任何课名都有图标可画
    val icon = remember(name) { CourseIcons.iconFor(name) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(palette.container)
            .border(1.dp, palette.title.copy(alpha = 0.28f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = palette.title,
                modifier = Modifier.size(size * 0.52f),
            )
        } else {
            Text(
                // 首字取「有意义的第一个字」：英文课名取首字母，中文取首字，都是单字最稳
                monogramOf(name),
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Bold,
                color = palette.title,
                maxLines = 1,
            )
        }
    }
}

/** 「大学英语」→「大」，「English Writing」→「E」，「」→「课」。 */
fun monogramOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "课"
    val first = trimmed.first()
    return if (first in '\u4e00'..'\u9fa5' || first.isLetter()) first.uppercaseChar().toString() else "课"
}

/** 详情页里的笔记区块：按类型分组，未完成的提醒排前面。 */
@Composable
fun CourseNotesSection(
    notes: List<CourseNote>,
    onAdd: () -> Unit,
    onOpen: (CourseNote) -> Unit,
    onToggleDone: (CourseNote) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassControlColor)
            .border(1.dp, GlassEdge, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("课程笔记", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
            Spacer(Modifier.width(8.dp))
            if (notes.isNotEmpty()) {
                Text(
                    "${notes.count { !it.done }}/${notes.size}",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = InkGray,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "添加",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(InkDark)
                    .liquidPress(pressedScale = 0.94f, onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (notes.isEmpty()) {
            Text(
                "还没有记录。可以记作业、课堂笔记或考点，也能贴图片、加提醒。",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = InkGray,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.take(4).forEach { note ->
                    NoteRow(note = note, onOpen = { onOpen(note) }, onToggleDone = { onToggleDone(note) })
                }
                if (notes.size > 4) {
                    Text(
                        "还有 ${notes.size - 4} 条，点「添加」进入列表查看",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = InkGray,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: CourseNote, onOpen: () -> Unit, onToggleDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .liquidPress(pressedScale = 0.985f, onClick = onOpen)
            .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选与打开是两件事：勾选是 28dp 独立热区，点卡片其余地方才进编辑
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .liquidPress(pressedScale = 0.88f, onClick = onToggleDone),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (note.done) InkDark else Color.Transparent)
                    .border(1.4.dp, if (note.done) InkDark else InkGray, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (note.done) {
                    Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteKindChip(note.kind)
                if (note.remindAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(12.dp), tint = InkGray)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        note.remindText.ifBlank { formatRemind(note.remindAt) },
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = InkGray,
                        maxLines = 1,
                    )
                }
                if (note.images.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Image, null, Modifier.size(12.dp), tint = InkGray)
                    Spacer(Modifier.width(2.dp))
                    Text("${note.images.size}", fontSize = 10.5.sp, lineHeight = 14.sp, color = InkGray)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                note.content.ifBlank { if (note.images.isNotEmpty()) "（图片）" else "（空）" },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (note.done) InkGray else InkDark,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NoteKindChip(kind: NoteKind) {
    val (fg, bg) = when (kind) {
        NoteKind.HOMEWORK -> Color(0xFFB7791F) to Color(0xFFFEF3C7)
        NoteKind.NOTE -> Color(0xFF2B6CB0) to Color(0xFFEBF8FF)
        NoteKind.POINT -> Color(0xFF805AD5) to Color(0xFFF3E8FF)
    }
    Text(
        kind.label,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

fun formatRemind(at: Long): String {
    val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%d/%d %02d:%02d".format(date.monthValue, date.dayOfMonth, time.hour, time.minute)
}

/** 新建 / 编辑一条课程笔记。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorSheet(
    courseName: String,
    existing: CourseNote?,
    onDismiss: () -> Unit,
    onSave: (CourseNote) -> Unit,
    onDelete: (String) -> Unit,
    onImportImage: suspend (ByteArray) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(existing?.kind ?: NoteKind.NOTE) }
    var text by remember { mutableStateOf(existing?.content.orEmpty()) }
    val images = remember { mutableStateListOf<String>().apply { existing?.images?.let { addAll(it) } } }
    var remindEnabled by remember { mutableStateOf(existing?.remindAt != null) }
    var remindAt by remember { mutableStateOf(existing?.remindAt ?: (System.currentTimeMillis() + 24 * 3600_000L)) }
    var remindText by remember { mutableStateOf(existing?.remindText.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    // 图片：用系统相册选择器（无需存储权限），选完立刻复制进私有目录
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            } ?: return@launch
            // 大图先缩：笔记里只需要看得到内容，原图很占空间
            val compressed = withContext(Dispatchers.IO) { downscaleJpeg(bytes) }
            onImportImage(compressed)?.let { images += it }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourseMonogram(courseName, size = 34.dp, corner = 11.dp, fontSize = 15.sp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (existing == null) "记一条" else "编辑",
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkDark,
                    )
                    Text(
                        courseName,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = InkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (existing != null) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        "删除",
                        tint = InkGray,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .liquidPress(pressedScale = 0.9f) { onDelete(existing.id) }
                            .padding(7.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // 类型三选一：并排等宽，选中色就是列表里那个颜色，视觉上前后一致
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteKind.entries.forEach { item ->
                    val selected = kind == item
                    Text(
                        item.label,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else InkMid,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) InkDark else GlassControlColor)
                            .clickable { kind = item }
                            .padding(vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(600) },
                label = { Text("内容") },
                placeholder = { Text("例如：第三章习题 1-8 题，周四交") },
                minLines = 3,
                maxLines = 7,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images.toList(), key = { it }) { path ->
                    Box(Modifier.size(72.dp)) {
                        val bitmap = remember(path) {
                            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap.asImageBitmap(),
                                null,
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GlassControlColor)
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { images.remove(path) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Close, "移除图片", Modifier.size(11.dp), tint = Color.White)
                        }
                    }
                }
                item {
                    Column(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassControlColor)
                            .border(1.dp, GlassEdge, RoundedCornerShape(10.dp))
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = InkMid)
                        Spacer(Modifier.height(2.dp))
                        Text("图片", fontSize = 10.5.sp, lineHeight = 14.sp, color = InkGray)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // 提醒：默认关闭（绝大多数笔记不需要闹钟），打开后给快捷选择，避免手输日期
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassControlColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(16.dp), tint = InkMid)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (remindEnabled) "提醒：${formatRemind(remindAt)}" else "不提醒",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = InkDark,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (remindEnabled) "关闭" else "加提醒",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable {
                            remindEnabled = !remindEnabled
                            if (remindEnabled && remindText.isBlank()) {
                                remindText = ""
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (remindEnabled) {
                Spacer(Modifier.height(6.dp))
                var showPicker by remember { mutableStateOf(false) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "今晚 20:00" to 0,
                        "明天 8:00" to 1,
                        "后天 8:00" to 2,
                        "本周末" to 3,
                    ).forEach { (label, offset) ->
                        Text(
                            label,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = InkMid,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.9f))
                                .clickable {
                                    remindAt = quickRemind(offset)
                                    remindText = label
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    // 快捷项覆盖不了「下周三晚上交手写稿」这种，给一个能精确到分钟的入口
                    Text(
                        "自定义…",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { showPicker = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                if (showPicker) {
                    RemindPickerDialog(
                        initial = remindAt,
                        onDismiss = { showPicker = false },
                        onConfirm = { at ->
                            remindAt = at
                            remindText = ""
                            showPicker = false
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "到点会发一条系统通知",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = InkGray,
                )
            }

            Spacer(Modifier.height(16.dp))
            val now = System.currentTimeMillis()
            val canSave = text.isNotBlank() || images.isNotEmpty()
            Text(
                if (canSave) "保存" else "写点内容或加张图",
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (canSave) Color.White else InkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) InkDark else GlassControlColor)
                    .clickable(enabled = canSave && !saving) {
                        saving = true
                        onSave(
                            CourseNote(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                course = courseName,
                                kind = kind,
                                content = text.trim(),
                                images = images.toList(),
                                remindAt = if (remindEnabled) remindAt else null,
                                remindText = if (remindEnabled) remindText else "",
                                done = existing?.done ?: false,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now,
                            )
                        )
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/**
 * 自定义提醒时间：先选日期再选时刻（分钟级）。
 *
 * 分两步而不是塞进同一个对话框：M3 的日期盘和表盘都很占高度，
 * 375dp 宽的底部弹层里并排放不下，分步反而每步都能点准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindPickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initialZoned = remember(initial) { Instant.ofEpochMilli(initial).atZone(zone) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialZoned.toLocalDate().toEpochDay() * 86_400_000L,
    )
    val timeState = rememberTimePickerState(
        initialHour = initialZoned.hour,
        initialMinute = initialZoned.minute,
        is24Hour = true,
    )
    var step by remember { mutableStateOf(0) }

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        ) {
            DatePicker(
                dateState,
                title = {
                    Text("提醒日期", modifier = Modifier.padding(16.dp), color = InkDark)
                },
            )
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("提醒时间", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkDark) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val day = LocalDate.ofEpochDay((dateState.selectedDateMillis ?: initial) / 86_400_000L)
                    val at = day.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
                    onConfirm(at)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) { Text("上一步") }
            },
            containerColor = SheetColor,
        )
    }
}
/** 快捷提醒时间：0 今晚 20:00、1 明天 8:00、2 后天 8:00、3 本周日 20:00。 */
private fun quickRemind(which: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    val target = when (which) {
        0 -> now.withHour(20).withMinute(0).withSecond(0).withNano(0)
            .let { if (it.isBefore(now)) it.plusDays(1) else it }
        1 -> now.plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        2 -> now.plusDays(2).withHour(8).withMinute(0).withSecond(0).withNano(0)
        else -> now.with(java.time.DayOfWeek.SUNDAY).withHour(20).withMinute(0).withSecond(0).withNano(0)
            .let { if (it.isBefore(now)) it.plusWeeks(1) else it }
    }
    return target.toInstant().toEpochMilli()
}

/**
 * 把任意尺寸的图片缩到长边 1280 再压成 JPEG。
 *
 * 相册里的原图常常 4000px、8MB 起步，直接存会让几十张笔记就吃掉几百兆，
 * 而笔记里只需要「看得清」。
 */
private fun downscaleJpeg(bytes: ByteArray, maxSide: Int = 1280, quality: Int = 82): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return bytes
    val longSide = maxOf(decoded.width, decoded.height)
    val scaled = if (longSide <= maxSide) decoded else {
        val ratio = maxSide.toFloat() / longSide
        android.graphics.Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

/** 已删除图片的清理：笔记里不再引用的文件不该一直留着。 */
fun pruneNoteImages(file: File, keep: Set<String>) {
    runCatching {
        file.listFiles()?.forEach { child ->
            if (child.absolutePath !in keep) child.delete()
        }
    }
}
