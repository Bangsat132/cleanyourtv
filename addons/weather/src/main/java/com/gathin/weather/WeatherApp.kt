package com.gathin.weather

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class WeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }
}

class AppLifecycleObserver : androidx.lifecycle.DefaultLifecycleObserver {
    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        WeatherSyncWorker.schedulePeriodicSync(owner as Application)
    }
}