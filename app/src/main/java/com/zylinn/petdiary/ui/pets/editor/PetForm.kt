package com.zylinn.petdiary.ui.pets.editor

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.domain.PetAge
import java.time.LocalDate

const val NAME_MAX = 30

/** Everything the add / edit pet screen holds. Pure data so validation is unit-testable. */
data class PetForm(
    val species: Species? = null,
    val customSpecies: String = "",
    val name: String = "",
    val birthday: LocalDate? = null,
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
        birth = when {
            birthday == null -> "請選擇生日"
            birthday.isAfter(today) -> "生日不能是未來的日期"
            else -> null
        },
    )

    fun canSave(today: LocalDate) = !problems(today).any

    /** Only valid forms can be converted; call [canSave] first. */
    fun toInput(today: LocalDate): PetInput {
        check(canSave(today)) { "form is not valid" }
        return PetInput(
            name = name,
            species = species!!,
            customSpecies = customSpecies,
            birthEpochDay = birthday!!.toEpochDay(),
            sex = sex,
            neutered = neutered,
            breed = breed,
            chipNumber = chipNumber,
            note = note,
            photoFile = photoFile,
        )
    }

    companion object {
        /** Pre-fills the form when editing. Birthdays saved by older versions as estimates reopen as plain dates. */
        fun from(pet: Pet) = PetForm(
            species = pet.species,
            customSpecies = pet.customSpecies.orEmpty(),
            name = pet.name,
            birthday = LocalDate.ofEpochDay(pet.birthEpochDay),
            sex = pet.sex,
            neutered = pet.neutered,
            breed = pet.breed.orEmpty(),
            chipNumber = pet.chipNumber.orEmpty(),
            note = pet.note.orEmpty(),
            photoFile = pet.photoFile,
        )
    }
}
