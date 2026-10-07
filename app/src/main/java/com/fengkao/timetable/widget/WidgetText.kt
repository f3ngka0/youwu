package com.fengkao.timetable.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint

/**
 * 文本预处理与测量：
 * - 教室缩写（明科楼1309→明科1309、明理楼S-205→明理S205、第3教学楼301→3教301、
 *   风雨球馆2楼实训室→球馆2F实训、C 栋 305→C305、运动场/田径场→操场）
 * - 课名清洗（去括号备注）；简称仅使用用户自定义，无内置词典
 * - 测量驱动的阶梯降级（sp→px、fitChars 保底截断）
 */
object WidgetText {

    /** 课名清洗：去掉括号内的教学班/教师/语种/班次备注 */
    fun cleanName(raw: String): String {
        val s = raw.replace(Regex("[（(【\\[][^（）()【】\\[\\]]*[）)\\]]"), "").trim()
        return s.ifBlank { raw.trim() }
    }

    /** 课名显示：用户自定义简称 > 清洗后全称（内置缩写词典已按需求移除） */
    fun shortName(cleaned: String, overrides: Map<String, String> = emptyMap()): String =
        overrides[cleaned] ?: cleaned

    /** 教室缩写（全称保留在 App 内，点击可看） */
    fun abbrRoom(raw: String): String {
        var s = raw.replace('（', '(').replace('）', ')').replace('　', ' ')
        s = s.replace(Regex("[（(][^()（）]*[)）]"), "")
        s = s.replace(Regex("[\\s\\-—–]"), "")
        s = s.replace(Regex("第?(\\d+)号?教学楼"), "$1教")
        s = s.replace(Regex("楼(?=[A-Za-z0-9])"), "")
        s = s.replace(Regex("栋(?=[A-Za-z0-9])"), "")
        s = s.replace(Regex("风雨球馆(\\d+)楼"), "球馆$1F")
        s = s.replace(Regex("体育馆主馆"), "体育馆")
        s = s.replace(Regex("实训室$"), "实训")
        s = s.replace(Regex("^(运动场|室外田径场|田径场)"), "操场")
        return s.trim().ifBlank { raw.trim() }
    }

    // ------------------------------------------------------------ 测量

    private fun paint(context: Context, sp: Float, bold: Boolean): TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = sp * context.resources.displayMetrics.scaledDensity
        typeface = if (bold) {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

    fun width(context: Context, text: String, sp: Float, bold: Boolean = false): Float =
        paint(context, sp, bold).measureText(text)

    /** 在 maxPx 内截断为「前 n 字 + …」，且保底不少于 minChars 个字符 */
    fun ellipsize(context: Context, text: String, sp: Float, maxPx: Float, minChars: Int = 4, bold: Boolean = false): String {
        val p = paint(context, sp, bold)
        if (p.measureText(text) <= maxPx) return text
        val cut = android.text.TextUtils.ellipsize(text, p, maxPx, android.text.TextUtils.TruncateAt.END).toString()
        return if (cut.length >= minChars) cut else text.take(minChars) + "…"
    }
}
