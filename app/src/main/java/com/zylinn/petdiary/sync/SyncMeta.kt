package com.zylinn.petdiary.sync

import androidx.room.Entity

/**
 * What the cloud and this phone last agreed on for one document (D55: the cloud's own clock decides order).
 * [localStamp] = our row's updatedAt at that moment; a row whose updatedAt differs has unsent edits.
 * [serverAt] = the cloud's write time (microseconds) of the newest version we have seen.
 */
@Entity(tableName = "sync_meta", primaryKeys = ["tableName", "id"])
data class SyncMeta(
    val tableName: String,
    val id: String,
    val localStamp: Long,
    val serverAt: Long,
)
