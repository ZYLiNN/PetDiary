package com.zylinn.petdiary.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import kotlinx.coroutines.runBlocking
import org.junit.After
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.ui.navigation.TopLevelDestination
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule val rule = createComposeRule()

    private val container = (ApplicationProvider.getApplicationContext<Context>().applicationContext as PetDiaryApplication).container
    private var petId: String? = null

    // The stats page shows an empty state without pets, so the test brings its own pet instead of relying on leftovers.
    @Before fun setUp() {
        petId = runBlocking { container.petRepository.add(PetInput(name = "導覽貓", species = Species.CAT, birthEpochDay = 1)).id }
        rule.setContent { PetDiaryTheme { PetDiaryApp() } }
    }

    @After fun tearDown() {
        petId?.let { id -> runBlocking { container.petRepository.moveToTrash(id) } }
    }

    // P0-01
    @Test fun fourTabsSwitchContent() {
        val titles = mapOf(
            TopLevelDestination.Pets to "我的毛孩",
            TopLevelDestination.Reminders to "提醒",
            TopLevelDestination.Stats to "統計",
            TopLevelDestination.Settings to "設定",
        )
        TopLevelDestination.entries.forEach { dest ->
            rule.onNodeWithTag("tab_${dest.name}").performClick().assertIsSelected()
            val content = when (dest) {
                TopLevelDestination.Pets -> "pet_grid"
                TopLevelDestination.Reminders -> "reminder_list"
                TopLevelDestination.Stats -> "stats_scroll"
                else -> "list_${dest.name}"
            }
            rule.onNodeWithTag(content).assertIsDisplayed()
        }
        rule.onNodeWithTag("tab_Pets").performClick()
        rule.onNodeWithText(titles.getValue(TopLevelDestination.Pets)).assertIsDisplayed()
    }

    // P0-03
    @Test fun tabStateKeptAcrossTabSwitch() {
        rule.onNodeWithTag("tab_Stats").performClick()
        rule.onNodeWithTag("range_MONTH").performClick().assertIsSelected()

        rule.onNodeWithTag("tab_Settings").performClick()
        rule.onNodeWithTag("list_Settings").assertIsDisplayed()

        rule.onNodeWithTag("tab_Stats").performClick()
        rule.onNodeWithTag("range_MONTH").assertIsSelected()
    }

    // C-07
    @Test fun tabsMeetMinimumTouchTarget() {
        TopLevelDestination.entries.forEach { dest ->
            rule.onNodeWithTag("tab_${dest.name}")
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }
}
