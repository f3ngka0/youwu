package com.fengkao.timetable.ui.shower

import android.app.Application
import android.os.SystemClock
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fengkao.timetable.data.shower.*
import com.fengkao.timetable.data.shower.ble.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException

data class ShowerUiState(
    val loggedIn: Boolean = false, val phone: String = "", val secret: String = "", val smsMode: Boolean = true,
    val smsSeconds: Int = 0, val busy: Boolean = false, val devices: List<ShowerDevice> = emptyList(),
    val selected: String = "", val active: Boolean = false, val owner: Boolean = false, val order: String = "",
    val unknown: Boolean = false, val message: String? = null, val error: Boolean = false,
    val refreshFailed: Boolean = false, val balance: ShowerBalance? = null, val balanceError: String? = null,
    val scanning: Boolean = false, val scanError: String? = null, val scanCompleted: Boolean = false,
    val nearby: List<NearbyShower> = emptyList(),
) {
    val device get() = devices.firstOrNull { it.sn == selected }
    val validPhone get() = phone.matches(Regex("1\\d{10}"))
}

class ShowerViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ShowerApi()
    private val store = ShowerStore(application)
    private val scanner = ShowerScanner(application)
    private val ble = ShowerBleController(application, api)
    private var scanJob: Job? = null
    private var auth = store.auth()
    private val savedBle = store.bleSession()
    private val savedDevices = store.devices().let { devices ->
        if (savedBle != null && devices.none { it.sn == savedBle.sn })
            devices + ShowerDevice(savedBle.sn, "当前用水设备", savedBle.mac, bluetoothOnly = true)
        else devices
    }
    private val _state = MutableStateFlow(ShowerUiState(loggedIn = auth != null, phone = store.phone(),
        devices = savedDevices, selected = savedBle?.sn ?: store.selected(), unknown = store.pending(),
        active = savedBle != null && !store.pending(), owner = savedBle != null, order = savedBle?.orderNo.orEmpty()))
    val state = _state.asStateFlow()
    fun logout() {
        if (_state.value.busy) return
        val s = _state.value
        if (!canLogoutShower(s.active, s.owner, s.unknown, store.pending()) || store.bleSession() != null) {
            message("请先在洗澡页结束用水并刷新确认，再退出账户")
            return
        }
        action {
            val account = auth
            val device = _state.value.device
            if (account != null && device != null && store.bleSession() == null) {
                val order = io { api.query(account, device.sn) }
                updateOrder(order)
                if (order.active && order.owner) {
                    throw IOException("该设备仍在用水，请先在洗澡页关水后再退出账户")
                }
            }
            io { store.expire() }; auth = null
            stopScan()
            ble.disconnect()
            _state.update { it.copy(loggedIn = false, secret = "", balance = null, balanceError = null, message = "已退出洗澡账户") }
        }
    }
    fun phone(value: String) { _state.update { it.copy(phone = value.filter(Char::isDigit).take(11), secret = "", message = null) } }
    fun secret(value: String) { _state.update { it.copy(secret = if (it.smsMode) value.filter(Char::isDigit).take(8) else value.take(128)) } }
    fun mode() { if (!_state.value.busy) _state.update { it.copy(smsMode = !it.smsMode, secret = "", message = null) } }
    fun message(text: String) { _state.update { it.copy(message = text, error = true) } }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ShowerSessionExpired) {
                    stopScan()
                    store.expire(); auth = null
                    _state.update { it.copy(loggedIn = false, secret = "", balance = null) }
                }
                message(when (e) {
                    is java.net.UnknownHostException -> "无法连接洗澡服务，请检查网络"
                    is java.net.SocketTimeoutException -> "连接超时，请刷新设备状态后重试"
                    else -> e.message ?: "操作失败，请重试"
                })
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    private fun blePermitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun sendSms() {
        val s = _state.value
        if (!s.validPhone || s.smsSeconds > 0) return
        action {
            io { api.sendSms(s.phone) }
            _state.update { it.copy(smsSeconds = 60, message = "短信已发送，请查收") }
            val end = SystemClock.elapsedRealtime() + 60_000
            viewModelScope.launch {
                do {
                    delay(1000)
                    val seconds = ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                    _state.update { it.copy(smsSeconds = seconds) }
                } while (seconds > 0)
            }
        }
    }
    fun login() {
        val s = _state.value
        if (!s.validPhone || s.secret.isBlank()) return
        action {
            try {
                val account = io { api.login(s.phone, s.secret, s.smsMode) }
                io { store.login(account) }
                auth = account
                _state.update { it.copy(loggedIn = true, devices = store.devices(), selected = store.selected(),
                    active = false, owner = false, order = "", unknown = store.pending()) }
                refreshAll()
            } finally { _state.update { it.copy(secret = "") } }
        }
    }
    fun enter() { if (auth != null) refresh() }
    fun refresh() { if (auth != null) action { refreshAll() } }
    private suspend fun refreshAll() {
        _state.update { it.copy(refreshFailed = false, balanceError = null) }
        try { refreshDevice() }
        catch (e: Exception) { _state.update { it.copy(refreshFailed = true) }; throw e }
        val account = auth ?: return
        try {
            val balance = io { api.balance(account) }
            _state.update { it.copy(balance = balance, balanceError = null) }
        } catch (e: CancellationException) { throw e }
        catch (e: ShowerSessionExpired) { throw e }
        catch (e: Exception) { _state.update { it.copy(balanceError = e.message ?: "余额读取失败") } }
    }
    fun scanNearby() {
        if (scanJob?.isActive == true || !_state.value.loggedIn) return
        _state.update { it.copy(scanning = true, scanCompleted = false, scanError = null, nearby = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(5_000) {
                    scanner.scan().collect { device ->
                        _state.update { it.copy(nearby = (it.nearby.filterNot { old -> old.mac == device.mac } + device).sortedByDescending { item -> item.rssi }) }
                    }
                }
                _state.update { it.copy(scanCompleted = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(scanError = e.message ?: "蓝牙扫描失败") } }
            finally { _state.update { it.copy(scanning = false) } }
        }
    }
    fun stopScan() { scanJob?.cancel(); scanJob = null }
    private suspend fun refreshDevice() {
        val account = auth ?: return
        val device = _state.value.device
        if (device == null) { io { api.validate(account) }; return }
        val session = store.bleSession()
        if (session == null && device.mac.isNotBlank()) {
            val fresh = io { api.device(account, device.mac) }
            require(fresh.sn == device.sn) { "设备编号已变更，请重新绑定" }
            val devices = _state.value.devices.map { if (it.sn == fresh.sn) fresh else it }
            io { store.saveDevices(devices, fresh.sn) }
            _state.update { it.copy(devices = devices) }
        }
        if (session != null) {
            if (!blePermitted()) throw IOException("请允许蓝牙连接权限后刷新用水状态")
            when (val inspection = ble.inspect(session, account)) {
                BleInspection.Running -> {
                    io { store.pending(false) }
                    _state.update { it.copy(active = true, owner = true, order = session.orderNo, unknown = false) }
                }
                BleInspection.Settled -> {
                    ble.disconnect()
                    io { store.bleSession(null); store.pending(false) }
                    _state.update { it.copy(active = false, owner = false, order = "", unknown = false) }
                }
                is BleInspection.Pending -> {
                    _state.update { it.copy(unknown = true, message = inspection.message, error = true) }
                    throw IOException(inspection.message)
                }
            }
        } else {
            // An order opened by an older release may still be controlled through the cloud.
            val cloud = try { io { api.query(account, device.sn) } }
                catch (e: ShowerSessionExpired) { throw e }
                catch (_: IOException) { ShowerOrder(false, false, "") }
            updateOrder(cloud)
        }
    }
    private suspend fun updateOrder(order: ShowerOrder) {
        io { store.pending(false) }
        _state.update { it.copy(active = order.active, owner = order.owner, order = order.number, unknown = false) }
    }
    fun bind(raw: String) {
        val code = ShowerApi.deviceCode(raw) ?: return message("未识别到设备，请扫描趣智校园热水器二维码")
        val account = auth ?: return
        if (_state.value.active || _state.value.unknown) return message("请先结束当前用水或刷新确认状态")
        action {
            stopScan()
            val device = io { api.device(account, code) }
            val devices = (_state.value.devices.filterNot { it.sn == device.sn } + device)
            io { store.saveDevices(devices, device.sn) }
            _state.update { it.copy(devices = devices, selected = device.sn, message = "已设为默认设备") }
            refreshDevice()
        }
    }
    fun select(sn: String) {
        if (_state.value.active || _state.value.unknown) return message("请先结束当前用水或刷新确认状态")
        if (_state.value.devices.none { it.sn == sn }) return
        action {
            io { store.saveDevices(_state.value.devices, sn) }
            _state.update { it.copy(selected = sn, active = false, owner = false, order = "") }
            refreshDevice()
        }
    }
    fun start() {
        val s = _state.value
        val device = s.device ?: return
        val account = auth ?: return
        if (s.active || s.unknown) return
        if (!blePermitted()) return message("请允许蓝牙连接权限后开水")
        action {
            io { store.pending(true) }
            _state.update { it.copy(unknown = true) }
            when (val result = ble.open(device, account, store.bleSession()) { prepared -> store.bleSession(prepared) }) {
                is BleOpenResult.Opened, is BleOpenResult.Resumed -> {
                    val session = if (result is BleOpenResult.Opened) result.session else (result as BleOpenResult.Resumed).session
                    io { store.bleSession(session); store.pending(false) }
                    _state.update { it.copy(active = true, owner = true, order = session.orderNo,
                        unknown = false, message = "蓝牙开水成功，结束后请在设备旁关水") }
                }
                is BleOpenResult.Recovered -> {
                    ble.disconnect()
                    io { store.bleSession(null); store.pending(false) }
                    _state.update { it.copy(active = false, owner = false, unknown = false, message = result.message) }
                }
                is BleOpenResult.Failed -> {
                    ble.disconnect()
                    io { store.pending(false) }
                    _state.update { it.copy(unknown = false) }
                    throw IOException(result.message)
                }
                is BleOpenResult.Unconfirmed -> throw IOException(result.message)
            }
        }
    }
    fun stop() {
        val s = _state.value
        val device = s.device
        val account = auth ?: return
        if (!s.active || !s.owner) return
        if (!blePermitted()) return message("请允许蓝牙连接权限后关水")
        action {
            val session = store.bleSession()
            if (session != null) {
                io { store.pending(true) }
                _state.update { it.copy(unknown = true) }
                when (val result = ble.close(session, account)) {
                    BleInspection.Settled -> {
                        ble.disconnect()
                        io { store.bleSession(null); store.pending(false) }
                        _state.update { it.copy(active = false, owner = false, order = "", unknown = false,
                            message = "已关水并完成结算") }
                        refreshAll()
                    }
                    BleInspection.Running -> throw IOException("设备仍在用水，请靠近后重试")
                    is BleInspection.Pending -> throw IOException(result.message)
                }
                return@action
            }
            val cloudDevice = device ?: return@action
            val current = io { api.query(account, cloudDevice.sn) }
            if (!current.active) { updateOrder(current); return@action }
            if (!current.owner) { updateOrder(current); throw IOException("该设备正在被他人使用") }
            val number = current.number.ifBlank { s.order }
            if (number.isBlank()) throw IOException("订单号暂未返回，请刷新后再关水")
            io { store.pending(true) }
            _state.update { it.copy(unknown = true) }
            try { io { api.stop(account, cloudDevice.sn, number) } }
            catch (e: ShowerSessionExpired) { throw e }
            catch (_: Exception) { throw IOException("关水结果未确认，请查看设备并刷新状态") }
            var closed = false
            repeat(5) {
                if (!closed) {
                    delay(800)
                    val order = io { api.query(account, cloudDevice.sn) }
                    if (!order.active) { updateOrder(order); closed = true }
                }
            }
            if (!closed) throw IOException("关水请求已受理，状态尚未更新，请确认设备并刷新")
            _state.update { it.copy(message = "已结束用水") }
            refreshAll()
        }
    }
    override fun onCleared() { ble.disconnect(); super.onCleared() }
}
