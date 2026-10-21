package com.zylinn.petdiary.ui.records

import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.isMedical
import com.zylinn.petdiary.domain.RecordFormat
import java.time.LocalDate

/** Fixed grooming items offered as chips. */
val GroomingItems = listOf("洗澡", "剪指甲", "美容", "清耳朵", "剃腳底毛", "刷牙")

const val TEXT_MAX = 50
const val NOTE_MAX = 500
const val PORTIONS_MAX = 99.0
const val WEIGHT_MAX_KG = 200.0
const val WALK_MAX_MIN = 600

/** One-tap walk lengths offered next to the minutes box. */
val WalkPresets = listOf(15, 30, 45, 60)

/** One-tap play lengths (D77). */
val PlayPresets = listOf(10, 15, 30)

/** Labels for the shared medical layout (S11): only the wording differs by type. */
data class MedicalLabels(val title: String, val detail: String?, val hospital: String, val next: String)

fun medicalLabels(type: RecordType): MedicalLabels = when (type) {
    RecordType.MEDICINE -> MedicalLabels("藥名 *", "劑量（例如 0.5 顆）", "醫院 / 購買地點", "下次用藥日期")
    RecordType.VACCINE -> MedicalLabels("疫苗名稱 *", null, "醫院", "下次施打日期")
    RecordType.DEWORMING -> MedicalLabels("驅蟲藥名稱 *", null, "醫院 / 購買地點", "下次驅蟲日期")
    RecordType.VET -> MedicalLabels("就診原因 *", "診斷", "醫院", "下次回診日期")
    else -> error("$type is not medical")
}

