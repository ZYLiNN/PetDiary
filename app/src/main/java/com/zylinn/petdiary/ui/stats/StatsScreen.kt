package com.zylinn.petdiary.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.ui.common.SettingsAction
import com.zylinn.petdiary.domain.RangeKind
import com.zylinn.petdiary.domain.StatMetric
import com.zylinn.petdiary.ui.records.DateDialog
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onOpenSettings: () -> Unit = {}) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val choices by vm.petChoices.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val today = vm.today()
    var pickFrom by rememberSaveable { mutableStateOf(false) }
    var pickTo by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("統計") },
                actions = { SettingsAction(onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (choices.isEmpty()) {
                Text(
                    "還沒有毛孩，新增寵物並記錄後就能在這裡看統計。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).testTag("stats_no_pets"),
                )
                return@Box
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("stats_scroll"),
            ) {
                // pets (multi-select)
                if (choices.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEachIndexed { index, pet ->
                            val selected = filters.petIds.isEmpty() || pet.id in filters.petIds
                            FilterChip(
                                selected = selected,
                                onClick = { vm.togglePet(pet.id, choices.map { it.id }) },
                                label = { Text(pet.name) },
                                leadingIcon = { Box(Modifier.size(12.dp).background(petColor(index, dark), CircleShape)) },
                                modifier = Modifier.testTag("stats_pet_${pet.id}"),
                            )
                        }
                    }
                }
                // range (single-select)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RangeKind.entries.forEachIndexed { i, kind ->
                        SegmentedButton(
                            selected = filters.kind == kind,
                            onClick = { vm.setKind(kind) },
                            shape = SegmentedButtonDefaults.itemShape(i, RangeKind.entries.size),
                            modifier = Modifier.testTag("range_${kind.name}"),
                        ) { Text(kind.label) }
                    }
                }
                if (filters.kind == RangeKind.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f).testTag("stats_from")) {
                            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                            Text("  起 " + (filters.customFrom ?: today).let { "${it.year}/${it.monthValue}/${it.dayOfMonth}" })
                        }
                        OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f).testTag("stats_to")) {
                            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                            Text("  迄 " + (filters.customTo ?: today).let { "${it.year}/${it.monthValue}/${it.dayOfMonth}" })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shift(-1) }, modifier = Modifier.testTag("stats_prev")) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一段")
                        }
                        Text(
                            ui?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).testTag("stats_title"),
                        )
                        IconButton(onClick = { vm.shift(1) }, enabled = filters.offset < 0, modifier = Modifier.testTag("stats_next")) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一段")
                        }
                    }
                }
                // metric (single-select)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("metric_row")) {
                    items(StatMetric.entries) { m ->
                        FilterChip(
                            selected = filters.metric == m,
                            onClick = { vm.setMetric(m) },
                            label = { Text(m.label) },
                            leadingIcon = {
                                val type = m.recordType
                                if (type != null) Icon(type.icon, null, tint = type.color, modifier = Modifier.size(16.dp))
                                else Icon(Icons.Outlined.Savings, null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("metric_${m.name}"),
                        )
                    }
                }

                val state = ui
                if (state != null) {
                    val result = state.result
                    if (!result.hasData) {
                        Text(
                            "這段期間沒有「${result.metric.label}」的紀錄。\n換個時間或項目看看，或先到寵物頁新增紀錄。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp).testTag("stats_empty"),
                        )
                    } else {
                        result.headline?.let {
                            Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("stats_headline"))
                        }
                        val description = result.metric.label + (if (result.metric.isLine) "折線圖" else "長條圖") + "，" + state.title + "。" +
                            result.series.joinToString("；") { it.name + "：" + it.summary }
                        Box(
                            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(12.dp).testTag("stats_chart"),
                        ) {
                            if (result.metric.isLine) LineChart(result, state.pets, description) else StackedBarChart(result, state.pets, description)
                        }
                        Text(
                            "單位：${result.metric.unit}" + if (result.metric.isLine && result.metric != StatMetric.WEIGHT && result.buckets.firstOrNull()?.days ?: 1 > 1) "（每天平均）" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val weights = result.series.filter { it.total > 0 }.map { it.total }
                        if (result.metric == StatMetric.WEIGHT && weights.size > 1 && weights.max() / weights.min() > 3) {
                            Text(
                                "提示：體型差很多的寵物放在同一張圖，折線會比較平；只選一隻看變化會更清楚。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("stats_weight_hint"),
                            )
                        }
                        result.series.forEach { s ->
                            val pet = state.pets.first { it.id == s.petId }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("stats_summary_${s.petId}"),
                            ) {
                                Box(Modifier.padding(top = 5.dp).size(12.dp).background(petColor(pet.colorIndex, dark), CircleShape))
                                Column {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text(s.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickFrom) {
        DateDialog(filters.customFrom ?: today, maxDate = today, onDismiss = { pickFrom = false }) { vm.setCustomFrom(it) }
    }
    if (pickTo) {
        DateDialog(filters.customTo ?: today, maxDate = today, minDate = filters.customFrom, onDismiss = { pickTo = false }) { vm.setCustomTo(it) }
    }
}
