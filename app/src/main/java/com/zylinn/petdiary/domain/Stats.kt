package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.RecordableTypes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What the stats page can show (P5): one metric at a time. */
enum class StatMetric(val label: String, val types: List<RecordType>) {
    FEEDING("餵食", listOf(RecordType.FEEDING)),
    WALK("散步", listOf(RecordType.WALK)),
    WEIGHT("體重", listOf(RecordType.WEIGHT)),
    COST("費用", RecordableTypes),
    MEDICINE("藥物", listOf(RecordType.MEDICINE)),
    VACCINE("疫苗", listOf(RecordType.VACCINE)),
    DEWORMING("驅蟲", listOf(RecordType.DEWORMING)),
    VET("就診", listOf(RecordType.VET)),
    GROOMING("美容", listOf(RecordType.GROOMING)),
    ABNORMAL("異常狀況", listOf(RecordType.ABNORMAL));

    val isLine get() = this == FEEDING || this == WALK || this == WEIGHT
    val unit: String
        get() = when (this) {
            FEEDING -> "份"
            WALK -> "分鐘"
            WEIGHT -> "kg"
            COST -> "NT$"
            else -> "次"
        }

    /** The record type whose color / icon represents this metric (cost has none). */
    val recordType: RecordType? get() = types.singleOrNull()
}

enum class RangeKind(val label: String) { WEEK("週"), MONTH("月"), YEAR("年"), CUSTOM("自訂") }

data class DateSpan(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
}

enum class Granularity { DAY, WEEK, MONTH }

data class Bucket(val start: LocalDate, val end: LocalDate, val label: String) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

object StatsRanges {

    /**
     * Week = Sunday–Saturday (same as the pet calendar), month / year = calendar month / year;
     * [offset] moves that many periods back (negative) or forward. Custom uses the given dates.
     */
    fun span(kind: RangeKind, offset: Int, today: LocalDate, customFrom: LocalDate? = null, customTo: LocalDate? = null): DateSpan =
        when (kind) {
            RangeKind.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong())
                DateSpan(start, start.plusDays(6))
            }
            RangeKind.MONTH -> YearMonth.from(today).plusMonths(offset.toLong()).let { DateSpan(it.atDay(1), it.atEndOfMonth()) }
            RangeKind.YEAR -> today.year.plus(offset).let { DateSpan(LocalDate.of(it, 1, 1), LocalDate.of(it, 12, 31)) }
            RangeKind.CUSTOM -> {
                val from = customFrom ?: today.minusDays(29)
                val to = customTo ?: today
                if (from.isAfter(to)) DateSpan(to, from) else DateSpan(from, to)
            }
        }

    fun title(kind: RangeKind, span: DateSpan, today: LocalDate): String {
        fun LocalDate.s() = RecordFormat.shortDate(this, today)
        return when (kind) {
            RangeKind.WEEK -> "${span.from.s()} – ${span.to.s()}"
            RangeKind.MONTH -> "${span.from.year} 年 ${span.from.monthValue} 月"
            RangeKind.YEAR -> "${span.from.year} 年"
            RangeKind.CUSTOM -> "${span.from.year}/${span.from.monthValue}/${span.from.dayOfMonth} – ${span.to.year}/${span.to.monthValue}/${span.to.dayOfMonth}"
        }
    }

    /** P5-08: the start can never be later than the end. */
    fun isValidCustom(from: LocalDate?, to: LocalDate?) = from == null || to == null || !from.isAfter(to)

    fun granularity(span: DateSpan) = when {
        span.days <= 45 -> Granularity.DAY
        span.days <= 200 -> Granularity.WEEK
        else -> Granularity.MONTH
    }

    fun buckets(span: DateSpan): List<Bucket> = when (granularity(span)) {
        Granularity.DAY -> generateSequence(span.from) { it.plusDays(1) }.takeWhile { !it.isAfter(span.to) }
            .map { Bucket(it, it, "${it.monthValue}/${it.dayOfMonth}") }.toList()
        Granularity.WEEK -> generateSequence(span.from) { it.plusWeeks(1) }.takeWhile { !it.isAfter(span.to) }
            .map { s -> Bucket(s, minOf(s.plusDays(6), span.to), "${s.monthValue}/${s.dayOfMonth}") }.toList()
        Granularity.MONTH -> generateSequence(YearMonth.from(span.from)) { it.plusMonths(1) }
            .takeWhile { !it.atDay(1).isAfter(span.to) }
            .map { m -> Bucket(maxOf(m.atDay(1), span.from), minOf(m.atEndOfMonth(), span.to), "${m.monthValue}月") }.toList()
    }
}

