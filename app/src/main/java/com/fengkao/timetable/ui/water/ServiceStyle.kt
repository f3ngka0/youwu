package com.fengkao.timetable.ui.water

import androidx.compose.ui.graphics.Color
import com.fengkao.timetable.ui.theme.BrandGreen
import com.fengkao.timetable.ui.theme.BrandGreenSoft
import com.fengkao.timetable.ui.theme.IsDark

/** Service identity is local to these pages; shared chrome keeps the Youwu brand. */
internal enum class ServiceKind {
    Drinking, Shower;

    val action: Color get() = when (this) {
        Drinking -> BrandGreen
        Shower -> Color(0xFF9B4D2F)
    }
    val ink: Color get() = if (!IsDark) action else when (this) {
        Drinking -> Color(0xFFA8CFFF)
        Shower -> Color(0xFFF2B69B)
    }
    val soft: Color get() = when (this) {
        Drinking -> BrandGreenSoft
        Shower -> if (IsDark) Color(0xFF38261F) else Color(0xFFF7ECE6)
    }
}
