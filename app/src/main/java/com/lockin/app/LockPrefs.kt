package com.lockin.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import java.time.ZoneId
import java.time.ZonedDateTime

object LockPrefs {
    private const val FILE = "lockin_settings"
    private const val EXEMPT = "exempt_packages"
    private const val OVERRIDE_PREFIX = "override_"
    private const val WAIT_PREFIX = "wait_"
    private const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun userExemptPackages(context: Context): MutableSet<String> {
        val stored = prefs(context).getStringSet(EXEMPT, null)
        if (stored != null) return stored.toMutableSet()
        val defaults = defaultUserExemptions(context)
        saveExemptPackages(context, defaults)
        return defaults
    }

    fun saveExemptPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(EXEMPT, packages.toSet()).apply()
    }

    /**
     * System-critical packages that must never be blocked so the user is never locked out
     * of the home screen, settings, system UI, or emergency/phone calls.
     */
    fun systemExemptPackages(context: Context): Set<String> {
        val critical = mutableSetOf(
            context.packageName,
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.emergency",
            "com.google.android.apps.safetyhub"
        )
        // Active and installed Home Launchers
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let(critical::add)
        }
        runCatching {
            context.packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .forEach(critical::add)
        }
        // Active Settings handler
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        runCatching {
            context.packageManager.resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let(critical::add)
        }
        // Default Dialer
        runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.defaultDialerPackage?.let(critical::add)
        }
        return critical
    }

    /**
     * Combined set of user-configured exemptions and mandatory system exemptions.
     */
    fun allExemptPackages(context: Context): Set<String> {
        return userExemptPackages(context) + systemExemptPackages(context)
    }

    /**
     * Default user-visible exemptions: Phone, Messages, Contacts, WhatsApp, and ChatGPT.
     */
    fun defaultUserExemptions(context: Context): MutableSet<String> {
        val result = mutableSetOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.openai.chatgpt",
            // Common Contacts packages across AOSP, Google, Samsung, Vivo/iQOO, Xiaomi, OnePlus/OPPO
            "com.google.android.contacts",
            "com.android.contacts",
            "com.samsung.android.app.contacts",
            "com.vivo.contacts",
            "com.bbk.contacts",
            "com.coloros.contacts",
            "com.oneplus.contacts",
            // Common Messages packages
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.vivo.message",
            // Common Phone/Dialer packages
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.vivo.dialer"
        )
        // Dynamically resolve default Dialer
        runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.defaultDialerPackage?.let(result::add)
        }
        // Dynamically resolve default SMS app
        runCatching {
            Telephony.Sms.getDefaultSmsPackage(context)?.let(result::add)
        }
        // Dynamically resolve Contacts handlers via multiple intents (avoiding android system resolver)
        val contactIntents = listOf(
            Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI),
            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        )
        for (intent in contactIntents) {
            runCatching {
                context.packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .filter { it != "android" && it != "com.android.internal.app" }
                    .forEach(result::add)
            }
        }
        // Keep only packages actually installed on this device (plus core targets) so the list stays clean
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = runCatching {
            context.packageManager.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        if (installed.isNotEmpty()) {
            result.retainAll(installed + setOf("com.whatsapp", "com.openai.chatgpt"))
        }
        return result
    }

    fun dailyLimitMs(): Long = SIX_HOURS_MS

    fun overrideUntil(context: Context, packageName: String): Long =
        prefs(context).getLong(OVERRIDE_PREFIX + packageName, 0L)

    fun grantOverride(context: Context, packageName: String, durationMs: Long = 10L * 60L * 1000L) {
        prefs(context).edit().putLong(OVERRIDE_PREFIX + packageName, System.currentTimeMillis() + durationMs).apply()
    }

    fun waitStartedAt(context: Context, packageName: String): Long =
        prefs(context).getLong(WAIT_PREFIX + packageName, 0L)

    fun startWait(context: Context, packageName: String): Long {
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(WAIT_PREFIX + packageName, now).apply()
        return now
    }

    fun clearWait(context: Context, packageName: String) {
        prefs(context).edit().remove(WAIT_PREFIX + packageName).apply()
    }

    fun isHardBlocked(context: Context, packageName: String): Boolean {
        val knownTargets = setOf(
            "com.netflix.mediaclient",
            "in.startv.hotstar",
            "in.hotstar.dplus",
            "com.hotstar"
        )
        val labelMatches = runCatching {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            val label = context.packageManager.getApplicationLabel(app).toString().lowercase()
            label == "netflix" || label == "hotstar" || label.contains("disney+ hotstar")
        }.getOrDefault(false)
        if (packageName !in knownTargets && !labelMatches) return false
        // India Standard Time (Asia/Kolkata): lock expires at 00:00 on 2 December 2026.
        val unlock = ZonedDateTime.of(2026, 12, 2, 0, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
            .toInstant().toEpochMilli()
        return System.currentTimeMillis() < unlock
    }
}
