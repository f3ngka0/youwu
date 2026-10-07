package com.fengkao.timetable.data.local

import android.content.Context
import com.fengkao.timetable.data.model.CourseChange
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 课程异动记录：每次课表同步时对比新旧课表，
 * 检测「新增 / 取消 / 调整（时间或教室变化）」并覆盖保存为最近一次同步的异动列表。
 */
class CourseChangeStore(context: Context) {

    private val file: File = File(context.filesDir, "course_changes.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(changes: List<CourseChange>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(CourseChange.serializer()), changes))
        }
    }

    fun load(): List<CourseChange> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString(ListSerializer(CourseChange.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun clear() {
        runCatching { file.delete() }
    }
}
