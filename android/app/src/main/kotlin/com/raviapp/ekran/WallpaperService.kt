package com.raviapp.ekran

import android.app.Service
import android.content.Intent
import android.os.IBinder

import android.app.*
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import kotlin.random.Random

class WallpaperService : Service() {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var usedIndices = mutableSetOf<Int>()
    private lateinit var sharedPreferences: SharedPreferences
    private var changeInterval: Long = 60000 // Default 1 dakika

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "WallpaperServiceChannel"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val PREF_IS_SERVICE_RUNNING = "is_service_running"
        
        var isServiceRunning: Boolean
            get() = servicePrefs?.getBoolean(PREF_IS_SERVICE_RUNNING, false) ?: false
            private set(value) {
                servicePrefs?.edit()?.putBoolean(PREF_IS_SERVICE_RUNNING, value)?.apply()
            }
            
        private var servicePrefs: SharedPreferences? = null

        fun updateServiceStatus(context: Context, running: Boolean) {
            servicePrefs = context.getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
            isServiceRunning = running
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        servicePrefs = applicationContext.getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
        sharedPreferences = applicationContext.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        loadSavedIndices()
        updateServiceStatus(applicationContext, true)
        startForeground(NOTIFICATION_ID, createNotification())
        startWallpaperChange()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        changeInterval = intent?.getLongExtra("interval", 5000) ?: 5000

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        updateServiceStatus(applicationContext, false)
        saveUsedIndices()
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, WallpaperService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }

        return builder
            .setContentTitle("Duvar Kağıdı Servisi")
            .setContentText("Duvar kağıdı değiştiriliyor...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Duvar Kağıdı Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duvar kağıdı değiştirme servisi bildirimleri"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun changeWallpaper() {
        try {
            val jsonString = loadJSONFromAsset()
            val jsonArray = JSONArray(jsonString)
            var randomIndex: Int

            do {
                randomIndex = Random.nextInt(jsonArray.length())
            } while (usedIndices.contains(randomIndex) && usedIndices.size < jsonArray.length())

            if (usedIndices.size >= jsonArray.length()) {
                usedIndices.clear()
            }

            usedIndices.add(randomIndex)
            saveUsedIndices()

            Log.d("WallpaperService", "Saved ${usedIndices.size} used indices")

            val hadis = jsonArray.getJSONObject(randomIndex)
            val metin = hadis.getString("metin")
            val kaynak = hadis.getString("kaynak")

            // Duvar kağıdını değiştir
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val metrics = resources.displayMetrics
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Arka plan rengini siyah yap
            canvas.drawColor(Color.BLACK)
            
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
            Log.d("WallpaperService", "Wallpaper changed successfully with hadis: $metin")
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadJSONFromAsset(): String {
        val inputStream = assets.open("hadisler.json")
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        return bufferedReader.use { it.readText() }
    }

    private fun saveUsedIndices() {
        val indicesStr = usedIndices.joinToString(",")
        sharedPreferences.edit().putString("used_indices", indicesStr).apply()
        Log.d("WallpaperService", "Saved ${usedIndices.size} used indices")
    }

    private fun loadSavedIndices() {
        val savedIndicesStr = sharedPreferences.getString("used_indices", "")
        if (!savedIndicesStr.isNullOrEmpty()) {
            usedIndices = savedIndicesStr.split(",")
                .mapNotNull { it.toIntOrNull() }
                .toMutableSet()
            Log.d("WallpaperService", "Loaded ${usedIndices.size} used indices")
        }
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

    private fun startWallpaperChange() {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    changeWallpaper()
                    delay(changeInterval)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
