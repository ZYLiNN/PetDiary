package com.zylinn.petdiary.sync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
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

/** A pretend Firestore shared by several "devices"; stamps every write with its own increasing server clock. */
class FakeCloud {
    val docs = SyncTable.entries.associateWith { linkedMapOf<String, CloudDoc>() }
    private val listeners = mutableListOf<Pair<SyncTable, (List<CloudDoc>) -> Unit>>()
    private var serverClock = 1_000L
    var pushes = 0
        private set

    fun remoteFor() = object : RemoteStore {
        override suspend fun push(table: SyncTable, docs: List<CloudDoc>) {
            synchronized(this@FakeCloud) {
                pushes += docs.size
                val stamped = docs.map { it + (SERVER_AT to ++serverClock) }
                stamped.forEach { this@FakeCloud.docs.getValue(table)[it["id"] as String] = it }
                listeners.filter { it.first == table }.forEach { (_, cb) -> cb(stamped) }
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

    /** A change made by someone else straight in the cloud. */
    fun external(table: SyncTable, doc: CloudDoc) {
        synchronized(this) {
            val stamped = doc + (SERVER_AT to ++serverClock)
            docs.getValue(table)[doc["id"] as String] = stamped
            listeners.filter { it.first == table }.forEach { (_, cb) -> cb(listOf(stamped)) }
        }
    }
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

    private val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

    private fun engine(db: PetDiaryDatabase) = SyncEngine(
        db.syncDao(), cloud.remoteFor(), clock, pushDelayMs = 50, onError = { errors += it },
        inTransaction = { block -> db.withTransaction { block() } },
    )

    /** Pretend this row was synced at [serverAt] and has no unsent edits. */
    private suspend fun markClean(db: PetDiaryDatabase, table: SyncTable, id: String, stamp: Long, serverAt: Long = 1) =
        db.syncDao().upsertMeta(SyncMeta(table.path, id, stamp, serverAt))

    private fun pet(id: String = "p1", updatedAt: Long = now, name: String = "雷米", deletedAt: Long? = null) =
        Pet(id, name, Species.CAT, birthEpochDay = 19000, createdAt = 1, updatedAt = updatedAt, deletedAt = deletedAt)

    private fun record(id: String = "r1", updatedAt: Long = now) =
        Record(id, "p1", RecordType.FEEDING, 20000, 480, portions = 1.0, createdAt = 1, updatedAt = updatedAt)

    private fun waitUntil(what: String, ms: Long = 8_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(50) }
        throw AssertionError("timed out waiting for: $what; engine errors=$errors")
    }

    @Test fun pushUploadsOnlyRowsWithUnsentEdits() = runBlocking {
        val a = engine(dbA)
        dbA.syncDao().upsertPet(pet()); dbA.syncDao().upsertRecord(record())
        a.pushPending()
        assertEquals(setOf("p1"), cloud.docs.getValue(SyncTable.PETS).keys)
        assertEquals(setOf("r1"), cloud.docs.getValue(SyncTable.RECORDS).keys)
        assertNotNull(cloud.docs.getValue(SyncTable.PETS).getValue("p1")[SERVER_AT])

        val before = cloud.pushes
        a.pushPending() // nothing edited since → nothing uploaded
        assertEquals(before, cloud.pushes)

        dbA.syncDao().upsertPet(pet(updatedAt = now + 1, name = "雷米2"))
        a.pushPending()
        assertEquals(before + 1, cloud.pushes)
        assertEquals("雷米2", cloud.docs.getValue(SyncTable.PETS).getValue("p1")["name"])
    }

    @Test fun ourOwnUploadEchoingBackChangesNothingAndStaysClean() = runBlocking {
        val a = engine(dbA)
        dbA.syncDao().upsertPet(pet())
        a.pushPending()
        a.apply(SyncTable.PETS, cloud.docs.getValue(SyncTable.PETS).values.toList())
        val before = cloud.pushes
        a.pushPending()
        assertEquals("the echo must not look like an unsent edit", before, cloud.pushes)
        assertTrue(dbA.syncDao().meta("pets", "p1")!!.serverAt > 0)
    }

    @Test fun anotherDeviceReceivesAndKeepsItsOwnNotifiedMark() = runBlocking {
        val reminder = Reminder("m1", "p1", RecordType.VET, "看診", 20001, 540, createdAt = 1, updatedAt = now, notifiedFor = 999)
        dbA.syncDao().upsertReminder(reminder)
        engine(dbA).pushPending()
        assertNull(cloud.docs.getValue(SyncTable.REMINDERS).getValue("m1")["notifiedFor"])

        // B holds an older, fully synced copy that it already announced.
        dbB.syncDao().upsertReminder(reminder.copy(updatedAt = now - 10, title = "舊", notifiedFor = 555))
        markClean(dbB, SyncTable.REMINDERS, "m1", stamp = now - 10)
        engine(dbB).apply(SyncTable.REMINDERS, cloud.docs.getValue(SyncTable.REMINDERS).values.toList())

        val got = dbB.reminderDao().getById("m1")!!
        assertEquals("看診", got.title)
        assertEquals(555L, got.notifiedFor)
    }

    @Test fun aSyncedRowFollowsTheCloudEvenIfPhoneClocksDisagree() = runBlocking {
        // B's clock is far ahead, so its row says updatedAt = far future; the cloud copy carries an "older" phone stamp.
        val b = engine(dbB)
        dbB.syncDao().upsertPet(pet(updatedAt = now + 10 * day, name = "舊版本"))
        markClean(dbB, SyncTable.PETS, "p1", stamp = now + 10 * day, serverAt = 5)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now, name = "雲端較新")) + (SERVER_AT to 6L)))
        assertEquals("雲端較新", dbB.petDao().getById("p1")!!.name)
    }

    @Test fun anOldServerVersionIsIgnored() = runBlocking {
        val b = engine(dbB)
        dbB.syncDao().upsertPet(pet(name = "現在"))
        markClean(dbB, SyncTable.PETS, "p1", stamp = now, serverAt = 10)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 5, name = "舊的雲端版本")) + (SERVER_AT to 10L)))
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 5, name = "更舊")) + (SERVER_AT to 3L)))
        assertEquals("現在", dbB.petDao().getById("p1")!!.name)
    }

    @Test fun unsentLocalEditsAreNotOverwrittenAndWinByUploadingLast() = runBlocking {
        val b = engine(dbB)
        dbB.syncDao().upsertPet(pet(updatedAt = now, name = "同步過"))
        markClean(dbB, SyncTable.PETS, "p1", stamp = now, serverAt = 5)
        dbB.syncDao().upsertPet(pet(updatedAt = now + 1, name = "我剛改的")) // unsent edit
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 99, name = "別人改的")) + (SERVER_AT to 6L)))
        assertEquals("我剛改的", dbB.petDao().getById("p1")!!.name)
        b.pushPending()
        assertEquals("我剛改的", cloud.docs.getValue(SyncTable.PETS).getValue("p1")["name"])
    }

    @Test fun docsNotYetConfirmedByTheCloudAreIgnored() = runBlocking {
        engine(dbB).apply(SyncTable.PETS, listOf(CloudMapper.pet(pet()))) // no serverUpdatedAt
        assertNull(dbB.petDao().getById("p1"))
    }

    @Test fun trashAndRestorePropagate() = runBlocking {
        val b = engine(dbB)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet()) + (SERVER_AT to 1L)))
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 1, deletedAt = now + 1)) + (SERVER_AT to 2L)))
        assertEquals(now + 1, dbB.petDao().getById("p1")!!.deletedAt)
        b.apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = now + 2)) + (SERVER_AT to 3L)))
        assertNull(dbB.petDao().getById("p1")!!.deletedAt)
    }

    @Test fun aLongTrashedCloudCopyIsNotResurrected() = runBlocking {
        val old = now - 8 * day
        engine(dbB).apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(updatedAt = old, deletedAt = old)) + (SERVER_AT to 1L)))
        assertNull(dbB.petDao().getById("p1"))
        val recent = now - 2 * day
        engine(dbB).apply(SyncTable.PETS, listOf(CloudMapper.pet(pet(id = "p2", updatedAt = recent, deletedAt = recent)) + (SERVER_AT to 2L)))
        assertNotNull(dbB.petDao().getById("p2"))
    }

    @Test fun malformedCloudDocumentsAreIgnored() = runBlocking {
        engine(dbB).apply(SyncTable.PETS, listOf(mapOf("id" to "bad", "updatedAt" to now, SERVER_AT to 1L), mapOf("name" to "no id")))
        assertNull(dbB.petDao().getById("bad"))
    }

    @Test fun attachmentListsTravelWithTheRecord() = runBlocking {
        dbA.syncDao().upsertRecord(record())
        dbA.syncDao().insertAttachments(listOf(Attachment("a1", "r1", "x.pdf", "application/pdf", "報告.pdf", 10, 1)))
        engine(dbA).pushPending()
        val doc = cloud.docs.getValue(SyncTable.RECORDS).getValue("r1")
        assertEquals(1, (doc["attachments"] as List<*>).size)

        engine(dbB).apply(SyncTable.RECORDS, listOf(doc))
        assertEquals(listOf("x.pdf"), dbB.syncDao().attachmentsFor(listOf("r1")).map { it.fileName })

        // removed on A → the next version of the record has an empty list → B drops it too
        dbA.attachmentDao().delete("a1")
        dbA.syncDao().upsertRecord(record(updatedAt = now + 1))
        engine(dbA).pushPending()
        engine(dbB).apply(SyncTable.RECORDS, listOf(cloud.docs.getValue(SyncTable.RECORDS).getValue("r1")))
        assertTrue(dbB.syncDao().attachmentsFor(listOf("r1")).isEmpty())
    }

    @Test fun supplementsSync() = runBlocking {
        dbA.syncDao().upsertSupplement(Supplement("s1", "魚油", 1, now))
        engine(dbA).pushPending()
        engine(dbB).apply(SyncTable.SUPPLEMENTS, cloud.docs.getValue(SyncTable.SUPPLEMENTS).values.toList())
        assertEquals("魚油", dbB.supplementDao().getById("s1")!!.name)
    }

    // P12: toys and the new record fields sync too
    @Test fun toysAndPlayRecordsSync() = runBlocking {
        dbA.syncDao().upsertToy(com.zylinn.petdiary.data.record.Toy("t1", "逗貓棒", 1, now))
        dbA.syncDao().upsertRecord(record().copy(type = com.zylinn.petdiary.data.record.RecordType.PLAY, durationMin = 15, toyIds = "t1"))
        engine(dbA).pushPending()
        engine(dbB).apply(SyncTable.TOYS, cloud.docs.getValue(SyncTable.TOYS).values.toList())
        engine(dbB).apply(SyncTable.RECORDS, cloud.docs.getValue(SyncTable.RECORDS).values.toList())
        assertEquals(listOf("逗貓棒"), dbB.toyDao().observeAll().first().map { it.name })
        assertEquals("t1", dbB.recordDao().getById("r1")!!.toyIds)
    }

    /** What an older app does with a record type it doesn't know: skip it (the new phone keeps it). */
    @Test fun unknownRecordTypeFromANewerPhoneIsSkipped() = runBlocking {
        val doc = CloudMapper.record(record()) + mapOf("type" to "SWIM", SERVER_AT to 5L)
        engine(dbB).apply(SyncTable.RECORDS, listOf(doc))
        assertNull(dbB.recordDao().getById("r1"))
    }

    @Test fun expiredTrashIsPurgedFromTheCloudAtStart() = runBlocking {
        val old = now - 8 * day
        cloud.docs.getValue(SyncTable.RECORDS)["old"] = CloudMapper.record(record("old", updatedAt = old).copy(deletedAt = old)) + (SERVER_AT to 1L)
        cloud.docs.getValue(SyncTable.RECORDS)["fresh"] = CloudMapper.record(record("fresh")) + (SERVER_AT to 2L)
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

            // B renames the pet later; A sees it — even though B's phone clock is behind A's.
            dbB.syncDao().upsertPet(pet(updatedAt = now - 5_000, name = "改名"))
            waitUntil("rename reaches A") { dbA.petDao().getById("p1")!!.name == "改名" }

            // A moves the pet to the trash; B sees that too.
            dbA.petDao().softDelete("p1", now + 200)
            try {
                waitUntil("trash reaches B") { dbB.petDao().getById("p1")!!.deletedAt != null }
            } catch (e: AssertionError) {
                throw AssertionError(
                    "A=${dbA.petDao().getById("p1")} metaA=${dbA.syncDao().meta("pets", "p1")} " +
                        "B=${dbB.petDao().getById("p1")} metaB=${dbB.syncDao().meta("pets", "p1")} cloud=${cloud.docs.getValue(SyncTable.PETS)["p1"]}", e,
                )
            }
            jobA.cancelAndJoin(); jobB.cancelAndJoin()
        }
    }
}
