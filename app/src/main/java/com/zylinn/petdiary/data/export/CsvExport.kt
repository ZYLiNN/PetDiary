package com.zylinn.petdiary.data.export

import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.RecordFormat.label
import java.time.LocalDate

/**
 * Records as CSV for Excel / Google Sheets (P6-03). A UTF-8 byte-order mark makes Excel read Chinese correctly;
 * fields are quoted when needed and cells that start with = + - @ are prefixed so a spreadsheet never runs them as formulas.
 */
object CsvExport {

    const val BOM = "﻿"

    val header = listOf(
        "日期", "時間", "寵物", "類型", "名稱／狀況／項目", "劑量／診斷", "醫院／地點", "費用", "下次日期",
        "食物種類", "份數", "有喝水", "保健食品", "體重(kg)", "散步／玩耍(分鐘)", "玩具", "有小便", "有大便", "驅蟲種類", "購物分類", "備註",
    )

    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun line(cells: List<String>) = cells.joinToString(",") { cell(it) }

    private fun yes(b: Boolean?) = if (b == true) "是" else ""

    /** [names]: supplement, toy and purchase category ids → names. */
    fun row(r: Record, petName: String, names: Map<String, String>): List<String> = listOf(
        LocalDate.ofEpochDay(r.dateEpochDay).toString(),
        RecordFormat.time(r.minuteOfDay),
        petName,
        r.type.label,
        r.title.orEmpty(),
        r.detail.orEmpty(),
        r.hospital.orEmpty(),
        r.cost?.toString().orEmpty(),
        r.nextEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
        r.effectiveFoodKind?.let { with(RecordFormat) { it.label } }.orEmpty(),
        r.portions?.let(RecordFormat::number).orEmpty(),
        yes(r.drankWater),
        r.supplementIdList.mapNotNull(names::get).joinToString("、"),
        r.weightKg?.let(RecordFormat::number).orEmpty(),
        r.durationMin?.toString().orEmpty(),
        r.toyIdList.mapNotNull(names::get).joinToString("、"),
        yes(r.peed),
        yes(r.pooped),
        when (r.dewormKind) { DewormKind.INTERNAL -> "內驅"; DewormKind.EXTERNAL -> "外驅"; null -> "" },
        r.category?.let(names::get).orEmpty(),
        r.note.orEmpty(),
    )

    /** Oldest first, one line per record; CRLF line endings like Excel writes. */
    fun build(records: List<Record>, petNames: Map<String, String>, names: Map<String, String>): String {
        val sorted = records.sortedWith(compareBy({ it.dateEpochDay }, { it.minuteOfDay }, { it.createdAt }))
        return BOM + (listOf(line(header)) + sorted.map { line(row(it, petNames[it.petId].orEmpty(), names)) }).joinToString("\r\n") + "\r\n"
    }
}
