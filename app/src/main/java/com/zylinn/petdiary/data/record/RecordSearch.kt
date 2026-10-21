package com.zylinn.petdiary.data.record

import androidx.sqlite.db.SimpleSQLiteQuery
import com.zylinn.petdiary.domain.SearchKeywords

/** What the user is searching for. Empty sets mean "no filter". */
data class SearchQuery(
    val text: String = "",
    val petIds: Set<String> = emptySet(),
    val types: Set<RecordType> = emptySet(),
    val fromDay: Long? = null,
    val toDay: Long? = null,
) {
    /** Nothing typed and no type chosen: show the hint instead of every record. */
    val isBlank get() = text.isBlank() && types.isEmpty()
}

/**
 * Builds the search SQL. Uses LIKE substring matching rather than FTS because SQLite's FTS
 * tokenizers don't split Chinese words (D27). All user text goes in as bound arguments.
 */
object RecordSearch {

    const val LIMIT = 500

    fun likePattern(text: String): String =
        "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    fun build(q: SearchQuery, matchingSupplementIds: List<String>, matchingToyIds: List<String> = emptyList()): SimpleSQLiteQuery {
        val where = mutableListOf("r.deletedAt IS NULL", "p.deletedAt IS NULL")
        val args = mutableListOf<Any>()

        if (q.petIds.isNotEmpty()) {
            where += "r.petId IN (${q.petIds.joinToString(",") { "?" }})"
            args.addAll(q.petIds)
        }
        if (q.types.isNotEmpty()) {
            where += "r.type IN (${q.types.joinToString(",") { "?" }})"
            args.addAll(q.types.map { it.name })
        }
        q.fromDay?.let { where += "r.dateEpochDay >= ?"; args += it }
        q.toDay?.let { where += "r.dateEpochDay <= ?"; args += it }

        val text = q.text.trim()
        if (text.isNotEmpty()) {
            val like = likePattern(text)
            val any = mutableListOf<String>()
            listOf("title", "detail", "hospital", "note").forEach { col ->
                any += "r.$col LIKE ? ESCAPE '\\'"
                args += like
            }
            val keywordTypes = SearchKeywords.typesFor(text)
            if (keywordTypes.isNotEmpty()) {
                any += "r.type IN (${keywordTypes.joinToString(",") { "?" }})"
                args.addAll(keywordTypes.map { it.name })
            }
            matchingSupplementIds.forEach { id ->
                any += "(',' || r.supplementIds || ',') LIKE ?"
                args += "%,$id,%"
            }
            matchingToyIds.forEach { id ->
                any += "(',' || r.toyIds || ',') LIKE ?"
                args += "%,$id,%"
            }
            where += "(" + any.joinToString(" OR ") + ")"
        }

        val sql = """
            SELECT r.* FROM records r JOIN pets p ON p.id = r.petId
            WHERE ${where.joinToString(" AND ")}
            ORDER BY r.dateEpochDay DESC, r.minuteOfDay DESC, r.createdAt DESC
            LIMIT $LIMIT
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
