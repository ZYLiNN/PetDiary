package com.zylinn.petdiary.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zylinn.petdiary.MainActivity
import com.zylinn.petdiary.R
import com.zylinn.petdiary.data.reminder.ReminderRow
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.domain.ReminderSchedule

class ReminderNotifier(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "寵物提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "餵藥、疫苗、驅蟲、回診等提醒"
                },
            )
        }
    }

    fun notificationId(reminderId: String) = reminderId.hashCode()

    fun show(row: ReminderRow) = show(listOf(row))

    /** One notification for reminders due together (same moment, type and name); 完成 finishes all of them. */
    fun show(rows: List<ReminderRow>) {
        if (!hasPermission() || rows.isEmpty()) return
        ensureChannel()
        val r = rows.first().reminder
        val open = PendingIntent.getActivity(
            context, notificationId(r.id),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, notificationId(r.id),
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_COMPLETE)
                .putExtra(ReminderScheduler.EXTRA_ID, r.id)
                .putExtra(ReminderScheduler.EXTRA_IDS, rows.map { it.reminder.id }.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val what = r.title ?: r.type.label
        val text = RecordFormat.time(r.minuteOfDay) + "・" + ReminderSchedule.repeatLabel(r.repeat, r.repeatEvery)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(rows.joinToString("、") { it.petName } + "：$what")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "完成", done)
            .build()
        manager.notify(notificationId(r.id), notification)
    }

    fun cancel(reminderId: String) = manager.cancel(notificationId(reminderId))

    fun activeCount() = manager.activeNotifications.count { it.notification.channelId == CHANNEL }

    companion object {
        const val CHANNEL = "reminders"
    }
}
