package com.zylinn.petdiary.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zylinn.petdiary.ui.detail.PetDetailScreen
import com.zylinn.petdiary.ui.detail.PetDetailViewModel
import com.zylinn.petdiary.ui.navigation.Main
import com.zylinn.petdiary.ui.navigation.PetDetail
import com.zylinn.petdiary.ui.navigation.PetEditor
import com.zylinn.petdiary.ui.navigation.RecordEditor
import com.zylinn.petdiary.ui.navigation.TopLevelDestination
import com.zylinn.petdiary.ui.navigation.Trash
import com.zylinn.petdiary.ui.pets.PetHomeRoute
import com.zylinn.petdiary.ui.pets.editor.PetEditorScreen
import com.zylinn.petdiary.ui.pets.editor.PetEditorViewModel
import com.zylinn.petdiary.ui.screens.PlaceholderScreen
import com.zylinn.petdiary.ui.settings.SettingsScreen
import com.zylinn.petdiary.ui.trash.TrashScreen
import com.zylinn.petdiary.ui.trash.TrashViewModel

/** Root navigation: the tabbed main screen, with full-screen pages (editor, detail…) pushed on top. */
@Composable
fun PetDiaryApp() {
    val backStack = rememberNavBackStack(Main)
    val navigate: (NavKey) -> Unit = { backStack.add(it) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Main> { MainScreen(navigate) }
            entry<PetEditor> { key ->
                val vm = containerViewModel {
                    PetEditorViewModel(key.petId, it.petRepository, it.photoStore, it.photoImporter, it.clock)
                }
                PetEditorScreen(vm = vm, onDone = back)
            }
            entry<PetDetail> { key ->
                val vm = containerViewModel {
                    PetDetailViewModel(key.petId, it.petRepository, it.recordRepository, it.photoStore, it.clock)
                }
                PetDetailScreen(
                    vm = vm,
                    onBack = back,
                    onPickType = { type, petIds, day -> navigate(RecordEditor(petIds, type.name, day.toEpochDay())) },
                    onOpenRecord = {},
                )
            }
            entry<RecordEditor> {
                // Replaced by the real record form in P2 / N5.
                PlaceholderScreen(destination = TopLevelDestination.Stats)
            }
            entry<Trash> {
                val vm = containerViewModel { TrashViewModel(it.petRepository, it.trashRepository, it.photoStore, it.clock) }
                TrashScreen(vm = vm, onBack = back)
            }
        },
    )
}

/**
 * Tabbed scaffold. NavigationSuiteScaffold shows a bottom bar on compact widths (folded)
 * and a side rail on wider windows (unfolded / wide-fold landscape).
 * Each tab keeps its own saved state (e.g. scroll position) via a SaveableStateHolder.
 */
@Composable
private fun MainScreen(navigate: (NavKey) -> Unit) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Pets) }
    val stateHolder = rememberSaveableStateHolder()
    val scheme = MaterialTheme.colorScheme
    // Selected tab uses the brand brown instead of the default secondary (sage) color.
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.primary,
            selectedTextColor = scheme.primary,
            indicatorColor = scheme.surfaceVariant,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = scheme.primary,
            selectedTextColor = scheme.primary,
            indicatorColor = scheme.surfaceVariant,
        ),
    )

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
                    colors = itemColors,
                    modifier = Modifier.testTag("tab_${dest.name}"),
                )
            }
        },
    ) {
        stateHolder.SaveableStateProvider(current.name) {
            when (current) {
                TopLevelDestination.Pets -> PetHomeRoute(
                    onAddPet = { navigate(PetEditor()) },
                    onOpenPet = { id -> navigate(PetDetail(id)) },
                    onEditPet = { id -> navigate(PetEditor(id)) },
                )
                TopLevelDestination.Settings -> SettingsScreen(onOpenTrash = { navigate(Trash) })
                else -> PlaceholderScreen(destination = current)
            }
        }
    }
}
