package com.zylinn.petdiary.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.zylinn.petdiary.ui.navigation.TopLevelDestination
import com.zylinn.petdiary.ui.screens.PlaceholderScreen

/**
 * Root scaffold. NavigationSuiteScaffold shows a bottom bar on compact widths (folded)
 * and a side rail on wider windows (unfolded / wide-fold landscape).
 * Each tab keeps its own saved state (e.g. scroll position) via a SaveableStateHolder.
 */
@Composable
fun PetDiaryApp() {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Pets) }
    val stateHolder = rememberSaveableStateHolder()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                val selected = dest == current
                item(
                    selected = selected,
                    onClick = { current = dest },
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(dest.label)) },
                    modifier = Modifier.testTag("tab_${dest.name}"),
                )
            }
        },
    ) {
        stateHolder.SaveableStateProvider(current.name) {
            PlaceholderScreen(destination = current)
        }
    }
}
