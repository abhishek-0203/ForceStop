package com.abhishek.forcestop.util

import android.content.Context
import com.abhishek.forcestop.block.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun formatDays(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKEND -> "Weekends"
    else -> DayOfWeek.entries.filter { it in days }.joinToString { it.shortName() }
}

fun formatMinuteOfDay(context: Context, minute: Int): String {
    val millis = LocalDate.now()
        .atTime(minute / 60, minute % 60)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    return formatTime(context, millis)
}

fun describeSchedule(context: Context, schedule: Schedule): String {
    val overnight = schedule.endMinute <= schedule.startMinute
    val end = formatMinuteOfDay(context, schedule.endMinute) + if (overnight) " next day" else ""
    return "${formatDays(schedule.days)} · ${formatMinuteOfDay(context, schedule.startMinute)} – $end"
}
