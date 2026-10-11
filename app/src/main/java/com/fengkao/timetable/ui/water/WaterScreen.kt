package com.fengkao.timetable.ui.water

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.fengkao.timetable.ui.profile.SettingsItem
import com.fengkao.timetable.ui.profile.SettingsSection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fengkao.timetable.ui.navigation.LocalPageActive
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fengkao.timetable.ui.theme.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun WaterScreen(model: WaterViewModel = viewModel()) {
    LifeTheme { WaterScreenContent(model) }
}

@Composable
internal fun LifeTheme(kind: ServiceKind = ServiceKind.Drinking, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = kind.ink, onPrimary = if (IsDark) Color(0xFF1C1C20) else Color.White,
        primaryContainer = kind.soft, onPrimaryContainer = kind.ink,
        surface = CardWhite, onSurface = InkDark, onSurfaceVariant = InkMid,
        background = SageBg, onBackground = InkDark,
    ), content = content)
}

@Composable
private fun WaterScreenContent(model: WaterViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manual by rememberSaveable { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { it.contents?.let(model::bind) }
    fun scan() { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("扫描饮水设备二维码").setBeepEnabled(false).setOrientationLocked(false)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scan() else model.message("未开启相机权限，可手动输入设备编号")
    }
    fun requestScan() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
        else permission.launch(Manifest.permission.CAMERA)
    }
    val active = LocalPageActive.current
    LifecycleResumeEffect(active) {
        if (active) model.enter()
        onPauseOrDispose { }
    }
    LifePage {
        LifeHeader("喝水", "校园饮水", Icons.Outlined.WaterDrop)
        if (!s.loggedIn) WaterLogin(s, model) else {
            ServiceDeviceCard(s.device?.name, Icons.Outlined.LocalDrink,
                when { s.busy -> "正在更新设备…"; s.refreshFailed -> "设备更新失败"; s.device == null -> "扫描饮水机二维码，添加第一台设备"; else -> "" },
                s.busy, if (s.startSeconds > 0) "已启动 · ${s.startSeconds}s" else "启动饮水设备",
                s.device != null && !s.busy && s.startSeconds == 0,
                "启动后，在设备上选择水温并接水", model::start)
            WaterBindingActions(s, model, ::requestScan) { manual = true }
        }
        ServiceMessage(s.message, s.error, s.loggedIn && (s.refreshFailed || s.error), s.busy, model::refresh)
    }
    if (manual && s.loggedIn) {
        var raw by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manual = false }, title = { Text("绑定饮水设备") },
            text = { OutlinedTextField(raw, { raw = it }, label = { Text("设备编号或二维码内容") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { manual = false; model.bind(raw) }, enabled = raw.isNotBlank() && !s.busy) { Text("绑定") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("取消") } }, containerColor = CardWhite)
    }
}

@Composable
private fun WaterBindingActions(s: WaterUiState, model: WaterViewModel, scan: () -> Unit, manual: () -> Unit) {
    SettingsSection {
        SettingsItem("扫码绑定", Icons.Outlined.QrCodeScanner, "扫描饮水机二维码",
            enabled = !s.busy, iconTint = ServiceKind.Drinking.ink, onClick = scan)
    }
    SettingsSection {
        SettingsItem("手动输入设备编号", Icons.Outlined.Edit, "输入设备编号或二维码内容",
            enabled = !s.busy, iconTint = ServiceKind.Drinking.ink, onClick = manual)
    }
    if (s.devices.size > 1) SettingsSection("更换设备") {
        s.devices.forEach { device ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(device.id == s.selectedId, onClick = { model.select(device.id) }, enabled = !s.busy)
                TextButton(onClick = { model.select(device.id) }, enabled = !s.busy, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(device.name, color = InkDark); Text("设备编号 ${device.id}", color = InkGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WaterLogin(s: WaterUiState, model: WaterViewModel, showHeading: Boolean = true,
    submitLabel: String = "登录喝水账户") {
    LifeCard {
        if (showHeading) {
            Text("登录喝水账户", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Spacer(Modifier.height(6.dp))
            Text("首次使用完成登录，之后即可直接接水", color = InkGray, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
        }
        OutlinedTextField(s.phone, model::phone, label = { Text("手机号") }, singleLine = true, enabled = !s.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(s.graphCode, model::graph, label = { Text("图形验证码") }, singleLine = true,
                enabled = !s.busy, modifier = Modifier.weight(1f))
            TextButton(onClick = model::loadCaptcha, enabled = !s.busy && s.validPhone && s.configured,
                modifier = Modifier.width(112.dp).height(56.dp)) {
                if (s.captcha != null) Image(s.captcha.asImageBitmap(), "点击刷新图形验证码", Modifier.fillMaxSize())
                else Text("获取图形码", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(s.smsCode, model::sms, label = { Text("短信验证码") }, singleLine = true, enabled = !s.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            TextButton(onClick = model::sendSms,
                enabled = !s.busy && s.validPhone && s.captcha != null && s.graphCode.isNotBlank() && s.smsSeconds == 0,
                modifier = Modifier.width(112.dp)) { Text(if (s.smsSeconds > 0) "${s.smsSeconds}s 后重发" else "发送验证码", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = model::login, enabled = !s.busy && s.validPhone && s.smsCode.isNotBlank() && s.configured,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ServiceKind.Drinking.action, contentColor = Color.White)) {
            if (s.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(submitLabel, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (s.configured) "登录信息加密保存在本机" else "喝水服务暂未开放，配置完成后即可登录", color = InkGray, fontSize = 12.sp)
    }
}

@Composable
internal fun LifePage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(appBackground()).statusBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}

@Composable
internal fun LifeHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    kind: ServiceKind = ServiceKind.Drinking) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(kind.soft).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(44.dp), tint = kind.ink)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 14.sp, color = InkMid)
        }
    }
}

@Composable
internal fun LifeCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).padding(20.dp), content = content)
}
