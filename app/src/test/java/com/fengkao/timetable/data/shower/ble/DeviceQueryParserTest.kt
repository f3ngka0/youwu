package com.fengkao.timetable.data.shower.ble

import org.junit.Assert.*
import org.junit.Test

class DeviceQueryParserTest {
    @Test fun completeShortLayoutUsesUsageStateAndTwoByteChallenge() {
        val payload = ByteArray(28).apply {
            this[0] = 0x80.toByte()
            this[19] = 1
            this[20] = 0xA5.toByte() // Fault/status is not the usage state.
            this[21] = 0
            this[22] = 7
            this[23] = 0x80.toByte()
            this[24] = 0xFE.toByte()
            this[25] = 0x11 // Charging method is not the sub type.
        }
        assertEquals(DeviceQueryState("", "0007", 1, 0x80, 0xFE), DeviceQueryParser.parse(payload))
    }

    @Test fun longLayoutKeepsFourByteChallengeAndExistingOffsets() {
        for (length in listOf(31, 48)) {
            val payload = ByteArray(length).apply {
                this[0] = 0x80.toByte()
                this[19] = 0x20
                this[24] = 0
                this[25] = 0xA1.toByte()
                this[26] = 0xB2.toByte()
                this[27] = 0xC3.toByte()
                this[28] = 6
                this[29] = 0x80.toByte()
                this[30] = 1
            }
            assertEquals(DeviceQueryState("20", "00A1B2C3", 6, 0x80, 1), DeviceQueryParser.parse(payload))
        }
    }

    @Test fun unsupportedLengthsAndDeviceErrorRepliesAreRejected() {
        for (length in listOf(0, 1, 2, 23, 27, 29, 30)) {
            val payload = ByteArray(length).apply { if (isNotEmpty()) this[0] = 0x80.toByte() }
            assertNull("length=$length", DeviceQueryParser.parse(payload))
        }
        for (length in listOf(28, 31, 48)) {
            assertNull(DeviceQueryParser.parse(ByteArray(length)))
        }
    }
}