/** One pet's line / bars. null = no data in that bucket (a gap in a line). */
data class PetSeries(
    val petId: String,
    val name: String,
    val values: List<Double?>,
    val summary: String,
    val total: Double,
)

data class StatsResult(
    val metric: StatMetric,
    val span: DateSpan,
    val buckets: List<Bucket>,
    val series: List<PetSeries>,
    /** One line above the chart (e.g. total cost); null when there is nothing to add. */
    val headline: String?,
    val hasData: Boolean,
)

/** Turns records into chart data. Pure, so every number on the page is unit-tested against raw records (P5-06). */
object StatsCalculator {

    fun compute(
        metric: StatMetric,
        span: DateSpan,
        pets: List<Pair<String, String>>, // id to name, in display order
        records: List<Record>,
        today: LocalDate,
    ): StatsResult {
        val buckets = StatsRanges.buckets(span)
        val inRange = records.filter { it.type in metric.types && it.dateEpochDay in span.from.toEpochDay()..span.to.toEpochDay() }
        val byPet = inRange.groupBy { it.petId }
        val series = pets.map { (id, name) -> petSeries(metric, id, name, byPet[id].orEmpty(), buckets, span, today) }
        // Cost counts every record type, but only records that actually carry a cost make a chart worth showing.
        val hasData = if (metric == StatMetric.COST) inRange.any { (it.cost ?: 0L) > 0L } else inRange.isNotEmpty()
        val headline = if (metric == StatMetric.COST && hasData) "總花費 ${RecordFormat.cost(series.sumOf { it.total }.toLong())}" else null
        return StatsResult(metric, span, buckets, series, headline, hasData)
    }

    private fun bucketIndex(buckets: List<Bucket>, day: Long): Int =
        buckets.indexOfFirst { day >= it.start.toEpochDay() && day <= it.end.toEpochDay() }

