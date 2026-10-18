package com.zylinn.petdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PetAgeTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun label(birth: String, today: String, estimated: Boolean = false) =
        PetAge.label(d(birth), estimated, d(today))

    // P1-05
    @Test fun yearsAndMonths() = assertEquals("3 歲 2 個月", label("2023-08-10", "2026-10-19"))

    @Test fun exactYears() = assertEquals("3 歲", label("2023-10-19", "2026-10-19"))

    @Test fun monthsOnly() = assertEquals("5 個月", label("2026-05-01", "2026-10-19"))

    @Test fun daysOnly() = assertEquals("12 天", label("2026-10-07", "2026-10-19"))

    @Test fun bornToday() = assertEquals("今天出生", label("2026-10-19", "2026-10-19"))

    @Test fun futureBirthday() = assertEquals("尚未出生", label("2026-10-20", "2026-10-19"))

    @Test fun dayBeforeMonthBoundaryIsNotYetAMonth() {
        assertEquals("29 天", label("2026-09-19", "2026-10-18"))
        assertEquals("1 個月", label("2026-09-19", "2026-10-19"))
    }

    @Test fun crossesYearEnd() = assertEquals("2 個月", label("2025-11-30", "2026-01-30"))

    @Test fun shortMonthEnd() {
        // Jan 31 → Feb 28 (non-leap) is 28 days, not yet a month.
        assertEquals("28 天", label("2026-01-31", "2026-02-28"))
        assertEquals("1 個月", label("2026-01-31", "2026-03-01"))
    }

    @Test fun leapDayBirthday() {
        assertEquals("11 個月", label("2024-02-29", "2025-02-28"))
        assertEquals("1 歲", label("2024-02-29", "2025-03-01"))
        assertEquals("4 歲", label("2024-02-29", "2028-02-29"))
    }

    // P1-04
    @Test fun estimatedBirthdayFromYears() {
        val birth = PetAge.estimatedBirthday(years = 3, today = d("2026-10-19"))
        assertEquals(d("2023-10-19"), birth)
        assertEquals("約 3 歲", PetAge.label(birth, estimated = true, today = d("2026-10-19")))
    }

    @Test fun estimatedLabelGrowsWithTime() {
        val birth = PetAge.estimatedBirthday(years = 3, today = d("2026-10-19"))
        assertEquals("約 3 歲", PetAge.label(birth, true, d("2027-10-18")))
        assertEquals("約 4 歲", PetAge.label(birth, true, d("2027-10-19")))
    }

    @Test fun estimatedYoungPetUsesMonths() {
        val birth = PetAge.estimatedBirthday(years = 0, months = 5, today = d("2026-10-19"))
        assertEquals(d("2026-05-19"), birth)
        assertEquals("約 5 個月", PetAge.label(birth, true, d("2026-10-19")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAgeRejected() {
        PetAge.estimatedBirthday(years = -1, today = d("2026-10-19"))
    }
}
