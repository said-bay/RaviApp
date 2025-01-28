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
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.util.Log
import android.content.ComponentName

class MainActivity: FlutterActivity() {
    private val SCREEN_STATE_CHANNEL = "com.example.wallpaper_changer/screen_state"
    private val WALLPAPER_CHANNEL = "com.example.wallpaper_changer/wallpaper"
    private val SERVICE_CHANNEL = "com.example.wallpaper_changer/service"
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
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
                    registerReceiver(
                        screenStateReceiver,
                        IntentFilter().apply {
                            addAction(Intent.ACTION_SCREEN_ON)
                            addAction(Intent.ACTION_SCREEN_OFF)
                        }
                    )
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

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SERVICE_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startService" -> {
                    startWallpaperService()
                    result.success("Service started")
                }
                "stopService" -> {
                    stopWallpaperService()
                    result.success("Service stopped")
                }
                "isServiceRunning" -> {
                    result.success(WallpaperService.isServiceRunning)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun startWallpaperService() {
        requestBatteryOptimizationPermission()
        val serviceIntent = Intent(this, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d("MainActivity", "Service started")
    }

    private fun stopWallpaperService() {
        val serviceIntent = Intent(this, WallpaperService::class.java)
        serviceIntent.action = WallpaperService.ACTION_STOP_SERVICE
        startService(serviceIntent)
        WallpaperAlarmReceiver.cancelAlarm(this)
        Log.d("MainActivity", "Service stopped")
    }

    private fun requestBatteryOptimizationPermission() {
        val packageName = packageName
        
        // Android standart pil optimizasyonu
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error requesting battery optimization ignore", e)
            }
        }

        // Xiaomi cihazlar için
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("MainActivity", "Device is not Xiaomi")
        }

        // Huawei cihazlar için
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("MainActivity", "Device is not Huawei")
        }

        // OPPO cihazlar için
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("MainActivity", "Device is not OPPO")
        }

        // Vivo cihazlar için
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("MainActivity", "Device is not Vivo")
        }

        // Samsung cihazlar için
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.d("MainActivity", "Device is not Samsung")
        }
    }
}
