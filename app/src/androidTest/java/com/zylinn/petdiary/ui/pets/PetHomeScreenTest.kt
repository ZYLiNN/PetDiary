package com.zylinn.petdiary.ui.pets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetHomeScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun tile(id: String, name: String = "寵物$id", species: Species = Species.CAT) =
        PetTile(id = id, name = name, species = species, ageLabel = "3 歲", photo = null)

    private fun show(
        pets: List<PetTile>,
        width: Dp = 411.dp,
        height: Dp = 900.dp,
        onAdd: () -> Unit = {},
    ) = rule.setContent {
        PetDiaryTheme {
            Box(Modifier.requiredSize(width, height)) {
                PetHomeScreen(state = PetHomeState.Ready(pets), onAddPet = onAdd, onOpenPet = {})
            }
        }
    }

    private fun columnsInFirstRow(ids: List<String>): Int {
        val tops = ids.map { rule.onNodeWithTag("pet_tile_$it").getUnclippedBoundsInRoot() }
        val firstTop = tops.minOf { it.top }
        return tops.count { it.top == firstTop }
    }

    // P1-01
    @Test fun emptyStateShowsOnlyAddTileAndGuide() {
        var added = 0
        show(emptyList(), onAdd = { added++ })
        rule.onNodeWithTag("empty_guide").assertIsDisplayed()
        rule.onNodeWithTag("add_pet_tile").assertIsDisplayed().performClick()
        assertEquals(1, added)
        assertEquals(0, rule.onAllNodesWithTag("pet_tile_", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun addTileAlwaysLastAndGuideHiddenWhenPetsExist() {
        show(listOf(tile("a"), tile("b")))
        rule.onNodeWithTag("empty_guide").assertDoesNotExist()
        val add = rule.onNodeWithTag("add_pet_tile").getUnclippedBoundsInRoot()
        val b = rule.onNodeWithTag("pet_tile_b").getUnclippedBoundsInRoot()
        assertTrue("add tile must come after last pet", add.top > b.top || (add.top == b.top && add.left > b.left))
    }

    // P1-08
    @Test fun eachSpeciesShowsItsSilhouette() {
        show(Species.entries.map { tile(it.name, species = it) }, height = 2400.dp)
        Species.entries.forEach { s ->
            rule.onNodeWithContentDescription("${s.label}剪影").assertExists()
        }
    }

    // P1-09
    @Test fun twoColumnsWhenFolded() {
        val ids = (1..6).map { "$it" }
        show(ids.map { tile(it) }, width = 411.dp)
        assertEquals(2, columnsInFirstRow(ids))
    }

    @Test fun threeToFourColumnsWhenUnfolded() {
        val ids = (1..6).map { "$it" }
        show(ids.map { tile(it) }, width = 760.dp)
        val cols = columnsInFirstRow(ids)
        assertTrue("expected 3–4 columns, got $cols", cols in 3..4)
    }

    // P1-12
    @Test fun longAndEmojiNamesStayInsideTile() {
        val long = "超級無敵可愛的小橘貓咪咪咪咪咪咪咪咪咪咪咪咪咪咪咪咪咪咪"
        show(listOf(tile("long", name = long), tile("emoji", name = "🐶🐱🐰毛毛"), tile("short", name = "雷")))
        listOf("long", "emoji").forEach { id ->
            val tileBounds = rule.onNodeWithTag("pet_tile_$id").getUnclippedBoundsInRoot()
            val nameBounds = rule.onNodeWithTag("pet_name_$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$id name overflows tile", nameBounds.right <= tileBounds.right && nameBounds.left >= tileBounds.left)
        }
        val h = listOf("long", "emoji", "short").map { rule.onNodeWithTag("pet_tile_$it").getUnclippedBoundsInRoot().let { b -> b.bottom - b.top } }
        assertTrue("tile heights differ: $h", h.maxOf { it.value } - h.minOf { it.value } < 0.5f)
    }
}
