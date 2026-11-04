package com.vitre.downloader

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.vitre.downloader.core.database.AppDatabase
import com.vitre.downloader.core.preferences.VitrePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VitreApplication : Application(), Application.ActivityLifecycleCallbacks {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: VitrePreferences
        private set

    var currentActivity: Activity? = null
        private set

    private val _unityAdsReady = MutableStateFlow(false)
    val unityAdsReady = _unityAdsReady.asStateFlow()

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

        // 4. Initialize Unity Ads once at application startup
        UnityAds.initialize(
            applicationContext,
            UNITY_GAME_ID,
            BuildConfig.DEBUG,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    _unityAdsReady.value = true
                    android.util.Log.i("VitreUnityAds", "Unity Ads initialized.")
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    _unityAdsReady.value = false
                    android.util.Log.e("VitreUnityAds", "Initialization failed: $error - $message")
                }
            }
        )

        // 5. Register activity callbacks for current-activity tracking
        registerActivityLifecycleCallbacks(this)

        // 7. Create Notification Channel for Foreground Service
        createNotificationChannel()
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
        const val UNITY_GAME_ID = "800424586"
        const val CHANNEL_ID = "vitre_downloads_channel"
        lateinit var instance: VitreApplication
            private set
    }
}
