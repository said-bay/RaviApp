package com.example.wallpaper_changer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    private val appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        try {
            // Servisi başlat
            val intent = Intent(applicationContext, WallpaperService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "WallpaperWorker"

        fun startWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                15, TimeUnit.MINUTES,  // Minimum periyot
                5, TimeUnit.MINUTES    // Esneklik aralığı
            )
            .setConstraints(constraints)
            .addTag(UNIQUE_WORK_NAME)
            .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,  // Var olan işi yenisiyle değiştir
                    workRequest
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override fun onStopped() {
        super.onStopped()
    }
}
