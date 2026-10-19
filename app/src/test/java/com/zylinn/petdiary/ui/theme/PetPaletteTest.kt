package com.zylinn.petdiary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPaletteTest {

    private fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    @Test fun textOnEveryPetColourIsReadableInBothThemes() {
        for (dark in listOf(false, true)) for (c in PetColor.entries) {
            val sw = PetPalette.swatch(c, dark)
            assertTrue("$c dark=$dark ink/fill %.2f".format(ratio(sw.ink, sw.fill)), ratio(sw.ink, sw.fill) >= 4.5)
        }
    }

    @Test fun everyColourStandsOutFromTheCardItIsDrawnOn() {
        val lightCard = Color(0xFFFFFFFF)
        for (c in PetColor.entries) {
            val sw = PetPalette.swatch(c, false)
            // white pets rely on their border; the others on their fill
            assertTrue("$c light border", ratio(sw.line, lightCard) >= 1.4 || ratio(sw.fill, lightCard) >= 1.1)
        }
    }

    @Test fun fillsAreAllDifferentInEachTheme() {
        for (dark in listOf(false, true)) assertEquals(PetColor.entries.size, PetColor.entries.map { PetPalette.swatch(it, dark).fill }.toSet().size)
    }

    private fun pet(id: String, created: Long, color: String? = null) =
        Pet(id, id, Species.CAT, birthEpochDay = 1, color = color, createdAt = created, updatedAt = created)

    @Test fun newPetsGetTheNextFreeColourInOrder() {
        assertEquals(PetColor.ORANGE, PetColor.nextFree(emptyList()))
        assertEquals(PetColor.WHITE, PetColor.nextFree(listOf(PetColor.ORANGE)))
        assertEquals(PetColor.BLUE, PetColor.nextFree(listOf(PetColor.WHITE, PetColor.ORANGE)))
        assertEquals(PetColor.ORANGE, PetColor.nextFree(PetColor.entries)) // all used: start over
    }

    @Test fun petsWithoutAChosenColourGetOneByCreationOrderWithoutClashingWithChosenOnes() {
        val pets = listOf(pet("old1", 1), pet("old2", 2), pet("chosen", 3, "ORANGE"))
        val eff = PetColor.effective(pets)
        assertEquals(PetColor.ORANGE, eff.getValue("chosen"))
        assertEquals(PetColor.WHITE, eff.getValue("old1"))
        assertEquals(PetColor.BLUE, eff.getValue("old2"))
        assertEquals(eff, PetColor.effective(pets.reversed())) // stable whatever the input order
    }

    @Test fun anUnknownStoredColourIsTreatedAsNotChosen() {
        assertEquals(PetColor.ORANGE, PetColor.effective(listOf(pet("a", 1, "MAGENTA"))).getValue("a"))
    }
}
