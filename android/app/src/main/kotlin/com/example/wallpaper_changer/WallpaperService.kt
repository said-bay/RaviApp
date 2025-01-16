package com.example.wallpaper_changer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.app.WallpaperManager
import android.graphics.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Color
import android.util.Log
import java.util.concurrent.TimeUnit

class WallpaperService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var screenStateReceiver: BroadcastReceiver? = null

    data class Hadis(
        val metin: String,
        val kitap: String,
        val bolum: String,
        val numara: String
    )

    companion object {
        const val TAG = "WallpaperService"
        const val CHANNEL_ID = "WallpaperServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.example.wallpaper_changer.STOP_SERVICE"
        var isServiceRunning = false
        
        // Hadisler listesi
        private val hadisler = listOf(
            Hadis("En hayırlınız, Kur'an'ı öğrenen ve öğretendir.", "Buhari", "Fedailü'l-Kur'an", "21"),
            Hadis("Kolaylaştırınız, zorlaştırmayınız. Müjdeleyiniz, nefret ettirmeyiniz.", "Buhari", "İlim", "11"),
            Hadis("İnsanlara merhamet etmeyene Allah da merhamet etmez.", "Buhari", "Edeb", "27"),
            Hadis("Güzel söz sadakadır.", "Buhari", "Edeb", "34"),
            Hadis("Cennet annelerin ayakları altındadır.", "Nesai", "Cihad", "6"),
            Hadis("Komşusu açken tok yatan bizden değildir.", "Buhari", "Edeb", "112"),
            Hadis("İki günü eşit olan ziyandadır.", "Deylemi", "Firdevs", "5290"),
            Hadis("İlim öğrenmek her Müslüman'a farzdır.", "İbn Mace", "Mukaddime", "17"),
            Hadis("Mümin, mümin için birbirini destekleyen bir bina gibidir.", "Buhari", "Mezalim", "5"),
            Hadis("Hiçbir baba, çocuğuna güzel terbiyeden daha üstün bir hediye veremez.", "Tirmizi", "Birr", "33")
        )
    }

    private val wallpaperRunnable = object : Runnable {
        override fun run() {
            try {
                changeWallpaper()
            } catch (e: Exception) {
                Log.e(TAG, "Error changing wallpaper", e)
            }
            handler.postDelayed(this, TimeUnit.HOURS.toMillis(1))
        }
    }

    private fun changeWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val metrics = resources.displayMetrics
            
            // Ekran boyutlarını al
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            
            // Siyah arka planlı bitmap oluştur
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Arka planı siyah yap
            canvas.drawColor(Color.BLACK)
            
            // Hadisi seç
            val hadis = hadisler[currentIndex]
            currentIndex = (currentIndex + 1) % hadisler.size
            
            // Yazı tipini ayarla - Serif kullan
            val typeface = Typeface.create("serif", Typeface.NORMAL)
            
            // Ana metin için yazı stilini ayarla
            val textPaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
            }
            
            // Kaynak için yazı stilini ayarla
            val kaynakPaint = Paint().apply {
                color = Color.WHITE
                alpha = 128 // Yarı saydam
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                this.typeface = typeface
            }
            
            // Metin boyutunu ayarla
            val baseTextSize = width * 0.06f // Tek bir punto boyutu
            textPaint.textSize = baseTextSize
            kaynakPaint.textSize = baseTextSize * 0.7f // Kaynak metni ana metnin %70'i kadar
            
            // Metni satırlara böl
            val maxWidth = width * 0.85f // Biraz daha geniş alan
            val words = hadis.metin.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()
            
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                if (textPaint.measureText(testLine) <= maxWidth) {
                    currentLine.append(if (currentLine.isEmpty()) word else " $word")
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }
            lines.add(currentLine.toString())
            
            // Ana metni çiz
            val lineHeight = textPaint.fontSpacing
            val totalTextHeight = lineHeight * lines.size
            var yPos = (height - totalTextHeight) / 2
            
            for (line in lines) {
                canvas.drawText(line, width / 2f, yPos + lineHeight, textPaint)
                yPos += lineHeight
            }
            
            // Son satırın y pozisyonunu kaydet
            val lastLineY = yPos
            
            // Kaynağı hadisin altına çiz - mesafeyi artır
            val kaynakMetni = "(${hadis.kitap}, \"${hadis.bolum}\", ${hadis.numara})"
            canvas.drawText(kaynakMetni, width / 2f, lastLineY + kaynakPaint.fontSpacing * 2f, kaynakPaint)
            
            // Duvar kağıdını ayarla
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in changeWallpaper", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        // Başlangıçta servis durumunu false yap
        isServiceRunning = false
        registerScreenStateReceiver()
    }

    private fun startService() {
        Log.d(TAG, "Starting service")
        isServiceRunning = true
        acquireWakeLock()
        handler.post(wallpaperRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stopping service")
                unregisterScreenStateReceiver()
                stopSelf()
                isServiceRunning = false
                return START_NOT_STICKY
            }
            else -> {
                if (!isServiceRunning) {
                    startService()
                    registerScreenStateReceiver()
                    isServiceRunning = true
                }
            }
        }
        
        // Servisin otomatik olarak yeniden başlatılmasını sağla
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Service onTaskRemoved - restarting service")
        
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
        Log.d(TAG, "Service onDestroy")
        
        if (isServiceRunning) {
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

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver == null) {
            screenStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF && isServiceRunning) {
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

    private fun startServiceBasedOnVersion(intent: Intent) {
        val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        when {
            isOreoOrLater -> startForegroundService(intent)
            else -> startService(intent)
        }
    }
}
