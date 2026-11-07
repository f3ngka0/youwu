package com.fengkao.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Activity

private val LightScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreen,
    secondary = Color(0xFF5A6172),
    background = SageBg,
    onBackground = InkDark,
    surface = CardWhite,
    onSurface = InkDark,
    surfaceVariant = Color(0xFFE6ECFA),
    onSurfaceVariant = InkMid,
    outlineVariant = Color(0xFFD8E0F2),
    error = Color(0xFFD64541),
)

private val DarkScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = Color(0xFFA8CFFF),
    secondary = Color(0xFF9AA1B0),
    background = SageBg,
    onBackground = InkDark,
    surface = CardWhite,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = InkMid,
    outlineVariant = Color(0xFF2A2D36),
    error = Color(0xFFFF6B61),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 外观模式：跟随系统 / 固定浅色 / 固定深色。 */
enum class ThemeMode(val stored: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun of(stored: String?): ThemeMode = entries.firstOrNull { it.stored == stored } ?: System
    }
}

/** 当前外观偏好，由设置页写入，TimetableTheme 读取。 */
object ThemePreference {
    var mode by mutableStateOf(ThemeMode.System)
}

@Composable
fun TimetableTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePreference.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(dark) {
        updateThemeDark(dark)
        // 强制浅色/深色时同步系统栏图标明暗，避免深色页面配深色状态栏文字。
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
