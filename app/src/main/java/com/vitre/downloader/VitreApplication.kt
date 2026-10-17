package com.vitre.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.vitre.downloader.core.database.AppDatabase
import com.vitre.downloader.core.preferences.VitrePreferences

class VitreApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: VitrePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Chaquopy Python Runtime
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // 2. Initialize Room Database
        database = AppDatabase.getInstance(this)

        // 3. Initialize DataStore Preferences
        preferences = VitrePreferences(this)

        // 4. Create Notification Channel for Foreground Service
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "vitre_downloads_channel"
        lateinit var instance: VitreApplication
            private set
    }
}
