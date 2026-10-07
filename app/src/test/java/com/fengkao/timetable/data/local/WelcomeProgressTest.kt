package com.fengkao.timetable.data.local

import org.junit.Assert.*
import org.junit.Test

class WelcomeProgressTest {
    @Test fun freshInstallStartsAtIntroduction() {
        assertEquals(WelcomeProgress(false, 0), WelcomeProgress.resolve(false, false, 0, false))
    }

    @Test fun upgradeWithExistingAccountBypassesWelcome() {
        assertTrue(WelcomeProgress.resolve(false, false, 0, true).completed)
    }

    @Test fun accountSavedDuringGuideDoesNotCompleteItOnRestart() {
        assertEquals(WelcomeProgress(false, 2), WelcomeProgress.resolve(true, false, 2, true))
    }

    @Test fun completedGuideStaysCompletedAfterLogout() {
        assertTrue(WelcomeProgress.resolve(true, true, 4, false).completed)
    }

    @Test fun restoredStepIsClampedToValidPages() {
        assertEquals(0, WelcomeProgress.resolve(true, false, -8, false).step)
        assertEquals(WelcomeProgress.LAST_STEP, WelcomeProgress.resolve(true, false, 90, false).step)
    }
}
