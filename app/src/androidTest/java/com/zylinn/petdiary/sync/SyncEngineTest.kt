package com.zylinn.petdiary.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A pretend Firestore shared by several "devices". */
class FakeCloud {
    val docs = SyncTable.entries.associateWith { linkedMapOf<String, CloudDoc>() }
    private val listeners = mutableListOf<Pair<SyncTable, (List<CloudDoc>) -> Unit>>()

    fun remoteFor() = object : RemoteStore {
        override suspend fun push(table: SyncTable, docs: List<CloudDoc>) {
            synchronized(this@FakeCloud) {
                docs.forEach { this@FakeCloud.docs.getValue(table)[it["id"] as String] = it }
                listeners.filter { it.first == table }.forEach { (_, cb) -> cb(docs) }
            }
        }
        override fun listen(table: SyncTable, onDocs: (List<CloudDoc>) -> Unit, onError: (Throwable) -> Unit): AutoCloseable {
            val entry = table to onDocs
            synchronized(this@FakeCloud) {
                listeners += entry
                if (docs.getValue(table).isNotEmpty()) onDocs(docs.getValue(table).values.toList())
            }
            return AutoCloseable { synchronized(this@FakeCloud) { listeners -= entry } }
        }
        override suspend fun purgeDeletedBefore(table: SyncTable, cutoff: Long) {
            synchronized(this@FakeCloud) {
                docs.getValue(table).values.removeAll { ((it["deletedAt"] as? Number)?.toLong() ?: Long.MAX_VALUE) <= cutoff }
            }
        }
    }
}

