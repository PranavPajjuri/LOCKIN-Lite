package com.lockin.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var permissionStatus: TextView
    private lateinit var usageStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 32)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        scroll.addView(content)
        setContentView(scroll)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) {
            updatePermissionStatus()
            updateUsageText()
        }
    }

    private fun buildScreen() {
        content.removeAllViews()
        val title = TextView(this).apply {
            text = "LOCKIN"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF171717.toInt())
        }
        content.addView(title)
        content.addView(label("Your phone. Your rules.", 15f, 0xFF666666.toInt()))
        content.addView(space(20))
        content.addView(label("DAILY LIMIT", 12f, 0xFF777777.toInt(), true))
        content.addView(label("6 hours", 27f, 0xFF171717.toInt(), true))
        usageStatus = label("Checking today's non-exempt app usage…", 14f, 0xFF555555.toInt())
        content.addView(usageStatus)
        content.addView(space(18))

        content.addView(label("REQUIRED PERMISSIONS", 12f, 0xFF777777.toInt(), true))
        permissionStatus = label("", 14f, 0xFF333333.toInt())
        content.addView(permissionStatus)
        content.addView(button("Grant Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        content.addView(button("Enable App Blocking") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(space(22))
        content.addView(label("ALWAYS ALLOWED", 12f, 0xFF777777.toInt(), true))
        content.addView(
            label(
                "Home Launcher, Android Settings, Emergency/Phone, Messages, Contacts, WhatsApp and ChatGPT are exempt by default. Select additional apps below.",
                14f,
                0xFF555555.toInt()
            )
        )
        content.addView(space(8))

        val userExempt = LockPrefs.userExemptPackages(this)
        val systemExempt = LockPrefs.systemExemptPackages(this)
        val apps = packageManager.getInstalledApplications(0)
            .filter {
                packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                    it.packageName !in systemExempt
            }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        apps.forEach { app ->
            val check = CheckBox(this).apply {
                text = packageManager.getApplicationLabel(app)
                textSize = 15f
                isChecked = app.packageName in userExempt
                setPadding(0, 5, 0, 5)
                setOnCheckedChangeListener { _, checked ->
                    val updated = LockPrefs.userExemptPackages(this@MainActivity)
                    if (checked) updated.add(app.packageName) else updated.remove(app.packageName)
                    LockPrefs.saveExemptPackages(this@MainActivity, updated)
                    updateUsageText()
                }
            }
            content.addView(check)
        }
        content.addView(space(16))
        content.addView(label("TEMPORARY OVERRIDE", 12f, 0xFF777777.toInt(), true))
        content.addView(
            label(
                "After the daily limit, wait 60 seconds to unlock one app for 10 minutes. Netflix and Hotstar cannot be overridden before December 2, 2026 (00:00 IST).",
                14f,
                0xFF555555.toInt()
            )
        )
        content.addView(space(12))
        content.addView(label("LOCKIN Lite · Version 0.1.0", 12f, 0xFF999999.toInt()))
        updatePermissionStatus()
        updateUsageText()
    }

    private fun updatePermissionStatus() {
        val usage = UsageCalculator.hasUsageAccess(this)
        val accessibility = isAccessibilityEnabled()
        permissionStatus.text = "Usage Access: ${if (usage) "Enabled" else "Not enabled"}\nApp Blocking: ${if (accessibility) "Enabled" else "Not enabled"}"
    }

    private fun updateUsageText() {
        val used = runCatching { UsageCalculator.nonExemptUsageToday(this) }.getOrDefault(0L)
        val hours = used / 3_600_000
        val minutes = (used % 3_600_000) / 60_000
        usageStatus.text = "Used today: ${hours}h ${minutes}m of 6h (exempt apps excluded)"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${LockAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, true) }
    }

    private fun label(textValue: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 4, 0, 4)
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }
}
