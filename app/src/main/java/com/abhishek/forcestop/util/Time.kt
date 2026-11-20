package com.abhishek.forcestop.util

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

fun formatTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

fun formatRemaining(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds < 60) "$seconds s left" else "${formatDuration(((seconds + 59) / 60).toInt())} left"
}
