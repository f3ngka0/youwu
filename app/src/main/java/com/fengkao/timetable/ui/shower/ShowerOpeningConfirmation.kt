package com.fengkao.timetable.ui.shower

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Poll only observations, bounded in time. Cancellation never becomes a successful opening. */
internal suspend fun awaitShowerOpening(timeoutMs: Long = 5_000, pollMs: Long = 1_000,
    probe: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeoutMs) {
    while (!probe()) delay(pollMs)
    true
} == true
