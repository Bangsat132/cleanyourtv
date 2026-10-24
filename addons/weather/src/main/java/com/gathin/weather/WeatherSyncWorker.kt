package com.gathin.weather

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gathin.weather.repository.WeatherRepository

class WeatherSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = WeatherRepository(applicationContext as WeatherApp)
        val result = repo.getCurrentWeather().await()
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val ACTION_SYNC = "com.gathin.weather.SYNC"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SyncReceiver::class.java).apply {
                action = ACTION_SYNC
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

            val intervalMillis = intervalMinutes * 60 * 1000L
            val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMillis

            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
        }

        fun cancelPeriodicSync(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SyncReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
            alarmManager.cancel(pendingIntent)
        }
    }
}

class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WeatherSyncWorker.ACTION_SYNC) {
            val repo = WeatherRepository(context.applicationContext as WeatherApp)
            repo.getCurrentWeather()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            val prefs = com.gathin.weather.prefs.WeatherPrefs
            val interval = prefs.getRefreshInterval(context)
            WeatherSyncWorker.schedulePeriodicSync(context, interval.minutes)
        }
    }
}