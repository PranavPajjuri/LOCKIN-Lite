package com.lockin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Accessibility services are restarted by Android when the user has enabled them.
        // This receiver intentionally performs no background launch.
    }
}
