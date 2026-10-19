package com.zylinn.petdiary.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.zylinn.petdiary.data.reminder.Reminder

/** Wraps AlarmManager: one alarm per reminder, no server involved (P4-02). */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun intent(id: String, dueMillis: Long = 0L) =
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("petdiary://reminder/$id"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_DUE, dueMillis)

    /** Schedules (or replaces) the alarm for [reminder] at [triggerAt]; [dueMillis] lets the receiver spot stale alarms. */
    fun schedule(reminder: Reminder, triggerAt: Long, dueMillis: Long) {
        val pi = PendingIntent.getBroadcast(
            context, 0, intent(reminder.id, dueMillis), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Exact when the user allows it (Android 12+ "alarms & reminders"), otherwise a Doze-friendly inexact alarm.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(id: String) {
        val pi = PendingIntent.getBroadcast(
            context, 0, intent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pi)
        pi.cancel()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_DUE = "reminder_due"
    }
}
