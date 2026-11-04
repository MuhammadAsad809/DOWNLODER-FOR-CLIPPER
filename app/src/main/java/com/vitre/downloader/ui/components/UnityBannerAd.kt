package com.vitre.downloader.ui.components

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import com.vitre.downloader.VitreApplication

private const val TAG = "VitreUnityBanner"
private const val BANNER_AD_UNIT_ID = "BP_Banner_Android"

/**
 * Displays the configured Unity Ads banner after SDK initialization.
 * The banner is intentionally absent until initialization succeeds.
 */
@Composable
fun UnityBannerAd(modifier: Modifier = Modifier) {
    val app = VitreApplication.instance
    val isReady by app.unityAdsReady.collectAsState()

    if (!isReady) return

    val activity = LocalContext.current as? Activity ?: return
    val bannerView = remember(activity) {
        BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50)).apply {
            setListener(object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView) {
                    Log.i(TAG, "Banner loaded: ${bannerAdView.placementId}")
                }

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView,
                    errorInfo: BannerErrorInfo
                ) {
                    Log.e(
                        TAG,
                        "Banner failed: ${bannerAdView.placementId} [${errorInfo.errorCode}] ${errorInfo.errorMessage}"
                    )
                }

                override fun onBannerClick(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner clicked: ${bannerAdView.placementId}")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView) {
                    Log.d(TAG, "Banner opened external destination.")
                }
            })
        }
    }

    DisposableEffect(bannerView) {
        onDispose {
            bannerView.destroy()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            FrameLayout(context).apply {
                addView(bannerView)
                bannerView.load()
            }
        },
        update = { container ->
            if (bannerView.parent !== container) {
                (bannerView.parent as? android.view.ViewGroup)?.removeView(bannerView)
                container.removeAllViews()
                container.addView(bannerView)
            }
        }
    )
}
