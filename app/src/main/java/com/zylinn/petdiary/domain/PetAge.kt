package com.zylinn.petdiary.domain

import java.time.LocalDate
import java.time.Period

/** Age shown on pet tiles, computed from the stored birthday so it never goes stale. */
object PetAge {

    /**
     * Birthday to store when the user only knows an approximate age ("about 3 years").
     * Displayed with an "約" prefix via [label].
     */
    fun estimatedBirthday(years: Int, months: Int = 0, today: LocalDate): LocalDate {
        require(years >= 0 && months >= 0) { "age must not be negative" }
        return today.minusYears(years.toLong()).minusMonths(months.toLong())
    }

    /**
     * Human-readable age:
     * - under 1 month: "12 天" (birthday today: "今天出生")
     * - under 1 year: "5 個月"
     * - otherwise: "3 歲" / "3 歲 2 個月"
     * Estimated birthdays drop the finer unit: "約 3 歲", "約 5 個月".
     */
    fun label(birthday: LocalDate, estimated: Boolean, today: LocalDate): String {
        if (birthday.isAfter(today)) return "尚未出生"
        val p = Period.between(birthday, today)
        val text = when {
            p.years == 0 && p.months == 0 && p.days == 0 -> return "今天出生"
            p.years == 0 && p.months == 0 -> "${p.days} 天"
            p.years == 0 -> "${p.months} 個月"
            estimated || p.months == 0 -> "${p.years} 歲"
            else -> "${p.years} 歲 ${p.months} 個月"
        }
        return if (estimated) "約 $text" else text
    }
}
