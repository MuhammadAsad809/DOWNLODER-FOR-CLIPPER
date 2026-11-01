package com.vitre.downloader

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.gms.ads.MobileAds
import com.vitre.downloader.core.ads.AppOpenAdManager
import com.vitre.downloader.core.database.AppDatabase
import com.vitre.downloader.core.preferences.VitrePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VitreApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: VitrePreferences
        private set

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    var currentActivity: Activity? = null
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

        // 4. Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {}

        // 5. Initialize AdMob App Open Ad Manager & Register Lifecycle Observers
        appOpenAdManager = AppOpenAdManager.getInstance(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appOpenAdManager.loadAd(this)

        // 6. Create Notification Channel for Foreground Service
        createNotificationChannel()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Show App Open Ad when returning to foreground
        currentActivity?.let { activity ->
            // Do not show over QuickShareActivity
            if (activity !is QuickShareActivity) {
                appOpenAdManager.showAdIfAvailable(activity)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!appOpenAdManager.isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!appOpenAdManager.isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
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
