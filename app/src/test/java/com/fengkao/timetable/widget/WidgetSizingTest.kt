package com.fengkao.timetable.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetSizingTest {
    @Test fun portraitUsesPortraitWidthDespiteWideLandscapeRange() {
        val size = WidgetSizing.resolve(146, 310, 130, 275, false, false)
        assertEquals(146, size.width)
        assertEquals(275, size.height)
        assertFalse(size.medium)
    }
    @Test fun landscapeUsesLandscapeWidthAndHeight() {
        val size = WidgetSizing.resolve(146, 310, 130, 275, true, false)
        assertEquals(310, size.width)
        assertEquals(130, size.height)
        assertTrue(size.medium)
    }
    @Test fun missingOptionsUseProviderDefaults() {
        assertFalse(WidgetSizing.resolve(0, 0, 0, 0, false, false).medium)
        assertTrue(WidgetSizing.resolve(0, 0, 0, 0, false, true).medium)
    }
    @Test fun tallLauncherCardsHaveBoundedHeight() {
        assertEquals(168, WidgetSizing.cardHeight(146, 275, false))
        assertEquals(180, WidgetSizing.cardHeight(360, 300, true))
    }
    @Test fun ordinaryResizingAndUnknownHeightRemainUnrestricted() {
        assertNull(WidgetSizing.cardHeight(180, 180, false))
        assertNull(WidgetSizing.cardHeight(360, 200, true))
        assertNull(WidgetSizing.cardHeight(360, 0, true))
    }
}
