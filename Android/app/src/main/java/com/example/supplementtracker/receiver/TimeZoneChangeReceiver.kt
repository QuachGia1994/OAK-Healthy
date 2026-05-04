package com.example.supplementtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver lắng nghe thay đổi múi giờ của hệ thống.
 */
class TimeZoneChangeReceiver(
    private val onTimeZoneChanged: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
            Log.d("TimeZoneChangeReceiver", "System time zone changed")
            onTimeZoneChanged()
        }
    }
}
