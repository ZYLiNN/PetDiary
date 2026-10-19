package com.zylinn.petdiary.ui.reminders

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zylinn.petdiary.data.reminder.LateChoice
import java.time.LocalDate

private fun LocalDate.s() = "$monthValue/$dayOfMonth"

/** D37: a repeating reminder was finished late — let the user pick how the next date is counted. */
@Composable
fun LateCompleteDialog(ask: LateChoice, onChoose: (rollFromToday: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("晚了 ${ask.lateDays} 天才完成") },
        text = { Text("下次提醒要從今天重新算（${ask.rolled.s()}），還是照原本的節奏（${ask.anchored.s()}）？") },
        confirmButton = {
            TextButton(onClick = { onChoose(true) }, modifier = Modifier.testTag("late_roll")) { Text("從今天重新算") }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(false) }, modifier = Modifier.testTag("late_keep")) { Text("照原本節奏") }
        },
    )
}
