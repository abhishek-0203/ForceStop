package com.abhishek.forcestop.target

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.abhishek.forcestop.BuildConfig

data class TargetApp(val packageName: String, val label: String) {

    fun isInstalled(context: Context): Boolean = appInfo(context) != null

    /** True when the app is in Android's stopped state, i.e. it cannot receive FCM pushes or broadcasts. */
    fun isStopped(context: Context): Boolean? =
        appInfo(context)?.let { it.flags and ApplicationInfo.FLAG_STOPPED != 0 }

    fun isSuspended(context: Context): Boolean? =
        appInfo(context)?.let { it.flags and ApplicationInfo.FLAG_SUSPENDED != 0 }

    fun isDisabled(context: Context): Boolean? = appInfo(context)?.let { !it.enabled }

    /** Disabled, suspended or stopped: it can't ring or show messages. */
    fun isBlocked(context: Context): Boolean? {
        val info = appInfo(context) ?: return null
        return !info.enabled ||
            info.flags and ApplicationInfo.FLAG_SUSPENDED != 0 ||
            info.flags and ApplicationInfo.FLAG_STOPPED != 0
    }

    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    /** Disabled packages are only visible with MATCH_DISABLED_COMPONENTS, and they are disabled mid-block. */
    private fun appInfo(context: Context): ApplicationInfo? = try {
        context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        val WHATSAPP = TargetApp("com.whatsapp", "WhatsApp")
        val WHATSAPP_BUSINESS = TargetApp("com.whatsapp.w4b", "WhatsApp Business")

        /** Debug builds add a harmless app so the flow can be tested without touching WhatsApp. */
        val all: List<TargetApp> = buildList {
            add(WHATSAPP)
            add(WHATSAPP_BUSINESS)
            if (BuildConfig.TEST_TARGET_PACKAGE.isNotEmpty()) {
                add(TargetApp(BuildConfig.TEST_TARGET_PACKAGE, BuildConfig.TEST_TARGET_LABEL))
            }
        }

        fun installed(context: Context): List<TargetApp> = all.filter { it.isInstalled(context) }

        fun fromPackage(packageName: String?): TargetApp? = all.firstOrNull { it.packageName == packageName }
    }
}
