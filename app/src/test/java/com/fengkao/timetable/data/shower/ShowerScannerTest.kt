package com.fengkao.timetable.data.shower

import org.junit.Assert.*
import org.junit.Test

class ShowerScannerTest {
    @Test fun onlyRecognizesSupportedBroadcasts() {
        assertTrue(isShowerBroadcast("KLCXKJ-Water"))
        assertTrue(isShowerBroadcast("klcxkj-water-123"))
        assertFalse(isShowerBroadcast("Headphones"))
        assertFalse(isShowerBroadcast(""))
    }
    @Test fun scannedMacCanUseExistingBindingParser() {
        assertEquals("AABBCCDDEEFF", ShowerApi.deviceCode("AA:BB:CC:DD:EE:FF"))
    }
}
