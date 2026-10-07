package com.fengkao.timetable.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.saveable.rememberSaveable
import com.fengkao.timetable.data.local.AppSettings
import com.fengkao.timetable.ui.MainViewModel
import java.time.LocalDate
import com.fengkao.timetable.data.model.PeriodTime
import com.fengkao.timetable.ui.theme.*

private val TIME_REGEX = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditors(editor: String?, settings: AppSettings, start: LocalDate, model: MainViewModel, dismiss: () -> Unit) {
    if (editor == "date") {
        val picker = rememberDatePickerState(initialSelectedDateMillis = start.toEpochDay() * 86_400_000L)
        DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { model.setTermStartEpochDay(it / 86_400_000L) }; dismiss() }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }) {
            DatePicker(picker, title = { Text("第一周第一天", modifier = Modifier.padding(16.dp), color = InkDark) })
        }
    }
    if (editor == "periods") PeriodEditorDialog(settings.periodTimes(), dismiss) { model.setPeriodTimes(it); dismiss() }
    if (editor == "weeks") {
        var value by rememberSaveable { mutableStateOf(settings.termWeeks.toString()) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = dismiss, title = { Text("学期周数") }, text = {
            OutlinedTextField(value, { value = it; error = false }, singleLine = true, label = { Text("4–30 周") }, isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { if (error) Text("请输入 4–30 的整数") })
        }, confirmButton = { TextButton(onClick = { val weeks = value.toIntOrNull(); if (weeks != null && weeks in 4..30) { model.setTermWeeks(weeks); dismiss() } else error = true }) { Text("保存") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }, containerColor = CardWhite)
    }
    if (editor == "url") {
        var url by rememberSaveable { mutableStateOf(settings.baseUrl) }
        AlertDialog(onDismissRequest = dismiss, title = { Text("教务系统地址") }, text = {
            OutlinedTextField(url, { url = it }, singleLine = true, placeholder = { Text("https://jwxt.xxx.edu.cn") })
        }, confirmButton = { TextButton(onClick = { model.setBaseUrl(url.trim().removeSuffix("/")); dismiss() }) { Text("保存") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }, containerColor = CardWhite)
    }
}

@Composable
internal fun PeriodEditorDialog(
    initial: List<PeriodTime>,
    onDismiss: () -> Unit,
    onSave: (List<PeriodTime>) -> Unit,
) {
    val periods = remember { initial.sortedBy { it.period } }
    val starts = remember { periods.map { it.startText() }.toMutableStateList() }
    val ends = remember { periods.map { it.endText() }.toMutableStateList() }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("作息时间", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                periods.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("第${p.period}节", fontSize = 13.sp, color = InkMid, modifier = Modifier.width(52.dp))
                        OutlinedTextField(
                            value = starts[i],
                            onValueChange = { starts[i] = it },
                            singleLine = true,
                            modifier = Modifier.width(86.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                        Text("  –  ", fontSize = 13.sp, color = InkGray)
                        OutlinedTextField(
                            value = ends[i],
                            onValueChange = { ends[i] = it },
                            singleLine = true,
                            modifier = Modifier.width(86.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = mutableListOf<PeriodTime>()
                for ((i, p) in periods.withIndex()) {
                    val sm = TIME_REGEX.matchEntire(starts[i].trim())
                    val em = TIME_REGEX.matchEntire(ends[i].trim())
                    if (sm == null || em == null) {
                        error = "时间格式应为 HH:mm，如 08:00"
                        return@TextButton
                    }
                    parsed += PeriodTime(
                        period = p.period,
                        startHour = sm.groupValues[1].toInt(),
                        startMinute = sm.groupValues[2].toInt(),
                        endHour = em.groupValues[1].toInt(),
                        endMinute = em.groupValues[2].toInt(),
                    )
                }
                onSave(parsed)
            }) { Text("保存", color = BrandGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp),
    )
}
