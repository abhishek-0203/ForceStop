package com.abhishek.forcestop.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File-backed log so spike results (e.g. stopped state after reboot) survive process death. */
object EventLog {

    private const val TAG = "ForceStop"
    private const val FILE_NAME = "events.log"
    private const val MAX_LINES = 300

    private lateinit var file: File
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        _lines.value = if (file.exists()) file.readLines().takeLast(MAX_LINES) else emptyList()
    }

    @Synchronized
    fun log(message: String) {
        Log.i(TAG, message)
        val updated = (_lines.value + "${timestampFormat.format(Date())}  $message").takeLast(MAX_LINES)
        _lines.value = updated
        file.writeText(updated.joinToString(separator = "\n", postfix = "\n"))
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        file.delete()
    }
}
