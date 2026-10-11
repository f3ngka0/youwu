package com.fengkao.timetable.data.shower.ble

import android.content.Context
import com.fengkao.timetable.data.shower.ShowerApi
import com.fengkao.timetable.data.shower.ShowerAuth
import com.fengkao.timetable.data.shower.ShowerDevice
import com.fengkao.timetable.data.shower.ShowerSessionExpired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Local transaction details are persisted before the command that may open the valve. */
data class BleSession(val sn: String, val mac: String, val consumeDate: String, val orderNo: String, val accountPhone: String = "") {
    fun json() = JSONObject().put("sn", sn).put("mac", mac).put("consumeDate", consumeDate).put("orderNo", orderNo).put("accountPhone", accountPhone)
    companion object { fun from(json: JSONObject) = BleSession(json.getString("sn"), json.getString("mac"),
        json.optString("consumeDate"), json.optString("orderNo"), json.optString("accountPhone")) }
}

sealed interface BleOpenResult {
    data class Opened(val session: BleSession) : BleOpenResult
    data class Resumed(val session: BleSession) : BleOpenResult
    data class Recovered(val message: String) : BleOpenResult
    data class Unconfirmed(val message: String) : BleOpenResult
    data class Failed(val message: String) : BleOpenResult
}

sealed interface BleInspection {
    data object Running : BleInspection
    data object Settled : BleInspection
    data class Pending(val message: String) : BleInspection
}

/** Based on LinYu's MIT-licensed FF00/FF01/FF02 protocol implementation. */
class ShowerBleController(context: Context, private val api: ShowerApi) {
    private val ble = BleController(context)
    fun disconnect() = ble.disconnect()
    val isBluetoothOn get() = ble.isBluetoothOn

    private suspend fun connect(mac: String): String? = when (val result = ble.connect(mac)) {
        BleConnectResult.Ready -> null
        BleConnectResult.NoService -> "设备没有蓝牙水控服务，请检查绑定的设备"
        is BleConnectResult.Failed -> result.reason
    }

    suspend fun open(device: ShowerDevice, auth: ShowerAuth, previous: BleSession?,
        onPrepared: (BleSession) -> Unit): BleOpenResult {
        require(previous == null || previous.accountPhone.isBlank() || previous.accountPhone == auth.phone) { "请使用开水时的账户" }
        if (!device.controlMac.matches(Regex("[0-9A-F]{12}"))) return BleOpenResult.Failed("设备蓝牙地址无效，请重新绑定")
        connect(device.controlMac)?.let { return BleOpenResult.Failed(it) }
        val state = ble.query() ?: return BleOpenResult.Failed("设备未响应，请靠近热水器重试")
        if (state.state == 3 || state.state == 6) {
            val result = settle(state, auth)
            ble.disconnect()
            return if (result == null) BleOpenResult.Recovered("上一笔用水已结算，请再点一次开水")
            else BleOpenResult.Failed(result)
        }
        if (state.state != 0) {
            return if (previous?.sn == device.sn) BleOpenResult.Resumed(previous)
            else BleOpenResult.Failed("设备正在使用中")
        }
        if (previous != null) return BleOpenResult.Failed("上次开水结果待确认，请先刷新状态")
        val rate = try { withContext(Dispatchers.IO) { api.bleRate(auth, device, state) } }
            catch (e: CancellationException) { throw e }
            catch (e: ShowerSessionExpired) { throw e }
            catch (e: Exception) { return BleOpenResult.Failed(e.message ?: "获取蓝牙费率失败") }
        val session = BleSession(device.sn, device.controlMac, rate.consumeDate, rate.orderNo, auth.phone)
        onPrepared(session)
        // From here on a timeout is unknown, never a safe retry.
        return if (ble.writeRate(rate.data)) BleOpenResult.Opened(session)
        else BleOpenResult.Unconfirmed("开水指令已发送，但设备未确认。请核对水流后刷新状态")
    }

    /** Read-only confirmation: do not send another rate or settle a bill while waiting. */
    suspend fun confirmRunning(session: BleSession, auth: ShowerAuth): Boolean {
        require(session.accountPhone.isBlank() || session.accountPhone == auth.phone) { "请使用开水时的账户" }
        if (connect(session.mac) != null) return false
        val state = ble.query(timeoutMs = 1_000) ?: return false
        return state.state != 0 && state.state != 3 && state.state != 6
    }

    suspend fun inspect(session: BleSession, auth: ShowerAuth): BleInspection {
        require(session.accountPhone.isBlank() || session.accountPhone == auth.phone) { "请使用开水时的账户" }
        connect(session.mac)?.let { return BleInspection.Pending(it) }
        val state = ble.query() ?: return BleInspection.Pending("设备未响应，请靠近后重试")
        return when (state.state) {
            0 -> BleInspection.Settled
            3, 6 -> {
                val error = settle(state, auth)
                if (error == null) BleInspection.Settled else BleInspection.Pending(error)
            }
            else -> BleInspection.Running
        }
    }

    suspend fun close(session: BleSession, auth: ShowerAuth): BleInspection {
        require(session.accountPhone.isBlank() || session.accountPhone == auth.phone) { "请使用开水时的账户" }
        connect(session.mac)?.let { return BleInspection.Pending(it) }
        val before = ble.query() ?: return BleInspection.Pending("设备未响应，未发送关水指令")
        if (before.state == 0) return BleInspection.Settled
        if (before.state == 3 || before.state == 6) {
            val error = settle(before, auth)
            return if (error == null) BleInspection.Settled else BleInspection.Pending(error)
        }
        if (!ble.closeValve()) return BleInspection.Pending("关水未确认，请检查水流并重试")
        // LinYu observed that collecting on the original connection can produce an invalid bill.
        ble.disconnect()
        var error = connect(session.mac)
        if (error != null) { delay(500); error = connect(session.mac) }
        if (error != null) return BleInspection.Pending("水已关闭，但结算未完成；靠近设备后点击重试")
        val after = ble.query() ?: return BleInspection.Pending("水已关闭，但结算状态未确认")
        val settleError = settle(after, auth)
        if (settleError != null) {
            runCatching { withContext(Dispatchers.IO) { api.bleFail(auth, session.consumeDate) } }
            return BleInspection.Pending("水已关闭，但结算未完成：$settleError")
        }
        return BleInspection.Settled
    }

    private suspend fun settle(state: DeviceQueryState, auth: ShowerAuth): String? {
        val payload = ble.collect()?.takeIf { it.isNotEmpty() } ?: return "未能读取设备消费数据"
        val upload = try { withContext(Dispatchers.IO) { api.bleUpload(auth, state, payload) } }
            catch (e: CancellationException) { throw e }
            catch (e: ShowerSessionExpired) { throw e }
            catch (e: Exception) { return e.message ?: "结算上传失败" }
        val plain = KlcxkjCrypto.decryptByAes(upload.clData)
        val back = plain?.substringAfter('-', "")?.let(BleFrame::fromHex)?.takeIf { it.isNotEmpty() }
            ?: return "结算已上传，但写回数据无效，请重试"
        return if (ble.writeBack(back)) null else "结算已上传，但设备写回失败，请重试"
    }
}
