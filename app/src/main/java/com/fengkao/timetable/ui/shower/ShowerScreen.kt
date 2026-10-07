package com.fengkao.timetable.ui.shower

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.fengkao.timetable.ui.navigation.PageStack
import com.fengkao.timetable.ui.profile.SettingsItem
import com.fengkao.timetable.ui.profile.SettingsSection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fengkao.timetable.ui.navigation.LocalPageActive
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fengkao.timetable.ui.theme.*
import com.fengkao.timetable.ui.water.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ShowerScreen(model: ShowerViewModel = viewModel()) {
    LifeTheme { ShowerContent(model) }
}

@Composable
private fun ShowerContent(model: ShowerViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val holder = rememberSaveableStateHolder()
    var managing by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { it.contents?.let(model::bind) }
    fun scan() { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("扫描趣智校园热水器二维码").setBeepEnabled(false).setOrientationLocked(false)) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scan() else model.message("未开启相机权限，可手动输入设备码")
    }
    fun requestScan() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val bluetoothPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) model.scanNearby() else model.message("未允许蓝牙扫描，可扫码或手动绑定")
    }
    fun scanNearby() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) model.scanNearby()
        else bluetoothPermissions.launch(permissions)
    }
    val bluetoothEnable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) scanNearby()
    }
    fun requestNearby() {
        // 先申请权限，蓝牙关闭时使用系统确认框开启，避免擅自修改系统设置。
        val permitted = android.os.Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val adapter = if (permitted) context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter else null
        if (adapter != null && !adapter.isEnabled) bluetoothEnable.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
        else scanNearby()
    }
    fun runWaterControl() { if (s.active && s.owner) model.stop() else model.start() }
    val enableForControl = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) runWaterControl()
        else model.message("请开启蓝牙并靠近设备后再试")
    }
    fun connectForControl() {
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (adapter == null) model.message("本机不支持蓝牙")
        else if (!adapter.isEnabled) enableForControl.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
        else runWaterControl()
    }
    val permissionForControl = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) connectForControl() else model.message("需要蓝牙连接权限才能开关水")
    }
    fun requestWaterControl() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            permissionForControl.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else connectForControl()
    }
    val active = LocalPageActive.current
    LifecycleResumeEffect(active) {
        if (active) model.enter()
        onPauseOrDispose { model.stopScan() }
    }
    PageStack(if (managing && s.loggedIn) listOf("shower", "devices") else listOf("shower"), { managing = false }) { page ->
        holder.SaveableStateProvider(page) {
            LifePage {
                if (page == "devices") {
                    DeviceManagementHeader { managing = false }
                    ShowerDeviceManager(s, model, ::requestNearby, ::requestScan) { manual = true }
                } else {
                    LifeHeader("洗澡", if (s.loggedIn) "温暖随手开启" else "登录后连接浴室热水器", Icons.Outlined.Shower)
                    if (!s.loggedIn) ShowerLogin(s, model) else {
                        ShowerBalanceCard(s, model::refresh)
                        ServiceDeviceCard(s.device?.name, Icons.Outlined.Shower,
                            when { s.unknown -> "用水状态待确认"; s.active && s.owner -> "正在用水"; s.active -> "设备正在被他人使用"; s.busy -> "正在更新设备…"; s.refreshFailed -> "设备更新失败"; s.device == null -> "优先扫描附近蓝牙热水器"; else -> "准备就绪" },
                            s.busy, if (s.active && s.owner) "蓝牙关水 · 结算" else "蓝牙开水",
                            s.device != null && !s.busy && !s.unknown && (!s.active || s.owner),
                            "请靠近设备使用蓝牙开关水，结束后确认设备停止出水", ::requestWaterControl)
                        if (s.device != null) DeviceManagementEntry { managing = true }
                        else ShowerDeviceManager(s, model, ::requestNearby, ::requestScan) { manual = true }
                    }
                }
                ServiceMessage(s.message, s.error, s.loggedIn && (s.refreshFailed || s.unknown || s.error), s.busy, model::refresh)
                if (s.unknown && s.message == null) ServiceMessage("用水状态待确认，请查看设备后重试", true, true, s.busy, model::refresh)
            }
        }
    }
    LaunchedEffect(managing, s.device != null) { if (!managing && s.device != null) model.stopScan() }
    if (manual && s.loggedIn) {
        var raw by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manual = false }, title = { Text("绑定洗澡设备") },
            text = { OutlinedTextField(raw, { raw = it }, label = { Text("12 位设备码或二维码内容") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { manual = false; model.bind(raw) }, enabled = raw.isNotBlank() && !s.busy) { Text("绑定") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("取消") } }, containerColor = CardWhite)
    }
}

