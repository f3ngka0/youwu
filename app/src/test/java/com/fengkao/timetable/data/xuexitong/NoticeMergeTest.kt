package com.fengkao.timetable.data.xuexitong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeMergeTest {

    private fun notice(id: String, time: Long, unread: Boolean = true) = XuexitongNotice(
        id = id,
        title = id,
        timeMillis = time,
        serverUnread = unread,
    )

    @Test
    fun firstBaselineNeverPushesHistoricalUnread() {
        val result = mergeNotices(
            fetched = listOf(notice("a", 300), notice("b", 200)),
            old = emptyList(),
            locallyRead = emptySet(),
            firstBaseline = true,
        )
        assertTrue(result.newUnread.isEmpty())
        assertEquals(2, result.unreadCount)
        assertEquals(listOf("a", "b"), result.notices.map { it.id })
    }

    @Test
    fun subsequentFetchOnlyPushesNewUnread() {
        val result = mergeNotices(
            fetched = listOf(notice("c", 400), notice("b", 200), notice("a", 100, unread = false)),
            old = listOf(notice("b", 200), notice("a", 100, unread = false)),
            locallyRead = emptySet(),
            firstBaseline = false,
        )
        assertEquals(listOf("c"), result.newUnread.map { it.id })
        assertEquals(2, result.unreadCount)
    }

    @Test
    fun locallyReadIsNotCountedOrPushedAgain() {
        val result = mergeNotices(
            fetched = listOf(notice("c", 400), notice("b", 200)),
            old = listOf(notice("b", 200)),
            locallyRead = setOf("b"),
            firstBaseline = false,
        )
        assertEquals(listOf("c"), result.newUnread.map { it.id })
        assertEquals(1, result.unreadCount)
    }

    @Test
    fun oldHistorySurvivesWhenServerReturnsOnlyFirstPage() {
        val result = mergeNotices(
            fetched = listOf(notice("new", 500)),
            old = listOf(notice("old", 100)),
            locallyRead = emptySet(),
            firstBaseline = false,
        )
        assertEquals(listOf("new", "old"), result.notices.map { it.id })
    }

    @Test
    fun mergeIsSortedByTimeDescending() {
        val result = mergeNotices(
            fetched = listOf(notice("old", 100), notice("new", 300)),
            old = listOf(notice("mid", 200)),
            locallyRead = emptySet(),
            firstBaseline = true,
        )
        assertEquals(listOf("new", "mid", "old"), result.notices.map { it.id })
    }
}
