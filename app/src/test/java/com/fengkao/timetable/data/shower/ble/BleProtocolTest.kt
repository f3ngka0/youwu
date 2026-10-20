package com.fengkao.timetable.data.shower.ble

import org.junit.Assert.*
import org.junit.Test

class BleProtocolTest {
    @Test fun deviceCommandsMatchSdkAsciiHexVectors() {
        val vectors = mapOf(
            0x23 to "#60000480230000A316\n",
            0x22 to "#60000480220000A216\n",
            0x85 to "#600004808500000516\n"
        )
        for ((cmd, expected) in vectors) {
            val data = if (cmd == 0x23) DeviceQueryParser.requestData() else byteArrayOf(0)
            val wire = BleFrame.cmdToWire(cmd, data)
            assertEquals(expected, String(wire, Charsets.US_ASCII))
            assertEquals(cmd, BleFrame.cmdOf(BleFrame.parseWire(wire)!!))
            wire[wire.lastIndex - 2] = '0'.code.toByte()
            assertNull(BleFrame.parseWire(wire))
        }
    }

    @Test fun rateSignatureUsesDeviceRandomInLowercase() {
        assertEquals("fbfc09eda7024f753917e058194626be",
            BleSignBuilder.signRateOrder("session", "13800138000", "123", "0", "A1B2C3D4"))
        assertEquals("2001", BleSignBuilder.macType(0x20, 0x01))
    }
}
