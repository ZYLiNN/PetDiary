package com.zylinn.petdiary.ui.search

import com.zylinn.petdiary.data.record.FAMILY_PET_ID
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.RecordableTypes
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.ui.detail.PetChoice
import com.zylinn.petdiary.ui.detail.RecordCardView
import com.zylinn.petdiary.ui.records.DateDialog
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon
import java.time.LocalDate

/** Example keywords shown before the user types anything; tapping one fills the box. */
private val examples = listOf("疫苗", "驅蟲", "看醫生", "體重", "洗澡")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onOpenHit: (petId: String, day: LocalDate) -> Unit,
    /** A shared purchase has no pet page, so it opens in the record editor (D84). */
    onOpenSharedRecord: (recordId: String, day: LocalDate) -> Unit = { _, _ -> },
    today: LocalDate,
    autoFocus: Boolean = true,
) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val pets by vm.petChoices.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                title = {
                    TextField(
                        value = filters.text,
                        onValueChange = vm::setText,
                        placeholder = { Text("搜尋紀錄") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filters.text.isNotEmpty()) {
                                IconButton(onClick = { vm.setText("") }, modifier = Modifier.testTag("search_clear")) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus).testTag("search_field"),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("search_results"),
            ) {
                item { Filters(filters, pets, vm, today) }
                when (val r = results) {
                    SearchResults.Idle -> item { IdleHint(onExample = vm::setText) }
                    is SearchResults.Found -> {
                        if (r.count == 0) {
                            item {
                                Text(
                                    text = if (filters.text.isNotBlank()) "找不到「${filters.text.trim()}」的紀錄\n換個關鍵字，或調整上面的篩選試試看"
                                    else "這些條件下沒有紀錄，調整篩選試試看",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 32.dp).testTag("search_empty"),
                                )
                            }
                        } else {
                            item {
                                Text(
                                    "共 ${r.count} 筆",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("search_count"),
                                )
                            }
                            r.groups.forEach { group ->
                                item(key = "h_${group.day}") {
                                    Text(
                                        group.header,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 8.dp).testTag("group_${group.day.toEpochDay()}"),
                                    )
                                }
                                items(group.hits, key = { it.card.id }) { hit ->
                                    RecordCardView(
                                        hit.card,
                                        onClick = { if (hit.petId == FAMILY_PET_ID) onOpenSharedRecord(hit.card.id, hit.day) else onOpenHit(hit.petId, hit.day) },
                                        petName = hit.petName,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(filters: SearchFilters, pets: List<PetChoice>, vm: SearchViewModel, today: LocalDate) {
    var pickFrom by rememberSaveable { mutableStateOf(false) }
    var pickTo by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (pets.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pets, key = { it.id }) { pet ->
                    FilterChip(
                        selected = pet.id in filters.petIds,
                        onClick = { vm.togglePet(pet.id) },
                        label = { Text(pet.name) },
                        modifier = Modifier.testTag("filter_pet_${pet.id}"),
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("type_filter_row")) {
            items(RecordableTypes) { type ->
                FilterChip(
                    selected = type in filters.types,
                    onClick = { vm.toggleType(type) },
                    label = { Text(type.label) },
                    leadingIcon = { Icon(type.icon, contentDescription = null, tint = type.color, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("filter_type_${type.name}"),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DateRange.entries) { range ->
                val label = if (range == DateRange.CUSTOM && filters.range == DateRange.CUSTOM) customLabel(filters) else range.label
                FilterChip(
                    selected = filters.range == range,
                    onClick = { if (range == DateRange.CUSTOM) pickFrom = true else vm.setRange(range) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("filter_range_${range.name}"),
                )
            }
        }
    }
    if (pickFrom) {
        DateDialog(filters.customFrom, maxDate = today, onDismiss = { pickFrom = false }) { d ->
            vm.setCustomRange(d, filters.customTo ?: today)
            pickTo = true
        }
    }
    if (pickTo) {
        DateDialog(filters.customTo ?: today, maxDate = today, minDate = filters.customFrom, onDismiss = { pickTo = false }) { d ->
            vm.setCustomRange(filters.customFrom, d)
        }
    }
}

private fun customLabel(f: SearchFilters): String {
    fun LocalDate.s() = "$monthValue/$dayOfMonth"
    return listOfNotNull(f.customFrom?.s(), f.customTo?.s()).joinToString("～").ifEmpty { "自訂" }
}

@Composable
private fun IdleHint(onExample: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 24.dp).testTag("search_idle")) {
        Text(
            "輸入關鍵字，或點上面的類型來找紀錄。\n藥名、醫院、備註、保健食品都找得到。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(examples) { word ->
                AssistChip(onClick = { onExample(word) }, label = { Text(word) }, modifier = Modifier.testTag("example_$word"))
            }
        }
    }
}
