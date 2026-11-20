package com.abhishek.forcestop.block

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TimeWindow(val start: Long, val end: Long) {
    operator fun contains(time: Long): Boolean = time in start until end
}

/**
 * A weekly repeating block. [days] are the days a window *starts* on, so "Mon–Fri 22:00–07:00"
 * blocks Monday night through Saturday morning.
 */
data class Schedule(
    val id: String,
    val packageName: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY)
    }

    /** Equal start and end means a full 24 hours. */
    val durationMinutes: Int
        get() = ((endMinute - startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY).takeIf { it != 0 } ?: MINUTES_PER_DAY

    fun windowStartingOn(date: LocalDate, zone: ZoneId): TimeWindow? {
        if (date.dayOfWeek !in days) return null
        val start = date.atTime(LocalTime.of(startMinute / 60, startMinute % 60)).atZone(zone)
        return TimeWindow(start.toInstant().toEpochMilli(), start.plusMinutes(durationMinutes.toLong()).toInstant().toEpochMilli())
    }

    fun activeWindow(now: Long, zone: ZoneId): TimeWindow? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today)
            .mapNotNull { windowStartingOn(it, zone) }
            .firstOrNull { now in it }
    }

    fun nextStart(now: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (0L..7L)
            .mapNotNull { windowStartingOn(today.plusDays(it), zone)?.start }
            .firstOrNull { it > now }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
