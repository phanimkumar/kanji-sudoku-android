package com.kaajjo.libresudoku.daily

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.kaajjo.libresudoku.R

/**
 * Google Play Games: sign-in, submitting the daily-challenge time, showing the leaderboard.
 * Every call is safe offline or signed-out: it simply reports failure.
 */
object PlayGamesHelper {

    private const val RC_LEADERBOARD = 9004

    /** Call once from Application.onCreate. */
    fun initialize(context: Context) {
        runCatching { PlayGamesSdk.initialize(context.applicationContext) }
    }

    private fun dailyLeaderboardId(context: Context): String? =
        context.getString(R.string.leaderboard_daily_challenge).takeIf { it.startsWith("Cg") }

    /**
     * Submits [timeMs] to the Daily Challenge leaderboard without showing any UI.
     * [onResult] is true only if Play Games confirmed the score.
     */
    fun submitDailyTime(activity: Activity, timeMs: Long, onResult: (Boolean) -> Unit = {}) {
        val id = dailyLeaderboardId(activity)
        if (id == null || timeMs <= 0L) {
            onResult(false)
            return
        }
        withSignIn(activity, interactive = false) { signedIn ->
            if (!signedIn) {
                onResult(false)
                return@withSignIn
            }
            runCatching {
                PlayGames.getLeaderboardsClient(activity)
                    .submitScoreImmediate(id, timeMs)
                    .addOnCompleteListener { onResult(it.isSuccessful) }
            }.onFailure { onResult(false) }
        }
    }

    /** Opens the Daily Challenge leaderboard on the "Today" tab, signing in first if needed. */
    fun showDailyLeaderboard(activity: Activity) {
        val id = dailyLeaderboardId(activity)
        if (id == null) {
            toast(activity, "Leaderboard is not set up yet")
            return
        }
        withSignIn(activity, interactive = true) { signedIn ->
            if (!signedIn) {
                toast(activity, "Couldn't sign in to Google Play Games")
                return@withSignIn
            }
            runCatching {
                PlayGames.getLeaderboardsClient(activity)
                    .getLeaderboardIntent(id, LeaderboardVariant.TIME_SPAN_DAILY)
                    .addOnSuccessListener { intent ->
                        @Suppress("DEPRECATION")
                        activity.startActivityForResult(intent, RC_LEADERBOARD)
                    }
                    .addOnFailureListener {
                        toast(activity, "Leaderboard unavailable. Check your connection.")
                    }
            }.onFailure { toast(activity, "Leaderboard unavailable. Check your connection.") }
        }
    }

    private fun withSignIn(activity: Activity, interactive: Boolean, block: (Boolean) -> Unit) {
        val client = runCatching { PlayGames.getGamesSignInClient(activity) }.getOrNull()
        if (client == null) {
            block(false)
            return
        }
        client.isAuthenticated().addOnCompleteListener { task ->
            val signedIn = task.isSuccessful && task.result?.isAuthenticated() == true
            when {
                signedIn -> block(true)
                !interactive -> block(false)
                else -> client.signIn().addOnCompleteListener { s ->
                    block(s.isSuccessful && s.result?.isAuthenticated() == true)
                }
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
