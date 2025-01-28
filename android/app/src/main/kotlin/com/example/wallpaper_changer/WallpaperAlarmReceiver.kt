package com.example.wallpaper_changer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class WallpaperAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        private const val ALARM_REQUEST_CODE = 123

        fun scheduleAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WallpaperAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Her 6 saatte bir çalışacak şekilde ayarla
            val interval = 6 * 60 * 60 * 1000L // 6 saat
            val firstTrigger = System.currentTimeMillis() + interval

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarm scheduled")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WallpaperAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarm cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Duvar kağıdını değiştir
        val serviceIntent = Intent(context, WallpaperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Bir sonraki alarmı planla
        scheduleAlarm(context)
    }
}
