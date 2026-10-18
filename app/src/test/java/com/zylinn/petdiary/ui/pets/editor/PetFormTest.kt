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

    @Test fun approximateAgeNeedsSomeValue() {
        val f = valid.copy(birthMode = BirthMode.APPROXIMATE)
        assertEquals("請輸入大約幾歲或幾個月", f.problems(today).birth)
        assertNull(f.copy(approxYears = "3").problems(today).birth)
        assertNull(f.copy(approxMonths = "5").problems(today).birth)
        assertEquals("月數請填 0～11", f.copy(approxMonths = "12").problems(today).birth)
        assertEquals("請輸入數字", f.copy(approxYears = "三").problems(today).birth)
        assertEquals("年齡最多 40 歲", f.copy(approxYears = "41").problems(today).birth)
    }

    // P1-02, P1-04
    @Test fun approximateAgeBecomesEstimatedBirthday() {
        val input = valid.copy(birthMode = BirthMode.APPROXIMATE, approxYears = "3").toInput(today)
        assertEquals(LocalDate.parse("2023-10-19").toEpochDay(), input.birthEpochDay)
        assertTrue(input.birthEstimated)
    }

    @Test fun exactBirthdayIsNotEstimated() {
        val input = valid.toInput(today)
        assertEquals(LocalDate.parse("2023-08-10").toEpochDay(), input.birthEpochDay)
        assertFalse(input.birthEstimated)
    }

    @Test fun editingEstimatedPetKeepsEstimatedFlag() {
        val pet = Pet(
            id = "p", name = "雷利", species = Species.DOG, birthEpochDay = LocalDate.parse("2021-10-19").toEpochDay(),
            birthEstimated = true, sex = Sex.MALE, neutered = true, breed = "柴犬", chipNumber = "123",
            note = "n", photoFile = "a.jpg", createdAt = 0, updatedAt = 0,
        )
        val form = PetForm.from(pet)
        assertTrue(form.toInput(today).birthEstimated)
        assertEquals("柴犬", form.breed)
        assertEquals("a.jpg", form.photoFile)
        // Picking a new exact date clears the estimate.
        assertFalse(form.copy(birthday = LocalDate.parse("2021-05-01"), birthdayEstimated = false).toInput(today).birthEstimated)
    }

    @Test(expected = IllegalStateException::class)
    fun invalidFormCannotConvert() {
        PetForm().toInput(today)
    }
}
