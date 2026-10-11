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

data class SecondaryShowerLogin(
    val phone: String = "", val secret: String = "", val smsMode: Boolean = true,
    val smsSeconds: Int = 0, val message: String? = null, val error: Boolean = false, val complete: Boolean = false,
) { val validPhone get() = phone.matches(Regex("1\\d{10}")) }

data class ShowerUiState(
    val loggedIn: Boolean = false, val phone: String = "", val secret: String = "", val smsMode: Boolean = true,
    val smsSeconds: Int = 0, val busy: Boolean = false, val devices: List<ShowerDevice> = emptyList(),
    val selected: String = "", val active: Boolean = false, val owner: Boolean = false, val order: String = "",
    val unknown: Boolean = false, val message: String? = null, val error: Boolean = false,
    val refreshFailed: Boolean = false, val balance: ShowerBalance? = null, val balanceError: String? = null,
    val scanning: Boolean = false, val scanError: String? = null, val scanCompleted: Boolean = false,
    val nearby: List<NearbyShower> = emptyList(),
    val accounts: List<String> = emptyList(), val selectedAccountPhone: String = "",
    val secondLogin: SecondaryShowerLogin = SecondaryShowerLogin(), val confirming: Boolean = false,
    val accountNotes: Map<String, String> = emptyMap(),
    val accountBalances: Map<String, ShowerBalance> = emptyMap(),
    val accountBalanceErrors: Map<String, String> = emptyMap(), val refreshingAccounts: Boolean = false,
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
    private var auth = store.activeAuth()
    private val savedBle = store.bleSession()
    private val savedDevices = store.devices().let { devices ->
        if (savedBle != null && devices.none { it.sn == savedBle.sn })
            devices + ShowerDevice(savedBle.sn, "当前用水设备", savedBle.mac, bluetoothOnly = true)
        else devices
    }
    private val _state = MutableStateFlow(ShowerUiState(loggedIn = auth != null, phone = store.activePhone(),
        accounts = store.accounts().map { it.phone }, selectedAccountPhone = store.activePhone(), accountNotes = store.accountNotes(),
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
            _state.update { it.copy(loggedIn = false, phone = store.phone(), secret = "", accounts = emptyList(), accountNotes = emptyMap(), accountBalances = emptyMap(), accountBalanceErrors = emptyMap(), selectedAccountPhone = "", secondLogin = SecondaryShowerLogin(), balance = null, balanceError = null, message = "已退出洗澡账户") }
        }
    }
    fun phone(value: String) { _state.update { it.copy(phone = value.filter(Char::isDigit).take(11), secret = "", message = null) } }
    fun secret(value: String) { _state.update { it.copy(secret = if (it.smsMode) value.filter(Char::isDigit).take(8) else value.take(128)) } }
    fun mode() { if (!_state.value.busy) _state.update { it.copy(smsMode = !it.smsMode, secret = "", message = null) } }
    fun message(text: String) { _state.update { it.copy(message = text, error = true) } }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        if (!com.fengkao.timetable.widget.ServiceOperationGate.shower.tryLock()) return message("洗澡操作正在进行，请稍后再试")
        _state.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ShowerSessionExpired) {
                    stopScan()
                    store.expireAccount(auth?.phone ?: store.activePhone()); auth = null
                    _state.update { it.copy(loggedIn = false, phone = store.activePhone(), secret = "", balance = null,
                        accounts = store.accounts().map { it.phone }, selectedAccountPhone = store.activePhone()) }
                }
                message(when (e) {
                    is java.net.UnknownHostException -> "无法连接洗澡服务，请检查网络"
                    is java.net.SocketTimeoutException -> "连接超时，请刷新设备状态后重试"
                    else -> e.message ?: "操作失败，请重试"
                })
            } finally {
                _state.update { it.copy(busy = false, confirming = false) }
                com.fengkao.timetable.widget.ServiceOperationGate.shower.unlock()
                com.fengkao.timetable.widget.ServiceWidgetUpdater.updateAll(getApplication())
            }
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
        if ((store.pending() || store.bleSession() != null) && s.phone != store.controllingPhone())
            return message("请用当前用水账户的手机号重新登录")
        action {
            try {
                val account = io { api.login(s.phone, s.secret, s.smsMode) }
                io { store.login(account) }
                auth = account
                _state.update { it.copy(loggedIn = true, devices = store.devices(), selected = store.selected(),
                    accounts = store.accounts().map { account -> account.phone }, selectedAccountPhone = account.phone,
                    accountNotes = store.accountNotes(), accountBalances = emptyMap(), accountBalanceErrors = emptyMap(),
                    active = false, owner = false, order = "", unknown = store.pending()) }
                refreshAll()
            } finally { _state.update { it.copy(secret = "") } }
        }
    }
    private fun accountChangesAllowed(): Boolean {
        val s = _state.value
        return canChangeShowerAccount(s.busy, s.active, s.unknown, store.pending(), store.bleSession() != null)
    }
    fun selectAccount(phone: String) {
        if (phone == _state.value.selectedAccountPhone) return
        if (!accountChangesAllowed()) return message("请先结束用水并确认状态，再切换账户")
        val next = store.accounts().firstOrNull { it.phone == phone } ?: return
        action {
            io { store.selectAccount(phone) }
            auth = next
            _state.update { it.copy(phone = phone, selectedAccountPhone = phone, balance = null,
                balanceError = null, message = null, active = false, owner = false, order = "") }
            refreshAll()
        }
    }
    fun beginSecondLogin() {
        if (!accountChangesAllowed()) return
        _state.update { it.copy(secondLogin = it.secondLogin.copy(phone = store.secondaryPhone(), secret = "",
            message = null, error = false, complete = false)) }
    }
    fun secondPhone(value: String) { if (!_state.value.busy) _state.update {
        it.copy(secondLogin = it.secondLogin.copy(phone = value.filter(Char::isDigit).take(11), secret = "", message = null, complete = false))
    } }
    fun secondSecret(value: String) { if (!_state.value.busy) _state.update {
        it.copy(secondLogin = it.secondLogin.copy(secret = if (it.secondLogin.smsMode) value.filter(Char::isDigit).take(8) else value.take(128)))
    } }
    fun secondMode() { if (!_state.value.busy) _state.update {
        it.copy(secondLogin = it.secondLogin.copy(smsMode = !it.secondLogin.smsMode, secret = "", message = null))
    } }
    private fun secondaryAction(block: suspend () -> Unit) {
        if (!accountChangesAllowed() || !_state.value.loggedIn) return
        if (!com.fengkao.timetable.widget.ServiceOperationGate.shower.tryLock()) return message("洗澡操作正在进行，请稍后再试")
        _state.update { it.copy(busy = true, secondLogin = it.secondLogin.copy(message = null, error = false)) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(secondLogin = it.secondLogin.copy(
                message = e.message ?: "第二账户登录失败，请重试", error = true)) } }
            finally {
                _state.update { it.copy(busy = false) }
                com.fengkao.timetable.widget.ServiceOperationGate.shower.unlock()
                com.fengkao.timetable.widget.ServiceWidgetUpdater.updateAll(getApplication())
            }
        }
    }
    fun secondSms() {
        val s = _state.value.secondLogin
        if (!s.validPhone || s.smsSeconds > 0) return
        if (s.phone == store.phone()) {
            _state.update { it.copy(secondLogin = it.secondLogin.copy(message = "请填写另一个手机号", error = true)) }; return
        }
        secondaryAction {
            io { api.sendSms(s.phone) }
            _state.update { it.copy(secondLogin = it.secondLogin.copy(smsSeconds = 60, message = "短信已发送，请查收")) }
            val end = SystemClock.elapsedRealtime() + 60_000
            viewModelScope.launch {
                do {
                    delay(1000)
                    val seconds = ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                    _state.update { it.copy(secondLogin = it.secondLogin.copy(smsSeconds = seconds)) }
                } while (seconds > 0)
            }
        }
    }
    fun loginSecond() {
        val s = _state.value.secondLogin
        if (!s.validPhone || s.secret.isBlank()) return
        if (s.phone == store.phone()) {
            _state.update { it.copy(secondLogin = it.secondLogin.copy(message = "请填写另一个手机号", error = true)) }; return
        }
        secondaryAction {
            try {
                val account = io { api.login(s.phone, s.secret, s.smsMode).also(api::validate) }
                io { store.loginSecondary(account) }
                val previousPhone = auth?.phone
                auth = store.activeAuth()
                _state.update { it.copy(accounts = store.accounts().map { saved -> saved.phone },
                    phone = auth?.phone ?: store.activePhone(), selectedAccountPhone = store.activePhone(),
                    balance = it.balance.takeIf { previousPhone == auth?.phone },
                    accountNotes = store.accountNotes(),
                    accountBalances = it.accountBalances.filterKeys { phone -> store.accounts().any { saved -> saved.phone == phone } },
                    secondLogin = it.secondLogin.copy(complete = true, secret = "", message = null)) }
                refreshAccountBalances()
            } finally { _state.update { it.copy(secondLogin = it.secondLogin.copy(secret = "")) } }
        }
    }
    fun saveAccountNote(phone: String, note: String) {
        runCatching { store.saveAccountNote(phone, note) }
            .onSuccess {
                _state.update { it.copy(accountNotes = store.accountNotes()) }
                com.fengkao.timetable.widget.ServiceWidgetUpdater.updateAll(getApplication())
            }.onFailure { message(it.message ?: "备注保存失败") }
    }
    fun refreshAccountBalances() {
        if (_state.value.refreshingAccounts) return
        val accounts = store.accounts()
        if (accounts.isEmpty()) return
        _state.update { it.copy(refreshingAccounts = true) }
        viewModelScope.launch {
            try {
                accounts.forEach { account ->
                    try {
                        val balance = io { api.balance(account) }
                        _state.update { it.copy(accountBalances = it.accountBalances + (account.phone to balance),
                            accountBalanceErrors = it.accountBalanceErrors - account.phone) }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { _state.update { it.copy(
                        accountBalanceErrors = it.accountBalanceErrors + (account.phone to
                            if (e is ShowerSessionExpired) "登录已过期，请重新验证" else e.message.orEmpty().ifBlank { "余额读取失败" })) } }
                }
            } finally { _state.update { it.copy(refreshingAccounts = false) } }
        }
    }
    fun enter() {
        // Widgets may have changed a session while the main screen was paused.
        if (!_state.value.busy) {
            auth = store.activeAuth()
            _state.update { it.copy(loggedIn = auth != null, accounts = store.accounts().map { a -> a.phone },
                selectedAccountPhone = store.activePhone(), phone = store.activePhone(), accountNotes = store.accountNotes(),
                unknown = store.pending(), selected = store.bleSession()?.sn ?: store.selected()) }
        }
        if (auth != null) refresh()
    }
    fun refresh() { if (auth != null) action { refreshAll() } }
    private suspend fun refreshAll() {
        _state.update { it.copy(refreshFailed = false, balanceError = null) }
        try { refreshDevice() }
        catch (e: Exception) { _state.update { it.copy(refreshFailed = true) }; throw e }
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
    fun start() { action { startWater() } }
    fun stop() { action { stopWater() } }
    fun controlFromWidget(phone: String, sn: String, operation: com.fengkao.timetable.widget.service.WidgetOperation) {
        action {
            val account = store.accounts().firstOrNull { it.phone == phone }
                ?: throw IOException("组件账户已退出，请重新登录并绑定")
            val device = store.devices().firstOrNull { it.sn == sn }
                ?: throw IOException("组件设备已移除，请重新绑定")
            val session = store.bleSession()
            if (store.pending() || session != null) {
                require(com.fengkao.timetable.widget.service.widgetMatchesPendingTarget(phone, sn, store.controllingPhone(), session?.sn, store.selected())) {
                    "另一个账户或设备的用水尚未结束，请先处理当前用水"
                }
            } else {
                io { store.selectAccount(phone); store.saveDevices(store.devices(), sn) }
            }
            auth = account
            _state.update { it.copy(loggedIn = true, phone = phone, selectedAccountPhone = phone,
                devices = store.devices(), selected = device.sn, unknown = store.pending(),
                active = session != null && !store.pending(), owner = session != null) }
            refreshDevice()
            val current = _state.value
            if (!com.fengkao.timetable.widget.service.widgetOperationAllowed(operation, current.active, current.owner, current.unknown)) {
                throw IOException(if (current.active) "设备已在用水，请确认状态后再操作" else "设备状态已改变，请返回桌面后再试")
            }
            when (operation) {
                com.fengkao.timetable.widget.service.WidgetOperation.START -> startWater()
                com.fengkao.timetable.widget.service.WidgetOperation.STOP -> stopWater()
                com.fengkao.timetable.widget.service.WidgetOperation.CHECK ->
                    _state.update { it.copy(message = if (it.active) "设备正在用水" else "设备已停止出水") }
            }
        }
    }
    private suspend fun startWater() {
        val s = _state.value
        val device = s.device ?: return
        val account = auth ?: return
        if (s.active || s.unknown) return
        if (!blePermitted()) return message("请允许蓝牙连接权限后开水")
        io { store.pending(true, account.phone) }
        _state.update { it.copy(unknown = true, confirming = true) }
        when (val result = ble.open(device, account, store.bleSession()) { prepared -> store.bleSession(prepared) }) {
            is BleOpenResult.Opened, is BleOpenResult.Resumed -> {
                val session = if (result is BleOpenResult.Opened) result.session else (result as BleOpenResult.Resumed).session
                io { store.bleSession(session); store.pending(false) }
                _state.update { it.copy(active = true, owner = true, order = session.orderNo,
                    unknown = false, confirming = false, message = "蓝牙开水成功，结束后请在设备旁关水") }
            }
            is BleOpenResult.Recovered -> {
                ble.disconnect()
                io { store.bleSession(null); store.pending(false) }
                _state.update { it.copy(active = false, owner = false, unknown = false, confirming = false, message = result.message) }
            }
            is BleOpenResult.Failed -> {
                ble.disconnect()
                io { store.pending(false) }
                _state.update { it.copy(unknown = false, confirming = false) }
                throw IOException(result.message)
            }
            is BleOpenResult.Unconfirmed -> {
                val session = store.bleSession()
                val running = session != null && awaitShowerOpening { ble.confirmRunning(session, account) }
                if (!running) throw IOException(result.message)
                io { store.pending(false) }
                _state.update { it.copy(active = true, owner = true, order = session.orderNo,
                    unknown = false, confirming = false, message = "蓝牙开水成功") }
            }
        }
    }
    private suspend fun stopWater() {
        val s = _state.value
        val device = s.device
        val account = auth ?: return
        if (!s.active || !s.owner) return
        if (!blePermitted()) return message("请允许蓝牙连接权限后关水")
        val session = store.bleSession()
        if (session != null) {
            io { store.pending(true, account.phone) }
            _state.update { it.copy(unknown = true, confirming = true) }
            when (val result = ble.close(session, account)) {
                BleInspection.Settled -> {
                    ble.disconnect()
                    io { store.bleSession(null); store.pending(false) }
                    _state.update { it.copy(active = false, owner = false, order = "", unknown = false, confirming = false,
                        message = "已关水并完成结算") }
                    refreshAll()
                }
                BleInspection.Running -> throw IOException("设备仍在用水，请靠近后重试")
                is BleInspection.Pending -> throw IOException(result.message)
            }
            return
        }
        val cloudDevice = device ?: return
        val current = io { api.query(account, cloudDevice.sn) }
        if (!current.active) { updateOrder(current); return }
        if (!current.owner) { updateOrder(current); throw IOException("该设备正在被他人使用") }
        val number = current.number.ifBlank { s.order }
        if (number.isBlank()) throw IOException("订单号暂未返回，请刷新后再关水")
        io { store.pending(true, account.phone) }
        _state.update { it.copy(unknown = true, confirming = true) }
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
    override fun onCleared() { ble.disconnect(); super.onCleared() }
}
