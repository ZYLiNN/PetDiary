package com.zylinn.petdiary.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * P4-02 / P4-03 with the real app container, real AlarmManager and the real notification button:
 * a reminder due at the next minute must pop a notification, and its 完成 button must record it.
 */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmE2ETest {

    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PetDiaryApplication).container
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun allowExactAlarms() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow").close()
    }

    @Test fun alarmPostsNotificationAndCompleteButtonRecordsIt() = runBlocking {
        allowExactAlarms()
        val pet = container.petRepository.add(PetInput(name = "E2E貓", species = Species.CAT, birthEpochDay = 1))
        val due = LocalDateTime.now(container.clock).plusMinutes(1)
        val reminder = container.reminderRepository.add(
            ReminderInput(pet.id, RecordType.MEDICINE, "E2E藥", due.toLocalDate().toEpochDay(), due.hour * 60 + due.minute),
        )
        val nid = container.notifier.notificationId(reminder.id)
        try {
            // The syncer (started with the app) arms the alarm; wait for it to fire at the next minute.
            val deadline = System.currentTimeMillis() + 100_000
            while (System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.id == nid }) Thread.sleep(1_000)
            val posted = manager.activeNotifications.firstOrNull { it.id == nid }
            assertNotNull("notification did not appear", posted)
            assertEquals("E2E貓：E2E藥", posted!!.notification.extras.getString("android.title"))
            assertNotNull(container.reminderRepository.get(reminder.id)!!.notifiedFor)

            // Tap the 完成 action exactly as the system would.
            posted.notification.actions.single { it.title == "完成" }.actionIntent.send()
            val until = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < until && container.reminderRepository.get(reminder.id)!!.completedAt == null) Thread.sleep(300)
            assertNotNull(container.reminderRepository.get(reminder.id)!!.completedAt)
            val recorded = container.recordRepository.observeDay(pet.id, java.time.LocalDate.now(container.clock).toEpochDay()).first()
            assertEquals("E2E藥", recorded.single().title)
            assertTrue(manager.activeNotifications.none { it.id == nid })
        } finally {
            container.reminderRepository.delete(reminder.id)
            container.petRepository.moveToTrash(pet.id)
            manager.cancel(nid)
        }
    }

    @Test fun deletedReminderNeverNotifies() = runBlocking {
        allowExactAlarms()
        val pet = container.petRepository.add(PetInput(name = "E2E狗", species = Species.DOG, birthEpochDay = 1))
        val due = LocalDateTime.now(container.clock).plusMinutes(1)
        val reminder = container.reminderRepository.add(
            ReminderInput(pet.id, RecordType.VET, "不該出現", due.toLocalDate().toEpochDay(), due.hour * 60 + due.minute),
        )
        val nid = container.notifier.notificationId(reminder.id)
        try {
            Thread.sleep(2_000) // let the syncer arm it
            container.reminderRepository.delete(reminder.id) // syncer cancels the alarm
            Thread.sleep(75_000)
            assertNull(manager.activeNotifications.firstOrNull { it.id == nid })
        } finally {
            container.petRepository.moveToTrash(pet.id)
        }
    }
}