@Composable
private fun ShowerBalanceCard(s: ShowerUiState, retry: () -> Unit) {
    LifeCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = BrandGreen, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(s.balance?.source ?: "账户余额", color = InkGray, fontSize = 12.sp)
                Text(s.balance?.let { "¥ ${it.amount}" } ?: if (s.busy) "读取中…" else "暂未获取", color = InkDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (s.balanceError != null) TextButton(onClick = retry, enabled = !s.busy) { Text("重试", fontSize = 12.sp) }
        }
        s.balanceError?.let { Text("余额未更新：$it", color = InkGray, fontSize = 11.sp) }
    }
}

@Composable
private fun ShowerDeviceManager(s: ShowerUiState, model: ShowerViewModel, nearby: () -> Unit, scan: () -> Unit, manual: () -> Unit) {
    val enabled = !s.busy && !s.active && !s.unknown
    SettingsSection {
        SettingsItem("蓝牙扫描绑定", Icons.AutoMirrored.Outlined.BluetoothSearching, "扫描附近热水器，按信号强度排序", enabled = enabled && !s.scanning, divider = true, onClick = nearby)
        SettingsItem("扫码绑定", Icons.Outlined.QrCodeScanner, enabled = enabled, divider = true, onClick = scan)
        SettingsItem("手动输入设备编号", Icons.Outlined.Edit, enabled = enabled, onClick = manual)
    }
    if (s.scanning) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
        Text("正在扫描附近热水器…", color = InkGray, fontSize = 12.sp)
    }
    s.scanError?.let { ServiceMessage(it, true, true, s.busy, nearby) }
    if (s.scanCompleted && s.nearby.isEmpty()) Text("附近未发现热水器，请靠近设备后重试，或使用扫码绑定", color = InkGray, fontSize = 12.sp)
    if (s.nearby.isNotEmpty()) SettingsSection("附近设备") {
        s.nearby.forEach { device -> SettingsItem(device.name, Icons.Outlined.Bluetooth,
            "${device.mac} · 信号 ${device.rssi} dBm", enabled = enabled) { model.bind(device.mac) } }
    }
    if (s.devices.isNotEmpty()) SettingsSection("更换设备") {
        s.devices.forEach { device ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(device.sn == s.selected, onClick = { model.select(device.sn) }, enabled = enabled)
                TextButton(onClick = { model.select(device.sn) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(device.name, color = InkDark); Text("设备编号 ${device.sn}", color = InkGray, fontSize = 11.sp)
                        if (device.mac.isNotBlank()) Text("蓝牙地址 ${device.mac}", color = InkGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    if (!enabled) Text("用水中或状态待确认时，不能更换设备", color = InkGray, fontSize = 12.sp)
}

@Composable
internal fun ShowerLogin(s: ShowerUiState, model: ShowerViewModel, showHeading: Boolean = true,
    submitLabel: String = "登录并开始使用") {
            LifeCard {
                if (showHeading) {
                    Text("登录洗澡账户", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkDark)
                    Spacer(Modifier.height(6.dp))
                    Text("首次登录后，下次直接开水", color = InkGray, fontSize = 12.sp)
                    Spacer(Modifier.height(22.dp))
                }
                OutlinedTextField(s.phone, model::phone, label = { Text("手机号") }, singleLine = true,
                    enabled = !s.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.secret, model::secret, label = { Text(if (s.smsMode) "短信验证码" else "密码") },
                        singleLine = true, enabled = !s.busy, modifier = Modifier.weight(1f),
                        visualTransformation = if (s.smsMode) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (s.smsMode) KeyboardType.Number else KeyboardType.Password))
                    if (s.smsMode) TextButton(onClick = model::sendSms, enabled = !s.busy && s.validPhone && s.smsSeconds == 0,
                        modifier = Modifier.width(112.dp)) {
                        Text(if (s.smsSeconds > 0) "${s.smsSeconds}s 后重发" else "发送验证码", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = model::mode, enabled = !s.busy) { Text(if (s.smsMode) "使用密码登录" else "使用验证码登录", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = model::login, enabled = !s.busy && s.validPhone && s.secret.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    if (s.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(submitLabel, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("登录信息加密保存在本机", color = InkGray, fontSize = 12.sp)
            }
}
