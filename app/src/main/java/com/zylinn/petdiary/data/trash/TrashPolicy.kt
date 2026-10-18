package com.zylinn.petdiary.data.trash

import java.time.Duration

/** Items stay in the trash for 7 days, then are purged permanently. */
object TrashPolicy {
    val RETENTION: Duration = Duration.ofDays(7)
    private val DAY_MS = Duration.ofDays(1).toMillis()

    /** Anything deleted at or before this instant is expired. */
    fun cutoff(nowMillis: Long): Long = nowMillis - RETENTION.toMillis()

    fun isExpired(deletedAt: Long, nowMillis: Long): Boolean = deletedAt <= cutoff(nowMillis)

    /** Days left before purge, rounded up ("剩餘 1 天" until the very end); 0 once expired. */
    fun remainingDays(deletedAt: Long, nowMillis: Long): Int {
        val left = deletedAt + RETENTION.toMillis() - nowMillis
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt()
    }
}
