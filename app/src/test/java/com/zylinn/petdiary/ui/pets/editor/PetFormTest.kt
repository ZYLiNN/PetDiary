package com.zylinn.petdiary.ui.pets.editor

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PetFormTest {

    private val today = LocalDate.parse("2026-10-19")
    private val valid = PetForm(species = Species.CAT, name = "雷米", birthday = LocalDate.parse("2023-08-10"))

    // P1-03
    @Test fun emptyFormListsAllRequiredProblems() {
        val p = PetForm().problems(today)
        assertEquals("請選擇寵物類型", p.species)
        assertEquals("請輸入名字", p.name)
        assertEquals("請選擇生日", p.birth)
        assertFalse(PetForm().canSave(today))
    }

    @Test fun validFormCanSave() {
        assertTrue(valid.canSave(today))
        assertFalse(valid.problems(today).any)
    }

    @Test fun blankNameRejected() = assertEquals("請輸入名字", valid.copy(name = "   ").problems(today).name)

    @Test fun nameLongerThanLimitRejected() {
        assertNull(valid.copy(name = "貓".repeat(NAME_MAX)).problems(today).name)
        assertEquals("名字最多 30 個字", valid.copy(name = "貓".repeat(NAME_MAX + 1)).problems(today).name)
    }

    @Test fun futureBirthdayRejected() =
        assertEquals("生日不能是未來的日期", valid.copy(birthday = today.plusDays(1)).problems(today).birth)

    @Test fun missingBirthdayRejected() =
        assertEquals("請選擇生日", valid.copy(birthday = null).problems(today).birth)

    @Test fun exactBirthdayIsNotEstimated() {
        val input = valid.toInput(today)
        assertEquals(LocalDate.parse("2023-08-10").toEpochDay(), input.birthEpochDay)
        assertFalse(input.birthEstimated)
    }

    // D8: the "about N years" mode is gone; a legacy estimate becomes a plain birthday once saved again.
    @Test fun editingLegacyEstimatedPetSavesExactBirthday() {
        val pet = Pet(
            id = "p", name = "雷利", species = Species.DOG, birthEpochDay = LocalDate.parse("2021-10-19").toEpochDay(),
            birthEstimated = true, sex = Sex.MALE, neutered = true, breed = "柴犬", chipNumber = "123",
            note = "n", photoFile = "a.jpg", createdAt = 0, updatedAt = 0,
        )
        val form = PetForm.from(pet)
        assertEquals(LocalDate.parse("2021-10-19"), form.birthday)
        assertFalse(form.toInput(today).birthEstimated)
        assertEquals("柴犬", form.breed)
        assertEquals("a.jpg", form.photoFile)
    }

    @Test(expected = IllegalStateException::class)
    fun invalidFormCannotConvert() {
        PetForm().toInput(today)
    }
}
