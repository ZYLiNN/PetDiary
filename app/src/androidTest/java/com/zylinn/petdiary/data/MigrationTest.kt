package com.zylinn.petdiary.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pets saved with v0.1.0 (DB v1) must survive the upgrade to v2. */
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
}
