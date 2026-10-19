package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.domain.RecordFormat.CostInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecordFormatTest {

    private val today = LocalDate.parse("2026-10-19")
    private fun d(s: String) = LocalDate.parse(s)
    private fun record(type: RecordType) = Record(
        id = "r", petId = "p", type = type, dateEpochDay = today.toEpochDay(), minuteOfDay = 490,
        createdAt = 0, updatedAt = 0,
    )

    // P2-08
    @Test fun costAcceptsDigitsAndCommas() {
        assertEquals(CostInput.Value(1200), RecordFormat.parseCost("1200"))
        assertEquals(CostInput.Value(1200), RecordFormat.parseCost("1,200"))
        assertEquals(CostInput.Value(0), RecordFormat.parseCost("0"))
        assertEquals(CostInput.Empty, RecordFormat.parseCost(""))
        assertEquals(CostInput.Empty, RecordFormat.parseCost("  "))
    }

    @Test fun costRejectsNonDigitsAndHugeValues() {
        assertEquals(CostInput.Invalid, RecordFormat.parseCost("12.5"))
        assertEquals(CostInput.Invalid, RecordFormat.parseCost("-300"))
        assertEquals(CostInput.Invalid, RecordFormat.parseCost("三百"))
        assertEquals(CostInput.Invalid, RecordFormat.parseCost("99999999999999999999999"))
        assertEquals(CostInput.Value(RecordFormat.COST_MAX), RecordFormat.parseCost(RecordFormat.COST_MAX.toString()))
    }

    @Test fun costShownWithThousandsSeparator() {
        assertEquals("NT$ 1,200", RecordFormat.cost(1200))
        assertEquals("NT$ 350", RecordFormat.cost(350))
        assertEquals("NT$ 1,234,567", RecordFormat.cost(1_234_567))
        assertEquals("NT$ 0", RecordFormat.cost(0))
    }

    @Test fun numbersDropTrailingZeros() {
        assertEquals("1.5", RecordFormat.number(1.5))
        assertEquals("2", RecordFormat.number(2.0))
        assertEquals("0.25", RecordFormat.number(0.25))
    }

    @Test fun timeIsZeroPadded() {
        assertEquals("08:10", RecordFormat.time(490))
        assertEquals("00:00", RecordFormat.time(0))
        assertEquals("23:59", RecordFormat.time(1439))
    }

    // P2-03
    @Test fun daysAgoLabels() {
        assertEquals("今天", RecordFormat.daysAgo(today, today))
        assertEquals("昨天", RecordFormat.daysAgo(d("2026-10-18"), today))
        assertEquals("45 天前", RecordFormat.daysAgo(d("2026-09-04"), today))
        assertEquals("120 天前", RecordFormat.daysAgo(d("2026-06-21"), today))
        assertEquals("明天", RecordFormat.daysAgo(d("2026-10-20"), today))
        assertEquals("3 天後", RecordFormat.daysAgo(d("2026-10-22"), today))
        assertEquals("驅蟲 45 天前", RecordFormat.lastChip(RecordType.DEWORMING, d("2026-09-04"), today))
    }

    @Test fun shortDateShowsYearOnlyWhenDifferent() {
        assertEquals("11/18", RecordFormat.shortDate(d("2026-11-18"), today))
        assertEquals("2027/1/5", RecordFormat.shortDate(d("2027-01-05"), today))
    }

    @Test fun feedingSummary() {
        val r = record(RecordType.FEEDING).copy(portions = 1.5, drankWater = true, supplementIds = "s1,s2,gone")
        assertEquals("主食 1.5 份・有喝水・保健：魚油、益生菌", RecordFormat.summary(r, mapOf("s1" to "魚油", "s2" to "益生菌"), today))
    }

    @Test fun medicineSummary() {
        val r = record(RecordType.MEDICINE).copy(title = "心臟藥", detail = "0.5 顆", cost = 350, nextEpochDay = d("2026-11-18").toEpochDay())
        assertEquals("心臟藥・0.5 顆・NT$ 350・下次 11/18", RecordFormat.summary(r, emptyMap(), today))
    }

    @Test fun dewormingSummaryLeadsWithKind() {
        val r = record(RecordType.DEWORMING).copy(dewormKind = DewormKind.EXTERNAL, title = "蚤不到", hospital = "愛心", cost = 1200)
        assertEquals("外驅・蚤不到・愛心・NT$ 1,200", RecordFormat.summary(r, emptyMap(), today))
    }

    @Test fun durationLabels() {
        assertEquals("30 分鐘", RecordFormat.duration(30))
        assertEquals("1 小時", RecordFormat.duration(60))
        assertEquals("1 小時 20 分", RecordFormat.duration(80))
    }

    @Test fun walkSummary() {
        assertEquals("45 分鐘・有喝水", RecordFormat.summary(record(RecordType.WALK).copy(durationMin = 45, drankWater = true), emptyMap(), today))
        assertEquals("30 分鐘", RecordFormat.summary(record(RecordType.WALK).copy(durationMin = 30, drankWater = false), emptyMap(), today))
    }

    @Test fun weightSummary() =
        assertEquals("4.2 kg", RecordFormat.summary(record(RecordType.WEIGHT).copy(weightKg = 4.2), emptyMap(), today))

    @Test fun emptyRecordHasEmptySummary() =
        assertEquals("", RecordFormat.summary(record(RecordType.ABNORMAL), emptyMap(), today))
}
