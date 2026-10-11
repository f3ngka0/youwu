package com.fengkao.timetable.ui.shower

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ShowerOpeningConfirmationTest {
    @Test fun delayedSignalIsAcceptedWithinGracePeriod() = runBlocking {
        var reads = 0
        assertTrue(awaitShowerOpening(timeoutMs = 500, pollMs = 10) { ++reads >= 3 })
        assertEquals(3, reads)
    }
    @Test fun missingSignalTimesOutWithoutSuccess() = runBlocking {
        var reads = 0
        assertFalse(awaitShowerOpening(timeoutMs = 80, pollMs = 10) { reads++; false })
        assertTrue(reads >= 2)
    }
    @Test fun cancellationDoesNotBecomeAnOpening() = runBlocking {
        val request = async { awaitShowerOpening(5_000, 10) { delay(100); false } }
        request.cancel()
        try { request.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    @Test fun aSlowProbeRemainsBounded() = runBlocking {
        assertFalse(awaitShowerOpening(timeoutMs = 30, pollMs = 10) { delay(500); true })
    }
}
