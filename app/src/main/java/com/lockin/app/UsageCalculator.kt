package com.lockin.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

object UsageCalculator {
    /**
     * Calculates today's total foreground usage (in milliseconds) for non-exempt launchable apps
     * starting from local midnight (00:00:00.000) up to now, using UsageStatsManager.queryEvents().
     *
     * Handles:
     * - Multiple activities per package (tracks active component count per package to avoid double-counting).
     * - Foreground/background transitions (ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED).
     * - Screen-off / lock events (SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN) which pause
     *   active foreground timers so screen-off time is never counted.
     * - Apps currently open in the foreground at query time (credits elapsed time up to `now` when interactive).
     *
     * Remaining accuracy limitations:
     * 1. If a non-exempt app was already open before 00:00 midnight and remained open across midnight
     *    without a new ACTIVITY_RESUMED event, usage prior to the first event after midnight is not captured
     *    because queryEvents(start, end) only emits events occurring within [start, end).
     * 2. On Android 10+ (API 29+), queryEvents() returns empty while the device is locked (Direct Boot).
     * 3. Split-screen / multi-window sessions with two non-exempt apps visible simultaneously will sum
     *    each visible app's foreground duration.
     */
    fun nonExemptUsageToday(context: Context): Long {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        if (now <= startOfDay) return 0L

        val exempt = LockPrefs.allExemptPackages(context)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = context.packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        val events = manager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        // Track active foreground start timestamp per package (avoids double-counting multi-activity transitions)
        val activeStartByPackage = mutableMapOf<String, Long>()
        var totalNonExemptMs = 0L
        var isScreenInteractive = true

        fun closePackageSession(pkg: String, endTime: Long) {
            val startedAt = activeStartByPackage.remove(pkg) ?: return
            if (endTime > startedAt && pkg in launchablePackages && pkg !in exempt) {
                totalNonExemptMs += (endTime - startedAt)
            }
        }

        fun closeAllActiveSessions(endTime: Long) {
            val keys = activeStartByPackage.keys.toList()
            for (pkg in keys) {
                closePackageSession(pkg, endTime)
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val ts = event.timeStamp.coerceIn(startOfDay, now)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!pkg.isNullOrEmpty() && isScreenInteractive) {
                        // Only record start if not already tracked in foreground (prevents double-counting)
                        if (!activeStartByPackage.containsKey(pkg)) {
                            activeStartByPackage[pkg] = ts
                        }
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (!pkg.isNullOrEmpty()) {
                        closePackageSession(pkg, ts)
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    closeAllActiveSessions(ts)
                    if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                        isScreenInteractive = false
                    }
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    isScreenInteractive = true
                }
            }
        }

        // Credit ongoing foreground session(s) still active right now
        if (isScreenInteractive) {
            closeAllActiveSessions(now)
        }

        return totalNonExemptMs
    }

    /**
     * Determines the current foreground package from recent UsageEvents as a fallback/verification
     * helper for periodic enforcement checks.
     */
    fun currentForegroundPackage(context: Context, lookbackMs: Long = 60_000L): String? {
        return runCatching {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(now - lookbackMs, now)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var screenOff = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        latestPkg = event.packageName
                        screenOff = false
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN -> {
                        screenOff = true
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        screenOff = false
                    }
                }
            }
            if (screenOff) null else latestPkg
        }.getOrNull()
    }

    fun hasUsageAccess(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
