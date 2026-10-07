package com.fengkao.timetable.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.ui.theme.*

internal val DangerBg: Color get() = if (IsDark) Color(0x33C0392B) else Color(0xFFFFF1F0)
internal val DangerFg: Color get() = if (IsDark) Color(0xFFFF9B95) else Color(0xFFD64541)

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = BrandGreen, onPrimary = Color.White,
        surface = CardWhite, onSurface = InkDark, onSurfaceVariant = InkMid, background = SageBg, onBackground = InkDark), content = content)
}

@Composable
internal fun SettingsPage(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(appBackground()).statusBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = InkDark, modifier = Modifier.size(24.dp))
            }
            Text(title, color = InkDark, fontSize = if (onBack == null) 28.sp else 22.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
internal fun SettingsSection(title: String? = null, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) Text(title, color = InkGray, fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (danger) DangerBg else CardWhite), content = content)
    }
}

@Composable
internal fun SettingsItem(title: String, icon: ImageVector, subtitle: String? = null, value: String? = null,
    enabled: Boolean = true, danger: Boolean = false, divider: Boolean = false,
    valueColor: Color = InkGray, onClick: () -> Unit) {
    SettingsRow(title, icon, subtitle, enabled, danger, onClick) {
        if (value != null) Text(value, color = valueColor, fontSize = 12.sp)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = if (danger) DangerFg else InkGray, modifier = Modifier.size(20.dp))
    }
    if (divider) SettingsDivider()
}

@Composable
internal fun SettingsSwitchItem(title: String, icon: ImageVector, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit,
    enabled: Boolean = true, divider: Boolean = false, subtitleColor: Color = InkGray) {
    SettingsRow(title, icon, subtitle, enabled, false, { onChange(!checked) }, subtitleColor) {
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = BrandGreen))
    }
    if (divider) SettingsDivider()
}

@Composable
internal fun AccountItem(title: String, icon: ImageVector, account: String, loggedIn: Boolean, busy: Boolean, onClick: () -> Unit, divider: Boolean = false) {
    SettingsRow(title, icon, account.ifBlank { "未登录" }, true, false, onClick) {
        val tint = if (loggedIn) BrandGreen else InkGray
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BrandGreen)
        else Text(if (loggedIn) "已登录" else "未登录", color = tint, fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 5.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = InkGray, modifier = Modifier.size(20.dp))
    }
    if (divider) SettingsDivider()
}

@Composable
private fun SettingsRow(title: String, icon: ImageVector, subtitle: String?, enabled: Boolean, danger: Boolean,
    onClick: () -> Unit, subtitleColor: Color = InkGray, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = if (subtitle == null) 56.dp else 72.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = (if (danger) DangerFg else BrandGreen).copy(alpha = if (enabled) 1f else .4f), modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (danger) DangerFg else InkDark)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = subtitleColor, modifier = Modifier.padding(top = 3.dp))
        }
        trailing()
    }
}

@Composable
internal fun SettingsDivider() { HorizontalDivider(Modifier.padding(start = 52.dp), thickness = .5.dp, color = Hairline) }

@Composable
internal fun SettingsValue(label: String, value: String, valueColor: Color = InkGray) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = InkMid, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "暂无信息" }, color = valueColor, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
    }
}

@Composable
internal fun SettingsHint(text: String, error: Boolean = false) {
    Text(text, color = if (error) MaterialTheme.colorScheme.error else InkGray, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
}

@Composable
internal fun StudentAvatar(name: String, large: Boolean = false) {
    Box(Modifier.size(if (large) 76.dp else 52.dp).clip(CircleShape).background(BrandGreen), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifBlank { "学" }, color = Color.White, fontSize = if (large) 30.sp else 22.sp, fontWeight = FontWeight.Bold)
    }
}
