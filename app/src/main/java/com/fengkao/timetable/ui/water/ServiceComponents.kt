package com.fengkao.timetable.ui.water

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengkao.timetable.ui.profile.SettingsItem
import com.fengkao.timetable.ui.profile.SettingsSection
import com.fengkao.timetable.ui.theme.*

@Composable
internal fun ServiceDeviceCard(name: String?, icon: ImageVector, status: String, busy: Boolean,
    action: String, enabled: Boolean, tip: String, onAction: () -> Unit) {
    LifeCard {
        Text("默认设备", color = InkGray, fontSize = 12.sp)
        Box(Modifier.padding(vertical = 20.dp).size(76.dp).clip(CircleShape).background(BrandGreenSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = BrandGreen)
        }
        Text(name ?: "还未绑定设备", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = InkDark)
        Spacer(Modifier.height(10.dp))
        Text(status, color = InkGray, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Button(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = InkGray)
            else Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp)); Text(action, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(tip, color = InkGray, fontSize = 12.sp)
    }
}

@Composable
internal fun DeviceManagementEntry(onClick: () -> Unit) {
    SettingsSection { SettingsItem("设备管理", Icons.Outlined.Settings, "绑定、更换与设备信息", onClick = onClick) }
}

@Composable
internal fun DeviceManagementHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = InkDark) }
        Text("设备管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkDark)
    }
}

@Composable
internal fun ServiceMessage(message: String?, error: Boolean, retry: Boolean = false, busy: Boolean = false, onRetry: () -> Unit = {}) {
    if (message != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (error) MaterialTheme.colorScheme.errorContainer else BrandGreenSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = if (error) MaterialTheme.colorScheme.error else BrandGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (retry) TextButton(onClick = onRetry, enabled = !busy) { Text("重试", fontSize = 12.sp) }
        }
    }
}
