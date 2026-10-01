package com.timer.minimal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One completed workout session. */
data class SessionRecord(
    val startedAtEpochMs: Long,
    val mode: TimerMode,
    /** Duration in seconds actually completed. */
    val durationSec: Int
)

/**
 * Stores the log of finished sessions as a bounded JSON array in SharedPreferences.
 * A workout timer produces very little data, so a database would be overkill.
 */
class HistoryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "timer_history"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_SESSION_DAY = "last_session_day"
        const val MAX_RECORDS = 100

        // Per-record JSON field names.
        private const val KEY_STARTED_AT = "startedAt"
        private const val KEY_MODE = "mode"
        private const val KEY_DURATION = "duration"
        private const val MILLIS_PER_DAY = 86_400_000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(session: SessionRecord) {
        val records = load().toMutableList()
        records += session
        // Keep only the most recent entries so the preference never grows unbounded.
        val trimmed = if (records.size > MAX_RECORDS) {
            records.takeLast(MAX_RECORDS)
        } else {
            records
        }
        save(trimmed)
        updateStreak(session)
    }

    fun load(): List<SessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val mode = runCatching { TimerMode.valueOf(obj.optString(KEY_MODE)) }
                    .getOrDefault(TimerMode.ROUTINE)
                SessionRecord(
                    startedAtEpochMs = obj.optLong(KEY_STARTED_AT),
                    mode = mode,
                    durationSec = obj.optInt(KEY_DURATION)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SESSIONS)
            .remove(KEY_STREAK_DAYS)
            .remove(KEY_LAST_SESSION_DAY)
            .apply()
    }

    /** Number of consecutive days with at least one session, counting today or yesterday. */
    fun streakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    private fun updateStreak(session: SessionRecord) {
        val today = dayIndex(session.startedAtEpochMs)
        val last = prefs.getLong(KEY_LAST_SESSION_DAY, -1L)
        val current = prefs.getInt(KEY_STREAK_DAYS, 0)

        val next = when {
            last == today -> current
            last == today - 1 -> current + 1
            else -> 1
        }
        prefs.edit()
            .putInt(KEY_STREAK_DAYS, next)
            .putLong(KEY_LAST_SESSION_DAY, today)
            .apply()
    }

    private fun save(records: List<SessionRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put(KEY_STARTED_AT, record.startedAtEpochMs)
                    put(KEY_MODE, record.mode.name)
                    put(KEY_DURATION, record.durationSec)
                }
            )
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    /** Local-day index so sessions are grouped by calendar day, not by 24h windows. */
    private fun dayIndex(epochMs: Long): Long =
        java.util.Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis / MILLIS_PER_DAY
}
