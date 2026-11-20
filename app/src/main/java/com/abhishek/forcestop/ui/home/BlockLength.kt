package com.abhishek.forcestop.ui.home

import android.content.Context
import android.text.format.DateUtils
import com.abhishek.forcestop.util.formatDuration
import com.abhishek.forcestop.util.formatTime
import java.util.Calendar

sealed interface BlockLength {

    fun endAt(now: Long): Long

    fun describe(context: Context, now: Long): String

    data class For(val minutes: Int) : BlockLength {
        override fun endAt(now: Long): Long = now + minutes * 60_000L

        override fun describe(context: Context, now: Long): String = "for ${formatDuration(minutes)}"
    }

    data class Until(val hour: Int, val minute: Int) : BlockLength {
        override fun endAt(now: Long): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }

        override fun describe(context: Context, now: Long): String {
            val endAt = endAt(now)
            val day = if (DateUtils.isToday(endAt)) "" else " tomorrow"
            return "until ${formatTime(context, endAt)}$day"
        }
    }
}
