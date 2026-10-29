package com.ovehbe.junkboy.work

object RetentionPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Cutoff for messages to delete, or null when retention is disabled. */
    fun cutoffMillis(now: Long, days: Int): Long? =
        if (days <= 0) null else now - days.toLong() * DAY_MS
}
