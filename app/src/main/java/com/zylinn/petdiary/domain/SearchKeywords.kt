package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.RecordType

/**
 * Maps a search keyword to record types, so "疫苗" finds every vaccine record even when
 * the record's text never says "疫苗" (P3-02), and everyday words work too (D28).
 */
object SearchKeywords {

    private val words: Map<RecordType, List<String>> = mapOf(
        RecordType.FEEDING to listOf("餵食", "吃飯", "飼料", "主食", "罐頭", "喝水", "保健"),
        RecordType.WALK to listOf("散步", "遛狗", "遛", "運動", "出門"),
        RecordType.MEDICINE to listOf("藥物", "吃藥", "餵藥", "用藥", "點藥", "藥"),
        RecordType.VACCINE to listOf("疫苗", "打針", "預防針"),
        RecordType.DEWORMING to listOf("驅蟲", "除蚤", "跳蚤", "壁蝨", "內驅", "外驅", "蚤"),
        RecordType.WEIGHT to listOf("體重", "公斤", "秤重", "kg"),
        RecordType.VET to listOf("就診", "看診", "看醫生", "回診", "健檢", "醫生"),
        RecordType.GROOMING to listOf("美容", "洗澡", "剪指甲", "修毛", "剃毛", "清耳朵", "刷牙"),
        RecordType.ABNORMAL to listOf("異常", "嘔吐", "拉肚子", "腹瀉", "咳嗽", "生病", "不舒服", "吐"),
    )

    /** Types whose words contain the query ("疫" → 疫苗) or are contained in it ("貓疫苗" → 疫苗). */
    fun typesFor(query: String): Set<RecordType> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptySet()
        return words.filterValues { list -> list.any { w -> w.contains(q) || q.contains(w) } }.keys
    }
}
