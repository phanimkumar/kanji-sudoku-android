package com.kaajjo.libresudoku.daily

import android.content.Context
import com.kaajjo.libresudoku.core.qqwing.DailyPuzzleGenerator

/**
 * Remembers the daily challenge: which board is today's, and whether it was solved,
 * given up, and submitted to the leaderboard. Stored in SharedPreferences, so no
 * database change is needed.
 */
class DailyChallengeStore(context: Context) {

    enum class Status { InProgress, Solved, GaveUp }

    data class Entry(
        val dateKey: Int,
        val boardUid: Long,
        val status: Status,
        val timeMs: Long,
        val submitted: Boolean
    )

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): Int = DailyPuzzleGenerator.todaySeed()

    /** The most recent daily (may be from an earlier day). */
    fun current(): Entry? {
        val date = prefs.getInt(K_DATE, 0)
        val uid = prefs.getLong(K_UID, 0L)
        if (date == 0 || uid <= 0L) return null
        val status = runCatching { Status.valueOf(prefs.getString(K_STATUS, null) ?: "") }
            .getOrDefault(Status.InProgress)
        return Entry(
            dateKey = date,
            boardUid = uid,
            status = status,
            timeMs = prefs.getLong(K_TIME, 0L),
            submitted = prefs.getBoolean(K_SUBMITTED, false)
        )
    }

    /** Today's daily, or null if it hasn't been started today. */
    fun todayEntry(): Entry? = current()?.takeIf { it.dateKey == today() }

    fun startToday(boardUid: Long) {
        prefs.edit()
            .putInt(K_DATE, today())
            .putLong(K_UID, boardUid)
            .putString(K_STATUS, Status.InProgress.name)
            .putLong(K_TIME, 0L)
            .putBoolean(K_SUBMITTED, false)
            .apply()
    }

    /** True if [boardUid] is a daily-challenge board (today's or an earlier one). */
    fun isDaily(boardUid: Long): Boolean = current()?.boardUid == boardUid

    /** True if [boardUid] is today's daily, i.e. its time counts on today's leaderboard. */
    fun isTodaysDaily(boardUid: Long): Boolean = todayEntry()?.boardUid == boardUid

    fun markSolved(boardUid: Long, timeMs: Long) {
        val e = current() ?: return
        if (e.boardUid != boardUid || e.status != Status.InProgress) return
        prefs.edit()
            .putString(K_STATUS, Status.Solved.name)
            .putLong(K_TIME, timeMs)
            .putBoolean(K_SUBMITTED, false)
            .apply()
    }

    fun markGaveUp(boardUid: Long) {
        val e = current() ?: return
        if (e.boardUid != boardUid || e.status != Status.InProgress) return
        prefs.edit().putString(K_STATUS, Status.GaveUp.name).apply()
    }

    fun markSubmitted() {
        prefs.edit().putBoolean(K_SUBMITTED, true).apply()
    }

    /** Today's solved time that hasn't reached the leaderboard yet (e.g. solved offline). */
    fun pendingSubmission(): Entry? =
        todayEntry()?.takeIf { it.status == Status.Solved && !it.submitted && it.timeMs > 0 }

    private companion object {
        const val PREFS = "daily_challenge"
        const val K_DATE = "date"
        const val K_UID = "board_uid"
        const val K_STATUS = "status"
        const val K_TIME = "time_ms"
        const val K_SUBMITTED = "submitted"
    }
}