class MemoryWatermarks : Watermarks {
    private val map = mutableMapOf<SyncTable, Long>()
    override fun get(table: SyncTable) = map[table] ?: 0L
    override fun set(table: SyncTable, value: Long) { map[table] = value }
}

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneId.of("Asia/Taipei"))
    private val now = clock.millis()
    private val day = 86_400_000L
    private lateinit var dbA: PetDiaryDatabase
    private lateinit var dbB: PetDiaryDatabase
    private val cloud = FakeCloud()

    @Before fun setUp() {
        dbA = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        dbB = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
    }

    @After fun tearDown() { dbA.close(); dbB.close() }

    private fun engine(db: PetDiaryDatabase, marks: Watermarks = MemoryWatermarks()) =
        SyncEngine(db.syncDao(), cloud.remoteFor(), marks, clock, pushDelayMs = 50)

    private fun pet(id: String = "p1", updatedAt: Long = now, name: String = "雷米", deletedAt: Long? = null) =
        Pet(id, name, Species.CAT, birthEpochDay = 19000, createdAt = 1, updatedAt = updatedAt, deletedAt = deletedAt)

    private fun record(id: String = "r1", updatedAt: Long = now) =
        Record(id, "p1", RecordType.FEEDING, 20000, 480, portions = 1.0, createdAt = 1, updatedAt = updatedAt)

    private fun waitUntil(what: String, ms: Long = 8_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(50) }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test fun pushUploadsChangesOnceAndAdvancesTheWatermark() = runBlocking {
        val marks = MemoryWatermarks()
        val a = engine(dbA, marks)
        dbA.syncDao().upsertPet(pet()); dbA.syncDao().upsertRecord(record())
        a.pushPending()
        assertEquals(setOf("p1"), cloud.docs.getValue(SyncTable.PETS).keys)
        assertEquals(setOf("r1"), cloud.docs.getValue(SyncTable.RECORDS).keys)
        assertEquals(now, marks.get(SyncTable.PETS))

        cloud.docs.getValue(SyncTable.PETS).clear()
        a.pushPending() // nothing new → nothing uploaded
        assertTrue(cloud.docs.getValue(SyncTable.PETS).isEmpty())

        dbA.syncDao().upsertPet(pet(updatedAt = now + 1, name = "雷米2"))
        a.pushPending()
        assertEquals("雷米2", cloud.docs.getValue(SyncTable.PETS).getValue("p1")["name"])
    }

    @Test fun anotherDeviceReceivesAndKeepsItsOwnNotifiedMark() = runBlocking {
        val syncB = dbB.syncDao()
        val reminder = Reminder("m1", "p1", RecordType.VET, "看診", 20001, 540, createdAt = 1, updatedAt = now, notifiedFor = 999)
        dbA.syncDao().upsertReminder(reminder)
        engine(dbA).pushPending()

        // B already has an older copy that it announced.
        syncB.upsertReminder(reminder.copy(updatedAt = now - 10, title = "舊", notifiedFor = 555))
        engine(dbB).apply(SyncTable.REMINDERS, cloud.docs.getValue(SyncTable.REMINDERS).values.toList())

        val got = dbB.reminderDao().getById("m1")!!
        assertEquals("看診", got.title)
        assertEquals(555L, got.notifiedFor) // not overwritten by A's mark (and A's mark was never uploaded)
    }

    @Test fun newestUpdatedAtWinsAndTiesAreIgnored() = runBlocking {
        val b = engine(dbB)
        dbB.syncDao().upsertPet(pet(updatedAt = now, name = "本機"))
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now - 5, name = "較舊的雲端"))))
        assertEquals("本機", dbB.petDao().getById("p1")!!.name)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now, name = "同時間"))))
        assertEquals("本機", dbB.petDao().getById("p1")!!.name)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 5, name = "較新的雲端"))))
        assertEquals("較新的雲端", dbB.petDao().getById("p1")!!.name)
    }

    @Test fun trashAndRestorePropagate() = runBlocking {
        val b = engine(dbB)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet())))
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 1, deletedAt = now + 1))))
        assertEquals(now + 1, dbB.petDao().getById("p1")!!.deletedAt)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 2))))
        assertNull(dbB.petDao().getById("p1")!!.deletedAt)
    }

    @Test fun aLongTrashedCloudCopyIsNotResurrected() = runBlocking {
        val old = now - 8 * day
        engine(dbB).apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = old, deletedAt = old))))
        assertNull(dbB.petDao().getById("p1"))
        val recent = now - 2 * day
        engine(dbB).apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(id = "p2", updatedAt = recent, deletedAt = recent))))
        assertNotNull(dbB.petDao().getById("p2"))
    }

    @Test fun malformedCloudDocumentsAreIgnored() = runBlocking {
        engine(dbB).apply(SyncTable.PETS, listOf(mapOf("id" to "bad", "updatedAt" to now), mapOf("name" to "no id")))
        assertNull(dbB.petDao().getById("bad"))
    }

    @Test fun supplementsSync() = runBlocking {
        dbA.syncDao().upsertSupplement(Supplement("s1", "魚油", 1, now))
        engine(dbA).pushPending()
        engine(dbB).apply(SyncTable.SUPPLEMENTS, cloud.docs.getValue(SyncTable.SUPPLEMENTS).values.toList())
        assertEquals("魚油", dbB.supplementDao().getById("s1")!!.name)
    }

    @Test fun expiredTrashIsPurgedFromTheCloudAtStart() = runBlocking {
        val old = now - 8 * day
        cloud.docs.getValue(SyncTable.RECORDS)["old"] = CloudMapper.record(record("old", updatedAt = old).copy(deletedAt = old))
        cloud.docs.getValue(SyncTable.RECORDS)["fresh"] = CloudMapper.record(record("fresh"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = engine(dbA).start(scope)
        waitUntil("expired doc purged") { "old" !in cloud.docs.getValue(SyncTable.RECORDS) }
        assertTrue("fresh" in cloud.docs.getValue(SyncTable.RECORDS))
        job.cancelAndJoin()
    }

    @Test fun twoRunningDevicesConverge() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val jobA = engine(dbA).start(scope)
        val jobB = engine(dbB).start(scope)
        runBlocking {
            dbA.syncDao().upsertPet(pet())
            waitUntil("A→B pet") { dbB.petDao().getById("p1") != null }

            dbB.syncDao().upsertRecord(record())
            waitUntil("B→A record") { dbA.recordDao().getById("r1") != null }

            // B renames the pet later; A sees it.
            dbB.syncDao().upsertPet(pet(updatedAt = now + 100, name = "改名"))
            waitUntil("rename reaches A") { dbA.petDao().getById("p1")!!.name == "改名" }

            // A moves the pet to the trash; B sees that too.
            dbA.petDao().softDelete("p1", now + 200)
            waitUntil("trash reaches B") { dbB.petDao().getById("p1")!!.deletedAt != null }
            jobA.cancelAndJoin(); jobB.cancelAndJoin()
        }
    }
}
