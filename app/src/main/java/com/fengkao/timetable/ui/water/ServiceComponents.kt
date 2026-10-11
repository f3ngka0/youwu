package com.fengkao.timetable.ui.water

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    action: String, enabled: Boolean, tip: String, onAction: () -> Unit,
    kind: ServiceKind = ServiceKind.Drinking, beforeAction: @Composable ColumnScope.() -> Unit = {}) {
    LifeCard {
        ServiceDeviceSummary(name, icon, kind)
        if (status.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(status, color = InkMid, fontSize = 14.sp)
        }
        beforeAction()
        Spacer(Modifier.height(22.dp))
        ServiceActionButton(action, Icons.Outlined.PowerSettingsNew, enabled, onAction, kind, busy)
        Spacer(Modifier.height(12.dp))
        Text(tip, color = InkMid, fontSize = 14.sp)
    }
}

@Composable
internal fun ServiceDeviceSummary(name: String?, icon: ImageVector, kind: ServiceKind = ServiceKind.Drinking) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(kind.soft),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(32.dp), tint = kind.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(if (kind == ServiceKind.Drinking) "校园饮水机" else "浴室热水器", color = InkMid, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(name ?: if (kind == ServiceKind.Drinking) "还未绑定饮水机" else "还未绑定热水器",
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkDark)
            }
        }
}

@Composable
internal fun ServiceActionButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit,
    kind: ServiceKind = ServiceKind.Drinking, busy: Boolean = false) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = kind.action, contentColor = androidx.compose.ui.graphics.Color.White)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        else Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DeviceManagementEntry(kind: ServiceKind = ServiceKind.Drinking, onClick: () -> Unit) {
    SettingsSection { SettingsItem("设备管理", Icons.Outlined.Settings, "绑定、更换与设备信息", iconTint = kind.ink, onClick = onClick) }
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
            .background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (retry) TextButton(onClick = onRetry, enabled = !busy) { Text("重试", fontSize = 12.sp) }
        }
    }
}
