package com.fengkao.timetable.widget.service

import org.junit.Assert.*
import org.junit.Test

class ServiceWidgetBindingTest {
    @Test fun showerTargetRoundTripsWithoutCredentials() {
        val binding = ServiceWidgetBinding(WidgetService.SHOWER, "meter-02", "13900005678")
        val json = binding.json()
        assertEquals(binding, ServiceWidgetBinding.parse(json.toString()))
        assertEquals(setOf("service", "device", "phone"), json.keys().asSequence().toSet())
    }
    @Test fun waterTargetKeepsAccountIdentityAcrossReload() {
        val binding = ServiceWidgetBinding(WidgetService.WATER, "water-01", "13800001234")
        assertEquals(binding, ServiceWidgetBinding.parse(binding.json().toString()))
    }
    @Test fun incompleteTargetRemainsUnboundInsteadOfChoosingDefault() {
        assertNull(ServiceWidgetBinding.parse("{}"))
        assertNull(ServiceWidgetBinding.parse("""{"service":"SHOWER","device":"meter-01","phone":""}"""))
        assertNull(ServiceWidgetBinding.parse("""{"service":"WATER","device":"","phone":"13800001234"}"""))
    }
    @Test fun corruptOrUnknownServiceCannotControlAnything() {
        assertNull(ServiceWidgetBinding.parse("not-json"))
        assertNull(ServiceWidgetBinding.parse("""{"service":"OTHER","device":"water-01","phone":"13800001234"}"""))
    }
}
