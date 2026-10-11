package com.fengkao.timetable.ui.water

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fengkao.timetable.data.water.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class WaterUiState(
    val loggedIn: Boolean = false,
    val configured: Boolean = false,
    val phone: String = "",
    val graphCode: String = "",
    val smsCode: String = "",
    val captcha: Bitmap? = null,
    val busy: Boolean = false,
    val smsSeconds: Int = 0,
    val devices: List<WaterDevice> = emptyList(),
    val selectedId: String = "",
    val message: String? = null,
    val error: Boolean = false,
    val startSeconds: Int = 0,
    val refreshFailed: Boolean = false,
) {
    val device: WaterDevice? get() = devices.firstOrNull { it.id == selectedId }
    val validPhone: Boolean get() = phone.matches(Regex("^1\\d{10}$"))
}

class WaterViewModel(application: Application) : AndroidViewModel(application) {
    private val api = WaterApi()
    private val store = WaterStore(application)
    private var token = store.token()
    private var captchaKey = ""
    private var smsPhone = ""
    private val _state = MutableStateFlow(WaterUiState(
        loggedIn = token.isNotBlank(), configured = api.configured, phone = store.phone(),
        devices = store.devices(), selectedId = store.selectedId(),
    ))
    val state = _state.asStateFlow()

    /** 仅移除喝水凭据，保留同一账户的默认设备，不影响其他服务。 */
    fun logout() {
        if (_state.value.busy) return
        runAction {
            withContext(Dispatchers.IO) { store.expire() }
            token = ""; captchaKey = ""; smsPhone = ""
            _state.update { it.copy(loggedIn = false, captcha = null, graphCode = "", smsCode = "", message = "已退出喝水账户") }
        }
    }

    fun enter() { if (token.isNotBlank()) refresh() }
    fun phone(value: String) {
        captchaKey = ""
        smsPhone = ""
        _state.update { it.copy(phone = value.filter(Char::isDigit).take(11), captcha = null, graphCode = "", smsCode = "", message = null) }
    }
    fun graph(value: String) { _state.update { it.copy(graphCode = value.trim().take(12)) } }
    fun sms(value: String) { _state.update { it.copy(smsCode = value.filter(Char::isDigit).take(8)) } }
    fun message(text: String) { _state.update { it.copy(message = text, error = true) } }

