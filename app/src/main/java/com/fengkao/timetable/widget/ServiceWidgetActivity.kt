package com.fengkao.timetable.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengkao.timetable.data.shower.ShowerStore
import com.fengkao.timetable.data.shower.maskedShowerPhone
import com.fengkao.timetable.data.shower.showerAccountDisplayName
import com.fengkao.timetable.data.water.WaterStore
import com.fengkao.timetable.ui.shower.ShowerScreen
import com.fengkao.timetable.ui.shower.ShowerViewModel
import com.fengkao.timetable.ui.theme.*
import com.fengkao.timetable.ui.water.*
import com.fengkao.timetable.widget.service.*
import kotlinx.coroutines.delay

/** User taps launch this foreground entry, so Bluetooth permission/enabling is visible. */
class ServiceWidgetActivity : ComponentActivity() {
    private val water: WaterViewModel by viewModels()
    private val shower: ShowerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val service = ServiceWidgetUpdater.service(this, id) ?: run { finish(); return }
        val binding = ServiceWidgetStore(this).binding(id)?.takeIf { it.service == service }
        val operation = runCatching { WidgetOperation.valueOf(intent.getStringExtra("operation").orEmpty()) }.getOrDefault(WidgetOperation.CHECK)
        enableEdgeToEdge()
        setContent {
            TimetableTheme {
                val kind = if (service == WidgetService.SHOWER) ServiceKind.Shower else ServiceKind.Drinking
                LifeTheme(kind) {
                    var bind by rememberSaveable { mutableStateOf(intent.getBooleanExtra("bind", false) || binding == null) }
                    if (bind) WidgetBindingPage(id, service, water, shower, ::finish)
                    else WidgetControlPage(id, binding!!, operation, water, shower, { bind = true }, ::finish)
                }
            }
        }
    }
}

@Composable
private fun WidgetBindingPage(id: Int, service: WidgetService, waterModel: WaterViewModel,
    showerModel: ShowerViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val water = remember { WaterStore(context) }
    val shower = remember { ShowerStore(context) }
    val widget = remember { ServiceWidgetStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var showService by rememberSaveable { mutableStateOf(false) }
    var selectedDevice by rememberSaveable { mutableStateOf("") }
    var selectedPhone by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) { revision++; onPauseOrDispose {} }
    LaunchedEffect(showService) { if (!showService) revision++ }
    if (showService) {
        BackHandler { showService = false }
        Column {
            TextButton(onClick = { showService = false }) { Text("返回组件绑定") }
            Box(Modifier.weight(1f)) { if (service == WidgetService.SHOWER) ShowerScreen(showerModel) else WaterScreen(waterModel) }
        }
        return
    }
    val devices = remember(revision) {
        if (service == WidgetService.SHOWER) shower.devices().map { it.sn to it.name }
        else water.devices().map { it.id to it.name }
    }
    val accounts = remember(revision) {
        if (service == WidgetService.SHOWER) shower.accounts().map { it.phone }
        else if (water.token().isNotBlank()) listOf(water.phone()) else emptyList()
    }
    val notes = remember(revision) { shower.accountNotes() }
    val kind = if (service == WidgetService.SHOWER) ServiceKind.Shower else ServiceKind.Drinking
    LifePage {
        DeviceManagementHeader("绑定${if (service == WidgetService.SHOWER) "洗澡" else "喝水"}组件", close)
        Text("选择这个桌面组件使用的设备", color = InkMid, fontSize = 15.sp)
        if (accounts.isEmpty() || devices.isEmpty()) {
            LifeCard {
                Text(if (accounts.isEmpty()) "请先登录${if (service == WidgetService.SHOWER) "洗澡" else "喝水"}账户" else "请先绑定一台设备", fontSize = 17.sp, color = InkDark)
                TextButton(onClick = { showService = true }) { Text("打开${if (service == WidgetService.SHOWER) "洗澡" else "喝水"}页面") }
            }
        } else {
            LifeCard {
                Text("设备", fontWeight = FontWeight.SemiBold, color = InkDark)
                devices.forEach { (deviceId, name) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(deviceId == selectedDevice, onClick = { selectedDevice = deviceId })
                        TextButton(onClick = { selectedDevice = deviceId }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(name, fontSize = 15.sp, color = InkDark)
                                Text(deviceId, fontSize = 12.sp, color = InkGray)
                            }
                        }
                    }
                }
            }
            if (service == WidgetService.SHOWER) LifeCard {
                Text("开水账户", fontWeight = FontWeight.SemiBold, color = InkDark)
                accounts.forEach { phone ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(phone == selectedPhone, onClick = { selectedPhone = phone })
                        TextButton(onClick = { selectedPhone = phone }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(showerAccountDisplayName(phone, notes[phone]), fontSize = 15.sp, color = InkDark)
                                if (!notes[phone].isNullOrBlank()) Text(maskedShowerPhone(phone), fontSize = 12.sp, color = InkGray)
                            }
                        }
                    }
                }
            }
            val phone = if (service == WidgetService.SHOWER) selectedPhone else accounts.first()
            ServiceActionButton("绑定到此组件", Icons.Outlined.Check,
                enabled = devices.any { it.first == selectedDevice } && phone in accounts, kind = kind, onClick = {
                runCatching {
                    widget.save(id, ServiceWidgetBinding(service, selectedDevice, phone))
                    ServiceWidgetUpdater.update(context, id)
                }.onSuccess { close() }.onFailure { error = it.message ?: "绑定失败，请重试" }
            })
            TextButton(onClick = { showService = true }) { Text("账户与设备设置") }
        }
        ServiceMessage(error, true)
    }
}

