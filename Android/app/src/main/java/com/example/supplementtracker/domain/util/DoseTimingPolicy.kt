package com.example.supplementtracker.domain.util

import com.example.supplementtracker.domain.model.IntakeStatus

/** Single source of truth for dose-state timing and adherence formulas. */
object DoseTimingPolicy {
    const val SOON_WINDOW_MS: Long = 20L * 60L * 1000L
    const val MISSED_AFTER_MS: Long = 2L * 60L * 60L * 1000L

    fun isDueSoon(scheduledAtEpochMs: Long, nowEpochMs: Long): Boolean =
        scheduledAtEpochMs > nowEpochMs && scheduledAtEpochMs - nowEpochMs <= SOON_WINDOW_MS

    fun isMissed(scheduledAtEpochMs: Long, nowEpochMs: Long): Boolean =
        scheduledAtEpochMs > 0L && nowEpochMs > scheduledAtEpochMs + MISSED_AFTER_MS

    fun isMissedSoon(scheduledAtEpochMs: Long, nowEpochMs: Long): Boolean {
        val missedAt = scheduledAtEpochMs + MISSED_AFTER_MS
        return scheduledAtEpochMs > 0L && nowEpochMs in (missedAt - SOON_WINDOW_MS) until missedAt
    }

    fun isLateTaken(status: String?, scheduledAtEpochMs: Long, updatedAtEpochMs: Long): Boolean =
        status == IntakeStatus.TAKEN.storageValue && updatedAtEpochMs > 0L &&
            updatedAtEpochMs > scheduledAtEpochMs + SOON_WINDOW_MS

    fun completionRate(taken: Int, skipped: Int): Double? {
        val total = taken + skipped
        return if (total <= 0) null else taken.toDouble() / total.toDouble()
    }

    fun completionPercent(taken: Int, skipped: Int): Int? =
        completionRate(taken, skipped)?.let { (it * 100.0).toInt() }
}
