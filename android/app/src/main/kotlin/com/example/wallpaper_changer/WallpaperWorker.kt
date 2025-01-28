package com.example.wallpaper_changer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

class WallpaperWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val serviceIntent = Intent(applicationContext, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "WallpaperWorker"

        fun startPeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(false)
                .setRequiresStorageNotLow(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
        }

        fun stopWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
