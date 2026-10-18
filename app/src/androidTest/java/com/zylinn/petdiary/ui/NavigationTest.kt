package com.zylinn.petdiary.ui

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

    @Before fun setUp() {
        rule.setContent { PetDiaryTheme { PetDiaryApp() } }
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
            val content = if (dest == TopLevelDestination.Pets) "pet_grid" else "list_${dest.name}"
            rule.onNodeWithTag(content).assertIsDisplayed()
        }
        rule.onNodeWithTag("tab_Pets").performClick()
        rule.onNodeWithText(titles.getValue(TopLevelDestination.Pets)).assertIsDisplayed()
    }

    // P0-03
    @Test fun scrollPositionKeptAcrossTabSwitch() {
        rule.onNodeWithTag("tab_Settings").performClick()
        rule.onNodeWithTag("list_Settings").performScrollToIndex(10)
        rule.onNodeWithTag("item_Settings_9").assertIsDisplayed()

        rule.onNodeWithTag("tab_Stats").performClick()
        rule.onNodeWithTag("list_Stats").assertIsDisplayed()
        rule.onNodeWithTag("item_Stats_0").assertIsDisplayed()

        rule.onNodeWithTag("tab_Settings").performClick()
        rule.onNodeWithTag("item_Settings_9").assertIsDisplayed()
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