@Composable
private fun WidgetControlPage(id: Int, binding: ServiceWidgetBinding, operation: WidgetOperation,
    water: WaterViewModel, shower: ShowerViewModel, rebind: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val ws by water.state.collectAsStateWithLifecycle()
    val ss by shower.state.collectAsStateWithLifecycle()
    val isShower = binding.service == WidgetService.SHOWER
    val busy = if (isShower) ss.busy else ws.busy
    val message = if (isShower) ss.message else ws.message
    val error = if (isShower) ss.error else ws.error
    var launched by rememberSaveable { mutableStateOf(false) }
    var permissionStarted by rememberSaveable { mutableStateOf(false) }
    val store = remember { ServiceWidgetStore(context) }
    fun fail(text: String) { launched = true; if (isShower) shower.message(text) else water.message(text) }
    fun execute() {
        if (launched) return
        launched = true
        store.status(id, "正在确认设备状态…")
        ServiceWidgetUpdater.update(context, id)
        if (isShower) shower.controlFromWidget(binding.accountPhone, binding.deviceId, operation)
        else water.startFromWidget(binding.accountPhone, binding.deviceId)
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) execute() else fail("请开启蓝牙并靠近设备后再试")
    }
    fun connect() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) fail("本机不支持蓝牙")
        else if (!adapter.isEnabled) enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        else execute()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) connect() else fail("需要蓝牙连接权限才能开关水")
    }
    LaunchedEffect(Unit) {
        if (!permissionStarted) {
            permissionStarted = true
            if (!isShower) execute()
            else if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            else connect()
        }
    }
    LaunchedEffect(launched, busy, message, error) {
        if (launched && !busy && message != null) {
            store.status(id, message)
            ServiceWidgetUpdater.update(context, id)
            if (!error) { delay(900); close() }
        }
    }
    BackHandler(busy) { /* Keep the foreground BLE connection alive until its result is persisted. */ }
    val kind = if (isShower) ServiceKind.Shower else ServiceKind.Drinking
    val name = if (isShower) ss.devices.firstOrNull { it.sn == binding.deviceId }?.name
        else ws.devices.firstOrNull { it.id == binding.deviceId }?.name
    LifePage {
        LifeHeader(if (isShower) "洗澡" else "喝水", "桌面组件", if (isShower) Icons.Outlined.Shower else Icons.Outlined.WaterDrop, kind)
        LifeCard {
            Text(name ?: binding.deviceId, color = InkDark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (isShower) Text(showerAccountDisplayName(binding.accountPhone, ss.accountNotes[binding.accountPhone]), color = InkGray, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            if (busy || !launched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在确认设备状态…", color = InkMid, fontSize = 15.sp)
                }
            } else ServiceMessage(message ?: "操作已中断，请返回桌面确认状态", error)
            if (!busy) {
                TextButton(onClick = close) { Text("返回桌面") }
                TextButton(onClick = rebind) { Text("重新绑定组件") }
            }
        }
    }
}
