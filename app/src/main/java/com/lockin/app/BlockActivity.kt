package com.lockin.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class BlockActivity : Activity() {
    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_HARD_BLOCK = "hard_block"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var action: Button
    private var targetPackage = ""
    private var hardBlock = false
    private var waiting = false
    private var waitStarted = 0L

    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPredictiveBackHandler()
        setupUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handler.removeCallbacksAndMessages(null)
            handleIntent(intent)
        }
    }

    private fun registerPredictiveBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback {
                goHome()
            }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
        }
    }

    private fun setupUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 48, 36, 48)
            setBackgroundColor(0xFFF7F7F7.toInt())
        }
        val title = TextView(this).apply {
            text = "LOCKIN"
            textSize = 30f
            setTextColor(0xFF171717.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFF444444.toInt())
            setPadding(0, 24, 0, 28)
        }
        action = Button(this).apply { textSize = 16f }
        root.addView(title)
        root.addView(status)
        root.addView(action)
        setContentView(root)
    }

    private fun handleIntent(currentIntent: Intent) {
        targetPackage = currentIntent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        hardBlock = currentIntent.getBooleanExtra(EXTRA_HARD_BLOCK, false) || LockPrefs.isHardBlocked(this, targetPackage)

        if (hardBlock) {
            status.text = "This app is locked until December 2, 2026 at midnight IST."
            action.text = "Back to studying"
            action.isEnabled = true
            action.setOnClickListener { goHome() }
        } else {
            renderNormalBlock()
        }
    }

    private fun renderNormalBlock() {
        status.text = "Your daily 6-hour allowance for non-exempt apps has been used."
        waitStarted = LockPrefs.waitStartedAt(this, targetPackage)
        val elapsed = System.currentTimeMillis() - waitStarted
        if (waitStarted > 0 && elapsed in 0 until 60_000) {
            waiting = true
            updateWaitButton()
        } else {
            waiting = false
            LockPrefs.clearWait(this, targetPackage)
            action.text = "Wait 60 seconds for 10-minute access"
            action.isEnabled = true
            action.setOnClickListener {
                waitStarted = LockPrefs.startWait(this, targetPackage)
                waiting = true
                updateWaitButton()
            }
        }
    }

    private fun updateWaitButton() {
        val remaining = (60_000 - (System.currentTimeMillis() - waitStarted)).coerceAtLeast(0L)
        if (remaining <= 0L) {
            waiting = false
            action.text = "Unlock for 10 minutes"
            action.isEnabled = true
            action.setOnClickListener {
                LockPrefs.grantOverride(this, targetPackage)
                LockPrefs.clearWait(this, targetPackage)
                finish()
            }
        } else {
            action.text = "Please wait ${((remaining + 999) / 1000)} seconds"
            action.isEnabled = false
            handler.postDelayed({ updateWaitButton() }, 500)
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        super.onDestroy()
    }
}
