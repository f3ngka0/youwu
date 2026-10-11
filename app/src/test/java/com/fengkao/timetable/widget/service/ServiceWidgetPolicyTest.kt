package com.fengkao.timetable.widget.service

import com.fengkao.timetable.widget.ServiceOperationGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServiceWidgetPolicyTest {
    @Test fun staleStartCannotOpenAnActiveValve() {
        assertFalse(widgetOperationAllowed(WidgetOperation.START, true, true, false))
        assertFalse(widgetOperationAllowed(WidgetOperation.START, true, false, false))
    }
    @Test fun pendingControlOnlyAllowsReadOnlyCheck() {
        assertFalse(widgetOperationAllowed(WidgetOperation.START, false, false, true))
        assertFalse(widgetOperationAllowed(WidgetOperation.STOP, true, true, true))
        assertTrue(widgetOperationAllowed(WidgetOperation.CHECK, true, true, true))
    }
    @Test fun staleStopCannotStartOrControlSomeoneElsesValve() {
        assertFalse(widgetOperationAllowed(WidgetOperation.STOP, false, false, false))
        assertFalse(widgetOperationAllowed(WidgetOperation.STOP, true, false, false))
        assertTrue(widgetOperationAllowed(WidgetOperation.STOP, true, true, false))
    }
    @Test fun confirmedIdleTargetCanOpen() {
        assertTrue(widgetOperationAllowed(WidgetOperation.START, false, false, false))
    }
    @Test fun concurrentEntriesAreRejectedInsteadOfQueued() = runBlocking {
        assertTrue(ServiceOperationGate.shower.tryLock())
        try {
            assertFalse(ServiceOperationGate.shower.tryLock())
            assertTrue(ServiceOperationGate.water.tryLock())
            ServiceOperationGate.water.unlock()
        } finally { ServiceOperationGate.shower.unlock() }
        assertTrue(ServiceOperationGate.shower.tryLock())
        ServiceOperationGate.shower.unlock()
    }
    @Test fun widgetCannotSwitchPendingTransactionAccountOrDevice() {
        assertTrue(widgetMatchesPendingTarget("account-a", "meter-a", "account-a", "meter-a", "meter-b"))
        assertFalse(widgetMatchesPendingTarget("account-b", "meter-a", "account-a", "meter-a", "meter-a"))
        assertFalse(widgetMatchesPendingTarget("account-a", "meter-b", "account-a", "meter-a", "meter-b"))
    }
    @Test fun legacyPendingTransactionUsesPersistedSelectedDevice() {
        assertTrue(widgetMatchesPendingTarget("account-a", "meter-a", "account-a", null, "meter-a"))
        assertFalse(widgetMatchesPendingTarget("account-a", "meter-b", "account-a", null, "meter-a"))
    }
}
