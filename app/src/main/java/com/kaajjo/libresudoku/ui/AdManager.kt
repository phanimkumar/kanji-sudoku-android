package com.kaajjo.libresudoku.ui

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*
import com.google.android.gms.ads.rewarded.*
import android.util.Log
import android.widget.Toast

object AdManager {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // 🔵 INTERSTITIAL
    fun loadInterstitial(context: Context) {
        val request = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            "ca-app-pub-4704776080952179/6658356421", // test id
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDone: () -> Unit) {
        if (interstitialAd != null) {

            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    Log.d("AdDebug", "Interstitial dismissed")

                    loadInterstitial(activity)
                    onDone()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    Log.d("AdDebug", "Interstitial failed: ${adError.message}")

                    loadInterstitial(activity)
                    onDone()
                }
            }

            interstitialAd?.show(activity)

        } else {
            Log.d("AdDebug", "Interstitial NULL")
            loadInterstitial(activity)
            onDone()
        }
    }

    // 🔵 REWARDED
    fun loadRewarded(context: Context) {
        val request = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            "ca-app-pub-4704776080952179/6140210627", // test id
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.show(activity) {
                onReward()
                Log.d("AdDebug", "RewardedAd = $rewardedAd")
            }
            rewardedAd = null
            loadRewarded(activity)
        } else {
            Log.d("AdDebug", "RewardedAd is NULL")
            Toast.makeText(activity, "Ad not ready", Toast.LENGTH_SHORT).show()
            loadRewarded(activity)
        }
    }
}
