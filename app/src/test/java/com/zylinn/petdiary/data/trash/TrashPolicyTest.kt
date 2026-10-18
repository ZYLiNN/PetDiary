package com.zylinn.petdiary.data.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class TrashPolicyTest {

    private val deletedAt = 1_800_000_000_000L
    private fun after(d: Duration) = deletedAt + d.toMillis()

    // T-06
    @Test fun stillPresentAt6Days23Hours() {
        assertFalse(TrashPolicy.isExpired(deletedAt, after(Duration.ofDays(6).plusHours(23))))
        assertFalse(TrashPolicy.isExpired(deletedAt, after(Duration.ofDays(7).minusMillis(1))))
    }

    @Test fun expiredAtSevenDays() {
        assertTrue(TrashPolicy.isExpired(deletedAt, after(Duration.ofDays(7))))
        assertTrue(TrashPolicy.isExpired(deletedAt, after(Duration.ofDays(30))))
    }

    // T-03 (value used by the trash screen in N7)
    @Test fun remainingDaysRoundsUp() {
        assertEquals(7, TrashPolicy.remainingDays(deletedAt, deletedAt))
        assertEquals(7, TrashPolicy.remainingDays(deletedAt, after(Duration.ofHours(1))))
        assertEquals(6, TrashPolicy.remainingDays(deletedAt, after(Duration.ofDays(1))))
        assertEquals(1, TrashPolicy.remainingDays(deletedAt, after(Duration.ofDays(6).plusHours(23))))
        assertEquals(0, TrashPolicy.remainingDays(deletedAt, after(Duration.ofDays(7))))
        assertEquals(0, TrashPolicy.remainingDays(deletedAt, after(Duration.ofDays(9))))
    }
}
