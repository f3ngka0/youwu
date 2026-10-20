package com.fengkao.timetable.data.shower.ble

/** Fields used by the rate request, extracted from a successful 0x23 payload. */
data class DeviceQueryState(
    /** Uppercase protocol byte for the long layout; absent in the 28-byte layout. */
    val protocolType: String,
    /** Device challenge as hex, preserving its two- or four-byte width. */
    val randomNumber: String,
    /** Usage state (onCharge), distinct from the device's fault/status field. */
    val state: Int,
    /** Device main/sub types used to construct macType. */
    val devType: Int,
    val a1: Int
)

internal object DeviceQueryParser {
    /** KRWMCore.krGetStateOfDevice uses a single zero byte, not an empty payload. */
    fun requestData(): ByteArray = byteArrayOf(0)

    fun parse(payload: ByteArray): DeviceQueryState? {
        if (payload.isEmpty() || payload[0].toInt() and 0xFF != 0x80) return null
        return when {
            // KRStateObject's short layout, also present in AshWashing.AnalyTools.
            // Only the complete 28-byte variant has been captured on this meter.
            payload.size == 28 -> DeviceQueryState(
                protocolType = "",
                randomNumber = BleFrame.toHex(payload.copyOfRange(21, 23)),
                state = payload[19].toInt() and 0xFF,
                devType = payload[23].toInt() and 0xFF,
                a1 = payload[24].toInt() and 0xFF
            )
            // Preserve the layout already supported by the app.
            payload.size >= 31 -> DeviceQueryState(
                protocolType = String.format("%02X", payload[19].toInt() and 0xFF),
                randomNumber = BleFrame.toHex(payload.copyOfRange(24, 28)),
                state = payload[28].toInt() and 0xFF,
                devType = payload[29].toInt() and 0xFF,
                a1 = payload[30].toInt() and 0xFF
            )
            else -> null
        }
    }
}
