package com.reelpilot.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.reelpilot.app.data.AutoStore
import com.reelpilot.app.data.Store
import com.reelpilot.app.work.AutoWorker

class App : Application() {
    companion object {
        const val CHANNEL_UPLOADS = "uploads"
        const val CHANNEL_RESULTS = "results"
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        AutoStore.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPLOADS, "Upload progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Post results", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (AutoStore.config.value.enabled) AutoWorker.schedule(this)
    }
}
