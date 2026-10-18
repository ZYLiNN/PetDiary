package com.zylinn.petdiary.ui.pets.editor

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.domain.PetAge
import java.time.LocalDate

enum class BirthMode { EXACT, APPROXIMATE }

const val NAME_MAX = 30
const val APPROX_YEARS_MAX = 40

/** Everything the add / edit pet screen holds. Pure data so validation is unit-testable. */
data class PetForm(
    val species: Species? = null,
    val customSpecies: String = "",
    val name: String = "",
    val birthMode: BirthMode = BirthMode.EXACT,
    val birthday: LocalDate? = null,
    /** Exact mode only: the date came from an earlier "about N years" entry. Cleared when a new date is picked. */
    val birthdayEstimated: Boolean = false,
    val approxYears: String = "",
    val approxMonths: String = "",
    val sex: Sex = Sex.UNKNOWN,
    val neutered: Boolean? = null,
    val breed: String = "",
    val chipNumber: String = "",
    val note: String = "",
    val photoFile: String? = null,
) {
    /** Reason shown next to each required field; null when the field is fine. */
    data class Problems(val species: String?, val name: String?, val birth: String?) {
        val any get() = species != null || name != null || birth != null
    }

    fun problems(today: LocalDate): Problems = Problems(
        species = if (species == null) "請選擇寵物類型" else null,
        name = when {
            name.isBlank() -> "請輸入名字"
            name.trim().length > NAME_MAX -> "名字最多 $NAME_MAX 個字"
            else -> null
        },
        birth = when (birthMode) {
            BirthMode.EXACT -> when {
                birthday == null -> "請選擇生日"
                birthday.isAfter(today) -> "生日不能是未來的日期"
                else -> null
            }
            BirthMode.APPROXIMATE -> {
                val y = approxYears.toIntOrNull() ?: 0
                val m = approxMonths.toIntOrNull() ?: 0
                when {
                    approxYears.isNotEmpty() && approxYears.toIntOrNull() == null -> "請輸入數字"
                    approxMonths.isNotEmpty() && approxMonths.toIntOrNull() == null -> "請輸入數字"
                    y > APPROX_YEARS_MAX -> "年齡最多 $APPROX_YEARS_MAX 歲"
                    m > 11 -> "月數請填 0～11"
                    y == 0 && m == 0 -> "請輸入大約幾歲或幾個月"
                    else -> null
                }
            }
        },
    )

    fun canSave(today: LocalDate) = !problems(today).any

    /** Only valid forms can be converted; call [canSave] first. */
    fun toInput(today: LocalDate): PetInput {
        check(canSave(today)) { "form is not valid" }
        val (birth, estimated) = when (birthMode) {
            BirthMode.EXACT -> birthday!! to birthdayEstimated
            BirthMode.APPROXIMATE -> PetAge.estimatedBirthday(
                years = approxYears.toIntOrNull() ?: 0,
                months = approxMonths.toIntOrNull() ?: 0,
                today = today,
            ) to true
        }
        return PetInput(
            name = name,
            species = species!!,
            customSpecies = customSpecies,
            birthEpochDay = birth.toEpochDay(),
            birthEstimated = estimated,
            sex = sex,
            neutered = neutered,
            breed = breed,
            chipNumber = chipNumber,
            note = note,
            photoFile = photoFile,
        )
    }

    companion object {
        /** Pre-fills the form when editing. Estimated birthdays reopen with their stored date, still marked as estimated. */
        fun from(pet: Pet) = PetForm(
            species = pet.species,
            customSpecies = pet.customSpecies.orEmpty(),
            name = pet.name,
            birthMode = BirthMode.EXACT,
            birthday = LocalDate.ofEpochDay(pet.birthEpochDay),
            birthdayEstimated = pet.birthEstimated,
            sex = pet.sex,
            neutered = pet.neutered,
            breed = pet.breed.orEmpty(),
            chipNumber = pet.chipNumber.orEmpty(),
            note = pet.note.orEmpty(),
            photoFile = pet.photoFile,
        )
    }
}
