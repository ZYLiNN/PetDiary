package com.zylinn.petdiary.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
            TopLevelDestination.Calendar to "行事曆",
            TopLevelDestination.Album to "相簿",
            TopLevelDestination.Stats to "統計",
        )
        TopLevelDestination.entries.forEach { dest ->
            rule.onNodeWithTag("tab_${dest.name}").performClick().assertIsSelected()
            val content = when (dest) {
                TopLevelDestination.Pets -> "pet_grid"
                TopLevelDestination.Album -> "album_tab_PHOTOS"
                TopLevelDestination.Stats -> "stats_scroll"
                TopLevelDestination.Calendar -> "cal_month_title"
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

        rule.onNodeWithTag("tab_Calendar").performClick()
        rule.onNodeWithTag("cal_month_title").assertIsDisplayed()

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

    // P10: settings is a gear on every main tab, not a tab
    @Test fun settingsIsAGearOnEveryTabAndBackReturnsToTheSameTab() {
        rule.onNodeWithTag("tab_Settings").assertDoesNotExist()
        TopLevelDestination.entries.forEach { dest ->
            rule.onNodeWithTag("tab_${dest.name}").performClick()
            rule.onNodeWithTag("open_settings").assertIsDisplayed().performClick()
            rule.onNodeWithTag("list_Settings").assertIsDisplayed()
            rule.onNodeWithTag("back_button").performClick()
            rule.onNodeWithTag("tab_${dest.name}").assertIsSelected()
        }
    }

    // P10: adding the gear must not disturb the search button on the pets tab
    @Test fun searchButtonStillWorksNextToTheGear() {
        rule.onNodeWithTag("tab_Pets").performClick()
        val search = rule.onNodeWithTag("home_search").assertIsDisplayed().getUnclippedBoundsInRoot()
        val gear = rule.onNodeWithTag("open_settings").assertIsDisplayed().getUnclippedBoundsInRoot()
        // Each icon button draws 40dp inside a 48dp touch target: neighbours must be 48dp apart and never overlap.
        assertTrue("gear sits right of search without overlap: $search / $gear", gear.left > search.right)
        assertTrue("48dp pitch: $search / $gear", (gear.left - search.left).value >= 47.5f)
        rule.onNodeWithTag("home_search").performClick()
        rule.onNodeWithTag("search_field").assertIsDisplayed()
    }

    // P13 (D80): 提醒 is no longer a tab; the full list opens from the calendar and ← comes back
    @Test fun reminderListOpensFromTheCalendar() {
        rule.onNodeWithTag("tab_Reminders").assertDoesNotExist()
        rule.onNodeWithTag("tab_Calendar").performClick()
        rule.onNodeWithTag("cal_open_reminders").assertIsDisplayed().performClick()
        rule.onNodeWithTag("reminder_list").assertIsDisplayed()
        rule.onNodeWithTag("fab_add_reminder").assertIsDisplayed()
        rule.onNodeWithTag("back_button").performClick()
        rule.onNodeWithTag("tab_Calendar").assertIsSelected()
    }

    // P10: from the all-pets calendar into one pet shows only that pet's data for that day
    @Test fun tappingACalendarEntryOpensThatPetAloneOnThatDay() = runBlocking {
        val today = java.time.LocalDate.now(container.clock)
        val other = container.petRepository.add(PetInput(name = "另一隻", species = Species.DOG, birthEpochDay = 1)).id
        val mine = petId!!
        val minute = 7 * 60 + 5
        val mineRecord = container.recordRepository.addForPets(listOf(mine), com.zylinn.petdiary.data.record.RecordInput(com.zylinn.petdiary.data.record.RecordType.VET, today.toEpochDay(), minute, title = "導覽貓健檢")).single()
        val otherRecord = container.recordRepository.addForPets(listOf(other), com.zylinn.petdiary.data.record.RecordInput(com.zylinn.petdiary.data.record.RecordType.VET, today.toEpochDay(), minute + 1, title = "另一隻健檢")).single()
        try {
            rule.onNodeWithTag("tab_Calendar").performClick()
            rule.waitUntil(8_000) { runCatching { rule.onNodeWithTag("cal_row_$mineRecord").assertExists() }.isSuccess }
            rule.onNodeWithTag("cal_row_$otherRecord").assertExists() // the calendar lists everyone
            rule.onNodeWithTag("cal_row_$mineRecord").performClick()
            rule.waitUntil(8_000) { runCatching { rule.onNodeWithTag("record_$mineRecord").assertExists() }.isSuccess }
            rule.onNodeWithTag("record_$otherRecord").assertDoesNotExist() // the pet's own page is that pet only
        } finally {
            container.petRepository.moveToTrash(other)
        }
    }
}
