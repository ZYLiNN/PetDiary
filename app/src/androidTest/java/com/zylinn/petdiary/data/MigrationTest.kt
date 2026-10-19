package com.zylinn.petdiary.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Data saved by older versions must survive each schema upgrade. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PetDiaryDatabase::class.java,
    )

    @Test fun v1PetsSurviveUpgradeToV2() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """INSERT INTO pets (id, name, species, customSpecies, birthEpochDay, birthEstimated, sex, neutered,
                   breed, chipNumber, note, photoFile, createdAt, updatedAt, deletedAt)
                   VALUES ('p1', '雷米', 'CAT', NULL, 19000, 1, 'FEMALE', 1, '米克斯', NULL, NULL, 'a.jpg', 1, 2, NULL)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val pet = db.petDao().getById("p1")!!
                assertEquals("雷米", pet.name)
                assertEquals("a.jpg", pet.photoFile)
                assertTrue(pet.birthEstimated)
                assertTrue(db.recordDao().getAllForPet("p1").isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test fun v2RecordsSurviveUpgradeToV3AndGainDuration() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """INSERT INTO pets (id, name, species, customSpecies, birthEpochDay, birthEstimated, sex, neutered,
                   breed, chipNumber, note, photoFile, createdAt, updatedAt, deletedAt)
                   VALUES ('p1', '雷利', 'DOG', NULL, 19000, 0, 'MALE', NULL, NULL, NULL, NULL, NULL, 1, 2, NULL)""",
            )
            execSQL(
                """INSERT INTO records (id, petId, type, dateEpochDay, minuteOfDay, drankWater, remindNext, createdAt, updatedAt)
                   VALUES ('r1', 'p1', 'FEEDING', 20000, 480, 1, 0, 1, 2)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val r = db.recordDao().getAllForPet("p1").single()
                assertEquals(true, r.drankWater)
                assertEquals(null, r.durationMin)
            }
        } finally {
            db.close()
        }
    }

    @Test fun v4RecordsSurviveUpgradeToV5AndRemindersWork() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """INSERT INTO pets (id, name, species, customSpecies, birthEpochDay, birthEstimated, sex, neutered,
                   breed, chipNumber, note, photoFile, createdAt, updatedAt, deletedAt)
                   VALUES ('p1', '雷利', 'DOG', NULL, 19000, 0, 'MALE', NULL, NULL, NULL, NULL, NULL, 1, 2, NULL)""",
            )
            execSQL(
                """INSERT INTO records (id, petId, type, dateEpochDay, minuteOfDay, durationMin, peed, pooped, remindNext, createdAt, updatedAt)
                   VALUES ('r1', 'p1', 'WALK', 20000, 480, 30, 1, 0, 0, 1, 2)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val r = db.recordDao().getAllForPet("p1").single()
                assertEquals(30, r.durationMin)
                assertEquals(true, r.peed)
                db.reminderDao().upsert(
                    com.zylinn.petdiary.data.reminder.Reminder(
                        "m1", "p1", com.zylinn.petdiary.data.record.RecordType.VET, null, 20010, 540, createdAt = 1, updatedAt = 1,
                    ),
                )
                assertEquals("m1", db.reminderDao().observeOpen().first().single().reminder.id)
            }
        } finally {
            db.close()
        }
    }

    @Test fun v5SupplementsGetUpdatedAtOnUpgradeToV6() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO supplements (id, name, createdAt, deletedAt) VALUES ('s1', '魚油', 5, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val s = db.supplementDao().getById("s1")!!
                assertEquals("魚油", s.name)
                assertEquals(0L, s.updatedAt)
            }
        } finally {
            db.close()
        }
    }

    @Test fun v6UpgradesToV7WithAnEmptySyncMarkTable() {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO supplements (id, name, createdAt, updatedAt, deletedAt) VALUES ('s1', '魚油', 5, 6, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 7, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                assertEquals("魚油", db.supplementDao().getById("s1")!!.name)
                assertEquals(null, db.syncDao().meta("supplements", "s1")) // never synced → counts as unsent, uploaded on first sync
            }
        } finally {
            db.close()
        }
    }

    @Test fun v8PetsGetAnEmptyColourOnUpgradeToV9() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                """INSERT INTO pets (id, name, species, customSpecies, birthEpochDay, birthEstimated, sex, neutered,
                   breed, chipNumber, note, photoFile, createdAt, updatedAt, deletedAt)
                   VALUES ('p1', '雷米', 'CAT', NULL, 19000, 0, 'UNKNOWN', NULL, NULL, NULL, NULL, NULL, 1, 2, NULL)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 9, true)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val p = db.petDao().getById("p1")!!
                assertEquals("雷米", p.name)
                assertEquals(null, p.color)
            }
        } finally {
            db.close()
        }
    }
}
