package com.fengkao.timetable.data.shower

import org.junit.Assert.*
import org.junit.Test

class ShowerAccountNotesTest {
    @Test fun blankNotesRestoreMaskedPhone() {
        assertEquals("138****1234", showerAccountDisplayName("13800001234", "  "))
        assertEquals("138****1234", showerAccountDisplayName("13800001234", null))
    }
    @Test fun notesShowWithoutChangingAccountIdentity() {
        assertEquals("宿舍账户", showerAccountDisplayName("13800001234", "  宿舍账户  "))
        assertEquals("备用账户", showerAccountDisplayName("13900005678", "备用账户"))
    }
    @Test fun controlCharactersAreRemovedAndLengthIsBounded() {
        assertEquals("宿舍账户", normalizeShowerAccountNote("宿舍\n账户\t"))
        assertEquals(24, normalizeShowerAccountNote("账".repeat(30)).length)
    }
}
