package com.fengkao.timetable.data.shower

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class NearbyShower(val name: String, val mac: String, val rssi: Int)

/** Finds nearby water meters for binding; valve control uses a separate GATT connection. */
class ShowerScanner(private val context: Context) {
    fun scan() = callbackFlow {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        check(permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) { "请允许蓝牙扫描权限" }
        if (Build.VERSION.SDK_INT < 31) {
            val location = context.getSystemService(LocationManager::class.java)
            check(location.isLocationEnabled) { "扫描附近设备需要开启系统定位，设备绑定不会记录位置" }
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        check(adapter != null) { "此设备不支持蓝牙，可扫码或手动绑定" }
        check(adapter.isEnabled) { "请先开启蓝牙，再扫描附近设备" }
        val scanner = adapter.bluetoothLeScanner ?: error("蓝牙扫描暂不可用")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                    if (isShowerBroadcast(name)) trySend(NearbyShower(name, result.device.address, result.rssi))
                } catch (e: SecurityException) { close(e) }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("蓝牙扫描失败（$errorCode），请稍后重试")) }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}

internal fun isShowerBroadcast(name: String) = name.contains("KLCXKJ-Water", ignoreCase = true)
