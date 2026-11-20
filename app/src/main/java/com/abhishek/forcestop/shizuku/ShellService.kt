package com.abhishek.forcestop.shizuku

import android.os.Bundle
import kotlin.system.exitProcess

/**
 * Runs inside a process spawned by Shizuku with shell (uid 2000) privileges,
 * not inside the app process. Keep it free of app-level dependencies.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Bundle {
        val (exitCode, output) = try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() to output
        } catch (e: Exception) {
            -1 to (e.message ?: e.toString())
        }
        return Bundle().apply {
            putInt(KEY_EXIT_CODE, exitCode)
            putString(KEY_OUTPUT, output)
        }
    }

    companion object {
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_OUTPUT = "output"
    }
}
