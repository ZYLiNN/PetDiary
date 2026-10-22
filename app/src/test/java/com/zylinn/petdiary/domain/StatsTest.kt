package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-19") // Monday
    private val pets = listOf("cat" to "雷米", "dog" to "雷利")
    private var n = 0

    private fun rec(pet: String, type: RecordType, day: String, f: Record.() -> Record = { this }) =
        Record(id = "r${n++}", petId = pet, type = type, dateEpochDay = d(day).toEpochDay(), minuteOfDay = 600, createdAt = 0, updatedAt = 0).f()

    private fun compute(metric: StatMetric, span: DateSpan, vararg r: Record) = StatsCalculator.compute(metric, span, pets, r.toList(), today)

    /** Same, but "today" is the last day of the week so no day of that week counts as future. */
    private fun computeAtWeekEnd(metric: StatMetric, vararg r: Record) =
        StatsCalculator.compute(metric, StatsRanges.span(RangeKind.WEEK, 0, today), pets, r.toList(), d("2026-10-24"))

    // ---- ranges ----

    @Test fun weekIsSundayToSaturday() {
        val s = StatsRanges.span(RangeKind.WEEK, 0, today)
        assertEquals(d("2026-10-18"), s.from)
        assertEquals(d("2026-10-24"), s.to)
        assertEquals(7, s.days)
    }

    @Test fun offsetsMovePeriods() {
        assertEquals(d("2026-10-11"), StatsRanges.span(RangeKind.WEEK, -1, today).from)
        assertEquals(DateSpan(d("2026-09-01"), d("2026-09-30")), StatsRanges.span(RangeKind.MONTH, -1, today))
        assertEquals(DateSpan(d("2027-01-01"), d("2027-12-31")), StatsRanges.span(RangeKind.YEAR, 1, today))
        assertEquals(DateSpan(d("2028-02-01"), d("2028-02-29")), StatsRanges.span(RangeKind.MONTH, 16, today))
    }

    // P5-08
    @Test fun customRangeNeverStartsAfterItEnds() {
        assertFalse(StatsRanges.isValidCustom(d("2026-10-20"), d("2026-10-19")))
        assertTrue(StatsRanges.isValidCustom(d("2026-10-19"), d("2026-10-19")))
        assertTrue(StatsRanges.isValidCustom(null, d("2026-10-19")))
        val s = StatsRanges.span(RangeKind.CUSTOM, 0, today, d("2026-10-20"), d("2026-10-10"))
        assertEquals(DateSpan(d("2026-10-10"), d("2026-10-20")), s)
    }

    @Test fun granularityByLength() {
        assertEquals(Granularity.DAY, StatsRanges.granularity(StatsRanges.span(RangeKind.MONTH, 0, today)))
        assertEquals(Granularity.WEEK, StatsRanges.granularity(DateSpan(d("2026-01-01"), d("2026-04-30"))))
        assertEquals(Granularity.MONTH, StatsRanges.granularity(StatsRanges.span(RangeKind.YEAR, 0, today)))
    }

    @Test fun bucketsCoverTheSpanExactly() {
        listOf(StatsRanges.span(RangeKind.WEEK, 0, today), StatsRanges.span(RangeKind.MONTH, 0, today),
            DateSpan(d("2026-01-03"), d("2026-05-20")), StatsRanges.span(RangeKind.YEAR, 0, today), DateSpan(d("2025-11-15"), d("2026-10-19")))
            .forEach { span ->
                val b = StatsRanges.buckets(span)
                assertEquals(span.from, b.first().start)
                assertEquals(span.to, b.last().end)
                assertEquals(span.days, b.sumOf { it.days })
                b.zipWithNext().forEach { (x, y) -> assertEquals(x.end.plusDays(1), y.start) }
            }
        assertEquals(12, StatsRanges.buckets(StatsRanges.span(RangeKind.YEAR, 0, today)).size)
        assertEquals("10月", StatsRanges.buckets(StatsRanges.span(RangeKind.YEAR, 0, today))[9].label)
    }

    @Test fun titles() {
        assertEquals("10/18 – 10/24", StatsRanges.title(RangeKind.WEEK, StatsRanges.span(RangeKind.WEEK, 0, today), today))
        assertEquals("2026 年 10 月", StatsRanges.title(RangeKind.MONTH, StatsRanges.span(RangeKind.MONTH, 0, today), today))
        assertEquals("2026 年", StatsRanges.title(RangeKind.YEAR, StatsRanges.span(RangeKind.YEAR, 0, today), today))
    }

    // ---- P5-06: numbers match the raw records ----

    private val week = StatsRanges.span(RangeKind.WEEK, 0, today)

    @Test fun feedingSumsPortionsPerDayPerPet() {
        val r = computeAtWeekEnd(
            StatMetric.FEEDING,
            rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 1.0, drankWater = true) },
            rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 0.5) },
            rec("cat", RecordType.FEEDING, "2026-10-20") { copy(portions = 2.0) },
            rec("dog", RecordType.FEEDING, "2026-10-19") { copy(portions = 3.0, drankWater = true) },
            rec("cat", RecordType.FEEDING, "2026-10-10") { copy(portions = 9.0) }, // outside the week
            rec("cat", RecordType.WEIGHT, "2026-10-19") { copy(weightKg = 4.0) }, // other type
        )
        val cat = r.series[0]
        assertEquals(listOf(0.0, 1.5, 2.0, 0.0, 0.0, 0.0, 0.0), cat.values)
        assertEquals(3.5, cat.total, 0.0)
        assertEquals("有記錄的日子平均每天 1.75 份（共 3.5 份）・有喝水 1 次", cat.summary)
        assertEquals(3.0, r.series[1].values[1]!!, 0.0)
        assertTrue(r.hasData)
    }

    @Test fun walkAveragesAndCountsChecklist() {
        val r = computeAtWeekEnd(
            StatMetric.WALK,
            rec("dog", RecordType.WALK, "2026-10-19") { copy(durationMin = 30, drankWater = true, peed = true) },
            rec("dog", RecordType.WALK, "2026-10-19") { copy(durationMin = 45, pooped = true) },
            rec("dog", RecordType.WALK, "2026-10-21") { copy(durationMin = 60, drankWater = true, peed = true, pooped = true) },
        )
        val dog = r.series[1]
        assertEquals(75.0, dog.values[1]!!, 0.0)
        assertEquals(60.0, dog.values[3]!!, 0.0)
        assertEquals(135.0, dog.total, 0.0)
        assertEquals("共 3 次・有記錄的日子平均每天 1 小時 7 分（共 2 小時 15 分）・有喝水 2 次・小便 2 次・大便 2 次", dog.summary)
    }

    @Test fun yearBucketsUseDailyAverage() {
        val year = StatsRanges.span(RangeKind.YEAR, 0, today)
        val r = compute(StatMetric.FEEDING, year, rec("cat", RecordType.FEEDING, "2026-01-05") { copy(portions = 31.0) })
        assertEquals(1.0, r.series[0].values[0]!!, 1e-9) // 31 portions over 31 days of January
        assertEquals(0.0, r.series[0].values[1]!!, 0.0)
    }

    @Test fun weightHasGapsAndTrend() {
        val r = compute(
            StatMetric.WEIGHT, week,
            rec("cat", RecordType.WEIGHT, "2026-10-18") { copy(weightKg = 4.0) },
            rec("cat", RecordType.WEIGHT, "2026-10-21") { copy(weightKg = 4.2) },
            rec("cat", RecordType.WEIGHT, "2026-10-21") { copy(weightKg = 4.4, minuteOfDay = 900) },
        )
        val cat = r.series[0]
        assertEquals(4.0, cat.values[0]!!, 0.0)
        assertNull(cat.values[1])
        assertEquals(4.3, cat.values[3]!!, 1e-9)
        assertEquals("最新 4.4 kg・這段期間 +0.4 kg", cat.summary)
        assertEquals("這段期間沒有體重紀錄", r.series[1].summary)
    }

    @Test fun weightLoss() {
        val r = compute(
            StatMetric.WEIGHT, week,
            rec("dog", RecordType.WEIGHT, "2026-10-18") { copy(weightKg = 20.0) },
            rec("dog", RecordType.WEIGHT, "2026-10-24") { copy(weightKg = 19.5) },
        )
        assertEquals("最新 19.5 kg・這段期間 −0.5 kg", r.series[1].summary)
    }

    @Test fun costStacksAllTypesAndShowsTotal() {
        val r = compute(
            StatMetric.COST, week,
            rec("cat", RecordType.VET, "2026-10-19") { copy(cost = 1200) },
            rec("cat", RecordType.GROOMING, "2026-10-19") { copy(cost = 800) },
            rec("dog", RecordType.VACCINE, "2026-10-22") { copy(cost = 650) },
            rec("dog", RecordType.FEEDING, "2026-10-22"), // no cost → 0
        )
        assertEquals(2000.0, r.series[0].values[1]!!, 0.0)
        assertEquals(650.0, r.series[1].values[4]!!, 0.0)
        assertEquals("總花費 NT$ 2,650", r.headline)
        assertEquals("共 NT$ 650", r.series[1].summary)
    }

    // P14-03 (D84): shared purchases form their own series; the breakdown splits purchases by category
    @Test fun costIncludesSharedPurchasesAndBreaksDownByCategory() {
        val withFamily = pets + ("family" to "全家共用")
        val r = StatsCalculator.compute(
            StatMetric.COST, week, withFamily,
            listOf(
                rec("cat", RecordType.VET, "2026-10-19") { copy(cost = 3000) },
                rec("family", RecordType.PURCHASE, "2026-10-20") { copy(cost = 1200, category = "food") },
                rec("dog", RecordType.PURCHASE, "2026-10-21") { copy(cost = 300, category = "c1") },
            ),
            today, categoryNames = mapOf("food" to "飼料", "c1" to "貓砂"),
        )
        assertEquals("總花費 NT$ 4,500", r.headline)
        assertEquals("共 NT$ 1,200", r.series[2].summary)
        assertEquals("就診 NT$ 3,000・飼料 NT$ 1,200・貓砂 NT$ 300", r.breakdown)
        // Narrowed to one pet: no family series, and one group needs no breakdown.
        val cat = StatsCalculator.compute(StatMetric.COST, week, listOf("cat" to "雷米"), listOf(rec("cat", RecordType.VET, "2026-10-19") { copy(cost = 3000) }), today)
        assertEquals(null, cat.breakdown)
    }

    @Test fun countMetricsCountPerBucket() {
        val r = compute(
            StatMetric.DEWORMING, StatsRanges.span(RangeKind.YEAR, 0, today),
            rec("cat", RecordType.DEWORMING, "2026-03-01"), rec("cat", RecordType.DEWORMING, "2026-03-20"),
            rec("cat", RecordType.DEWORMING, "2026-09-10"), rec("dog", RecordType.DEWORMING, "2026-09-11"),
            rec("cat", RecordType.VACCINE, "2026-09-11"),
        )
        assertEquals(2.0, r.series[0].values[2]!!, 0.0)
        assertEquals(1.0, r.series[0].values[8]!!, 0.0)
        assertEquals("共 3 次・最近一次 9/10（39 天前）", r.series[0].summary)
        assertEquals(1.0, r.series[1].total, 0.0)
    }

    // P5-07
    @Test fun noDataIsReportedAsEmpty() {
        val r = compute(StatMetric.VACCINE, week)
        assertFalse(r.hasData)
        assertEquals("這段期間沒有紀錄", r.series[0].summary)
        assertNull(r.headline)
        val noCost = compute(StatMetric.COST, week, rec("cat", RecordType.FEEDING, "2026-10-19"))
        assertFalse(noCost.hasData)
        assertNull(noCost.headline)
    }

    @Test fun axisStartsAtZeroWithReadableSteps() {
        fun ticks(max: Double) = StatsCalculator.axisFromZero(max).let { a -> (0..a.ticks).map { Math.round(a.value(it) * 1e6) / 1e6 } }
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), ticks(3.5))
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0), ticks(5.0))
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0), ticks(1700.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), ticks(0.0)) // nothing to scale: a plain 0–4 grid
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6), ticks(0.45))
    }

    @Test fun weightAxisIsFittedAroundTheData() {
        val a = StatsCalculator.axisAround(4.0, 4.4)
        assertTrue(a.lo > 0.0 && a.lo <= 3.5)
        assertTrue(a.hi >= 4.9)
        assertTrue(a.ticks in 3..8)
        val b = StatsCalculator.axisAround(19.5, 20.5)
        assertEquals(0.0, (b.lo / b.step) - Math.round(b.lo / b.step), 1e-9)
    }

    @Test fun futureDaysAreGapsNotZeros() {
        val r = compute(StatMetric.FEEDING, week, rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 1.0) })
        val v = r.series[0].values
        assertEquals(0.0, v[0]!!, 0.0) // Sunday 10/18: past, nothing recorded
        assertEquals(1.0, v[1]!!, 0.0)
        assertNull(v[2]) // Tuesday 10/20 hasn't happened
        assertNull(v[6])
    }

    // P12-03 (D76): feeding can be narrowed to one kind; "all" adds a per-kind breakdown; old records count as dry
    @Test fun feedingByKind() {
        val recs = listOf(
            rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 2.0) }, // before P12: dry
            rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 1.0, foodKind = FoodKind.WET) },
            rec("cat", RecordType.FEEDING, "2026-10-19") { copy(portions = 0.5, foodKind = FoodKind.SNACK) },
        )
        val span = StatsRanges.span(RangeKind.WEEK, 0, today)
        val all = StatsCalculator.compute(StatMetric.FEEDING, span, pets, recs, today)
        assertEquals(3.5, all.series[0].total, 0.0)
        assertTrue(all.series[0].summary, all.series[0].summary.contains("（乾食 2 份・濕食 1 份・零食 0.5 份）"))
        val snack = StatsCalculator.compute(StatMetric.FEEDING, span, pets, recs, today, FoodKind.SNACK)
        assertEquals(0.5, snack.series[0].total, 0.0)
        assertFalse(snack.series[0].summary.contains("乾食"))
        assertEquals(2.0, StatsCalculator.compute(StatMetric.FEEDING, span, pets, recs, today, FoodKind.DRY).series[0].total, 0.0)
        assertFalse(StatsCalculator.compute(StatMetric.FEEDING, span, pets, recs, today, FoodKind.MIXED).hasData)
    }

    // P12-03 (D77): play minutes like walks
    @Test fun playMinutes() {
        val r = computeAtWeekEnd(StatMetric.PLAY,
            rec("cat", RecordType.PLAY, "2026-10-19") { copy(durationMin = 10) },
            rec("cat", RecordType.PLAY, "2026-10-19") { copy(durationMin = 20) },
            rec("dog", RecordType.WALK, "2026-10-19") { copy(durationMin = 60) })
        assertEquals(30.0, r.series[0].total, 0.0)
        assertEquals(0.0, r.series[1].total, 0.0)
        assertEquals("分鐘", StatMetric.PLAY.unit)
        assertTrue(StatMetric.PLAY.isLine)
        assertTrue(r.series[0].summary, r.series[0].summary.startsWith("共 2 次"))
    }
}
