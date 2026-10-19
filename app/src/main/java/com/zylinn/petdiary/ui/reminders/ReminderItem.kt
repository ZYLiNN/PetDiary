package com.zylinn.petdiary.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zylinn.petdiary.data.reminder.ReminderRow
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.domain.ReminderSchedule
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon
import java.time.LocalDate

/**
 * One reminder row: type color + icon, "what · pet", when and how often, and a checkbox that completes it.
 * Overdue rows are red and say "已過期" in words too (never color alone).
 */
@Composable
fun ReminderItem(
    row: ReminderRow,
    today: LocalDate,
    overdue: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    showPet: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val r = row.reminder
    val color = r.type.color
    val shape = RoundedCornerShape(14.dp)
    val whenText = ReminderSchedule.dueLabel(LocalDate.ofEpochDay(r.dueEpochDay), r.minuteOfDay, today)
    val repeatText = ReminderSchedule.repeatLabel(r.repeat, r.repeatEvery).takeIf { r.repeat.name != "NONE" }
    val subtitle = listOfNotNull(if (overdue) "已過期" else null, whenText, repeatText).joinToString("・")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .testTag("reminder_${r.id}"),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)),
            ) {
                Icon(
                    r.type.icon, contentDescription = null, tint = color,
                    modifier = Modifier.size(20.dp).semantics { contentDescription = "${r.type.label}圖示" },
                )
            }
            Column(Modifier.weight(1f)) {
                val what = r.title ?: r.type.label
                Text(if (showPet) "$what・${row.petName}" else what, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = false,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "完成" }.testTag("reminder_check_${r.id}"),
            )
        }
    }
}
