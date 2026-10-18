package com.zylinn.petdiary.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.SearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RecordSearchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.parse("2026-10-19").toEpochDay()
    private lateinit var db: PetDiaryDatabase
    private lateinit var repo: RecordRepository
    private lateinit var pets: PetRepository
    private lateinit var cat: String
    private lateinit var dog: String

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        repo = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), FileStore(File(context.cacheDir, "search")), clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun add(pet: String, type: RecordType, day: Long, input: RecordInput.() -> RecordInput = { this }) =
        runBlocking { repo.addForPets(listOf(pet), RecordInput(type, day, 600).input()).single() }

    private fun search(q: SearchQuery): List<Record> = runBlocking { repo.search(q).first() }
    private fun ids(q: SearchQuery) = search(q).map { it.id }

    // P3-02
    @Test fun typeKeywordFindsAllRecordsOfThatType() {
        val v1 = add(cat, RecordType.VACCINE, today - 100) { copy(title = "三合一") }
        val v2 = add(dog, RecordType.VACCINE, today - 5) { copy(title = "狂犬病") }
        add(cat, RecordType.DEWORMING, today - 3) { copy(title = "蚤不到") }
        assertEquals(listOf(v2, v1), ids(SearchQuery(text = "疫苗")))
    }

    // P3-03
    @Test fun contentFieldsAreSearched() {
        val byTitle = add(cat, RecordType.MEDICINE, today - 1) { copy(title = "心臟藥") }
        val byHospital = add(cat, RecordType.VET, today - 2) { copy(title = "嘔吐", hospital = "愛心動物醫院") }
        val byNote = add(dog, RecordType.WEIGHT, today - 3) { copy(weightKg = 20.0, note = "愛吃零食變胖") }
        val byDetail = add(dog, RecordType.VET, today - 4) { copy(title = "健檢", detail = "牙結石") }
        assertEquals(listOf(byTitle), ids(SearchQuery(text = "心臟")))
        assertEquals(listOf(byHospital), ids(SearchQuery(text = "愛心")))
        assertEquals(listOf(byNote), ids(SearchQuery(text = "零食")))
        assertEquals(listOf(byDetail), ids(SearchQuery(text = "結石")))
    }

    @Test fun chineseSubstringMatches() {
        val r = add(cat, RecordType.DEWORMING, today) { copy(title = "蚤不到滴劑") }
        assertEquals(listOf(r), ids(SearchQuery(text = "不到")))
    }

    @Test fun supplementNameFindsFeeding() = runBlocking {
        val fishOil = repo.addSupplement("魚油")
        val r = add(cat, RecordType.FEEDING, today) { copy(portions = 1.0, supplementIds = listOf(fishOil.id)) }
        add(cat, RecordType.FEEDING, today - 1) { copy(portions = 1.0) }
        assertEquals(listOf(r), ids(SearchQuery(text = "魚油")))
    }

    // P3-06
    @Test fun filtersByPetTypeAndDate() {
        val catOld = add(cat, RecordType.VACCINE, today - 400)
        val catNew = add(cat, RecordType.DEWORMING, today - 10)
        val dogNew = add(dog, RecordType.DEWORMING, today - 2)
        assertEquals(listOf(catNew, catOld), ids(SearchQuery(types = setOf(RecordType.VACCINE, RecordType.DEWORMING), petIds = setOf(cat))))
        assertEquals(listOf(dogNew, catNew), ids(SearchQuery(types = setOf(RecordType.DEWORMING))))
        assertEquals(listOf(catNew), ids(SearchQuery(types = setOf(RecordType.VACCINE, RecordType.DEWORMING), petIds = setOf(cat), fromDay = today - 365)))
        assertEquals(listOf(catOld), ids(SearchQuery(types = setOf(RecordType.VACCINE, RecordType.DEWORMING), petIds = setOf(cat), toDay = today - 365)))
    }

    // P3-04
    @Test fun newestFirstThenLatestTime() {
        val early = runBlocking { repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today, 480, portions = 1.0)).single() }
        val late = runBlocking { repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today, 1080, portions = 1.0)).single() }
        val yesterday = runBlocking { repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today - 1, 1300, portions = 1.0)).single() }
        assertEquals(listOf(late, early, yesterday), ids(SearchQuery(text = "餵食")))
    }

    @Test fun deletedRecordsAndTrashedPetsExcluded() = runBlocking {
        val gone = add(cat, RecordType.VACCINE, today)
        val dogs = add(dog, RecordType.VACCINE, today)
        val kept = add(cat, RecordType.VACCINE, today - 1)
        repo.moveToTrash(gone)
        assertEquals(listOf(dogs, kept), ids(SearchQuery(text = "疫苗")))
        pets.moveToTrash(dog)
        assertEquals(listOf(kept), ids(SearchQuery(text = "疫苗")))
    }

    // P3-07 (data side)
    @Test fun blankQueryReturnsNothingAndNoMatchIsEmpty() {
        add(cat, RecordType.VACCINE, today)
        assertTrue(search(SearchQuery()).isEmpty())
        assertTrue(search(SearchQuery(petIds = setOf(cat))).isEmpty())
        assertTrue(search(SearchQuery(text = "完全不存在的字")).isEmpty())
    }

    @Test fun likeWildcardsAreLiteral() {
        add(cat, RecordType.VET, today) { copy(title = "一般") }
        val pct = add(cat, RecordType.VET, today) { copy(title = "折扣 50%") }
        assertEquals(listOf(pct), ids(SearchQuery(text = "%")))
        assertTrue(ids(SearchQuery(text = "_")).isEmpty())
    }

    // P3-08
    @Test fun tenThousandRecordsSearchUnderHalfSecond() = runBlocking {
        val titles = listOf("蚤不到", "心臟藥", "三合一", "健檢", "洗澡")
        val types = listOf(RecordType.DEWORMING, RecordType.MEDICINE, RecordType.VACCINE, RecordType.VET, RecordType.GROOMING)
        val now = clock.millis()
        val rows = (0 until 10_000).map { i ->
            Record(
                id = "r$i", petId = if (i % 2 == 0) cat else dog, type = types[i % 5], dateEpochDay = today - (i % 1500),
                minuteOfDay = i % 1440, title = titles[i % 5], hospital = "醫院${i % 37}", note = "備註第 $i 筆",
                createdAt = now, updatedAt = now,
            )
        }
        db.recordDao().upsertAll(rows)

        listOf("蚤", "醫院12", "第 9999 筆", "看醫生").forEach { text ->
            repo.search(SearchQuery(text = "warm-up")).first()
            val start = System.nanoTime()
            val result = repo.search(SearchQuery(text = text)).first()
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.i("RecordSearchTest", "query=$text results=${result.size} took=${ms}ms")
            assertTrue("'$text' took ${ms}ms", ms < 500)
            assertTrue("'$text' found nothing", result.isNotEmpty())
        }
    }
}
