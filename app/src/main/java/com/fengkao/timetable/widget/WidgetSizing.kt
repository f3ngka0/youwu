package com.fengkao.timetable.widget

/** Launcher options describe a portrait/landscape range, not one current maximum size. */
object WidgetSizing {
    data class Size(val width: Int, val height: Int, val medium: Boolean)

    fun resolve(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int,
        landscape: Boolean, defaultMedium: Boolean): Size {
        val width = (if (landscape) maxWidth else minWidth).takeIf { it > 0 }
            ?: (if (landscape) minWidth else maxWidth).takeIf { it > 0 }
            ?: if (defaultMedium) 360 else 180
        val height = (if (landscape) minHeight else maxHeight).coerceAtLeast(0)
        return Size(width, height, width >= 200)
    }

    /** Preserve normal resizing; cap only abnormally tall launcher allocations. */
    fun cardHeight(width: Int, height: Int, medium: Boolean): Int? {
        if (width <= 0 || height <= 0) return null
        val preferred = (if (medium) width / 2 else width).coerceAtLeast(168)
        return preferred.takeIf { height > preferred * 1.35f }
    }
}
