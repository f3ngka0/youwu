package com.fengkao.timetable.data.shower

import com.fengkao.timetable.data.shower.ble.BleSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShowerAccountsTest {
    private val first = ShowerAuth("first-token", "u1", "a1", "p1", "13800001234")
    private val second = ShowerAuth("second-token", "u2", "a2", "p2", "13900005678")
    @Test fun secondarySelectionUsesItsOwnCredentials() {
        assertEquals(second, resolveShowerAccount(first, second, second.phone))
        assertEquals("second-token", resolveShowerAccount(first, second, second.phone)!!.fields()["loginCode"])
    }
    @Test fun runningSecondaryOrderPinsSecondaryRegardlessOfSelection() {
        assertEquals(second, resolveShowerAccount(first, second, first.phone, second.phone))
    }
    @Test fun expiredRunningSecondaryMustNotFallBackToFirst() {
        assertNull(resolveShowerAccount(first, null, first.phone, second.phone))
    }
    @Test fun expiredSelectedAccountRequiresReauthentication() {
        assertNull(resolveShowerAccount(null, second, first.phone))
    }
    @Test fun legacyAccountWithoutSelectionStillResolvesFirst() {
        assertEquals(first, resolveShowerAccount(first, null, ""))
    }
    @Test fun controlIdentitySurvivesSessionSerialization() {
        val session = BleSession("meter", "001122334455", "date", "order", second.phone)
        assertEquals(second.phone, BleSession.from(session.json()).accountPhone)
    }
    @Test fun legacySessionStillLoadsWithoutPhone() {
        val old = JSONObject().put("sn", "meter").put("mac", "001122334455").put("consumeDate", "date").put("orderNo", "order")
        assertEquals("", BleSession.from(old).accountPhone)
    }
}
