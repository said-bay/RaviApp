package com.example.wallpaper_changer

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class WallpaperJobService : JobService() {
    companion object {
        const val JOB_ID = 1000
        private const val INTERVAL_MILLIS = 15 * 60 * 1000L // 15 dakika

        fun schedule(context: Context) {
            val serviceComponent = ComponentName(context, WallpaperJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, serviceComponent)
                .setPeriodic(INTERVAL_MILLIS)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(false)
                .setRequiresStorageNotLow(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setRequiresBatteryNotLow(false)
            }

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val result = jobScheduler.schedule(builder.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d("WallpaperJobService", "Job scheduled successfully")
            } else {
                Log.e("WallpaperJobService", "Job scheduling failed")
            }
        }

        fun cancel(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d("WallpaperJobService", "Job cancelled")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("WallpaperJobService", "Job started")
        val serviceIntent = Intent(applicationContext, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("WallpaperJobService", "Job stopped")
        return true
    }
}
