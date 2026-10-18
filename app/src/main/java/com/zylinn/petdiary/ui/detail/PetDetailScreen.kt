package com.zylinn.petdiary.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.ui.pets.PetAvatar
import com.zylinn.petdiary.ui.pets.UNDO_WINDOW_MS
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Width at which the calendar and the record list sit side by side (P2-10). */
private val TwoPaneMinWidth = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    vm: PetDetailViewModel,
    onBack: () -> Unit,
    onPickType: (RecordType, List<String>, LocalDate) -> Unit,
    onOpenRecord: (id: String, type: RecordType, day: LocalDate) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val header by vm.header.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val chips by vm.lastChips.collectAsStateWithLifecycle()
    val petChoices by vm.petChoices.collectAsStateWithLifecycle()
    val quickFeeding by vm.quickFeeding.collectAsStateWithLifecycle()
    val justAdded by vm.justAdded.collectAsStateWithLifecycle()
    val justDeleted by vm.justDeleted.collectAsStateWithLifecycle()
    var showPanel by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LaunchedEffect(justAdded) {
        val ids = justAdded ?: return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbar.showSnackbar("已記錄餵食", actionLabel = "復原", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) vm.undoQuickFeed(ids) else vm.quickFeedAcknowledged()
    }
    LaunchedEffect(justDeleted) {
        val card = justDeleted ?: return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbar.showSnackbar("已刪除「${card.type.label}」", actionLabel = "復原", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(card) else vm.deleteAcknowledged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                title = { Text(calendar.selected.titleText(), modifier = Modifier.testTag("detail_title")) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPanel = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("fab_add_record"),
            ) { Icon(Icons.Filled.Add, contentDescription = "新增紀錄") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val paneWidth = maxWidth
            val twoPane = paneWidth >= TwoPaneMinWidth
            if (showPanel) {
                AddRecordPanel(
                    wide = twoPane,
                    currentPetId = vm.petId,
                    pets = petChoices,
                    quickFeeding = quickFeeding,
                    onDismiss = { showPanel = false },
                    onPickType = { type, petIds ->
                        showPanel = false
                        onPickType(type, petIds, calendar.selected)
                    },
                    onQuickFeed = { petIds ->
                        showPanel = false
                        vm.quickFeed(petIds)
                    },
                )
            }
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .width(minOf(paneWidth * 0.45f, 420.dp))
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("calendar_pane"),
                    ) {
                        header?.let { HeaderRow(it) }
                        // Wide screens always show the whole month next to the list.
                        MonthCalendar(calendar, vm::select, vm::shiftMonth)
                        LastChipsRow(chips)
                    }
                    RecordList(
                        cards = cards,
                        onOpenRecord = { card -> onOpenRecord(card.id, card.type, calendar.selected) },
                        onDelete = vm::deleteRecord,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("records_pane"),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).testTag("calendar_pane"),
                    ) {
                        header?.let { HeaderRow(it) }
                        WeekStrip(calendar, vm::select, vm::shiftWeek, vm::toggleExpanded, showExpandToggle = true)
                        if (calendar.expanded) MonthCalendar(calendar, vm::select, vm::shiftMonth)
                        LastChipsRow(chips)
                    }
                    RecordList(
                        cards = cards,
                        onOpenRecord = { card -> onOpenRecord(card.id, card.type, calendar.selected) },
                        onDelete = vm::deleteRecord,
                        modifier = Modifier.weight(1f).testTag("records_pane"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(header: PetHeader) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PetAvatar(name = header.name, seed = header.id, photo = header.photo, size = 44.dp)
        Column {
            Text(header.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                header.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("header_subtitle"),
            )
        }
    }
}

/** "上次紀錄" strip: answers "when was the last deworming?" without searching. */
@Composable
private fun LastChipsRow(chips: List<LastChip>) {
    if (chips.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("last_chips")) {
        items(chips, key = { it.type }) { chip ->
            AssistChip(
                onClick = {},
                label = { Text(chip.text, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(chip.type.icon, contentDescription = null, tint = chip.type.color, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = null,
                modifier = Modifier.testTag("last_${chip.type.name}"),
            )
        }
    }
}

@Composable
private fun RecordList(
    cards: List<RecordCard>?,
    onOpenRecord: (RecordCard) -> Unit,
    onDelete: (RecordCard) -> Unit,
    modifier: Modifier,
) {
    val list = cards ?: return
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            // Bottom padding keeps the last card clear of the FAB (P2-04).
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("record_list"),
        ) {
            if (list.isEmpty()) {
                item {
                    Text(
                        "這天還沒有紀錄，按右下角「＋」新增",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp).testTag("records_empty"),
                    )
                }
            }
            items(list, key = { it.id }) { card ->
                SwipeToDelete(onDelete = { onDelete(card) }, modifier = Modifier.animateItem()) {
                    RecordCardView(card, onClick = { onOpenRecord(card) })
                }
            }
        }
    }
}

@Composable
fun RecordCardView(card: RecordCard, onClick: () -> Unit) {
    val color = card.type.color
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .testTag("record_${card.id}"),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)),
            ) {
                Icon(
                    card.type.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp).semantics { contentDescription = "${card.type.label}圖示" },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(card.type.label, style = MaterialTheme.typography.titleSmall)
                if (card.summary.isNotEmpty()) {
                    Text(card.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                card.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(card.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.attachmentCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("attach_count_${card.id}")) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = "附件", modifier = Modifier.size(14.dp))
                        Text("${card.attachmentCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Swipe a card to the left to delete it (S12). No confirmation: the snackbar offers "復原" (S6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                // The list restores item state by key; without resetting, an undone record would come
                // back still "swiped away" and immediately delete itself again.
                scope.launch { state.reset() }
            }
        },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        modifier = modifier,
    ) { content() }
}

