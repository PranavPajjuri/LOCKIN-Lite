package com.lockin.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {
    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val DEBOUNCE_MS = 1_200L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastLaunchAt: Long = 0L
    private var isPolling = false

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            runCatching { evaluateCurrentForeground() }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) {
            // BlockActivity or MainActivity is visible; update state without re-blocking
            currentForegroundPackage = packageName
            return
        }
        if (packageName == "com.android.systemui") return

        currentForegroundPackage = packageName
        evaluatePackage(packageName, isPeriodicPoll = false)
    }

    private fun evaluateCurrentForeground() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return

        // Prefer active window root package, fall back to last observed package or UsageEvents
        val activePkg = rootInActiveWindow?.packageName?.toString()
            ?: currentForegroundPackage
            ?: UsageCalculator.currentForegroundPackage(this)
            ?: return

        if (activePkg == this.packageName || activePkg == "com.android.systemui") return
        currentForegroundPackage = activePkg
        evaluatePackage(activePkg, isPeriodicPoll = true)
    }

    private fun evaluatePackage(packageName: String, isPeriodicPoll: Boolean) {
        // 1. Date-based hard locks (Netflix/Hotstar) always take absolute priority
        val hardBlocked = LockPrefs.isHardBlocked(this, packageName)
        if (!hardBlocked) {
            // 2. Never block system-critical apps (Launcher, Settings, SystemUI, Phone/Emergency) or user exemptions
            if (packageName in LockPrefs.allExemptPackages(this)) return

            // 3. Check if 6-hour daily non-exempt limit has been reached
            val overDailyLimit = runCatching {
                UsageCalculator.nonExemptUsageToday(this) >= LockPrefs.dailyLimitMs()
            }.getOrDefault(false)
            if (!overDailyLimit) return

            // 4. Check if a 10-minute override is currently active
            if (LockPrefs.overrideUntil(this, packageName) > System.currentTimeMillis()) return
        }

        // 5. Debounce rapid duplicate window events (allow periodic poll to re-assert immediately)
        val now = System.currentTimeMillis()
        if (!isPeriodicPoll && lastBlockedPackage == packageName && (now - lastLaunchAt) < DEBOUNCE_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastLaunchAt = now
        startActivity(Intent(this, BlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockActivity.EXTRA_PACKAGE, packageName)
            putExtra(BlockActivity.EXTRA_HARD_BLOCK, hardBlocked)
        })
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        handler.removeCallbacks(periodicCheckRunnable)
        handler.postDelayed(periodicCheckRunnable, POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        isPolling = false
        handler.removeCallbacks(periodicCheckRunnable)
    }

    override fun onInterrupt() {
        stopPolling()
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }
}
