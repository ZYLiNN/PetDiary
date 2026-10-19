package com.zylinn.petdiary.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.RecordableTypes
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon

/**
 * "新增紀錄" panel (S1): pick pets (S3), then tap a type icon — no dropdown.
 * Compact screens get a bottom sheet; wide screens a centered dialog so it isn't stretched (P2-05c).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordPanel(
    wide: Boolean,
    currentPetId: String,
    pets: List<PetChoice>,
    quickFeeding: QuickFeeding?,
    quickWalk: QuickFeeding?,
    onDismiss: () -> Unit,
    onPickType: (RecordType, List<String>) -> Unit,
    onQuickFeed: (List<String>) -> Unit,
    onQuickWalk: (List<String>) -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(listOf(currentPetId)) }
    val content: @Composable () -> Unit = {
        PanelContent(
            pets = pets,
            chosen = chosen,
            onToggle = { id ->
                // At least one pet must stay selected.
                chosen = if (id in chosen) (chosen - id).ifEmpty { chosen } else chosen + id
            },
            quickFeeding = quickFeeding,
            quickWalk = quickWalk,
            onPickType = { onPickType(it, chosen) },
            onQuickFeed = { onQuickFeed(chosen) },
            onQuickWalk = { onQuickWalk(chosen) },
        )
    }
    if (wide) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 480.dp).testTag("add_record_dialog"),
            ) { Box(Modifier.padding(vertical = 16.dp)) { content() } }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("add_record_sheet"),
        ) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelContent(
    pets: List<PetChoice>,
    chosen: List<String>,
    onToggle: (String) -> Unit,
    quickFeeding: QuickFeeding?,
    quickWalk: QuickFeeding?,
    onPickType: (RecordType) -> Unit,
    onQuickFeed: () -> Unit,
    onQuickWalk: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Column {
            Text("新增紀錄", style = MaterialTheme.typography.titleLarge)
            Text(
                "選好寵物後，點類型開始記錄",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pets.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pets.forEach { pet ->
                    val selected = pet.id in chosen
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(pet.id) },
                        label = { Text(pet.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("pick_pet_${pet.id}"),
                    )
                }
            }
        }
        FlowRow(
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RecordableTypes.forEach { type -> TypeTile(type, onClick = { onPickType(type) }, modifier = Modifier.weight(1f)) }
        }
        if (quickFeeding != null || quickWalk != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("一鍵記錄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (quickFeeding != null) QuickRow("quick_feeding", RecordType.FEEDING, quickFeeding, onQuickFeed)
            if (quickWalk != null) QuickRow("quick_walk", RecordType.WALK, quickWalk, onQuickWalk)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun QuickRow(tag: String, type: RecordType, quick: QuickFeeding, onSave: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag(tag),
    ) {
        Icon(type.icon, contentDescription = null, tint = type.color)
        Text(
            text = "${type.label}・同上次" + quick.summary.takeIf { it.isNotEmpty() }?.let { "（$it）" }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSave, modifier = Modifier.testTag("${tag}_save")) { Text("記錄") }
    }
}

@Composable
private fun TypeTile(type: RecordType, onClick: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("type_${type.name}"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(type.color.copy(alpha = 0.14f)),
        ) { Icon(type.icon, contentDescription = null, tint = type.color) }
        Spacer(Modifier.height(4.dp))
        Text(type.label, style = MaterialTheme.typography.labelMedium)
    }
}
