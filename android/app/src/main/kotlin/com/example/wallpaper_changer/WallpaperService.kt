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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import android.text.TextPaint
import java.io.File
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat

class WallpaperService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hadisler: JSONArray? = null
    private var usedIndices = mutableSetOf<Int>()
    private lateinit var sharedPreferences: SharedPreferences
    private var screenStateReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("wallpaper_service", Context.MODE_PRIVATE)
        loadSavedIndices()
        loadHadisler()
        acquireWakeLock()
        createNotificationChannel()
        registerScreenStateReceiver()
        startForeground(NOTIFICATION_ID, createNotification())
        startWallpaperChange()
        isServiceRunning = true
    }

    private fun loadSavedIndices() {
        val savedIndicesStr = sharedPreferences.getString("used_indices", "")
        if (!savedIndicesStr.isNullOrEmpty()) {
            usedIndices = savedIndicesStr.split(",")
                .mapNotNull { it.toIntOrNull() }
                .toMutableSet()
            Log.d(TAG, "Loaded ${usedIndices.size} used indices")
        }
    }

    private fun saveUsedIndices() {
        val indicesStr = usedIndices.joinToString(",")
        sharedPreferences.edit().putString("used_indices", indicesStr).apply()
        Log.d(TAG, "Saved ${usedIndices.size} used indices")
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen turned ON")
                        // Ekran açıldığında hadisi değiştir
                        changeWallpaper()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen turned OFF")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        
        // Bildirim oluştur
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // İlk çalıştığında hemen duvar kağıdını değiştir
        changeWallpaper()
        
        return START_STICKY
    }

    private fun loadHadisler() {
        try {
            val assetManager = applicationContext.assets
            val inputStream = assetManager.open("hadisler.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)
            
            // JSON içeriğini kontrol et
            Log.d(TAG, "Raw JSON content: $json")
            
            hadisler = JSONArray(json)
            val hadisCount = hadisler?.length() ?: 0
            Log.d(TAG, "Successfully loaded $hadisCount hadis")
            
            // İlk birkaç hadisi kontrol et
            for (i in 0 until minOf(5, hadisCount)) {
                val hadis = hadisler?.getJSONObject(i)
                Log.d(TAG, "Hadis $i: ${hadis?.getString("metin")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading hadisler", e)
            e.printStackTrace()
        }
    }

    private fun getRandomHadis(): Pair<String, String>? {
        hadisler?.let { hadislerArray ->
            val hadisCount = hadislerArray.length()
            if (hadisCount == 0) {
                Log.e(TAG, "No hadis available")
                return null
            }
            
            Log.d(TAG, "Total hadis count: $hadisCount")
            Log.d(TAG, "Currently used indices: ${usedIndices.joinToString()}")
            
            // Tüm hadisler kullanıldıysa listeyi sıfırla
            if (usedIndices.size >= hadisCount) {
                usedIndices.clear()
                Log.d(TAG, "All hadis used, resetting list")
            }
            
            // Kullanılmamış rastgele bir hadis seç
            var randomIndex: Int
            var attempts = 0
            do {
                randomIndex = Random.nextInt(hadisCount)
                attempts++
                // Sonsuz döngüye girmemek için
                if (attempts > 1000) {
                    usedIndices.clear()
                    Log.d(TAG, "Too many attempts, resetting list")
                    break
                }
            } while (randomIndex in usedIndices)
            
            usedIndices.add(randomIndex)
            saveUsedIndices()
            
            val hadis = hadislerArray.getJSONObject(randomIndex)
            val metin = hadis.getString("metin")
            val kaynak = hadis.getString("kaynak")
            
            Log.d(TAG, "Selected hadis index: $randomIndex")
            Log.d(TAG, "Selected hadis text: $metin")
            Log.d(TAG, "Total used indices after selection: ${usedIndices.size}")
            
            return Pair(metin, kaynak)
        }
        return null
    }

    private fun drawMultilineText(canvas: Canvas, text: String, paint: Paint, maxWidth: Float, startY: Float): Float {
        val lines = ArrayList<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()
        
        // Metni satırlara böl
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    lines.add(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        // Satırları çiz
        var y = startY
        val lineHeight = paint.fontSpacing
        for (line in lines) {
            canvas.drawText(line, canvas.width / 2f, y, paint)
            y += lineHeight
        }
        
        // Son satırın y pozisyonunu döndür
        return y
    }

    private fun changeWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val metrics = resources.displayMetrics
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Arka plan rengini siyah yap
            canvas.drawColor(Color.BLACK)
            
            val hadis = getRandomHadis()
            if (hadis == null) {
                Log.e(TAG, "No hadis available")
                return
            }
            
            val (metin, kaynak) = hadis
            
            val typeface = Typeface.create("serif", Typeface.NORMAL)
            
            // Hadis metni için paint
            val textPaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
                textSize = width * 0.045f
            }
            
            // Kaynak metni için paint
            val kaynakPaint = Paint().apply {
                color = Color.WHITE
                alpha = 128  // Yarı saydam
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                this.typeface = typeface
                textSize = width * 0.035f
            }
            
            val padding = width * 0.1f
            val maxWidth = width - (padding * 2)
            
            // Hadis metnini çiz ve yüksekliğini al
            val hadisHeight = drawMultilineText(canvas, metin, textPaint, maxWidth, height * 0.4f)
            
            // Kaynağı hadisin hemen altına çiz (20dp boşluk bırak)
            val kaynakY = hadisHeight + (20 * resources.displayMetrics.density)
            canvas.drawText(kaynak, width / 2f, kaynakY, kaynakPaint)
            
            // Sadece kilit ekranı duvar kağıdını değiştir
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Log.d(TAG, "Wallpaper changed successfully with hadis: $metin")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error changing wallpaper", e)
        }
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

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WallpaperService::WakeLock"
        ).apply {
            acquire(TimeUnit.DAYS.toMillis(365)) // 1 yıl boyunca wake lock'u tut
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Duvar kağıdı değiştirme servisi"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hadis Duvar Kağıdı")
            .setContentText("Duvar kağıdı servisi çalışıyor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startWallpaperChange() {
        handler.post(wallpaperRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        handler.removeCallbacks(wallpaperRunnable)
        isServiceRunning = false
        screenStateReceiver?.let {
            unregisterReceiver(it)
        }

        // Servisi yeniden başlat
        val intent = Intent(this, WallpaperService::class.java)
        startService(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "WallpaperService"
        const val CHANNEL_ID = "WallpaperServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.example.wallpaper_changer.STOP_SERVICE"
        var isServiceRunning = false
    }
}
