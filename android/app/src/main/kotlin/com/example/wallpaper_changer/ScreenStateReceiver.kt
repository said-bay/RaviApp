package com.example.wallpaper_changer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.EventChannel

class ScreenStateReceiver(private val events: EventChannel.EventSink) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> {
                events.success("SCREEN_ON")
            }
        }
    }
}
