package com.zylinn.petdiary.domain

import java.time.LocalDate
import java.time.Period

/** Age shown on pet tiles, computed from the stored birthday so it never goes stale. */
object PetAge {

    /**
     * Human-readable age:
     * - under 1 month: "12 天" (birthday today: "今天出生")
     * - under 1 year: "5 個月"
     * - otherwise: "3 歲" / "3 歲 2 個月"
     * Birthdays flagged as estimates (saved by v0.1–v0.3, no longer created) drop the finer unit: "約 3 歲".
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
