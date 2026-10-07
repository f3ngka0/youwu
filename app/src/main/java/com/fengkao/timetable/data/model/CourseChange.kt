package com.fengkao.timetable.data.model

import kotlinx.serialization.Serializable

/** 课程异动类型 */
object ChangeType {
    const val ADDED = "added"       // 新增
    const val CHANGED = "changed"   // 调整（时间/教室变化）
    const val REMOVED = "removed"   // 取消
}

@Serializable
data class CourseChange(
    val type: String,
    /** 课程名 */
    val course: String,
    /** 变化明细（渲染用文本，如「周一 3-4节 · 明科楼1309 → 周五 6-7节 · 明理S205」） */
    val detail: String,
    /** 检测时间戳 */
    val detectedAt: Long,
)
