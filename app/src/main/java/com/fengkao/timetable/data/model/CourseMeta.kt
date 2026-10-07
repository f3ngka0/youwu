package com.fengkao.timetable.data.model

import kotlinx.serialization.Serializable

/** 课程类型（用户可在统计页手动调整） */
object CourseType {
    const val MAJOR = "major"   // 专业课
    const val PUB = "pub"       // 公共课
    const val ELE = "ele"       // 选修课
    const val EXP = "exp"       // 实验课

    val LABELS = mapOf(MAJOR to "专业课", PUB to "公共课", ELE to "选修课", EXP to "实验课")
    val ALL = listOf(MAJOR, PUB, ELE, EXP)

    fun label(t: String) = LABELS[t] ?: "专业课"

    /** 按课名推断默认类型（教务不提供分类） */
    fun infer(name: String): String = when {
        Regex("实验|实训|上机") in name -> EXP
        Regex("体育|军事|形势与政策|劳动|安全教育|心理健康|职业生涯|就业指导|美育|军事理论") in name -> PUB
        Regex("选修|通识|美育鉴赏|艺术鉴赏") in name -> ELE
        Regex("思想政治|马克思主义|毛泽东|习近平|思想道德|近现代史|大学英语|英语") in name -> PUB
        else -> MAJOR
    }
}

/** 每门课的本地标签：类型 / 学分 / 是否硬核课 */
@Serializable
data class CourseMeta(
    val type: String = CourseType.MAJOR,
    val credits: Double = 0.0,
    val isHard: Boolean = false,
)
