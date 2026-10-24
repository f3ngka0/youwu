package com.fengkao.timetable.ui.xuexitong

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.fengkao.timetable.data.model.CourseBlock
import com.fengkao.timetable.data.xuexitong.NoticeCategory
import com.fengkao.timetable.data.xuexitong.NoticeFormat
import com.fengkao.timetable.data.xuexitong.XuexitongNotice
import com.fengkao.timetable.ui.theme.IsDark
import com.fengkao.timetable.ui.theme.paletteFor

// 设计稿强调色：科技蓝
val NoticeBlue: Color get() = if (IsDark) Color(0xFF8EC3F0) else Color(0xFF2B6CB0)
val NoticeBlueSoft: Color get() = if (IsDark) Color(0xFF17324D) else Color(0xFFEBF8FF)
val NoticeBg: Color get() = if (IsDark) Color(0xFF0A0A0D) else Color(0xFFF7F8FA)
val NoticeCard: Color get() = if (IsDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
val NoticeHairline: Color get() = if (IsDark) Color(0xFF2C2F37) else Color(0xFFF0F4F8)
val NoticeCapsule: Color get() = if (IsDark) Color(0xFF2A2D36) else Color(0xFFEDF2F7)
val NoticeInk: Color get() = if (IsDark) Color(0xFFF0F1F5) else Color(0xFF1A202C)
val NoticeSub: Color get() = if (IsDark) Color(0xFFC3C8D2) else Color(0xFF4A5568)

// 11–12sp 的 meta 文字按 WCAG AA 要 4.5:1。#718096 在白底只有 4.02、
// 在画布 #F7F8FA 上 3.78，属不合格；#667085 是 4.97 / 4.68，跨这两种底都过线。
val NoticeMeta: Color get() = if (IsDark) Color(0xFF98A0AE) else Color(0xFF667085)

// 只用于「非文字」场景（图标 tint、轨道、占位装饰）。任何会被阅读的小字
// 都不该用它：它在白底只有 2.26:1，正是 P4 那批不合格文字的来源。
val NoticeFaint: Color get() = if (IsDark) Color(0xFF737B89) else Color(0xFFA0AEC0)

// 作业红：原来的 #E53E3E 落在 #FFF5F5 上只有 3.86:1，#C53030 是 5.11:1。
val NoticeDangerFg: Color get() = if (IsDark) Color(0xFFF3A0A0) else Color(0xFFC53030)
val NoticeDangerBg: Color get() = if (IsDark) Color(0xFF3A1F1F) else Color(0xFFFFF5F5)
val NoticeDangerBorder: Color get() = if (IsDark) Color(0xFF7C3A3A) else Color(0xFFFEB2B2)
val NoticeNeutral: Color get() = if (IsDark) Color(0xFF56606E) else Color(0xFFCBD5E0)

/** 玻璃卡外缘描边：白卡落在浅灰背景上，需要一点冷灰对比才看得出玻璃边 */
val GlassEdgeShadow: Color get() = if (IsDark) Color(0xFF11131A) else Color(0x1A2B3441)

val NoticeCardShape = RoundedCornerShape(20.dp)
val NoticeSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

data class NoticeCourseVisual(
    val courseName: String,
    val base: Color,
    val pillBg: Color,
    val pillText: Color,
)

/** 设计稿指定的课表马卡龙联动色；其余课程沿用应用既有课程色。 */
fun courseBaseColor(courseName: String): Color = when {
    courseName.isBlank() -> NoticeNeutral
    courseName.contains("软件工程") -> Color(0xFFF4B8B8)
    courseName.contains("WEB应用开发", ignoreCase = true) ||
        courseName.contains("WEB 应用开发", ignoreCase = true) -> Color(0xFFF2C6B6)
    courseName.contains("计算机网络") -> Color(0xFFE9B8D2)
    courseName.contains("操作系统") -> Color(0xFFC3CEEA)
    else -> paletteFor(courseName).container
}

fun noticeCourseVisual(notice: XuexitongNotice, courses: List<CourseBlock>): NoticeCourseVisual {
    val name = NoticeFormat.matchCourseName(
        notice.title,
        notice.content,
        courses.map { it.name },
    )
    val base = courseBaseColor(name)
    return NoticeCourseVisual(
        courseName = name,
        base = base,
        pillBg = if (IsDark) lerp(base, Color.Black, 0.55f) else lerp(base, Color.White, 0.55f),
        pillText = if (IsDark) lerp(base, Color.White, 0.40f) else lerp(base, Color.Black, 0.48f),
    )
}

data class CategoryStyle(val bg: Color, val fg: Color, val border: Color)

fun categoryStyle(category: NoticeCategory): CategoryStyle = when (category) {
    NoticeCategory.HOMEWORK -> CategoryStyle(NoticeDangerBg, NoticeDangerFg, NoticeDangerBorder)
    NoticeCategory.SIGNIN -> CategoryStyle(NoticeBlueSoft, NoticeBlue, NoticeBlue.copy(alpha = 0.35f))
    NoticeCategory.ANNOUNCEMENT -> CategoryStyle(NoticeCapsule, NoticeSub, NoticeHairline)
    NoticeCategory.OTHER -> CategoryStyle(NoticeCapsule, NoticeMeta, NoticeHairline)
}
