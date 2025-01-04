package com.example.wallpaper_changer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Color
import android.util.Log

class WallpaperService : Service() {
    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperRunnable = object : Runnable {
        override fun run() {
            val shouldRunWallpaper = isServiceRunning
            if (shouldRunWallpaper) {
                changeWallpaper()
                handler.postDelayed(this, 15 * 60 * 1000) // 15 dakika
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        startServiceWithNotification()
    }

    private fun startServiceWithNotification() {
        Log.d(TAG, "Starting service with notification")
        isServiceRunning = true
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        registerScreenReceiver()
        handler.post(wallpaperRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        val shouldStartService = !isServiceRunning
        if (shouldStartService) {
            startServiceWithNotification()
        }
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Service onTaskRemoved - restarting service")
        val restartServiceIntent = Intent(applicationContext, WallpaperService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            Log.d(TAG, "Acquiring WakeLock")
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WallpaperChanger::WakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun registerScreenReceiver() {
        try {
            screenReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering previous receiver", e)
        }

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val isScreenOn = action == Intent.ACTION_SCREEN_ON
                if (isScreenOn) {
                    Log.d(TAG, "Screen turned on - changing wallpaper")
                    changeWallpaper()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenReceiver, filter)
        Log.d(TAG, "Screen receiver registered")
    }

    private fun changeWallpaper() {
        try {
            Log.d(TAG, "Changing wallpaper")
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpapers = listOf(
                "Ravi-1.png",
                "Ravi-2.png",
                "Ravi-3.png",
                "Ravi-4.png",
                "Ravi-5.png"
            )
            
            val randomWallpaper = wallpapers.random()
            Log.d(TAG, "Selected wallpaper: $randomWallpaper")
            
            val assetManager = assets
            assetManager.open("wallpapers/$randomWallpaper").use { inputStream ->
                wallpaperManager.setStream(inputStream, null, true, WallpaperManager.FLAG_LOCK)
            }
            
            Log.d(TAG, "Wallpaper changed successfully to $randomWallpaper")
            
            // Bildirimi güncelle
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Error changing wallpaper", e)
        }
    }

    private fun createNotificationChannelIfNeeded(): String {
        val channelId = "wallpaper_service"
        val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        
        if (isOreoOrLater) {
            val channel = NotificationChannel(
                channelId,
                "Wallpaper Service",
                NotificationManager.IMPORTANCE_MIN // En düşük önem seviyesi
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET // Kilit ekranında gizle
                setShowBadge(false) // Uygulama ikonunda rozet gösterme
                enableLights(false) // LED ışığını devre dışı bırak
                enableVibration(false) // Titreşimi devre dışı bırak
                setSound(null, null) // Sesi devre dışı bırak
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        return channelId
    }

    @Suppress("DEPRECATION")
    private fun createNotificationBuilder(channelId: String): Notification.Builder {
        val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val builder = if (isOreoOrLater) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this).apply {
                setPriority(Notification.PRIORITY_MIN) // En düşük öncelik (Android O öncesi için)
            }
        }
        return builder
    }

    private fun createNotification(): Notification {
        val channelId = createNotificationChannelIfNeeded()
        val builder = createNotificationBuilder(channelId)
        val pendingIntent = createMainActivityPendingIntent()
        val stopPendingIntent = createStopServicePendingIntent()

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("") // Boş başlık
            .setContentText("") // Boş metin
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setVisibility(Notification.VISIBILITY_SECRET) // Kilit ekranında gizle
            .setShowWhen(false) // Zaman gösterme
            .build()
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopServicePendingIntent(): PendingIntent {
        val stopIntent = Intent(this, WallpaperService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        return PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            try {
                when {
                    lock.isHeld -> lock.release()
                    else -> Log.d(TAG, "WakeLock is not held")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock", e)
            }
        }
    }

    private fun unregisterScreenReceiverSafely() {
        screenReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacks(wallpaperRunnable)
        
        releaseWakeLock()
        unregisterScreenReceiverSafely()

        // Servisi yeniden başlat
        val intent = Intent(applicationContext, WallpaperService::class.java)
        startServiceBasedOnVersion(intent)
    }

    private fun startServiceBasedOnVersion(intent: Intent) {
        val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        when {
            isOreoOrLater -> startForegroundService(intent)
            else -> startService(intent)
        }
    }

    companion object {
        private const val TAG = "WallpaperService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.example.wallpaper_changer.STOP_SERVICE"
    }
}
