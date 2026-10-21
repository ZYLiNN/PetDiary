package com.zylinn.petdiary.data.export

import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {

    private fun d(s: String) = LocalDate.parse(s).toEpochDay()
    private fun rec(id: String, type: RecordType, day: String, minute: Int = 600, f: Record.() -> Record = { this }) =
        Record(id = id, petId = "p1", type = type, dateEpochDay = d(day), minuteOfDay = minute, createdAt = 0, updatedAt = 0).f()

    // P6-03: Excel needs a BOM to read UTF-8 Chinese
    @Test fun startsWithByteOrderMarkAndHeader() {
        val csv = CsvExport.build(emptyList(), emptyMap(), emptyMap())
        assertTrue(csv.startsWith("﻿日期,時間,寵物,類型"))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test fun cellsAreQuotedWhenNeeded() {
        assertEquals("plain", CsvExport.cell("plain"))
        assertEquals("\"a,b\"", CsvExport.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.cell("line1\nline2"))
        assertEquals("中文，逗號", CsvExport.cell("中文，逗號")) // full-width comma is not a separator
    }

    // spreadsheets would run a cell starting with = + - @ as a formula
    @Test fun formulaLikeCellsAreNeutralised() {
        assertEquals("'=SUM(A1)", CsvExport.cell("=SUM(A1)"))
        assertEquals("'+1", CsvExport.cell("+1"))
        assertEquals("'-2", CsvExport.cell("-2"))
        assertEquals("'@x", CsvExport.cell("@x"))
        assertEquals("4.2", CsvExport.cell("4.2"))
    }

    @Test fun recordsBecomeRowsOldestFirst() {
        val csv = CsvExport.build(
            listOf(
                rec("b", RecordType.VET, "2026-10-19", 900) { copy(title = "健檢", detail = "牙結石", hospital = "愛心, 醫院", cost = 1200, nextEpochDay = d("2027-10-19"), note = "不錯") },
                rec("a", RecordType.FEEDING, "2026-10-18", 480) { copy(portions = 1.5, drankWater = true, supplementIds = "s1,s2") },
                rec("c", RecordType.WALK, "2026-10-19", 1000) { copy(durationMin = 45, drankWater = true, peed = true, pooped = false) },
                rec("d", RecordType.DEWORMING, "2026-10-20") { copy(title = "蚤不到", dewormKind = DewormKind.EXTERNAL) },
                rec("e", RecordType.WEIGHT, "2026-10-21") { copy(weightKg = 4.25) },
                rec("f", RecordType.PLAY, "2026-10-22") { copy(durationMin = 15, toyIds = "t1") },
                rec("g", RecordType.FEEDING, "2026-10-22", 660) { copy(portions = 1.0, foodKind = FoodKind.SNACK) },
            ),
            mapOf("p1" to "雷米"),
            mapOf("s1" to "魚油", "s2" to "益生菌", "t1" to "逗貓棒"),
        )
        val lines = csv.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals(8, lines.size)
        assertEquals(20, CsvExport.header.size)
        assertEquals("2026-10-18,08:00,雷米,餵食,,,,,,乾食,1.5,是,魚油、益生菌,,,,,,,", lines[1]) // old feeding record without a kind = 乾食 (D76)
        assertEquals("2026-10-19,15:00,雷米,就診,健檢,牙結石,\"愛心, 醫院\",1200,2027-10-19,,,,,,,,,,,不錯", lines[2])
        assertEquals("2026-10-19,16:40,雷米,散步,,,,,,,,是,,,45,,是,,,", lines[3])
        assertEquals("2026-10-20,10:00,雷米,驅蟲,蚤不到,,,,,,,,,,,,,,外驅,", lines[4])
        assertEquals("2026-10-21,10:00,雷米,體重,,,,,,,,,,4.25,,,,,,", lines[5])
        assertEquals("2026-10-22,10:00,雷米,玩耍,,,,,,,,,,,15,逗貓棒,,,,", lines[6])
        assertEquals("2026-10-22,11:00,雷米,餵食,,,,,,零食,1,,,,,,,,,", lines[7])
    }

    @Test fun unknownPetAndSupplementDoNotCrash() {
        val csv = CsvExport.build(listOf(rec("a", RecordType.FEEDING, "2026-10-18") { copy(supplementIds = "gone") }), emptyMap(), emptyMap())
        assertTrue(csv.contains("2026-10-18,10:00,,餵食"))
    }
}
