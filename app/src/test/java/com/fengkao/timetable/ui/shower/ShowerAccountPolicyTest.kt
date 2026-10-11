package com.fengkao.timetable.ui.shower

import org.junit.Assert.*
import org.junit.Test

class ShowerAccountPolicyTest {
    @Test fun ownRunningOrderKeepsCredentials() { assertFalse(canLogoutShower(true, true, false, false)) }
    @Test fun unknownControlKeepsCredentials() { assertFalse(canLogoutShower(false, false, true, false)) }
    @Test fun persistedPendingControlKeepsCredentialsAfterRestart() { assertFalse(canLogoutShower(false, false, false, true)) }
    @Test fun idleAccountCanLogout() { assertTrue(canLogoutShower(false, false, false, false)) }
    @Test fun OtherUsersOrderDoesNotPreventLogout() { assertTrue(canLogoutShower(true, false, false, false)) }
    @Test fun accountSwitchRequiresIdleAndConfirmedControl() {
        assertTrue(canChangeShowerAccount(false, false, false, false, false))
        assertFalse(canChangeShowerAccount(true, false, false, false, false))
        assertFalse(canChangeShowerAccount(false, true, false, false, false))
        assertFalse(canChangeShowerAccount(false, false, true, false, false))
        assertFalse(canChangeShowerAccount(false, false, false, true, false))
        assertFalse(canChangeShowerAccount(false, false, false, false, true))
    }
}
