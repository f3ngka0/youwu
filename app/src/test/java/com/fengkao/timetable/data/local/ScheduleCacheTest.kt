package com.fengkao.timetable.data.local

import com.fengkao.timetable.data.TermSpec
import com.fengkao.timetable.data.model.CachedSchedule
import com.fengkao.timetable.data.model.CourseBlock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.Json
import java.io.File

class ScheduleCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private fun schedule(year: Int, term: Int, name: String) = CachedSchedule(
        xnm = year, term = term, fetchedAt = 123L,
        courses = listOf(CourseBlock(name, "", "", "", 1, 1, 2, listOf(1), "1周")))

    @Test fun oldTermIsAvailableForBrowsingButCannotReplaceLatest() {
        val cache = ScheduleCache(folder.root)
        val current = schedule(2026, 1, "新学期")
        val previous = schedule(2025, 2, "旧学期")
        assertTrue(cache.save(current))
        assertFalse(cache.save(previous))
        assertEquals(current, cache.load())
        assertEquals(previous, cache.load(TermSpec(2025, 2)))
        assertEquals(current, ScheduleCache(folder.root).load())
    }

    @Test fun legacySingleFileIsReadAndMigratedOnNextSync() {
        val legacy = schedule(2025, 2, "已缓存")
        File(folder.root, "schedule_cache.json").writeText(Json.encodeToString(CachedSchedule.serializer(), legacy))
        val cache = ScheduleCache(folder.root)
        assertEquals(legacy, cache.load(TermSpec(2025, 2)))
        val current = schedule(2026, 1, "新学期")
        assertTrue(cache.save(current))
        assertEquals(legacy, cache.load(TermSpec(2025, 2)))
        assertEquals(current, cache.load())
    }
}
