package com.vitre.downloader.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * Manages Google AdMob App Open Ads with lifecycle awareness, 4-hour expiration check,
 * and policy-compliant cooldown guardrails.
 */
class AppOpenAdManager private constructor(private val context: Context) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
        private set

    /** Keep track of the time an ad was loaded to ensure it is not expired. */
    private var loadTime: Long = 0

    /** Keep track of the time an ad was dismissed to prevent back-to-back spamming. */
    private var lastDismissedTime: Long = 0

    /**
     * Request an ad if not already loading or available.
     */
    fun loadAd(context: Context, onLoaded: ((Boolean) -> Unit)? = null) {
        if (isLoadingAd || isAdAvailable()) {
            onLoaded?.invoke(isAdAvailable())
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App Open Ad loaded successfully.")
                    onLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    onLoaded?.invoke(false)
                }
            }
        )
    }

    /**
     * Check if ad was loaded less than 4 hours ago.
     */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /**
     * Check if ad exists and is not expired.
     */
    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /**
     * Show the App Open Ad if available and cooldown period has passed.
     */
    fun showAdIfAvailable(
        activity: Activity,
        force: Boolean = false,
        onShowAdCompleteListener: () -> Unit = {}
    ) {
        // If an ad is already showing, do nothing and return.
        if (isShowingAd) {
            Log.d(TAG, "Ad is already showing.")
            onShowAdCompleteListener()
            return
        }

        // If ad is not available, try to load one and complete.
        if (!isAdAvailable()) {
            Log.d(TAG, "Ad is not available. Loading next ad.")
            onShowAdCompleteListener()
            loadAd(activity)
            return
        }

        // AdMob policy guard: Check cooldown to avoid rapid-fire ad displays
        val now = System.currentTimeMillis()
        if (!force && (now - lastDismissedTime < COOLDOWN_MILLIS)) {
            Log.d(TAG, "Ad skipped due to cooldown guard.")
            onShowAdCompleteListener()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                lastDismissedTime = System.currentTimeMillis()
                Log.d(TAG, "Ad dismissed fullscreen content.")
                onShowAdCompleteListener()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.w(TAG, "Ad failed to show fullscreen content: ${adError.message}")
                onShowAdCompleteListener()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                Log.d(TAG, "Ad showing fullscreen content.")
            }
        }

        isShowingAd = true
        appOpenAd?.show(activity)
    }

    /**
     * Specifically used when the user clicks "Watch Ad & Support" in dialog or settings.
     * If an ad is available, shows it immediately (bypassing cooldown).
     * If not available, attempts to load and show immediately upon loading.
     */
    fun showUserRequestedAd(
        activity: Activity,
        onStatusMessage: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        if (isShowingAd) {
            onComplete()
            return
        }

        if (isAdAvailable()) {
            showAdIfAvailable(activity, force = true, onShowAdCompleteListener = onComplete)
        } else {
            onStatusMessage("Loading ad to support developer...")
            loadAd(activity) { loaded ->
                if (loaded) {
                    showAdIfAvailable(activity, force = true, onShowAdCompleteListener = onComplete)
                } else {
                    onStatusMessage("No ad available at the moment. Thank you for your support!")
                    onComplete()
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        const val AD_UNIT_ID = "ca-app-pub-2775984163501785/1916614609"
        private const val COOLDOWN_MILLIS = 20_000L // 20 seconds cooldown

        @Volatile
        private var INSTANCE: AppOpenAdManager? = null

        fun getInstance(context: Context): AppOpenAdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppOpenAdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
