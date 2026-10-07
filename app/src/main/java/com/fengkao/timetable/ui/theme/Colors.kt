package com.fengkao.timetable.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color

// ---------------- 动态主题状态（TimetableTheme 按 system dark 更新，全部组合期读取） ----------------
private var themeDark by mutableStateOf(false)

/** 当前是否深色模式 */
val IsDark: Boolean get() = themeDark

fun updateThemeDark(dark: Boolean) {
    themeDark = dark
}

// ---------------- 画布 / 卡片 ----------------
val SageBg: Color get() = if (themeDark) Color(0xFF060608) else Color(0xFFF5F5F7)
val CardWhite: Color get() = if (themeDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
val Hairline: Color get() = if (themeDark) Color(0x24FFFFFF) else Color(0xFFE0E0E0)

// ---------------- 文字（iOS 语义色：label / secondary / tertiary） ----------------
val InkDark: Color get() = if (themeDark) Color(0xF5FFFFFF) else Color(0xFF1D1D1F)
val InkMid: Color get() = if (themeDark) Color(0xBDEBEBF5) else Color(0xB83C3C43)
val InkGray: Color get() = if (themeDark) Color(0x85EBEBF5) else Color(0x6B3C3C43)

// ---------------- 有梧主强调色：RGB 23 / 75 / 142 ----------------
val BrandGreen: Color get() = Color(0xFF174B8E)
val BrandGreenSoft: Color get() = if (themeDark) Color(0xFF132C4F) else Color(0xFFE8EDF4)

// ---------------- 玻璃材质分层（亮：白系；暗：深灰 tile 系） ----------------
val GlassSurfaceColor: Color get() = if (themeDark) Color(0xC2222228) else Color.White.copy(alpha = 0.72f)
val GlassControlColor: Color get() = if (themeDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.44f)
val GlassElevatedColor: Color get() = if (themeDark) Color(0xF0303038) else Color.White.copy(alpha = 0.88f)
val GlassSelectedColor: Color get() = if (themeDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.84f)
val GlassEdge: Color get() = if (themeDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.55f)
val SheetColor: Color get() = if (themeDark) Color(0xF62A2A31) else Color.White.copy(alpha = 0.97f)

// 底栏遮罩（亮：白 42%；暗：黑 30%，滚动联动由 GlassBarState 再加深）
val BarScrim: Color get() = if (themeDark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.42f)
val BarCenterVeil: Color get() = if (themeDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)

/** 底栏顶缘高光条颜色（1~2dp，白 15%） */
val BarTopGlint: Color get() = if (themeDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.15f)

/** 玻璃高光描边（三段：顶亮 / 中弱 / 底亮） */
fun glassRim(): Brush = if (themeDark)
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.34f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.22f),
        )
    )
else
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.95f),
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.55f),
        )
    )

/** 应用画布：纯净灰白 #F7F7F9（深色 #08080B），不做渐变 */
fun appBackground(): Brush = if (themeDark)
    SolidColor(Color(0xFF08080B))
else
    SolidColor(Color(0xFFF7F7F9))

/** Shared icon surfaces use the same solid brand color as the SVG. */
fun brandGradient(): Brush = SolidColor(BrandGreen)

// ---------------- 课程色（Apple 系统色降饱和 50%，明暗通用） ----------------
data class CoursePalette(
    val container: Color,
    val title: Color,
    val body: Color,
    val dot: Color,
)

/** 马卡龙色：淡色底 + 同色系深色字（参考 WakeUp 课程表配色） */
private fun macaron(h: Float) = CoursePalette(
    container = Color.hsl(h, 0.68f, 0.90f),
    title = Color.hsl(h, 0.52f, 0.36f),
    body = Color.hsl(h, 0.46f, 0.42f),
    dot = Color.hsl(h, 0.52f, 0.36f),
)

val CoursePalettes = listOf(0f, 25f, 50f, 85f, 130f, 165f, 195f, 225f, 265f, 300f, 330f, 355f).map { macaron(it) }

/**
 * 按学期课程表分配颜色：同一周内出现的两门不同课程绝不撞色（贪心选第一个不冲突色）。
 * 排序保证分配结果稳定，同名课跨页面/周次同色。
 */
fun paletteMapForCourses(courses: List<com.fengkao.timetable.data.model.CourseBlock>): Map<String, CoursePalette> {
    val byName = courses.groupBy { it.name }
    val result = linkedMapOf<String, CoursePalette>()
    byName.keys.sorted().forEach { name ->
        val weeks = byName[name]!!.fold(emptySet<Int>()) { a, b -> a + b.weeks }
        val used = mutableSetOf<Int>()
        result.forEach { (other, pal) ->
            val otherWeeks = byName[other]!!.fold(emptySet<Int>()) { a, b -> a + b.weeks }
            if (otherWeeks.intersect(weeks).isNotEmpty()) used += CoursePalettes.indexOf(pal)
        }
        val fallback = ((byName.keys.sorted().indexOf(name) % CoursePalettes.size) + CoursePalettes.size) % CoursePalettes.size
        val idx = (0 until CoursePalettes.size).firstOrNull { it !in used } ?: fallback
        result[name] = CoursePalettes[idx]
    }
    return result
}

/** 按索引直接取色（星期图例、环形图等固定顺序场景） */
fun paletteAt(index: Int): CoursePalette {
    val idx = ((index % CoursePalettes.size) + CoursePalettes.size) % CoursePalettes.size
    return CoursePalettes[idx]
}

/**
 * 为一批课程名分配互不相同的颜色（≤12 门时绝不撞色；超出按名排序循环）。
 * 排序保证同一门课在不同页面/周次颜色稳定。
 */
fun paletteMapFor(names: Collection<String>): Map<String, CoursePalette> =
    names.distinct().sorted().mapIndexed { i, n -> n to paletteAt(i) }.toMap()

fun paletteFor(name: String): CoursePalette {
    val idx = ((name.hashCode() % CoursePalettes.size) + CoursePalettes.size) % CoursePalettes.size
    return CoursePalettes[idx]
}
