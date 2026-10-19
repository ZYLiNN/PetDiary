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
import androidx.compose.runtime.remember
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
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.ui.detail.PetDetailScreen
import com.zylinn.petdiary.ui.detail.PetDetailViewModel
import com.zylinn.petdiary.ui.calendar.CalendarScreen
import com.zylinn.petdiary.ui.calendar.CalendarViewModel
import com.zylinn.petdiary.ui.navigation.About
import com.zylinn.petdiary.ui.navigation.Settings
import com.zylinn.petdiary.ui.navigation.Family
import com.zylinn.petdiary.ui.navigation.Main
import com.zylinn.petdiary.ui.navigation.PetDetail
import com.zylinn.petdiary.ui.navigation.PetEditor
import com.zylinn.petdiary.ui.navigation.RecordEditor
import com.zylinn.petdiary.ui.navigation.ReminderEditor
import com.zylinn.petdiary.ui.navigation.Search
import com.zylinn.petdiary.ui.navigation.TopLevelDestination
import com.zylinn.petdiary.ui.navigation.Supplements
import com.zylinn.petdiary.ui.navigation.Trash
import com.zylinn.petdiary.ui.pets.PetHomeRoute
import com.zylinn.petdiary.ui.pets.editor.PetEditorScreen
import com.zylinn.petdiary.ui.pets.editor.PetEditorViewModel
import com.zylinn.petdiary.ui.records.RecordEditorScreen
import com.zylinn.petdiary.ui.records.RecordEditorViewModel
import com.zylinn.petdiary.ui.reminders.ReminderEditorScreen
import com.zylinn.petdiary.ui.reminders.ReminderEditorViewModel
import com.zylinn.petdiary.ui.reminders.RemindersScreen
import com.zylinn.petdiary.ui.reminders.RemindersViewModel
import com.zylinn.petdiary.ui.search.SearchScreen
import com.zylinn.petdiary.ui.search.SearchViewModel
import com.zylinn.petdiary.ui.settings.AboutScreen
import com.zylinn.petdiary.ui.settings.FamilyScreen
import com.zylinn.petdiary.ui.settings.FamilyViewModel
import com.zylinn.petdiary.ui.settings.SettingsScreen
import com.zylinn.petdiary.ui.settings.SettingsViewModel
import com.zylinn.petdiary.ui.settings.SupplementsScreen
import com.zylinn.petdiary.ui.settings.SupplementsViewModel
import com.zylinn.petdiary.ui.stats.StatsScreen
import com.zylinn.petdiary.ui.stats.StatsViewModel
import com.zylinn.petdiary.ui.trash.TrashScreen
import com.zylinn.petdiary.ui.trash.TrashViewModel
import java.time.LocalDate

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
                    PetDetailViewModel(
                        key.petId, it.petRepository, it.recordRepository, it.photoStore, it.clock,
                        initialDay = key.dayEpochDay?.let(LocalDate::ofEpochDay),
                        reminders = it.reminderRepository,
                        authorNames = it.cloudSync.authorNames,
                    )
                }
                PetDetailScreen(
                    vm = vm,
                    onBack = back,
                    onPickType = { type, petIds, day -> navigate(RecordEditor(petIds, type.name, day.toEpochDay())) },
                    onOpenRecord = { id, type, day -> navigate(RecordEditor(listOf(key.petId), type.name, day.toEpochDay(), id)) },
                    onSearch = { navigate(Search(key.petId)) },
                    onAddReminder = { day -> navigate(ReminderEditor(petId = key.petId, dayEpochDay = day.toEpochDay())) },
                    onOpenReminder = { id -> navigate(ReminderEditor(reminderId = id)) },
                )
            }
            entry<RecordEditor> { key ->
                val vm = containerViewModel {
                    RecordEditorViewModel(
                        petIds = key.petIds,
                        type = RecordType.valueOf(key.type),
                        date = LocalDate.ofEpochDay(key.dateEpochDay),
                        recordId = key.recordId,
                        pets = it.petRepository,
                        records = it.recordRepository,
                        clock = it.clock,
                        attachmentFiles = it.attachmentStore,
                        importer = it.attachmentImporter,
                    )
                }
                RecordEditorScreen(vm = vm, onDone = back)
            }
            entry<ReminderEditor> { key ->
                val vm = containerViewModel {
                    ReminderEditorViewModel(
                        key.reminderId, key.petId, key.dayEpochDay?.let(LocalDate::ofEpochDay),
                        it.petRepository, it.reminderRepository, it.clock,
                    )
                }
                ReminderEditorScreen(vm = vm, onDone = back)
            }
            entry<Search> { key ->
                val container = appContainer()
                val vm = containerViewModel { SearchViewModel(key.petId, it.petRepository, it.recordRepository, it.clock, it.cloudSync.authorNames) }
                SearchScreen(
                    vm = vm,
                    onBack = back,
                    onOpenHit = { petId, day -> navigate(PetDetail(petId, day.toEpochDay())) },
                    today = LocalDate.now(container.clock),
                )
            }
            entry<Supplements> {
                val vm = containerViewModel { SupplementsViewModel(it.recordRepository) }
                SupplementsScreen(vm = vm, onBack = back)
            }
            entry<Settings> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val vm = containerViewModel {
                    SettingsViewModel(it.settings, it.petRepository, it.recordRepository, java.io.File(context.cacheDir, "exports"), it.clock, it.cloudSync, it.accountService)
                }
                SettingsScreen(
                    vm = vm,
                    versionName = appVersionName(),
                    onOpenTrash = { navigate(Trash) },
                    onOpenSupplements = { navigate(Supplements) },
                    onOpenAbout = { navigate(About) },
                    onOpenFamily = { navigate(Family) },
                    onBack = back,
                )
            }
            entry<Family> {
                val vm = containerViewModel { FamilyViewModel(it.cloudSync, it.settings) }
                FamilyScreen(vm = vm, onBack = back)
            }
            entry<About> {
                AboutScreen(versionName = appVersionName(), onBack = back)
            }
            entry<Trash> {
                val vm = containerViewModel { TrashViewModel(it.petRepository, it.recordRepository, it.trashRepository, it.photoStore, it.clock) }
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
                    onSearch = { navigate(Search()) },
                    onOpenReminder = { id -> navigate(ReminderEditor(reminderId = id)) },
                    onOpenSettings = { navigate(Settings) },
                )
                TopLevelDestination.Calendar -> {
                    val vm = containerViewModel { CalendarViewModel(it.petRepository, it.recordRepository, it.reminderRepository, it.clock) }
                    CalendarScreen(
                        vm = vm,
                        onOpenDay = { petId, day -> navigate(PetDetail(petId, day.toEpochDay())) },
                        onAddReminder = { day -> navigate(ReminderEditor(dayEpochDay = day.toEpochDay())) },
                        onOpenSettings = { navigate(Settings) },
                    )
                }
                TopLevelDestination.Reminders -> {
                    val vm = containerViewModel { RemindersViewModel(it.reminderRepository, it.clock) }
                    RemindersScreen(
                        vm = vm,
                        onAdd = { navigate(ReminderEditor()) },
                        onEdit = { id -> navigate(ReminderEditor(reminderId = id)) },
                        onOpenSettings = { navigate(Settings) },
                    )
                }
                TopLevelDestination.Stats -> {
                    val vm = containerViewModel { StatsViewModel(it.petRepository, it.recordRepository, it.clock) }
                    StatsScreen(vm = vm, onOpenSettings = { navigate(Settings) })
                }
            }
        }
    }
}

@Composable
private fun appVersionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
}
