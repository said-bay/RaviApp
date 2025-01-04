package com.example.wallpaper_changer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Color
import android.util.Log
import java.io.ByteArrayInputStream
import androidx.core.app.NotificationCompat

class WallpaperService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperRunnable = object : Runnable {
        override fun run() {
            val shouldRunWallpaper = Companion.isServiceRunning
            if (shouldRunWallpaper) {
                changeWallpaper()
                handler.postDelayed(this, 15 * 60 * 1000) // 15 dakika
            }
        }
    }

    private var screenStateReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(Companion.TAG, "Service onCreate")
        // Başlangıçta servis durumunu false yap
        Companion.isServiceRunning = false
        registerScreenStateReceiver()
    }

    private fun startService() {
        Log.d(Companion.TAG, "Starting service")
        Companion.isServiceRunning = true
        acquireWakeLock()
        handler.post(wallpaperRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(Companion.TAG, "Service onStartCommand")
        
        when (intent?.action) {
            Companion.ACTION_STOP_SERVICE -> {
                Log.d(Companion.TAG, "Stopping service")
                unregisterScreenStateReceiver()
                stopSelf()
                Companion.isServiceRunning = false
                return START_NOT_STICKY
            }
            else -> {
                if (!Companion.isServiceRunning) {
                    startService()
                    registerScreenStateReceiver()
                    Companion.isServiceRunning = true
                }
            }
        }
        
        // Servisin otomatik olarak yeniden başlatılmasını sağla
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(Companion.TAG, "Service onTaskRemoved - restarting service")
        
        // Uygulama kapatıldığında servisi yeniden başlat
        val restartServiceIntent = Intent(applicationContext, WallpaperService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Companion.TAG, "Service onDestroy")
        
        if (Companion.isServiceRunning) {
            // Servis sistem tarafından kapatıldıysa ve hala çalışır durumdaysa
            // yeniden başlat
            val intent = Intent(applicationContext, WallpaperService::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startService(intent)
        }
        
        unregisterScreenStateReceiver()
        handler.removeCallbacks(wallpaperRunnable)
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            Log.d(Companion.TAG, "Acquiring WakeLock")
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WallpaperChanger::WakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(Companion.TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver == null) {
            screenStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF && Companion.isServiceRunning) {
                        // Ekran kapandığında hemen duvar kağıdını değiştir
                        Thread {
                            // Kısa bir bekleme ekleyelim ki ekran tam kapansın
                            Thread.sleep(100)
                            changeWallpaper()
                        }.start()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            registerReceiver(screenStateReceiver, filter)
        }
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            unregisterReceiver(it)
            screenStateReceiver = null
        }
    }

    private fun changeWallpaper() {
        try {
            Log.d(Companion.TAG, "Changing wallpaper")
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpapers = listOf(
                "1.png",
                "2.png",
                "3.png",
                "4.png",
                "5.png",
                "6.png",
                "7.png",
                "8.png",
                "9.png",
                "10.png",
                "11.png",
                "12.png",
                "13.png",
                "14.png",
                "15.png"
            )
            
            val randomWallpaper = wallpapers.random()
            Log.d(Companion.TAG, "Selected wallpaper: $randomWallpaper")
            
            // Ekran boyutlarını al
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Duvar kağıdını yükle
            val assetManager = assets
            val inputStream = assetManager.open("wallpapers/$randomWallpaper")
            
            // Bitmap'i oluştur ve boyutlarını al
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.reset()
            
            // Ölçekleme oranını hesapla
            val widthRatio = options.outWidth.toFloat() / screenWidth
            val heightRatio = options.outHeight.toFloat() / screenHeight
            val scaleFactor = maxOf(widthRatio, heightRatio)
            
            // Resmi ölçeklendir
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor.toInt().coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream, null, finalOptions)
                ?: throw IllegalStateException("Failed to decode bitmap")
            inputStream.close()
            
            // Resmi ekran boyutuna ölçekle
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                screenWidth,
                screenHeight,
                true
            )
            
            try {
                // Duvar kağıdını ayarla
                wallpaperManager.setBitmap(
                    scaledBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK
                )
                Log.d(Companion.TAG, "Wallpaper changed successfully to $randomWallpaper")
            } finally {
                // Belleği temizle
                bitmap.recycle()
                scaledBitmap.recycle()
            }
            
        } catch (e: Exception) {
            Log.e(Companion.TAG, "Error changing wallpaper", e)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            try {
                when {
                    lock.isHeld -> lock.release()
                    else -> Log.d(Companion.TAG, "WakeLock is not held")
                }
            } catch (e: Exception) {
                Log.e(Companion.TAG, "Error releasing WakeLock", e)
            }
        }
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
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "WallpaperServiceChannel"
        const val ACTION_STOP_SERVICE = "com.example.wallpaper_changer.STOP_SERVICE"

        @Volatile
        var isServiceRunning = false
            private set
    }
}