/** Everything the record form holds. Pure data so validation is unit-testable. */
data class RecordForm(
    val type: RecordType,
    val date: LocalDate,
    val minuteOfDay: Int,
    val note: String = "",
    // Feeding
    val portions: String = "",
    val drankWater: Boolean = false,
    val supplementIds: Set<String> = emptySet(),
    val foodKind: FoodKind = FoodKind.DRY,
    // Play (minutes reuse [durationMin])
    val toyIds: Set<String> = emptySet(),
    // Medical / abnormal
    val title: String = "",
    val detail: String = "",
    val hospital: String = "",
    val cost: String = "",
    val nextDate: LocalDate? = null,
    val remindNext: Boolean = false,
    val dewormKind: DewormKind? = null,
    // Weight
    val weightKg: String = "",
    // Grooming
    val groomingItems: Set<String> = emptySet(),
    // Walk (water checklist reuses [drankWater])
    val durationMin: String = "",
    val peed: Boolean = false,
    val pooped: Boolean = false,
) {
    /** First problem to fix, or null when the form can be saved. */
    fun problem(today: LocalDate): String? {
        if (date.isAfter(today)) return "日期不能是未來"
        if (RecordFormat.parseCost(cost) == RecordFormat.CostInput.Invalid) return "費用只能填整數金額"
        if (nextDate != null && !nextDate.isAfter(date)) return "下次日期要在這次之後"
        return when (type) {
            RecordType.FEEDING -> {
                val p = portions.toDoubleOrNull()
                when {
                    portions.isNotEmpty() && (p == null || p <= 0 || p > PORTIONS_MAX) -> "份數請填 0～99 之間的數字"
                    portions.isEmpty() && !drankWater && supplementIds.isEmpty() && note.isBlank() -> "請填份數，或勾選喝水 / 保健食品"
                    else -> null
                }
            }
            RecordType.WALK -> {
                val m = durationMin.toIntOrNull()
                if (m == null || m <= 0 || m > WALK_MAX_MIN) "請填散步時間（1～$WALK_MAX_MIN 分鐘）" else null
            }
            RecordType.PLAY -> {
                val m = durationMin.toIntOrNull()
                if (m == null || m <= 0 || m > WALK_MAX_MIN) "請填玩耍時間（1～$WALK_MAX_MIN 分鐘）" else null
            }
            RecordType.WEIGHT -> {
                val w = weightKg.toDoubleOrNull()
                if (w == null || w <= 0 || w > WEIGHT_MAX_KG) "請填體重（公斤）" else null
            }
            RecordType.GROOMING ->
                if (groomingItems.isEmpty() && note.isBlank()) "請選擇美容項目" else null
            RecordType.ABNORMAL ->
                if (title.isBlank()) "請描述狀況（例如：嘔吐）" else null
            RecordType.CUSTOM -> null
            else -> // medical
                if (title.isBlank()) "請填寫${medicalLabels(type).title.removeSuffix(" *")}" else null
        }
    }

    fun canSave(today: LocalDate) = problem(today) == null

    fun toInput(today: LocalDate): RecordInput {
        check(canSave(today)) { "form is not valid" }
        val costValue = (RecordFormat.parseCost(cost) as? RecordFormat.CostInput.Value)?.amount
        return when (type) {
            RecordType.FEEDING -> RecordInput(
                type, date.toEpochDay(), minuteOfDay, note = note,
                portions = portions.toDoubleOrNull(), drankWater = drankWater, supplementIds = supplementIds.toList(), foodKind = foodKind,
            )
            RecordType.PLAY -> RecordInput(
                type, date.toEpochDay(), minuteOfDay, note = note, durationMin = durationMin.toInt(), toyIds = toyIds.toList(),
            )
            RecordType.WALK -> RecordInput(
                type, date.toEpochDay(), minuteOfDay, note = note, drankWater = drankWater, durationMin = durationMin.toInt(), peed = peed, pooped = pooped,
            )
            RecordType.WEIGHT -> RecordInput(type, date.toEpochDay(), minuteOfDay, note = note, weightKg = weightKg.toDouble())
            RecordType.GROOMING -> RecordInput(
                type, date.toEpochDay(), minuteOfDay, note = note, cost = costValue,
                title = GroomingItems.filter { it in groomingItems }.joinToString("、").ifEmpty { null },
                hospital = hospital,
            )
            RecordType.ABNORMAL -> RecordInput(type, date.toEpochDay(), minuteOfDay, note = note, title = title)
            RecordType.CUSTOM -> error("custom is reminder-only")
            else -> RecordInput(
                type, date.toEpochDay(), minuteOfDay, note = note, cost = costValue,
                title = title, detail = detail.takeIf { medicalLabels(type).detail != null },
                hospital = hospital, nextEpochDay = nextDate?.toEpochDay(), remindNext = remindNext && nextDate != null,
                dewormKind = dewormKind.takeIf { type == RecordType.DEWORMING },
            )
        }
    }

    companion object {
        fun from(r: Record) = RecordForm(
            type = r.type,
            date = LocalDate.ofEpochDay(r.dateEpochDay),
            minuteOfDay = r.minuteOfDay,
            note = r.note.orEmpty(),
            portions = r.portions?.let(RecordFormat::number).orEmpty(),
            drankWater = r.drankWater == true,
            supplementIds = r.supplementIdList.toSet(),
            foodKind = r.effectiveFoodKind ?: FoodKind.DRY,
            toyIds = r.toyIdList.toSet(),
            title = if (r.type == RecordType.GROOMING) "" else r.title.orEmpty(),
            detail = r.detail.orEmpty(),
            hospital = r.hospital.orEmpty(),
            cost = r.cost?.toString().orEmpty(),
            nextDate = r.nextEpochDay?.let(LocalDate::ofEpochDay),
            remindNext = r.remindNext,
            dewormKind = r.dewormKind,
            weightKg = r.weightKg?.let(RecordFormat::number).orEmpty(),
            durationMin = r.durationMin?.toString().orEmpty(),
            peed = r.peed == true,
            pooped = r.pooped == true,
            groomingItems = if (r.type == RecordType.GROOMING) r.title?.split("、")?.toSet().orEmpty() else emptySet(),
        )
    }
}

val RecordType.hasNextDate get() = isMedical
