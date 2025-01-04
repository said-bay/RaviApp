package com.example.wallpaper_changer

import android.app.WallpaperManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity: FlutterActivity() {
    private val SCREEN_STATE_CHANNEL = "com.example.wallpaper_changer/screen_state"
    private val WALLPAPER_CHANNEL = "com.example.wallpaper_changer/wallpaper"
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService()
    }

    private fun startService() {
        val serviceIntent = Intent(this, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, SCREEN_STATE_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    screenStateReceiver = ScreenStateReceiver(events)
                    val filter = IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                    }
                    registerReceiver(screenStateReceiver, filter)
                }

                override fun onCancel(arguments: Any?) {
                    screenStateReceiver?.let { unregisterReceiver(it) }
                    screenStateReceiver = null
                }
            }
        )

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WALLPAPER_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "setLockScreenWallpaper" -> {
                    val path = call.argument<String>("path")
                    if (path != null) {
                        try {
                            val wallpaperManager = WallpaperManager.getInstance(this)
                            val bitmap = BitmapFactory.decodeFile(path)
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("ERROR", e.message, null)
                        }
                    } else {
                        result.error("INVALID_PATH", "Path cannot be null", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
}
