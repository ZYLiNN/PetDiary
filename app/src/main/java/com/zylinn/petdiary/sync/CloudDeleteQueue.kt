package com.zylinn.petdiary.sync

import android.content.SharedPreferences

/** [DeleteQueue] kept in SharedPreferences: filled by the file stores, emptied by [FileSync] (D63). */
class CloudDeleteQueue(private val prefs: SharedPreferences) : DeleteQueue {

    @Synchronized fun add(name: String) {
        val now = pending()
        if (now.size >= MAX) return // only while sync stays off for a very long time; the cloud copy then just remains
        prefs.edit().putStringSet(KEY, now + name).apply()
    }

    @Synchronized override fun pending(): Set<String> = prefs.getStringSet(KEY, emptySet())!!.toSet()

    @Synchronized override fun done(name: String) {
        prefs.edit().putStringSet(KEY, pending() - name).apply()
    }

    private companion object {
        const val KEY = "pending_cloud_deletes"
        const val MAX = 5_000
    }
}
