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
import com.fengkao.timetable.data.shower.maskedShowerPhone
import com.fengkao.timetable.data.shower.showerAccountDisplayName
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ShowerScreen(model: ShowerViewModel = viewModel()) {
    LifeTheme(ServiceKind.Shower) { ShowerContent(model) }
}

@Composable
private fun ShowerContent(model: ShowerViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val holder = rememberSaveableStateHolder()
    var managing by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var secondLoginOpen by rememberSaveable { mutableStateOf(false) }
    var notePhone by rememberSaveable { mutableStateOf<String?>(null) }
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
                    DeviceManagementHeader("账户与设备管理") { managing = false }
                    ShowerAccountsManager(s, model) { notePhone = it }
                    ShowerDeviceManager(s, model, ::requestNearby, ::requestScan, { model.beginSecondLogin(); secondLoginOpen = true }) { manual = true }
                } else {
                    LifeHeader("洗澡", "浴室热水", Icons.Outlined.Shower, ServiceKind.Shower)
                    if (!s.loggedIn) ShowerLogin(s, model) else {
                        ServiceDeviceCard(s.device?.name, Icons.Outlined.Shower,
                            when { s.confirming -> if (s.active && s.owner) "正在确认关水状态…" else "正在确认开水状态…"; s.unknown -> "用水状态待确认"; s.active && s.owner -> "正在用水"; s.active -> "设备正在被他人使用"; s.busy -> "正在更新设备…"; s.refreshFailed -> "设备更新失败"; s.device == null -> "优先扫描附近蓝牙热水器"; else -> "" },
                            s.busy, if (s.active && s.owner) "蓝牙关水 · 结算" else "蓝牙开水",
                            s.device != null && !s.busy && !s.unknown && (!s.active || s.owner),
                            "靠近设备，用蓝牙开关水。", ::requestWaterControl, kind = ServiceKind.Shower,
                            beforeAction = {
                                if (s.accounts.size > 1) {
                                    Spacer(Modifier.height(16.dp))
                                    ShowerAccountSelector(s, model::selectAccount)
                                }
                            })
                        if (s.devices.size > 1) ShowerDeviceSelector(s, model)
                        DeviceManagementEntry(ServiceKind.Shower, "账户与设备管理", "账户余额、备注与设备绑定") { managing = true }
                    }
                }
                ServiceMessage(s.message, s.error, s.loggedIn && (s.refreshFailed || s.unknown || s.error), s.busy, model::refresh)
                if (s.unknown && !s.confirming && s.message == null) ServiceMessage("用水状态待确认，请查看设备后重试", true, true, s.busy, model::refresh)
            }
        }
    }
    LaunchedEffect(managing, s.accounts) { if (managing) model.refreshAccountBalances() }
    LaunchedEffect(managing, s.device != null) { if (!managing && s.device != null) model.stopScan() }
    LaunchedEffect(s.secondLogin.complete) {
        if (s.secondLogin.complete) secondLoginOpen = false
    }
    notePhone?.let { phone ->
        var note by rememberSaveable(phone) { mutableStateOf(s.accountNotes[phone].orEmpty()) }
        AlertDialog(onDismissRequest = { notePhone = null }, title = { Text("账户备注") },
            text = { OutlinedTextField(note, { note = it.take(24) }, singleLine = true,
                label = { Text(maskedShowerPhone(phone)) }, supportingText = { Text("最多 24 字；留空恢复手机号显示") }) },
            confirmButton = { TextButton(onClick = { model.saveAccountNote(phone, note); notePhone = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { notePhone = null }) { Text("取消") } }, containerColor = CardWhite)
    }
    if (secondLoginOpen && s.loggedIn) {
        AlertDialog(onDismissRequest = { if (!s.busy) secondLoginOpen = false },
            title = { Text("登录第二个账户") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val form = s.secondLogin
                    ShowerLoginFields(form.phone, form.secret, form.smsMode, form.smsSeconds, s.busy,
                        model::secondPhone, model::secondSecret, model::secondMode, model::secondSms,
                        model::loginSecond, "验证并绑定账户")
                    ServiceMessage(form.message, form.error)
                }
            },
            confirmButton = { TextButton(onClick = { secondLoginOpen = false }, enabled = !s.busy) { Text("取消") } },
            containerColor = CardWhite)
    }
    if (manual && s.loggedIn) {
        var raw by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manual = false }, title = { Text("绑定洗澡设备") },
            text = { OutlinedTextField(raw, { raw = it }, label = { Text("12 位设备码或二维码内容") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { manual = false; model.bind(raw) }, enabled = raw.isNotBlank() && !s.busy) { Text("绑定") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("取消") } }, containerColor = CardWhite)
    }
}

