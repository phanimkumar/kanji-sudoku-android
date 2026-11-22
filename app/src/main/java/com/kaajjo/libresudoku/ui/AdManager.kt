package com.kaajjo.libresudoku.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.kaajjo.libresudoku.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {

    private const val TAG = "AdManager"

    // Debug builds always use Google's official test units, so development and
    // testing never generate live impressions on the real account.
    private val INTERSTITIAL_ID =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712"
        else "ca-app-pub-4704776080952179/6658356421"

    private val REWARDED_ID =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/5224354917"
        else "ca-app-pub-4704776080952179/6140210627"

    val BANNER_ID =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/9214589741"
        else "ca-app-pub-4704776080952179/9723503468"

    // Loaded full-screen ads go stale after about an hour; refresh a little earlier.
    private const val AD_TTL_MS = 55 * 60 * 1000L
    private const val RETRY_DELAY_MS = 60 * 1000L

    // Interstitial pacing
    private const val FREE_COMPLETIONS = 3                    // no interstitial for the first N solves
    private const val MIN_INTERSTITIAL_GAP_MS = 3 * 60 * 1000L
    private const val PREFS = "ad_pacing"
    private const val KEY_COMPLETIONS = "completed_puzzles"

    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True once the consent flow allows ad requests. Compose state, so the banner reacts. */
    var adsAllowed by mutableStateOf(false)
        private set

    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var interstitialLoading = false
    private var interstitialRetryScheduled = false
    private var lastInterstitialShownAt = 0L

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadedAt = 0L
    private var rewardedLoading = false
    private var rewardedRetryScheduled = false

    private fun expired(loadedAt: Long) =
        SystemClock.elapsedRealtime() - loadedAt > AD_TTL_MS

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    // ───────────────────── init ─────────────────────

    /**
     * Call only after consent allows ads (ConsentInformation.canRequestAds()).
     * Safe to call repeatedly. Must be called on the main thread.
     */
    fun initOnce(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        adsAllowed = true
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) { log("MobileAds initialized") }
            withContext(Dispatchers.Main) { preload(app) }
        }
    }

    /** Load whatever isn't already loaded. Cheap to call; does nothing before consent. */
    fun preload(context: Context) {
        if (!adsAllowed) return
        loadInterstitial(context)
        loadRewarded(context)
    }

    // ───────────────────── interstitial ─────────────────────

    fun loadInterstitial(context: Context) {
        if (!adsAllowed) return
        if (interstitialLoading) return
        if (interstitialAd != null && !expired(interstitialLoadedAt)) return

        interstitialLoading = true
        val app = context.applicationContext
        InterstitialAd.load(
            app,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialLoadedAt = SystemClock.elapsedRealtime()
                    interstitialLoading = false
                    log("Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    interstitialLoading = false
                    log("Interstitial failed to load: ${error.message}")
                    if (!interstitialRetryScheduled) {
                        interstitialRetryScheduled = true
                        mainHandler.postDelayed({
                            interstitialRetryScheduled = false
                            loadInterstitial(app)
                        }, RETRY_DELAY_MS)
                    }
                }
            }
        )
    }

    /**
     * Call when the player leaves a solved puzzle (not when it is solved).
     * Applies pacing: skips the first few solves and enforces a minimum gap.
     * [onDone] is always called exactly once, after the ad closes or immediately.
     */
    fun maybeShowInterstitial(activity: Activity, onDone: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val completions = prefs.getInt(KEY_COMPLETIONS, 0) + 1
        prefs.edit().putInt(KEY_COMPLETIONS, completions).apply()

        val now = SystemClock.elapsedRealtime()
        val tooSoon = lastInterstitialShownAt != 0L &&
                now - lastInterstitialShownAt < MIN_INTERSTITIAL_GAP_MS

        if (!adsAllowed || completions <= FREE_COMPLETIONS || tooSoon) {
            onDone()
            return
        }
        showInterstitial(activity, onDone)
    }

    /** Shows an interstitial if one is ready; otherwise calls [onDone] immediately. */
    fun showInterstitial(activity: Activity, onDone: () -> Unit) {
        val ad = interstitialAd?.takeUnless { expired(interstitialLoadedAt) }
        interstitialAd = null

        if (ad == null) {
            log("Interstitial not ready")
            loadInterstitial(activity)
            onDone()
            return
        }

        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            loadInterstitial(activity)
            onDone()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                log("Interstitial failed to show: ${adError.message}")
                finish()
            }
        }
        lastInterstitialShownAt = SystemClock.elapsedRealtime()
        ad.show(activity)
    }

    // ───────────────────── rewarded ─────────────────────

    fun loadRewarded(context: Context) {
        if (!adsAllowed) return
        if (rewardedLoading) return
        if (rewardedAd != null && !expired(rewardedLoadedAt)) return

        rewardedLoading = true
        val app = context.applicationContext
        RewardedAd.load(
            app,
            REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardedLoadedAt = SystemClock.elapsedRealtime()
                    rewardedLoading = false
                    log("Rewarded loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    rewardedLoading = false
                    log("Rewarded failed to load: ${error.message}")
                    if (!rewardedRetryScheduled) {
                        rewardedRetryScheduled = true
                        mainHandler.postDelayed({
                            rewardedRetryScheduled = false
                            loadRewarded(app)
                        }, RETRY_DELAY_MS)
                    }
                }
            }
        )
    }

    /**
     * Shows a rewarded ad. [onResult] is called exactly once:
     *  - with true after the ad closes, if the reward was earned;
     *  - with false if the ad was closed early, failed to show, or wasn't ready.
     * No reward is ever granted without an ad.
     */
    fun showRewarded(activity: Activity, onResult: (earned: Boolean) -> Unit) {
        val ad = rewardedAd?.takeUnless { expired(rewardedLoadedAt) }
        rewardedAd = null

        if (ad == null) {
            Toast.makeText(
                activity,
                "Ad not available right now. Please try again shortly.",
                Toast.LENGTH_SHORT
            ).show()
            loadRewarded(activity)
            onResult(false)
            return
        }

        var earned = false
        var finished = false
        fun finish(result: Boolean) {
            if (finished) return
            finished = true
            loadRewarded(activity)
            onResult(result)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish(earned)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                log("Rewarded failed to show: ${adError.message}")
                finish(false)
            }
        }
        ad.show(activity) { earned = true }
    }
}
