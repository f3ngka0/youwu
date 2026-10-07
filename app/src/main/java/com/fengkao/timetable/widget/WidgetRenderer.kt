package com.fengkao.timetable.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.fengkao.timetable.R

/**
 * 渲染器（1:1 设计稿终版）：白卡 + 六态同源配色 + 确定性环形计时器 + 省略号规避阶梯。
 * 省略号规避：课程名一级信息绝不硬截断，牺牲顺序 = 间距 → 教室缩写 → 课名缩字 → 课名简称 →
 * 最后才省略且保底 ≥4 字；全部由真实文本测量驱动（WidgetText）。
 */
object WidgetRenderer {

    fun render(
        context: Context,
        medium: Boolean,
        snap: WidgetSnapshot,
        weather: WidgetWeather.Info?,
        widthDp: Int = 0,
        heightDp: Int = 0,
    ): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (medium) R.layout.widget_next_class_medium else R.layout.widget_next_class_small,
        )
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            WidgetSizing.cardHeight(widthDp, heightDp, medium)?.let { height ->
                views.setViewLayoutHeight(R.id.widget_card, height.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        val st = snap.status
        val emptyState = st.tone == WidgetTone.IDLE && st.kind != WidgetStatus.Kind.NO_DATA
        val stl = styleFor(st.tone)
        val strong = context.color(stl.strong)
        // 与文本 px 测量同单位（sp→px 经 scaledDensity）
        val leftWidth = if (medium) (widthDp - 49) * 0.75f / 1.75f else (widthDp - 28).toFloat()
        val contentW = leftWidth.coerceAtLeast(60f) * context.resources.displayMetrics.density

        // ---- 状态徽章 + 天气（同基线） ----
        views.setTextViewText(R.id.status_label, st.label)
        views.setTextColor(R.id.status_label, strong)
        views.setInt(R.id.label_chip, "setBackgroundResource", stl.badge)
        views.setViewVisibility(R.id.weather_icon, if (weather != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.weather_text, if (weather != null) View.VISIBLE else View.GONE)
        if (weather != null) views.setTextViewText(R.id.weather_text, "${weather.temp}°")

        // ---- 倒计时（徽章行下）：小标签 + 大数字（AutoSize：两位 36/32、三位 30/27、时刻 27/24） ----
        val big = st.bigNumber.orEmpty()
        views.setViewVisibility(R.id.big_row, if (big.isBlank()) View.GONE else View.VISIBLE)
        if (big.isNotBlank()) {
            views.setTextViewText(R.id.prefix_text, st.prefix)
            views.setViewVisibility(R.id.prefix_text, if (st.prefix.isNullOrBlank()) View.GONE else View.VISIBLE)
            views.setTextColor(R.id.prefix_text, context.color(R.color.w_text3))
            views.setTextViewText(R.id.big_text, big)
            views.setTextColor(R.id.big_text, context.color(stl.num))
            val bigSp = when {
                big.length <= 2 -> if (medium) 32f else 36f
                big.length == 3 -> if (medium) 27f else 30f
                else -> if (medium) 24f else 27f
            }
            views.setTextViewTextSize(R.id.big_text, TypedValue.COMPLEX_UNIT_SP, bigSp)
            views.setTextViewText(R.id.unit_text, st.unit)
        }

        // ---- 标题行（省略号规避阶梯）+ 教室行（所有分支显式复位，杜绝上次状态残留） ----
        val countdown = st.tone == WidgetTone.NEXT || st.tone == WidgetTone.SOON
        val room = st.place.orEmpty()
        val titleSp = if (medium) 15f else 16f
        // 先全部复位（教室行已由 room_line 统一承担，📍 定位针行已删除）
        views.setViewVisibility(R.id.content_course, if (emptyState) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.room_line, View.GONE)
        views.setViewVisibility(R.id.place_tv, View.GONE)
        views.setViewVisibility(R.id.place_sep, View.GONE)
        views.setViewVisibility(R.id.name_row, if (emptyState) View.GONE else View.VISIBLE)
        if (countdown) {
            if (medium) {
                // 4×2：「预计上课」框已移除，课名独占 + 教室独立行（与 2×2 的教室显示一致）
                val nameF = st.title
                val nameS = st.titleShort ?: st.title
                var text = nameF
                var sp = 15f
                when {
                    WidgetText.width(context, nameF, 15f, true) <= contentW -> Unit
                    WidgetText.width(context, nameF, 13f, true) <= contentW -> sp = 13f
                    WidgetText.width(context, nameS, 15f, true) <= contentW -> text = nameS
                    else -> {
                        text = WidgetText.ellipsize(context, nameF, 13f, contentW, minChars = 4, bold = true)
                        sp = 13f
                    }
                }
                views.setTextViewText(R.id.course_name, text)
                views.setTextViewTextSize(R.id.course_name, TypedValue.COMPLEX_UNIT_SP, sp)
                if (room.isNotBlank()) {
                    views.setTextViewText(R.id.room_line, room)
                    views.setViewVisibility(R.id.room_line, View.VISIBLE)
                }
            } else {
                // 2×2：课名独占 + 教室独立行（副标题在课程名下方，不与课名同行）
                val roomSp = 12f
                val roomW = WidgetText.width(context, room, roomSp)
                val aloneSp = 17f
                val nameF = st.title
                val nameS = st.titleShort ?: st.title

                val fitsAlone = { n: String, sp: Float -> WidgetText.width(context, n, sp, true) <= contentW }

                val text: String
                val chosenSp: Float
                when {
                    fitsAlone(nameF, aloneSp) -> {
                        text = nameF; chosenSp = aloneSp
                    }
                    else -> {
                        // 缩字 → 简称 → 保底省略（≥4 字）
                        var chosen: String? = null
                        var sp = aloneSp
                        for (cand in listOf(aloneSp - 1, aloneSp - 2)) {
                            if (fitsAlone(nameF, cand)) {
                                chosen = nameF; sp = cand; break
                            }
                        }
                        if (chosen == null) {
                            for (cand in listOf(aloneSp, aloneSp - 1, aloneSp - 2)) {
                                if (fitsAlone(nameS, cand)) {
                                    chosen = nameS; sp = cand; break
                                }
                            }
                        }
                        text = chosen
                            ?: WidgetText.ellipsize(context, nameF, 13f, contentW, minChars = 4, bold = true)
                        chosenSp = if (chosen == null) 13f else sp
                    }
                }
                views.setTextViewText(R.id.course_name, text)
                views.setTextViewTextSize(R.id.course_name, TypedValue.COMPLEX_UNIT_SP, chosenSp)
                if (room.isNotBlank()) {
                    views.setTextViewText(R.id.room_line, room)
                    views.setViewVisibility(R.id.room_line, View.VISIBLE)
                }
                views.setViewVisibility(R.id.place_tv, View.GONE)
                views.setViewVisibility(R.id.place_sep, View.GONE)
            }
        } else {
            val nameF = st.title
            val nameS = st.titleShort ?: st.title
            val fullSp = if (medium) 15f else 16f
            val text = when {
                WidgetText.width(context, nameF, fullSp, true) <= contentW -> nameF
                WidgetText.width(context, nameF, fullSp - 2f, true) <= contentW -> {
                    views.setTextViewTextSize(R.id.course_name, TypedValue.COMPLEX_UNIT_SP, fullSp - 2f)
                    nameF
                }
                WidgetText.width(context, nameS, fullSp, true) <= contentW -> nameS
                else -> WidgetText.ellipsize(context, nameF, fullSp - 2f, contentW.toFloat(), minChars = 4, bold = true)
            }
            views.setTextViewText(R.id.course_name, text)
            views.setTextViewTextSize(R.id.course_name, TypedValue.COMPLEX_UNIT_SP, fullSp)
            if (!emptyState && room.isNotBlank()) {
                views.setTextViewText(R.id.room_line, room)
                views.setViewVisibility(R.id.room_line, View.VISIBLE)
            }
        }
        views.setTextColor(R.id.course_name, context.color(R.color.w_text))

        // ---- 第二行（已下课=刚结束课名；课程已结束=辛苦啦！+礼花） ----
        val sub = st.subTitle.orEmpty()
        views.setTextViewText(R.id.sub_title, sub)
        views.setViewVisibility(R.id.sub_row, if (sub.isBlank()) View.GONE else View.VISIBLE)
        views.setViewVisibility(
            R.id.celebrate,
            if (st.kind == WidgetStatus.Kind.DAY_OVER) View.VISIBLE else View.GONE,
        )

        // ---- 今天无课空状态 ----
        views.setViewVisibility(R.id.content_empty, if (emptyState) View.VISIBLE else View.GONE)
        if (emptyState) {
            views.setTextViewText(R.id.rest_text, st.restText ?: "好好休息一下吧")
        }

        // ---- 底部状态胶囊（文案颜色按状态语义）+ 确定性进度环 ----
        val cardLabel = st.cardLabel.orEmpty()
        val cardMain = st.cardMain.orEmpty()
        val cardPost = st.cardPost.orEmpty()
        // 「下一节课 / 即将上课 / 已下课」态：底部框（含进度环）整体移除，
        // 已下课改由「课名 + 教室」两行呈现，下一节课改由课名下方教室行呈现
        val hideCard = st.tone == WidgetTone.NEXT || st.tone == WidgetTone.SOON || st.tone == WidgetTone.DONE
        views.setViewVisibility(
            R.id.status_card,
            if (cardLabel.isBlank() || hideCard) View.GONE else View.VISIBLE,
        )
        if (cardLabel.isNotBlank() && !hideCard) {
            views.setInt(R.id.status_card, "setBackgroundResource", stl.soft)
            views.setTextViewText(R.id.card_label, cardLabel)
            views.setTextViewText(R.id.card_main, cardMain)
            views.setTextViewText(R.id.card_post, cardPost)
            when (st.tone) {
                WidgetTone.ACTIVE -> {
                    views.setTextViewTextSize(R.id.card_label, TypedValue.COMPLEX_UNIT_SP, 9f)
                    views.setTextColor(R.id.card_label, context.color(R.color.w_text3))
                }
                else -> {
                    // 其余态：#49454F / 500
                    views.setTextViewTextSize(R.id.card_label, TypedValue.COMPLEX_UNIT_SP, if (medium) 10f else 11f)
                    views.setTextColor(R.id.card_label, context.color(R.color.w_text2))
                }
            }
            views.setViewVisibility(R.id.card_main, if (cardMain.isBlank()) View.GONE else View.VISIBLE)
            views.setTextColor(R.id.card_main, strong)
            views.setViewVisibility(R.id.card_post, if (cardPost.isBlank()) View.GONE else View.VISIBLE)
            views.setTextColor(R.id.card_post, context.color(R.color.w_text3))

            // 2×2 / 4×2 均展示进度环（位图尺寸与各自布局框一致）
            views.setImageViewBitmap(
                R.id.w_ring,
                ringBitmap(context, st.tone, st.ringProgress, if (medium) 34 else 40),
            )
            views.setImageViewResource(R.id.ring_icon, ringIconFor(st.tone))
            views.setInt(R.id.ring_icon, "setColorFilter", strong)
        }
        if (medium) {
            // ---- 右栏：今日课程 / 明日课程 ----
            val useTomorrow = snap.today.isEmpty() && snap.tomorrow.isNotEmpty()
            val items = if (useTomorrow) snap.tomorrow else snap.today
            views.setTextViewText(
                R.id.right_header,
                when {
                    snap.today.isNotEmpty() -> "今日课程"
                    useTomorrow -> snap.tomorrowLabel
                    st.kind == WidgetStatus.Kind.VACATION -> "假期中"
                    else -> "今日课程"
                },
            )
            views.setTextColor(
                R.id.right_header,
                context.color(if (st.kind == WidgetStatus.Kind.DAY_OVER) R.color.w_orange else R.color.w_text2),
            )
            views.setTextViewText(R.id.count_text, "${items.size} 节")
            if (items.isEmpty()) {
                views.setViewVisibility(R.id.today_rows, View.GONE)
                views.setViewVisibility(R.id.today_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.today_empty,
                    if (st.kind == WidgetStatus.Kind.VACATION) "暂无课程安排" else "今天没有课程",
                )
            } else {
                views.setViewVisibility(R.id.today_rows, View.VISIBLE)
                views.setViewVisibility(R.id.today_empty, View.GONE)
                bindCourseList(context, views, items, st, useTomorrow, widthDp)
            }
        }

        // ---- 交互：整体开 App；课名开详情 ----
        views.setOnClickPendingIntent(android.R.id.background, WidgetClicks.openApp(context))
        st.courseKey?.let {
            views.setOnClickPendingIntent(R.id.course_name, WidgetClicks.openCourse(context, it))
        }
        return views
    }

    /**
     * 今日课程行：28dp 固定标记槽（时间列全行对齐）；当前/即将行状态浅底胶囊+主色实心圆白▶+全行主色；
     * 结束行灰描边对勾圈+文字降灰。课名 AutoSize 13→12→11 保底 44dp，教室缩写右对齐优先被压缩。
     */
    private fun bindCourseList(
        context: Context,
        views: RemoteViews,
        items: List<WidgetCourse>,
        st: WidgetStatus,
        useTomorrow: Boolean,
        widthDp: Int,
    ) {
        val shown = windowAround(items, maxRows = 4)
        val allDone = st.kind == WidgetStatus.Kind.DAY_OVER
        val highlightKey = if (!useTomorrow && !allDone) st.courseKey else null
        val hlRes = styleFor(st.tone).rowHl
        val hlColor = context.color(styleFor(st.tone).strong)
        val rightCol = ((widthDp - 47).coerceAtLeast(60) / 1.75f) * context.resources.displayMetrics.density
        // 行内固定开销：标记槽 16dp + 时间左边距 4dp + 时间列(tnum 等宽，wrap 实测约 34dp)
        // + 课名左边距 5dp = 59dp；再扣行自身 paddingRight 8dp → 67dp。
        // 时间列 wrap_content + tnum：各 "HH:mm" 等宽且完整显示，起点固定 → 全行平齐。
        val nameAvail = (rightCol - dp(context, 67)).coerceAtLeast(dp(context, 44))

        for (i in 0 until 4) {
            val rowId = ROW_IDS[i]
            val c = shown.getOrNull(i)
            if (c == null) {
                views.setViewVisibility(rowId, View.GONE)
                continue
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            val highlighted = c.key == highlightKey
            val finished = allDone || (!highlighted && c.state == WidgetCourse.PAST)

            // 28dp 固定标记槽：三选一，全部槽内居中 → 时间列左边缘全行对齐
            views.setViewVisibility(PLAY_IDS[i], if (highlighted) View.VISIBLE else View.GONE)
            views.setViewVisibility(TRI_IDS[i], if (highlighted) View.VISIBLE else View.GONE)
            views.setViewVisibility(CHECK_IDS[i], if (finished) View.VISIBLE else View.GONE)
            views.setViewVisibility(DOT_IDS[i], if (!highlighted && !finished) View.VISIBLE else View.GONE)
            if (!highlighted && !finished) {
                views.setInt(DOT_IDS[i], "setColorFilter", context.color(DOT_COLORS[i % 4]))
            }
            if (highlighted) views.setInt(PLAY_IDS[i], "setColorFilter", hlColor)

            views.setTextViewText(TIME_IDS[i], c.start.toString())

            // 课名：flex 全宽；AutoSize 13→12→11 保底 44dp（教室列已按需求移除，宽度全给课名）
            var nameText = c.name
            var nameSp = 13f
            if (WidgetText.width(context, nameText, nameSp) > nameAvail) nameSp = 12f
            if (WidgetText.width(context, nameText, nameSp) > nameAvail) {
                val short = c.nameShort
                nameText = if (WidgetText.width(context, short, nameSp) <= nameAvail) short
                else WidgetText.ellipsize(context, nameText, nameSp, nameAvail, minChars = 4)
            }
            views.setTextViewText(NAME_IDS[i], nameText)
            views.setTextViewTextSize(NAME_IDS[i], TypedValue.COMPLEX_UNIT_SP, nameSp)

            if (highlighted) {
                views.setTextColor(TIME_IDS[i], hlColor)
                views.setTextColor(NAME_IDS[i], hlColor)
            } else {
                views.setTextColor(TIME_IDS[i], context.color(R.color.w_text2))
                views.setTextColor(NAME_IDS[i], context.color(R.color.w_text))
            }
            views.setOnClickPendingIntent(rowId, WidgetClicks.openCourse(context, c.key))
        }
    }

    /** ≤4 行全量展示；更多时围绕「当前/即将」取窗口，尽量保留一节已上过的课 */
    private fun windowAround(items: List<WidgetCourse>, maxRows: Int): List<WidgetCourse> {
        if (items.size <= maxRows) return items
        val focus = items.indexOfFirst { it.state != WidgetCourse.PAST }
            .let { if (it < 0) items.size - 1 else it }
        val start = (focus - 1).coerceIn(0, items.size - maxRows)
        return items.subList(start, start + maxRows)
    }

    private data class Style(val strong: Int, val soft: Int, val badge: Int, val rowHl: Int, val num: Int)

    private fun styleFor(tone: WidgetTone): Style = when (tone) {
        WidgetTone.NEXT -> Style(R.color.w_orange, R.drawable.w_soft_orange, R.drawable.w_badge_orange, R.drawable.w_row_hl_orange, R.color.w_orange)
        WidgetTone.SOON -> Style(R.color.w_blue, R.drawable.w_soft_blue, R.drawable.w_badge_blue, R.drawable.w_row_hl_blue, R.color.w_blue_num)
        WidgetTone.ACTIVE -> Style(R.color.w_green, R.drawable.w_soft_green, R.drawable.w_badge_green, R.drawable.w_row_hl_green, R.color.w_green)
        WidgetTone.DONE -> Style(R.color.w_purple, R.drawable.w_soft_purple, R.drawable.w_badge_purple, R.drawable.w_row_hl_green, R.color.w_purple)
        WidgetTone.IDLE -> Style(R.color.w_gray, R.drawable.w_soft_gray, R.drawable.w_badge_gray, R.drawable.w_row_hl_green, R.color.w_gray)
    }

    /**
     * 确定性环形计时器位图（非 loading，绝不旋转）：
     * 轨道 = 状态主色 12%；弧 = 状态主色实色，startAngle 165°、sweep = progress×300°、圆头线帽，
     * 固定在左下留 ~60° 缺口；已下课(100%)画满 360° 闭环。
     */
    private fun ringBitmap(context: Context, tone: WidgetTone, progress: Int, sizeDp: Int): android.graphics.Bitmap {
        val size = (sizeDp * context.resources.displayMetrics.density).toInt().coerceIn(24, 220)
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val state = context.color(styleFor(tone).strong)
        val track = androidx.core.graphics.ColorUtils.setAlphaComponent(state, Math.round(255 * 0.12f))
        val stroke = size * 7f / 46f
        val pad = stroke / 2f + size * 0.01f
        val rect = android.graphics.RectF(pad, pad, size - pad, size - pad)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        paint.color = track
        c.drawArc(rect, 165f, 300f, false, paint)
        paint.color = state
        val sweep = if (tone == WidgetTone.DONE) 359.9f else progress.coerceIn(0, 100) / 100f * 300f
        if (sweep > 0.5f) c.drawArc(rect, 165f, sweep, false, paint)
        return bmp
    }

    private fun ringIconFor(tone: WidgetTone): Int = when (tone) {
        WidgetTone.ACTIVE -> R.drawable.i_book_w
        WidgetTone.DONE -> R.drawable.i_check_w
        WidgetTone.SOON -> R.drawable.i_walk_w
        else -> R.drawable.i_assign_w
    }

    private val ROW_IDS = intArrayOf(R.id.today_row1, R.id.today_row2, R.id.today_row3, R.id.today_row4)
    private val PLAY_IDS = intArrayOf(R.id.row_play1, R.id.row_play2, R.id.row_play3, R.id.row_play4)
    private val TRI_IDS = intArrayOf(R.id.row_play_tri1, R.id.row_play_tri2, R.id.row_play_tri3, R.id.row_play_tri4)
    private val CHECK_IDS = intArrayOf(R.id.row_check1, R.id.row_check2, R.id.row_check3, R.id.row_check4)
    private val DOT_IDS = intArrayOf(R.id.row_dot1, R.id.row_dot2, R.id.row_dot3, R.id.row_dot4)
    private val TIME_IDS = intArrayOf(R.id.row_time1, R.id.row_time2, R.id.row_time3, R.id.row_time4)
    private val NAME_IDS = intArrayOf(R.id.row_name1, R.id.row_name2, R.id.row_name3, R.id.row_name4)
    private val DOT_COLORS = intArrayOf(R.color.w_dot0, R.color.w_dot1, R.color.w_dot2, R.color.w_dot3)

    private fun Context.color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun dp(context: Context, v: Int): Float = v * context.resources.displayMetrics.density
}
