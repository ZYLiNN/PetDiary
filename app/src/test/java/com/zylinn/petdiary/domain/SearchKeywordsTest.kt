package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeywordsTest {

    // P3-02
    @Test fun typeNamesMatch() {
        assertEquals(setOf(RecordType.VACCINE), SearchKeywords.typesFor("疫苗"))
        assertEquals(setOf(RecordType.DEWORMING), SearchKeywords.typesFor("驅蟲"))
        assertEquals(setOf(RecordType.WEIGHT), SearchKeywords.typesFor("體重"))
    }

    @Test fun partialAndLongerQueriesMatch() {
        assertEquals(setOf(RecordType.VACCINE), SearchKeywords.typesFor("疫"))
        assertEquals(setOf(RecordType.VACCINE), SearchKeywords.typesFor("貓的疫苗"))
    }

    // D28
    @Test fun everydayWords() {
        assertEquals(setOf(RecordType.VET), SearchKeywords.typesFor("看醫生"))
        assertEquals(setOf(RecordType.GROOMING), SearchKeywords.typesFor("洗澡"))
        assertEquals(setOf(RecordType.ABNORMAL), SearchKeywords.typesFor("吐毛球"))
        assertEquals(setOf(RecordType.VACCINE), SearchKeywords.typesFor("打針"))
        assertEquals(setOf(RecordType.WEIGHT), SearchKeywords.typesFor("KG"))
        assertEquals(setOf(RecordType.WALK), SearchKeywords.typesFor("遛狗"))
        assertEquals(setOf(RecordType.WALK), SearchKeywords.typesFor("散步"))
    }

    @Test fun productNameContainingKeywordMatchesItsType() {
        assertTrue(SearchKeywords.typesFor("蚤不到").contains(RecordType.DEWORMING))
    }

    @Test fun unrelatedTextMatchesNoType() {
        assertTrue(SearchKeywords.typesFor("愛心動物").isEmpty())
        assertTrue(SearchKeywords.typesFor("   ").isEmpty())
    }
}
