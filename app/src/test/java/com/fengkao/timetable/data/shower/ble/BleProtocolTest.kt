package com.fengkao.timetable.data.shower.ble

import org.junit.Assert.*
import org.junit.Test

class BleProtocolTest {
    @Test fun queryCommandUsesAsciiHexWireFraming() {
        val wire = BleFrame.cmdToWire(0x23)
        assertEquals("#600003802300A316\n", String(wire, Charsets.US_ASCII))
        assertEquals(0x23, BleFrame.cmdOf(BleFrame.parseWire(wire)!!))
        wire[wire.lastIndex - 2] = '0'.code.toByte()
        assertNull(BleFrame.parseWire(wire))
    }

    @Test fun rateSignatureUsesDeviceRandomInLowercase() {
        assertEquals("fbfc09eda7024f753917e058194626be",
            BleSignBuilder.signRateOrder("session", "13800138000", "123", "0", "A1B2C3D4"))
        assertEquals("2001", BleSignBuilder.macType(0x20, 0x01))
    }
}
