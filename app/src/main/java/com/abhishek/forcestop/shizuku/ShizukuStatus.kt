package com.abhishek.forcestop.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_RUNNING,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    READY;

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        const val PERMISSION_REQUEST_CODE = 1001

        fun current(): ShizukuStatus = runCatching {
            when {
                !Shizuku.pingBinder() -> NOT_RUNNING
                Shizuku.isPreV11() -> UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> READY
                else -> PERMISSION_REQUIRED
            }
        }.getOrDefault(NOT_RUNNING)

        /** The Shizuku binder arrives asynchronously after process start, so callers in fresh processes must wait. */
        suspend fun awaitBinder(timeoutMs: Long): ShizukuStatus {
            if (Shizuku.pingBinder()) return current()
            val received = CompletableDeferred<Unit>()
            val listener = Shizuku.OnBinderReceivedListener { received.complete(Unit) }
            Shizuku.addBinderReceivedListenerSticky(listener)
            try {
                withTimeoutOrNull(timeoutMs) { received.await() }
            } finally {
                Shizuku.removeBinderReceivedListener(listener)
            }
            return current()
        }

        fun isInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