@Composable
private fun ShowerAccountsManager(s: ShowerUiState, model: ShowerViewModel, editNote: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("洗澡账户", color = InkGray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = model::refreshAccountBalances, enabled = !s.refreshingAccounts) {
            Text(if (s.refreshingAccounts) "读取中…" else "刷新余额")
        }
    }
    s.accounts.forEachIndexed { index, phone ->
        LifeCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PersonOutline, null, tint = ServiceKind.Shower.ink, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(showerAccountDisplayName(phone, s.accountNotes[phone]), color = InkDark,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (s.accountNotes[phone].isNullOrBlank()) "账户 ${index + 1}" else "账户 ${index + 1} · ${maskedShowerPhone(phone)}", color = InkGray, fontSize = 13.sp)
                }
                TextButton(onClick = { editNote(phone) }) { Text("备注") }
            }
            Spacer(Modifier.height(14.dp))
            val balance = s.accountBalances[phone]
            val error = s.accountBalanceErrors[phone]
            Text(balance?.source ?: "账户余额", color = InkGray, fontSize = 13.sp)
            Text(balance?.let { "¥ ${it.amount}" } ?: if (s.refreshingAccounts) "读取中…" else "暂未获取",
                color = InkDark, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (error != null) Text("余额未更新：$error", color = InkGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShowerDeviceManager(s: ShowerUiState, model: ShowerViewModel, nearby: () -> Unit, scan: () -> Unit, secondLogin: () -> Unit, manual: () -> Unit) {
    val enabled = !s.busy && !s.active && !s.unknown
    SettingsSection {
        SettingsItem("登陆第二个账户", Icons.Outlined.PersonAdd,
            s.accounts.getOrNull(1)?.let { "已绑定 ${showerAccountLabel(it)}" } ?: "绑定另一个手机号后可选择开水账户",
            enabled = enabled, divider = true, iconTint = ServiceKind.Shower.ink, onClick = secondLogin)
        SettingsItem("蓝牙扫描绑定", Icons.AutoMirrored.Outlined.BluetoothSearching, "扫描附近热水器，按信号强度排序", enabled = enabled && !s.scanning, divider = true, iconTint = ServiceKind.Shower.ink, onClick = nearby)
        SettingsItem("扫码绑定", Icons.Outlined.QrCodeScanner, enabled = enabled, divider = true, iconTint = ServiceKind.Shower.ink, onClick = scan)
        SettingsItem("手动输入设备编号", Icons.Outlined.Edit, enabled = enabled, iconTint = ServiceKind.Shower.ink, onClick = manual)
    }
    if (s.scanning) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
        Text("正在扫描附近热水器…", color = InkGray, fontSize = 12.sp)
    }
    s.scanError?.let { ServiceMessage(it, true, true, s.busy, nearby) }
    if (s.scanCompleted && s.nearby.isEmpty()) Text("附近未发现热水器，请靠近设备后重试，或使用扫码绑定", color = InkGray, fontSize = 12.sp)
    if (s.nearby.isNotEmpty()) SettingsSection("附近设备") {
        s.nearby.forEach { device -> SettingsItem(device.name, Icons.Outlined.Bluetooth,
            "${device.mac} · 信号 ${device.rssi} dBm", enabled = enabled, iconTint = ServiceKind.Shower.ink) { model.bind(device.mac) } }
    }
    if (!enabled) Text("用水中或状态待确认时，不能更换设备或账户", color = InkGray, fontSize = 12.sp)

}

private fun showerAccountLabel(phone: String): String = maskedShowerPhone(phone)

@Composable
private fun ShowerAccountSelector(s: ShowerUiState, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = !s.busy && !s.active && !s.unknown
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
            Text("开水账户 · ${showerAccountDisplayName(s.selectedAccountPhone, s.accountNotes[s.selectedAccountPhone])}", Modifier.weight(1f), fontSize = 14.sp)
            Icon(Icons.Outlined.ArrowDropDown, "选择开水账户")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            s.accounts.forEachIndexed { i, phone ->
                DropdownMenuItem(text = { Column {
                    Text(showerAccountDisplayName(phone, s.accountNotes[phone]))
                    Text("账户 ${i + 1} · ${maskedShowerPhone(phone)}", fontSize = 12.sp, color = InkGray)
                } },
                    onClick = { expanded = false; select(phone) },
                    trailingIcon = { if (phone == s.selectedAccountPhone) Icon(Icons.Outlined.Check, null) })
            }
        }
    }
}

@Composable
private fun ShowerDeviceSelector(s: ShowerUiState, model: ShowerViewModel) {
    val enabled = !s.busy && !s.active && !s.unknown
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
}

@Composable
internal fun ShowerLogin(s: ShowerUiState, model: ShowerViewModel, showHeading: Boolean = true,
    submitLabel: String = "登录洗澡账户") {
            LifeCard {
                if (showHeading) {
                    Text("登录洗澡账户", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkDark)
                    Spacer(Modifier.height(6.dp))
                    Text("首次登录后，下次直接开水", color = InkGray, fontSize = 12.sp)
                    Spacer(Modifier.height(22.dp))
                }
                ShowerLoginFields(s.phone, s.secret, s.smsMode, s.smsSeconds, s.busy,
                    model::phone, model::secret, model::mode, model::sendSms, model::login, submitLabel)
            }
}

@Composable
private fun ShowerLoginFields(phone: String, secret: String, smsMode: Boolean, smsSeconds: Int, busy: Boolean,
    onPhone: (String) -> Unit, onSecret: (String) -> Unit, onMode: () -> Unit, onSms: () -> Unit,
    onLogin: () -> Unit, submitLabel: String) {
                OutlinedTextField(phone, onPhone, label = { Text("手机号") }, singleLine = true,
                    enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(secret, onSecret, label = { Text(if (smsMode) "短信验证码" else "密码") },
                        singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
                        visualTransformation = if (smsMode) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (smsMode) KeyboardType.Number else KeyboardType.Password))
                    if (smsMode) TextButton(onClick = onSms, enabled = !busy && phone.matches(Regex("1\\d{10}")) && smsSeconds == 0,
                        modifier = Modifier.width(112.dp)) {
                        Text(if (smsSeconds > 0) "${smsSeconds}s 后重发" else "发送验证码", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onMode, enabled = !busy) { Text(if (smsMode) "使用密码登录" else "使用验证码登录", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onLogin, enabled = !busy && phone.matches(Regex("1\\d{10}")) && secret.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ServiceKind.Shower.action, contentColor = Color.White)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(submitLabel, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("登录信息加密保存在本机", color = InkGray, fontSize = 12.sp)
 }
