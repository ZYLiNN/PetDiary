package com.zylinn.petdiary.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import com.zylinn.petdiary.data.reminder.ReminderRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {

    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifier = ReminderNotifier(context)
    private val row = ReminderRow(
        Reminder("rem-test-1", "p1", RecordType.MEDICINE, "點眼藥", 20000, 9 * 60, ReminderRepeat.DAILY, 1, createdAt = 0, updatedAt = 0),
        "雷米",
    )

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Posting is asynchronous, so wait (briefly) for the system to list the notification. */
    private fun waitUntilListed(listed: Boolean) {
        val id = notifier.notificationId(row.reminder.id)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && manager.activeNotifications.any { it.id == id } != listed) Thread.sleep(100)
    }

    @After fun cleanUp() = notifier.cancel(row.reminder.id)

    // P11-03: several pets due together → one notification naming all of them
    @Test fun mergedNotificationNamesEveryPet() {
        val other = ReminderRow(row.reminder.copy(id = "rem-test-2", petId = "p2"), "雷利")
        notifier.show(listOf(row, other))
        waitUntilListed(true)
        val n = manager.activeNotifications.single { it.id == notifier.notificationId(row.reminder.id) }.notification
        assertEquals("雷米、雷利：點眼藥", n.extras.getString("android.title"))
    }

    // P4-02: the notification carries the pet, what to do, the time and a 完成 button (P4-03)
    @Test fun showsNotificationWithCompleteAction() {
        notifier.show(row)
        waitUntilListed(true)
        val n = manager.activeNotifications
            .single { it.id == notifier.notificationId(row.reminder.id) }.notification
        assertEquals("雷米：點眼藥", n.extras.getString("android.title"))
        assertTrue(n.extras.getString("android.text")!!.startsWith("09:00"))
        assertEquals(listOf("完成"), n.actions.map { it.title.toString() })
    }

    @Test fun cancelRemovesIt() {
        notifier.show(row)
        waitUntilListed(true)
        notifier.cancel(row.reminder.id)
        waitUntilListed(false)
        val active = manager.activeNotifications
        assertTrue(active.none { it.id == notifier.notificationId(row.reminder.id) })
    }
}
