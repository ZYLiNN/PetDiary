package com.zylinn.petdiary.ui.records

import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordFormTest {

    private val today = LocalDate.parse("2026-10-19")
    private fun form(type: RecordType) = RecordForm(type = type, date = today, minuteOfDay = 600)

    @Test fun futureDateRejected() =
        assertEquals("日期不能是未來", form(RecordType.WEIGHT).copy(date = today.plusDays(1), weightKg = "4").problem(today))

    // D22 (walk): time is required, water is an optional checkbox
    @Test fun walkNeedsDuration() {
        val f = form(RecordType.WALK)
        assertEquals("請填散步時間（1～600 分鐘）", f.problem(today))
        assertEquals("請填散步時間（1～600 分鐘）", f.copy(durationMin = "0").problem(today))
        assertEquals("請填散步時間（1～600 分鐘）", f.copy(durationMin = "601").problem(today))
        assertNull(f.copy(durationMin = "30").problem(today))
        assertNull(f.copy(durationMin = "600").problem(today))
    }

    @Test fun walkToInputKeepsWater() {
        val input = form(RecordType.WALK).copy(durationMin = "45", drankWater = true, peed = true, pooped = false).toInput(today)
        assertEquals(true, input.peed)
        assertEquals(false, input.pooped)
        assertEquals(45, input.durationMin)
        assertEquals(true, input.drankWater)
        assertNull(input.portions)
    }

    // P2-06
    @Test fun feedingNeedsSomething() {
        val f = form(RecordType.FEEDING)
        assertEquals("請填份數，或勾選喝水 / 保健食品", f.problem(today))
        assertNull(f.copy(portions = "1.5").problem(today))
        assertNull(f.copy(drankWater = true).problem(today))
        assertNull(f.copy(supplementIds = setOf("s1")).problem(today))
        assertEquals("份數請填 0～99 之間的數字", f.copy(portions = "abc").problem(today))
        assertEquals("份數請填 0～99 之間的數字", f.copy(portions = "0").problem(today))
    }

    @Test fun feedingToInput() {
        val input = form(RecordType.FEEDING).copy(portions = "1.5", drankWater = true, supplementIds = setOf("s1")).toInput(today)
        assertEquals(1.5, input.portions!!, 0.0)
        assertEquals(true, input.drankWater)
        assertEquals(listOf("s1"), input.supplementIds)
        assertNull(input.cost)
    }

    // P2-07a: four medical types share one layout and one rule
    @Test fun medicalTypesRequireName() {
        listOf(RecordType.MEDICINE, RecordType.VACCINE, RecordType.DEWORMING, RecordType.VET).forEach { t ->
            val f = form(t)
            assertFalse("$t", f.canSave(today))
            assertTrue("$t", f.copy(title = "x").canSave(today))
        }
        assertEquals("請填寫藥名", form(RecordType.MEDICINE).problem(today))
        assertEquals("請填寫就診原因", form(RecordType.VET).problem(today))
    }

    // P2-08
    @Test fun costMustBeWholeNumber() {
        val f = form(RecordType.VACCINE).copy(title = "三合一")
        assertEquals("費用只能填整數金額", f.copy(cost = "12.5").problem(today))
        assertEquals(1200L, f.copy(cost = "1,200").toInput(today).cost)
        assertNull(f.toInput(today).cost)
    }

    // P2-07
    @Test fun nextDateMustBeLaterAndDrivesReminderFlag() {
        val f = form(RecordType.DEWORMING).copy(title = "蚤不到", dewormKind = DewormKind.EXTERNAL)
        assertEquals("下次日期要在這次之後", f.copy(nextDate = today).problem(today))
        val withNext = f.copy(nextDate = today.plusDays(30), remindNext = true).toInput(today)
        assertEquals(today.plusDays(30).toEpochDay(), withNext.nextEpochDay)
        assertTrue(withNext.remindNext)
        assertEquals(DewormKind.EXTERNAL, withNext.dewormKind)
        assertFalse(f.copy(remindNext = true).toInput(today).remindNext)
    }

    @Test fun vaccineDropsDetailField() {
        val input = form(RecordType.VACCINE).copy(title = "狂犬病", detail = "stale").toInput(today)
        assertNull(input.detail)
    }

    @Test fun weightValidation() {
        assertEquals("請填體重（公斤）", form(RecordType.WEIGHT).problem(today))
        assertEquals("請填體重（公斤）", form(RecordType.WEIGHT).copy(weightKg = "300").problem(today))
        assertEquals(4.25, form(RecordType.WEIGHT).copy(weightKg = "4.25").toInput(today).weightKg!!, 0.0)
    }

    @Test fun groomingItemsKeepCanonicalOrder() {
        val input = form(RecordType.GROOMING).copy(groomingItems = setOf("剪指甲", "洗澡"), cost = "800").toInput(today)
        assertEquals("洗澡、剪指甲", input.title)
        assertEquals(800L, input.cost)
        assertEquals("請選擇美容項目", form(RecordType.GROOMING).problem(today))
    }

    @Test fun abnormalNeedsDescription() {
        assertEquals("請描述狀況（例如：嘔吐）", form(RecordType.ABNORMAL).problem(today))
        assertEquals("吐毛球", form(RecordType.ABNORMAL).copy(title = "吐毛球").toInput(today).title)
    }

    // P2-05 (edit prefill)
    @Test fun roundTripsThroughRecord() {
        val r = Record(
            id = "r", petId = "p", type = RecordType.GROOMING, dateEpochDay = today.toEpochDay(), minuteOfDay = 700,
            title = "洗澡、剪指甲", cost = 800, note = "乖", createdAt = 0, updatedAt = 0,
        )
        val f = RecordForm.from(r)
        assertEquals(setOf("洗澡", "剪指甲"), f.groomingItems)
        assertEquals("800", f.cost)
        assertEquals("洗澡、剪指甲", f.toInput(today).title)
    }

    // P12-01 (D76): feeding has one of four kinds, dry by default; old records without a kind load as dry
    @Test fun feedingKindIsSavedAndOldRecordsAreDry() {
        assertEquals(FoodKind.DRY, form(RecordType.FEEDING).foodKind)
        val snack = form(RecordType.FEEDING).copy(foodKind = FoodKind.SNACK, portions = "1")
        assertEquals(FoodKind.SNACK, snack.toInput(today).foodKind)
        val old = com.zylinn.petdiary.data.record.Record("r", "p", RecordType.FEEDING, today.toEpochDay(), 480, portions = 1.0, createdAt = 0, updatedAt = 0)
        assertEquals(FoodKind.DRY, RecordForm.from(old).foodKind)
    }

    // P12-02 (D77): play needs minutes; toys are optional and multi-select
    @Test fun playNeedsMinutesAndKeepsToys() {
        val f = form(RecordType.PLAY)
        assertEquals("請填玩耍時間（1～600 分鐘）", f.problem(today))
        assertEquals("請填玩耍時間（1～600 分鐘）", f.copy(durationMin = "0").problem(today))
        val ok = f.copy(durationMin = "15", toyIds = setOf("t1", "t2"))
        val input = ok.toInput(today)
        assertEquals(15, input.durationMin)
        assertEquals(setOf("t1", "t2"), input.toyIds.toSet())
        assertEquals(listOf(10, 15, 30), PlayPresets)
    }
}