    private fun petSeries(
        metric: StatMetric, id: String, name: String, recs: List<Record>,
        buckets: List<Bucket>, span: DateSpan, today: LocalDate,
    ): PetSeries {
        val perBucket = buckets.map { b -> recs.filter { bucketIndex(buckets, it.dateEpochDay).let { i -> i >= 0 && buckets[i] == b } } }
        return when (metric) {
            StatMetric.FEEDING, StatMetric.WALK -> {
                val amount: (Record) -> Double = if (metric == StatMetric.FEEDING) { r -> r.portions ?: 0.0 } else { r -> (r.durationMin ?: 0).toDouble() }
                // Average per day so week / month / year buckets stay comparable with day buckets.
                // Days that have not happened yet are gaps, not zeros.
                val values = buckets.mapIndexed { i, b ->
                    when {
                        b.start.isAfter(today) -> null
                        perBucket[i].isEmpty() -> 0.0
                        else -> perBucket[i].sumOf(amount) / b.days
                    }
                }
                val total = recs.sumOf(amount)
                val activeDays = recs.map { it.dateEpochDay }.distinct().size
                val water = recs.count { it.drankWater == true }
                val avg = if (activeDays == 0) 0.0 else total / activeDays
                val summary = if (recs.isEmpty()) "這段期間沒有紀錄" else when (metric) {
                    StatMetric.FEEDING -> "有記錄的日子平均每天 ${RecordFormat.number(avg)} 份（共 ${RecordFormat.number(total)} 份）・有喝水 $water 次"
                    else -> "共 ${recs.size} 次・有記錄的日子平均每天 ${RecordFormat.duration(avg.toInt())}（共 ${RecordFormat.duration(total.toInt())}）" +
                        "・有喝水 $water 次・小便 ${recs.count { it.peed == true }} 次・大便 ${recs.count { it.pooped == true }} 次"
                }
                PetSeries(id, name, values, summary, total)
            }
            StatMetric.WEIGHT -> {
                val values = perBucket.map { list -> list.mapNotNull { it.weightKg }.takeIf { it.isNotEmpty() }?.average() }
                val ordered = recs.filter { it.weightKg != null }.sortedWith(compareBy({ it.dateEpochDay }, { it.minuteOfDay }))
                val summary = when {
                    ordered.isEmpty() -> "這段期間沒有體重紀錄"
                    ordered.size == 1 -> "最新 ${RecordFormat.number(ordered.last().weightKg!!)} kg"
                    else -> {
                        val diff = ordered.last().weightKg!! - ordered.first().weightKg!!
                        val sign = if (diff > 0) "+" else if (diff < 0) "−" else ""
                        "最新 ${RecordFormat.number(ordered.last().weightKg!!)} kg・這段期間 $sign${RecordFormat.number(kotlin.math.abs(diff))} kg"
                    }
                }
                PetSeries(id, name, values, summary, ordered.lastOrNull()?.weightKg ?: 0.0)
            }
            StatMetric.COST -> {
                val values = perBucket.map { list -> list.sumOf { it.cost ?: 0L }.toDouble() }
                val total = recs.sumOf { it.cost ?: 0L }
                PetSeries(id, name, values, if (recs.isEmpty()) "這段期間沒有紀錄" else "共 ${RecordFormat.cost(total)}", total.toDouble())
            }
            else -> {
                val values = perBucket.map { it.size.toDouble() }
                val last = recs.maxOfOrNull { it.dateEpochDay }?.let { LocalDate.ofEpochDay(it) }
                val summary = if (recs.isEmpty()) "這段期間沒有紀錄"
                else "共 ${recs.size} 次・最近一次 ${RecordFormat.shortDate(last!!, today)}（${RecordFormat.daysAgo(last, today)}）"
                PetSeries(id, name, values, summary, recs.size.toDouble())
            }
        }
    }

    /** Gridline scale: a 1 / 2 / 5 × 10^k step so labels read cleanly (0, 1, 2, 3, 4 – never 1.25, 3.75). */
    data class AxisScale(val lo: Double, val hi: Double, val step: Double) {
        val ticks get() = Math.round((hi - lo) / step).toInt().coerceAtLeast(1)
        fun value(i: Int) = lo + step * i
    }

    private fun niceStep(raw: Double): Double {
        if (raw <= 0.0) return 1.0
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
        val f = raw / mag
        return mag * when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 5.0 -> 5.0
            else -> 10.0
        }
    }

    /** Scale starting at 0 (bars, feeding, walk). */
    fun axisFromZero(max: Double): AxisScale {
        if (max <= 0.0) return AxisScale(0.0, 1.0, 0.25).let { AxisScale(0.0, 4.0, 1.0) }
        val step = niceStep(max / 4)
        return AxisScale(0.0, Math.ceil(max / step - 1e-9) * step, step)
    }

    /** Scale fitted around [min]..[max] (weight: a flat line from 0 would hide the trend). */
    fun axisAround(min: Double, max: Double): AxisScale {
        val pad = maxOf((max - min) * 0.2, 0.5)
        val lo = Math.max(0.0, min - pad)
        val hi = max + pad
        val step = niceStep((hi - lo) / 4)
        val nLo = Math.floor(lo / step + 1e-9) * step
        return AxisScale(nLo, Math.ceil(hi / step - 1e-9) * step, step)
    }
}
