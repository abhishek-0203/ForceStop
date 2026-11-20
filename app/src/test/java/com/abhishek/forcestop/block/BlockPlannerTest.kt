package com.abhishek.forcestop.block

import com.abhishek.forcestop.target.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class BlockPlannerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val weeknights = Schedule(
        id = "night",
        packageName = TargetApp.WHATSAPP_BUSINESS.packageName,
        days = weekdays,
        startMinute = 22 * 60,
        endMinute = 7 * 60,
    )

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun overnightWindowCoversLateEveningAndNextMorning() {
        assertEquals(TimeWindow(at(16, 22), at(17, 7)), weeknights.activeWindow(at(16, 23), zone))
        assertEquals(TimeWindow(at(16, 22), at(17, 7)), weeknights.activeWindow(at(17, 6, 59), zone))
        assertNull(weeknights.activeWindow(at(17, 7), zone))
        assertNull(weeknights.activeWindow(at(16, 21, 59), zone))
    }

    @Test
    fun windowBelongsToTheDayItStarts() {
        // Friday night carries into Saturday morning, but Sunday night is not scheduled.
        assertEquals(TimeWindow(at(20, 22), at(21, 7)), weeknights.activeWindow(at(21, 6), zone))
        assertNull(weeknights.activeWindow(at(22, 23), zone))
        assertNull(weeknights.activeWindow(at(16, 6), zone))
    }

    @Test
    fun nextStartSkipsUnscheduledDays() {
        assertEquals(at(16, 22), weeknights.nextStart(at(16, 12), zone))
        assertEquals(at(23, 22), weeknights.nextStart(at(21, 12), zone))
    }

    @Test
    fun equalStartAndEndMeansFullDay() {
        val allDay = weeknights.copy(startMinute = 9 * 60, endMinute = 9 * 60)
        assertEquals(24 * 60, allDay.durationMinutes)
        assertEquals(TimeWindow(at(16, 9), at(17, 9)), allDay.activeWindow(at(17, 8), zone))
    }

    @Test
    fun manualAndScheduledBlocksMergeToLatestEnd() {
        val state = StoredState(
            manual = mapOf(weeknights.packageName to at(17, 8)),
            schedules = listOf(weeknights),
        )
        val block = BlockPlanner.activeBlocks(state, at(16, 23), zone).single()
        assertEquals(TargetApp.WHATSAPP_BUSINESS, block.target)
        assertEquals(at(17, 8), block.endAt)
        assertTrue(block.manual)
        assertEquals(listOf("night"), block.scheduleIds)
    }

    @Test
    fun skippedWindowIsNotActiveButNextOneIs() {
        val state = StoredState(schedules = listOf(weeknights), skips = mapOf("night" to at(17, 7)))
        assertTrue(BlockPlanner.activeBlocks(state, at(16, 23), zone).isEmpty())
        assertEquals(1, BlockPlanner.activeBlocks(state, at(17, 23), zone).size)
    }

    @Test
    fun disabledScheduleIsIgnored() {
        val state = StoredState(schedules = listOf(weeknights.copy(enabled = false)))
        assertTrue(BlockPlanner.activeBlocks(state, at(16, 23), zone).isEmpty())
        assertNull(BlockPlanner.nextTransition(state, at(16, 23), zone))
    }

    @Test
    fun nextTransitionIsEarliestOfManualEndAndScheduleEdges() {
        val state = StoredState(
            manual = mapOf(TargetApp.WHATSAPP.packageName to at(16, 20)),
            schedules = listOf(weeknights),
        )
        assertEquals(at(16, 20), BlockPlanner.nextTransition(state, at(16, 12), zone))
        assertEquals(at(16, 22), BlockPlanner.nextTransition(state, at(16, 21), zone))
        assertEquals(at(17, 7), BlockPlanner.nextTransition(state, at(16, 23), zone))
    }
}
