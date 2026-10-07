package com.fengkao.timetable.ui.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileNavigationTest {
    @Test fun nestedBackReturnsToActualCaller() {
        val root = listOf(ProfilePage.HOME.name)
        val sync = pushProfilePage(root, ProfilePage.SYNC)
        val settings = pushProfilePage(sync, ProfilePage.TEACHING_SYNC)
        assertEquals(sync, popProfilePage(settings))
        val advanced = pushProfilePage(pushProfilePage(root, ProfilePage.APP), ProfilePage.ADVANCED)
        assertEquals(advanced, popProfilePage(pushProfilePage(advanced, ProfilePage.TEACHING_SYNC)))
    }
    @Test fun repeatedTapDoesNotAddDuplicatePage() {
        val stack = listOf(ProfilePage.HOME.name, ProfilePage.ACCOUNT.name)
        assertEquals(stack, pushProfilePage(stack, ProfilePage.ACCOUNT))
    }
    @Test fun backAtRootKeepsRoot() { assertEquals(listOf("HOME"), popProfilePage(listOf("HOME"))) }
    @Test fun savedRouteNamesRestoreAllDestinations() {
        ProfilePage.entries.forEach { page -> assertEquals(page, ProfilePage.valueOf(page.name)) }
    }
    @Test fun studentAndPhoneAccountsAreMasked() {
        assertEquals("2025****2208", maskAccount("202509512208"))
        assertEquals("138****8000", maskAccount("13800138000"))
        assertEquals("ab****ef", maskAccount("abcdef"))
        assertEquals("****", maskAccount("1234"))
        assertEquals("", maskAccount(""))
    }
}