    private fun runAction(action: suspend () -> Unit) {
        if (_state.value.busy) return
        if (!com.fengkao.timetable.widget.ServiceOperationGate.water.tryLock()) return message("饮水操作正在进行，请稍后再试")
        _state.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is WaterSessionExpired) {
                    store.expire(); token = ""
                    captchaKey = ""; smsPhone = ""
                    _state.update { it.copy(loggedIn = false, captcha = null, graphCode = "", smsCode = "") }
                }
                _state.update { it.copy(message = e.message ?: "网络连接失败，请重试", error = true) }
            } finally {
                _state.update { it.copy(busy = false) }
                com.fengkao.timetable.widget.ServiceOperationGate.water.unlock()
                com.fengkao.timetable.widget.ServiceWidgetUpdater.updateAll(getApplication())
            }
        }
    }

    fun loadCaptcha() {
        if (!_state.value.validPhone) return message("请输入正确的手机号")
        runAction {
            captchaKey = ""
            _state.update { it.copy(captcha = null, graphCode = "") }
            val key = UUID.randomUUID().toString().replace("-", "").take(10)
            val bitmap = withContext(Dispatchers.IO) {
                val bytes = api.captcha(key)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("图形验证码解析失败")
            }
            captchaKey = key
            _state.update { it.copy(captcha = bitmap) }
        }
    }

    fun sendSms() {
        val s = _state.value
        if (!s.validPhone || captchaKey.isBlank() || s.graphCode.isBlank() || s.smsSeconds > 0) return
        val key = captchaKey
        runAction {
            withContext(Dispatchers.IO) { api.sendSms(s.phone, s.graphCode, key) }
            smsPhone = s.phone
            _state.update { it.copy(message = "短信已发送，请查收") }
            countdown(60, sms = true)
        }
    }

    fun login() {
        val s = _state.value
        if (!s.validPhone || s.smsCode.isBlank() || smsPhone != s.phone) return message("请先获取短信验证码")
        runAction {
            val newToken = withContext(Dispatchers.IO) { api.login(s.phone, s.smsCode) }
            withContext(Dispatchers.IO) { store.saveLogin(newToken, s.phone) }
            token = newToken
            _state.update { it.copy(loggedIn = true, captcha = null, graphCode = "", smsCode = "",
                devices = store.devices(), selectedId = store.selectedId()) }
            refreshDevices()
        }
    }

    private suspend fun syncDevices() {
        val remote = withContext(Dispatchers.IO) { api.devices(token) }
        val devices = (remote + _state.value.devices).distinctBy { it.id }
        val selected = _state.value.selectedId.takeIf { id -> devices.any { it.id == id } } ?: devices.firstOrNull()?.id.orEmpty()
        store.saveDevices(devices, selected)
        _state.update { it.copy(devices = devices, selectedId = selected) }
    }
    private suspend fun refreshDevices() {
        _state.update { it.copy(refreshFailed = false) }
        try { syncDevices() }
        catch (e: Exception) { _state.update { it.copy(refreshFailed = true) }; throw e }
    }
    fun refresh() { if (token.isNotBlank()) runAction { refreshDevices() } }
    fun select(id: String) {
        if (_state.value.busy || _state.value.devices.none { it.id == id }) return
        store.saveDevices(_state.value.devices, id)
        _state.update { it.copy(selectedId = id, message = null) }
    }
    fun bind(raw: String) {
        val id = DeviceIdParser.normalize(raw)
        if (!Regex("[A-Za-z0-9][A-Za-z0-9_-]{5,63}").matches(id) || !id.any(Char::isDigit)) return message("未识别到有效设备编号，请扫描饮水机二维码")
        if (token.isBlank()) return message("请先登录喝水账户")
        runAction {
            withContext(Dispatchers.IO) { api.bind(token, id) }
            val devices = _state.value.devices.let { if (it.any { device -> device.id == id }) it else it + WaterDevice(id, "饮水设备") }
            store.saveDevices(devices, id)
            _state.update { it.copy(devices = devices, selectedId = id, message = "已绑定，并设为默认设备") }
            syncDevices()
        }
    }
    fun start() {
        val device = _state.value.device ?: return
        startFromWidget(store.phone(), device.id)
    }
    fun startFromWidget(phone: String, id: String) {
        runAction {
            val currentToken = store.token()
            if (currentToken.isBlank() || phone != store.phone()) throw java.io.IOException("饮水账户已退出，请重新登录并绑定")
            val device = store.devices().firstOrNull { it.id == id } ?: throw java.io.IOException("组件设备已移除，请重新绑定")
            if (store.controlCooldownRemaining() > 0) throw java.io.IOException("刚发送过启动请求，请先确认设备状态")
            // Persist before sending: timeouts and repeated widget taps cannot resend immediately.
            store.markControlAttempt()
            try { withContext(Dispatchers.IO) { api.start(currentToken, device.id) } }
            catch (e: java.net.SocketTimeoutException) { throw java.io.IOException("启动结果未确认，请先查看设备状态，避免重复启动") }
            _state.update { it.copy(message = "设备已启动，请在饮水机上选择水温并接水") }
            countdown(10, sms = false)
        }
    }
    private fun countdown(seconds: Int, sms: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
        _state.update { if (sms) it.copy(smsSeconds = seconds) else it.copy(startSeconds = seconds) }
        viewModelScope.launch {
            do {
                delay(1000)
                val remaining = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                _state.update { if (sms) it.copy(smsSeconds = remaining) else it.copy(startSeconds = remaining) }
            } while (remaining > 0)
        }
    }
}
