package com.example.wallpaper_changer

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build

class WallpaperJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        // Servisi başlat
        val intent = Intent(applicationContext, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // false döndürerek işin bittiğini belirtiyoruz
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // true döndürerek işin yeniden planlanmasını istiyoruz
        return true
    }
}
