package com.fengkao.timetable.data.model

import kotlinx.serialization.Serializable

/**
 * 一门课程在课表中的一个“格子”：第 dayOfWeek 天的第 startPeriod..endPeriod 节，
 * 在 weeks 中的每一周上课。字段名与 new-school-sdk 解析结果保持一致。
 */
@Serializable
data class CourseBlock(
    val name: String,
    val place: String,
    val teacher: String,
    val campus: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weeksText: String,
    val className: String = "",
) {
    fun coversWeek(week: Int): Boolean = week in weeks

    /** 本地管理（隐藏/恢复）用的稳定标识：同一门课的同一格子 */
    val stableKey: String
        get() = listOf(name, dayOfWeek, startPeriod, endPeriod, teacher, className).joinToString("|")
}

@Serializable
data class StudentInfo(
    val name: String = "",
    val studentId: String = "",
    val className: String = "",
    val department: String = "",
    val major: String = "",
    val grade: String = "",
)

/**
 * 统计页「空闲时间块分析」：用户手动添加的事务占用（每周重复）。
 * startMin / endMin 为当日 0 点起的分钟数，[startMin, endMin) 视为被占用。
 */
@Serializable
data class BusyBlock(
    val id: Long,
    val title: String,
    val dayOfWeek: Int,   // 1=周一 .. 7=周日
    val startMin: Int,
    val endMin: Int,
)

@Serializable
data class CachedSchedule(
    val xnm: Int = 0,
    val term: Int = 1,
    val fetchedAt: Long = 0L,
    val student: StudentInfo = StudentInfo(),
    val courses: List<CourseBlock> = emptyList(),
) {
    fun isEmpty() = courses.isEmpty()
}

/** 单节课的作息时间（24 小时制） */
@Serializable
data class PeriodTime(
    val period: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
) {
    fun startText(): String = "%02d:%02d".format(startHour, startMinute)
    fun endText(): String = "%02d:%02d".format(endHour, endMinute)
}

/** 用户手动添加的课程（本地，独立于教务同步数据，合并进课表显示） */
@Serializable
data class CustomCourse(
    val name: String,
    val place: String = "",
    val teacher: String = "",
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val oddOnly: Boolean = false,
    val evenOnly: Boolean = false,
) {
    fun toCourseBlock(): CourseBlock {
        val weeks = (startWeek..endWeek).filter {
            if (oddOnly) it % 2 == 1 else if (evenOnly) it % 2 == 0 else true
        }
        return CourseBlock(
            name = name, place = place, teacher = teacher, campus = "",
            dayOfWeek = dayOfWeek, startPeriod = startPeriod, endPeriod = endPeriod,
            weeks = weeks, weeksText = "",
        )
    }
}

/**
 * 课程笔记：一门课下用户自己记的东西。
 *
 * 三种类型（作业 / 笔记 / 考点）用同一张表，只是 kind 不同——
 * 用户心里「这门课要交什么、老师讲了什么、考什么」本来就是同一处翻。
 */
@Serializable
data class CourseNote(
    val id: String,
    /** 归属课程名，与 CourseBlock.name 对齐 */
    val course: String,
    val kind: NoteKind = NoteKind.NOTE,
    val content: String = "",
    /** 图片的本地绝对路径（复制到 files/notes 后再存，原图被删也不影响） */
    val images: List<String> = emptyList(),
    /** 可选提醒时间；null 表示不提醒 */
    val remindAt: Long? = null,
    val remindText: String = "",
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
enum class NoteKind(val label: String) {
    HOMEWORK("作业"),
    NOTE("笔记"),
    POINT("考点"),
}

/** 学校作息时间表（默认 12 节） */
fun defaultPeriodTimes(): List<PeriodTime> = listOf(
    PeriodTime(1, 8, 0, 8, 40),
    PeriodTime(2, 8, 45, 9, 25),
    PeriodTime(3, 9, 35, 10, 15),
    PeriodTime(4, 10, 25, 11, 5),
    PeriodTime(5, 11, 10, 11, 50),
    PeriodTime(6, 14, 30, 15, 10),
    PeriodTime(7, 15, 15, 15, 55),
    PeriodTime(8, 16, 5, 16, 45),
    PeriodTime(9, 16, 50, 17, 30),
    PeriodTime(10, 19, 30, 20, 10),
    PeriodTime(11, 20, 15, 20, 55),
    PeriodTime(12, 21, 5, 21, 45),
)
