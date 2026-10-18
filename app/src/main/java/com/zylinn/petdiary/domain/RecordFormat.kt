package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import java.text.DecimalFormat
import java.time.LocalDate

/** Display text for records. Pure functions so every format is unit-tested. */
object RecordFormat {

    const val COST_MAX = 9_999_999L

    val RecordType.label: String
        get() = when (this) {
            RecordType.FEEDING -> "餵食"
            RecordType.MEDICINE -> "藥物"
            RecordType.VACCINE -> "疫苗"
            RecordType.DEWORMING -> "驅蟲"
            RecordType.WEIGHT -> "體重"
            RecordType.VET -> "就診"
            RecordType.GROOMING -> "美容"
            RecordType.ABNORMAL -> "異常狀況"
        }

    val DewormKind.label: String
        get() = when (this) {
            DewormKind.INTERNAL -> "內驅"
            DewormKind.EXTERNAL -> "外驅"
        }

    /** Digits only (commas and spaces ignored). Empty → null; non-digits or > [COST_MAX] → invalid. */
    fun parseCost(input: String): CostInput {
        val cleaned = input.filterNot { it == ',' || it.isWhitespace() }
        if (cleaned.isEmpty()) return CostInput.Empty
        if (!cleaned.all(Char::isDigit)) return CostInput.Invalid
        val value = cleaned.toBigInteger()
        if (value > COST_MAX.toBigInteger()) return CostInput.Invalid
        return CostInput.Value(value.toLong())
    }

    sealed interface CostInput {
        data object Empty : CostInput
        data object Invalid : CostInput
        data class Value(val amount: Long) : CostInput
    }

    /** 1200 → "NT$ 1,200" */
    fun cost(amount: Long): String = "NT$ ${DecimalFormat("#,##0").format(amount)}"

    /** 1.5 → "1.5", 2.0 → "2", 0.25 → "0.25" */
    fun number(value: Double): String = DecimalFormat("0.##").format(value)

    /** 490 → "08:10" */
    fun time(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** Same year: "11/18"; otherwise "2027/1/5". */
    fun shortDate(day: LocalDate, today: LocalDate): String =
        if (day.year == today.year) "${day.monthValue}/${day.dayOfMonth}"
        else "${day.year}/${day.monthValue}/${day.dayOfMonth}"

    /** "今天" / "昨天" / "45 天前" / "明天" / "3 天後" */
    fun daysAgo(day: LocalDate, today: LocalDate): String {
        val diff = today.toEpochDay() - day.toEpochDay()
        return when {
            diff == 0L -> "今天"
            diff == 1L -> "昨天"
            diff > 1 -> "$diff 天前"
            diff == -1L -> "明天"
            else -> "${-diff} 天後"
        }
    }

    /** Last-record chip: "驅蟲 45 天前" */
    fun lastChip(type: RecordType, lastDay: LocalDate, today: LocalDate) = "${type.label} ${daysAgo(lastDay, today)}"

    /** One-line card summary; the note is shown separately on the card. */
    fun summary(record: Record, supplementNames: Map<String, String>, today: LocalDate): String {
        val parts = mutableListOf<String>()
        when (record.type) {
            RecordType.FEEDING -> {
                record.portions?.let { parts += "主食 ${number(it)} 份" }
                if (record.drankWater == true) parts += "有喝水"
                val supps = record.supplementIdList.mapNotNull(supplementNames::get)
                if (supps.isNotEmpty()) parts += "保健：${supps.joinToString("、")}"
            }
            RecordType.WEIGHT -> record.weightKg?.let { parts += "${number(it)} kg" }
            RecordType.DEWORMING -> {
                record.dewormKind?.let { parts += it.label }
                record.title?.let { parts += it }
            }
            else -> {
                record.title?.let { parts += it }
                record.detail?.let { parts += it }
            }
        }
        record.hospital?.let { parts += it }
        record.cost?.let { parts += cost(it) }
        record.nextEpochDay?.let { parts += "下次 ${shortDate(LocalDate.ofEpochDay(it), today)}" }
        return parts.joinToString("・")
    }
}
