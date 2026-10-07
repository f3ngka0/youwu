package com.fengkao.timetable.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.fengkao.timetable.data.local.AppSettings

class TermCalculatorTest {
    @Test fun autumnRolloverDoesNotReusePreviousSpringTerm() {
        val spring = TermSpec(2025, 2)
        val autumn = TermCalculator.autoTerm(LocalDate.of(2026, 10, 7))
        assertEquals(TermSpec(2026, 1), autumn)
        assertFalse(TermCalculator.shouldReplaceLatest(autumn, spring))
        assertTrue(TermCalculator.shouldReplaceLatest(spring, autumn))
    }

    @Test fun sameTermRefreshCanReplaceItsOwnCache() {
        assertTrue(TermCalculator.shouldReplaceLatest(TermSpec(2026, 1), TermSpec(2026, 1)))
        assertTrue(TermCalculator.shouldReplaceLatest(null, TermSpec(2026, 1)))
    }

    @Test fun earlySemesterAlreadyCachedBeatsCalendarEstimate() {
        assertEquals(TermSpec(2026, 1),
            TermCalculator.automaticTarget(TermSpec(2025, 2), TermSpec(2026, 1)))
        assertEquals(TermSpec(2026, 1),
            TermCalculator.automaticTarget(TermSpec(2026, 1), TermSpec(2025, 2)))
    }

    @Test fun previousSemesterStartDateCannotShiftCurrentWeek() {
        val previousStart = LocalDate.of(2026, 3, 2).toEpochDay()
        val current = TermSpec(2026, 1)
        val settings = AppSettings(termStartEpochDay = previousStart, termStartXnm = 2025, termStartTerm = 2)
        assertEquals(TermCalculator.defaultTermStart(2026, 1), settings.termStartFor(current))
        val manual = LocalDate.of(2026, 9, 7)
        assertEquals(manual, settings.copy(termStartEpochDay = manual.toEpochDay(),
            termStartXnm = 2026, termStartTerm = 1).termStartFor(current))
    }
}
