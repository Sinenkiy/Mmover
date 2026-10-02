package com.divinegames.mmover

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import kotlinx.coroutines.launch

/** Yandex-only policy of the Russian app. SDK owns sticky banner refreshes. */
internal class MainAdsController(
    private val activity: BaseActivity,
    private val layoutRoot: View,
    private val topContainer: FrameLayout,
    private val bottomContainer: FrameLayout,
    onReward: () -> Unit,
    onStateChanged: () -> Unit,
    onMessage: (Int) -> Unit
) {
    internal val requestPolicy = AdRequestPolicy()
    private var closed = false
    private var resumed = false
    private var sdkStarted = false
    private var sdkReady = false
    private var sdkFailed = false
    private var bannersInitialized = false
    private var top: BannerAdView? = null
    private var bottom: BannerAdView? = null
    private var topLoaded = false
    private var bottomLoaded = false
    private var topSchedule: BannerRefreshController? = null
    private var bottomSchedule: BannerRefreshController? = null
    private var loader: RewardedAdLoader? = null
    private var rewarded: RewardedAd? = null
    private var showing: RewardedAd? = null
    private var session: RewardSession? = null
    private var loading = false
    private var loadFailed = false
    private var lastError: String? = null
    private var rewardCallback: (() -> Unit)? = onReward
    private var stateCallback: (() -> Unit)? = onStateChanged
    private var messageCallback: ((Int) -> Unit)? = onMessage

    // Preserve the Russian production configuration, including its demo top slot.
    private val topId = "demo-banner-yandex"
    private val bottomId = if (BuildConfig.DEBUG) "demo-banner-yandex" else "R-M-17944676-2"
    private val rewardedId = if (BuildConfig.DEBUG) "demo-rewarded-yandex" else "R-M-17944676-3"

    data class Diagnostics(
        val sdkInitializationStarted: Boolean, val sdkReady: Boolean,
        val bannersInitialized: Boolean, val topLoaded: Boolean, val bottomLoaded: Boolean,
        val rewardedLoaded: Boolean, val closed: Boolean, val rewardedLoading: Boolean,
        val yandexRewardedError: String?
    )
    val diagnostics get() = Diagnostics(sdkStarted, sdkReady, bannersInitialized,
        topLoaded, bottomLoaded, rewarded != null, closed, loading, lastError)
    val rewardedState: RewardedAdState get() = when {
        !allowed() || session != null -> RewardedAdState.UNAVAILABLE
        sdkFailed || loadFailed -> RewardedAdState.FAILED
        !sdkReady || loading -> RewardedAdState.LOADING
        rewarded != null -> RewardedAdState.READY
        else -> RewardedAdState.UNAVAILABLE
    }

    fun initialize() { if (!closed) requestPolicy.consentReady = true }
    private fun allowed() = !closed && requestPolicy.isAllowed(true, false)
    private fun accepts(token: Int) = !closed && requestPolicy.acceptsResult(token, true, false)
    private fun canRequest() = resumed && allowed() && !activity.isFinishing && !activity.isDestroyed

    fun onResume() {
        if (closed) return
        activity.lifecycleScope.launch {
            activity.lifecycle.withResumed {
                if (!closed) { resumed = true; resumeAds() }
            }
        }
    }

    fun onPause() {
        resumed = false
        topSchedule?.pause()
        bottomSchedule?.pause()
    }

    private fun resumeAds() {
        if (!canRequest()) return
        if (!sdkStarted && !sdkFailed) {
            sdkStarted = true
            val token = requestPolicy.generation
            try {
                MobileAds.initialize(activity.applicationContext) {
                    activity.runOnUiThread {
                        if (accepts(token)) {
                            sdkReady = true
                            stateCallback?.invoke()
                            resumeAds()
                        }
                    }
                }
            } catch (error: Exception) {
                sdkFailed = true
                fail("initialize", error.message ?: error.javaClass.simpleName)
            }
        }
        if (!sdkReady) return
        layoutRoot.doOnLayout {
            if (canRequest() && !bannersInitialized) {
                bannersInitialized = true
                setupBanner(true)
                setupBanner(false)
            }
            if (canRequest()) { topSchedule?.resume(); bottomSchedule?.resume() }
        }
        if (!loadFailed) loadRewarded()
    }

    private fun setupBanner(isTop: Boolean) {
        val container = if (isTop) topContainer else bottomContainer
        val token = requestPolicy.generation
        var banner: BannerAdView? = null
        try {
            banner = BannerAdView(activity)
            val view = banner
            view.setAdUnitId(if (isTop) topId else bottomId)
            val width = (container.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels)
            view.setAdSize(BannerAdSize.stickySize(activity,
                (width / activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)))
            val schedule = BannerRefreshController(MainThreadScheduler(),
                BannerTimingRegistry.slot(activity, if (isTop) "top" else "bottom"),
                sdkOwnsRefresh = true, canLoad = ::canRequest,
                load = {
                    try { view.loadAd(AdRequest.Builder().build()) }
                    catch (error: Exception) {
                        (if (isTop) topSchedule else bottomSchedule)?.failed()
                        Log.w("MainAds", "banner request failed: ${error.message}")
                    }
                })
            if (isTop) { top = view; topSchedule = schedule } else { bottom = view; bottomSchedule = schedule }
            view.setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    if (!accepts(token)) return
                    if (isTop) topLoaded = true else bottomLoaded = true
                    schedule.loaded()
                }
                override fun onAdFailedToLoad(error: AdRequestError) {
                    if (!accepts(token)) return
                    schedule.failed()
                    Log.w("MainAds", "banner load: ${error.code}, ${error.description}")
                }
                override fun onImpression(impressionData: ImpressionData?) {
                    if (accepts(token)) schedule.impression()
                }
                override fun onAdClicked() {}
                override fun onLeftApplication() {}
                override fun onReturnedToApplication() {}
            })
            container.addView(view)
        } catch (error: Exception) {
            banner?.setBannerAdEventListener(null)
            banner?.destroy()
            Log.w("MainAds", "banner initialization failed: ${error.message}")
        }
    }

    private fun loadRewarded() {
        if (!canRequest() || !sdkReady || loading || rewarded != null || session != null) return
        val token = requestPolicy.generation
        loading = true
        loadFailed = false
        lastError = null
        stateCallback?.invoke()
        try {
            if (loader == null) loader = RewardedAdLoader(activity).apply {
                setAdLoadListener(object : RewardedAdLoadListener {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (!accepts(token)) { ad.setAdEventListener(null); return }
                        loading = false
                        loadFailed = false
                        rewarded = ad
                        stateCallback?.invoke()
                    }
                    override fun onAdFailedToLoad(error: AdRequestError) {
                        if (accepts(token)) fail("load", "${error.code}, ${error.description}")
                    }
                })
            }
            loader?.loadAd(AdRequestConfiguration.Builder(rewardedId).build())
        } catch (error: Exception) {
            fail("request", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun fail(stage: String, detail: String) {
        loading = false
        loadFailed = true
        lastError = "$stage: $detail"
        Log.w("MainAds", "Yandex rewarded $stage failed: unit=$rewardedId, $detail")
        stateCallback?.invoke()
    }

    fun retryRewarded() {
        if (!canRequest() || rewardedState != RewardedAdState.FAILED) return
        loadFailed = false
        if (sdkFailed) { sdkFailed = false; sdkStarted = false; resumeAds() }
        else loadRewarded()
    }

    fun showRewarded() {
        if (!canRequest() || session != null) return
        val ad = rewarded ?: run { messageCallback?.invoke(R.string.rewarded_ad_not_ready); return }
        val token = requestPolicy.generation
        val claim = RewardSession { rewardCallback?.invoke() }
        session = claim
        rewarded = null
        showing = ad
        ad.setAdEventListener(object : RewardedAdEventListener {
            override fun onAdShown() {}
            override fun onAdClicked() {}
            override fun onAdImpression(impressionData: ImpressionData?) {}
            override fun onRewarded(reward: Reward) {
                // Grant immediately even while the presentation pauses MainActivity.
                if (accepts(token) && session === claim) claim.reward()
            }
            override fun onAdDismissed() {
                if (accepts(token) && session === claim) {
                    endPresentation()
                    stateCallback?.invoke()
                    loadRewarded()
                }
            }
            override fun onAdFailedToShow(error: AdError) {
                if (accepts(token) && session === claim) {
                    endPresentation()
                    fail("show", error.description)
                }
            }
        })
        stateCallback?.invoke()
        try { ad.show(activity) }
        catch (error: Exception) { endPresentation(); fail("show", error.message ?: "SDK exception") }
    }

    private fun endPresentation() {
        session?.close()
        session = null
        showing?.setAdEventListener(null)
        showing = null
    }

    fun close() {
        if (closed) return
        closed = true
        resumed = false
        requestPolicy.close()
        topSchedule?.close()
        bottomSchedule?.close()
        listOfNotNull(top, bottom).forEach {
            it.setBannerAdEventListener(null)
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        top = null; bottom = null
        loader?.setAdLoadListener(null)
        loader = null
        rewarded?.setAdEventListener(null)
        rewarded = null
        endPresentation()
        loading = false
        rewardCallback = null; stateCallback = null; messageCallback = null
    }
}
