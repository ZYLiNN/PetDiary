package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderInput
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), zone) // 10:30 Taipei
    private val today = LocalDate.parse("2026-10-19")
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var cat: String

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "reminder-test").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock, reminders = db.reminderDao())
        reminders = ReminderRepository(db.reminderDao(), records, clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun input(due: LocalDate = today, repeat: ReminderRepeat = ReminderRepeat.NONE, every: Int = 1, title: String? = null) =
        ReminderInput(cat, RecordType.DEWORMING, title, due.toEpochDay(), 9 * 60, repeat, every)

    private fun open() = runBlocking { reminders.observeOpen().first() }
    private fun recordsOn(day: LocalDate) = runBlocking { records.observeDay(cat, day.toEpochDay()).first() }

    // P4-01
    @Test fun addedReminderIsListedWithPetName() = runBlocking {
        reminders.add(input(today.plusDays(2), title = " 蚤不到 "))
        val row = open().single()
        assertEquals("雷米", row.petName)
        assertEquals("蚤不到", row.reminder.title)
        assertEquals(today.plusDays(2).toEpochDay(), row.reminder.dueEpochDay)
    }

    @Test fun listIsSortedSoonestFirst() = runBlocking {
        val later = reminders.add(input(today.plusDays(5)))
        val sooner = reminders.add(input(today.minusDays(1)))
        assertEquals(listOf(sooner.id, later.id), open().map { it.reminder.id })
    }

    // P4-03 / P4-11
    @Test fun completingOneTimeReminderCreatesRecordAndCloses() = runBlocking {
        val r = reminders.add(input(today, title = "蚤不到"))
        val c = reminders.complete(r.id)!!
        assertTrue(open().isEmpty())
        val rec = recordsOn(today).single()
        assertEquals(c.recordId, rec.id)
        assertEquals(RecordType.DEWORMING, rec.type)
        assertEquals("蚤不到", rec.title)
        assertEquals(10 * 60 + 30, rec.minuteOfDay)
        assertNull(reminders.complete(r.id)) // already done
    }

    @Test fun completingWithoutTitleUsesTypeName() = runBlocking {
        val r = reminders.add(input(today))
        reminders.complete(r.id)
        assertEquals("驅蟲", recordsOn(today).single().title)
    }

    // P4-04
    @Test fun completingRepeatingReminderSchedulesNext() = runBlocking {
        val r = reminders.add(input(today.minusDays(2), ReminderRepeat.EVERY_N_DAYS, 7))
        reminders.complete(r.id, rollFromToday = false)
        val next = open().single().reminder
        assertEquals(today.plusDays(5).toEpochDay(), next.dueEpochDay) // schedule: -2, +5, ...
        assertNull(next.completedAt)
        assertEquals(1, recordsOn(today).size)
    }

    // D37: finishing late counts the next date from today by default
    @Test fun lateCompletionRollsFromTodayByDefault() = runBlocking {
        val r = reminders.add(input(today.minusDays(2), ReminderRepeat.EVERY_N_DAYS, 7))
        reminders.complete(r.id)
        assertEquals(today.plusDays(7).toEpochDay(), open().single().reminder.dueEpochDay)
    }

    @Test fun lateChoiceOnlyWhenTheTwoDatesDiffer() = runBlocking {
        val weekly = reminders.add(input(today.minusDays(2), ReminderRepeat.EVERY_N_DAYS, 7))
        val choice = reminders.lateChoice(weekly.id)!!
        assertEquals(2, choice.lateDays)
        assertEquals(today.plusDays(7), choice.rolled)
        assertEquals(today.plusDays(5), choice.anchored)
        assertNull(reminders.lateChoice(reminders.add(input(today.minusDays(2), ReminderRepeat.DAILY)).id)) // same either way
        assertNull(reminders.lateChoice(reminders.add(input(today.minusDays(2))).id)) // does not repeat
        assertNull(reminders.lateChoice(reminders.add(input(today, ReminderRepeat.EVERY_N_DAYS, 7)).id)) // not late
    }

    // D36: a custom reminder has no record type, so finishing it records nothing
    @Test fun completingCustomReminderCreatesNoRecord() = runBlocking {
        val r = reminders.add(input(today, title = "剪指甲").let { it.copy(type = RecordType.CUSTOM) })
        assertEquals(listOf("剪指甲"), reminders.customTitles.first())
        val c = reminders.complete(r.id)!!
        assertNull(c.recordId)
        assertTrue(open().isEmpty())
        assertTrue(recordsOn(today).isEmpty())
        reminders.undo(c)
        assertEquals(r.id, open().single().reminder.id)
    }

    // P4-12
    @Test fun undoPutsEverythingBack() = runBlocking {
        val r = reminders.add(input(today, ReminderRepeat.DAILY))
        val c = reminders.complete(r.id)!!
        assertEquals(today.plusDays(1).toEpochDay(), open().single().reminder.dueEpochDay)
        reminders.undo(c)
        assertEquals(today.toEpochDay(), open().single().reminder.dueEpochDay)
        assertTrue(recordsOn(today).isEmpty())
    }

    @Test fun undoOfOneTimeReminderBringsItBack() = runBlocking {
        val r = reminders.add(input(today))
        val c = reminders.complete(r.id)!!
        assertTrue(open().isEmpty())
        reminders.undo(c)
        assertEquals(r.id, open().single().reminder.id)
    }

    @Test fun editingMovedTimeClearsNotifiedMark() = runBlocking {
        val r = reminders.add(input(today))
        reminders.markNotified(r.id, 123L)
        reminders.update(r.id, input(today).copy(title = "新名稱"))
        assertEquals(123L, reminders.get(r.id)!!.notifiedFor) // same time → keeps the mark
        reminders.update(r.id, input(today.plusDays(1)))
        assertNull(reminders.get(r.id)!!.notifiedFor)
    }

    @Test fun deletedReminderDisappears() = runBlocking {
        val r = reminders.add(input(today))
        reminders.delete(r.id)
        assertTrue(open().isEmpty())
    }

    // pets in the trash hide their reminders
    @Test fun trashedPetHidesItsReminders() = runBlocking {
        reminders.add(input(today))
        pets.moveToTrash(cat)
        assertTrue(open().isEmpty())
        pets.restore(cat)
        assertEquals(1, open().size)
    }

    // P4-09
    @Test fun recordNextDateCreatesLinkedReminder() = runBlocking {
        val next = today.plusDays(30).toEpochDay()
        val id = records.addForPets(listOf(cat), RecordInput(RecordType.VACCINE, today.toEpochDay(), 600, title = "三合一", nextEpochDay = next, remindNext = true)).single()
        val r = open().single().reminder
        assertEquals(id, r.sourceRecordId)
        assertEquals(RecordType.VACCINE, r.type)
        assertEquals("三合一", r.title)
        assertEquals(next, r.dueEpochDay)
        assertEquals(9 * 60, r.minuteOfDay)
    }

    @Test fun noReminderWithoutTheFlag() = runBlocking {
        records.addForPets(listOf(cat), RecordInput(RecordType.VACCINE, today.toEpochDay(), 600, title = "x", nextEpochDay = today.plusDays(30).toEpochDay(), remindNext = false))
        assertTrue(open().isEmpty())
    }

    @Test fun editingTheRecordMovesOrDropsItsReminder() = runBlocking {
        val next = today.plusDays(30).toEpochDay()
        val input = RecordInput(RecordType.VACCINE, today.toEpochDay(), 600, title = "三合一", nextEpochDay = next, remindNext = true)
        val id = records.addForPets(listOf(cat), input).single()
        records.update(id, input.copy(nextEpochDay = next + 10))
        assertEquals(next + 10, open().single().reminder.dueEpochDay)
        records.update(id, input.copy(remindNext = false))
        assertTrue(open().isEmpty())
    }

    @Test fun tickedOffReminderIsNotRecreatedByAnEdit() = runBlocking {
        val next = today.plusDays(30).toEpochDay()
        val input = RecordInput(RecordType.VACCINE, today.toEpochDay(), 600, title = "三合一", nextEpochDay = next, remindNext = true)
        val id = records.addForPets(listOf(cat), input).single()
        reminders.complete(open().single().reminder.id)
        records.update(id, input.copy(note = "改備註"))
        assertTrue(open().isEmpty())
    }

    @Test fun trashingTheRecordHidesItsReminderAndRestoreBringsItBack() = runBlocking {
        val input = RecordInput(RecordType.VET, today.toEpochDay(), 600, title = "回診", nextEpochDay = today.plusDays(14).toEpochDay(), remindNext = true)
        val id = records.addForPets(listOf(cat), input).single()
        records.moveToTrash(id)
        assertTrue(open().isEmpty())
        records.restore(id)
        assertEquals(1, open().size)
    }

    @Test fun purgingThePetRemovesItsReminders() = runBlocking {
        reminders.add(input(today))
        pets.moveToTrash(cat)
        val trash = TrashRepository(db.petDao(), db.recordDao(), db.attachmentDao(), FileStore(context.cacheDir), FileStore(context.cacheDir), clock, db.reminderDao())
        trash.deletePermanently(cat)
        assertEquals(0, db.reminderDao().observeOpen().first().size)
        assertTrue(db.reminderDao().getBySource("x").isEmpty())
    }

    @Test fun reminderOnlyForReminderTypes() = runBlocking {
        // weight has no "next date", but even if flagged it must not create a reminder
        records.addForPets(listOf(cat), RecordInput(RecordType.WEIGHT, today.toEpochDay(), 600, weightKg = 4.0, nextEpochDay = today.plusDays(3).toEpochDay(), remindNext = true))
        assertTrue(open().isEmpty())
    }
}
