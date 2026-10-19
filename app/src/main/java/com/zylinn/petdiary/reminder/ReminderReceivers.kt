package com.zylinn.petdiary.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.domain.ReminderSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try { block() } finally { pending.finish() }
    }
}

/** An alarm went off: show the notification if the reminder is still open and the alarm is not stale (P4-02). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val container = (context.applicationContext as PetDiaryApplication).container
        async {
            val row = container.reminderRepository.row(id) ?: return@async
            val r = row.reminder
            if (r.deletedAt != null || r.completedAt != null) return@async
            val due = ReminderSchedule.dueMillis(r.dueEpochDay, r.minuteOfDay, container.clock.zone)
            if (due > container.clock.millis() + STALE_SLACK_MS) return@async // the reminder was moved later
            container.notifier.show(row)
            container.reminderRepository.markNotified(id, due)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.zylinn.petdiary.REMINDER_FIRE"
        private const val STALE_SLACK_MS = 60_000L
    }
}

/** The "完成" button on the notification (P4-03). */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val container = (context.applicationContext as PetDiaryApplication).container
        container.notifier.cancel(id)
        async {
            val done = container.reminderRepository.complete(id)
            if (done != null) {
                CoroutineScope(Dispatchers.Main).launch { Toast.makeText(context, "已記錄完成", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.zylinn.petdiary.REMINDER_COMPLETE"
    }
}

/** Reboot / time change / app update: starting the process is enough, the syncer re-arms every alarm (P4-06). */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as PetDiaryApplication).container.reminderSyncer.start()
    }
}
