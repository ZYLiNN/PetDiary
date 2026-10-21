package com.zylinn.petdiary.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.sync.CloudDeleteQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** D63: every deliberate file deletion is queued for the cloud, and survives a new queue instance (restart). */
@RunWith(AndroidJUnit4::class)
class CloudDeleteQueueTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun deletingAStoredFileQueuesItForTheCloud() {
        val prefs = context.getSharedPreferences("cloud-delete-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val queue = CloudDeleteQueue(prefs)
        val dir = File(context.cacheDir, "cdq").apply { deleteRecursively(); mkdirs() }
        val store = FileStore(dir, queue::add)
        File(dir, "a.jpg").writeText("x")
        store.delete("a.jpg")
        store.delete("never-downloaded.pdf") // not on this phone, but may still be in the cloud
        assertFalse(File(dir, "a.jpg").exists())
        assertEquals(setOf("a.jpg", "never-downloaded.pdf"), CloudDeleteQueue(prefs).pending())
        queue.done("a.jpg")
        assertEquals(setOf("never-downloaded.pdf"), CloudDeleteQueue(prefs).pending())
    }
}
