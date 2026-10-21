package com.zylinn.petdiary.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.ui.records.AttachmentExport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** P11-06: "下載到手機" really lands in the gallery (photos) / Downloads (PDFs) with the same bytes. */
@RunWith(AndroidJUnit4::class)
class AttachmentExportDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun roundTrip(mime: String, ext: String, collection: android.net.Uri, expectedPlace: String) {
        val bytes = ByteArray(3000) { (it * 13).toByte() }
        val file = File(context.cacheDir, "export-test.$ext").apply { writeBytes(bytes) }
        val name = "PetDiary測試-${System.nanoTime()}"
        assertEquals(expectedPlace, AttachmentExport.save(context, file, mime, name))
        val resolver = context.contentResolver
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$name.$ext"), null)!!.use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            val uri = ContentUris.withAppendedId(collection, c.getLong(0))
            assertArrayEquals(bytes, resolver.openInputStream(uri)!!.use { it.readBytes() })
            resolver.delete(uri, null, null)
        }
    }

    @Test fun photoGoesToTheGallery() =
        roundTrip("image/jpeg", "jpg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "相簿的 PetDiary 資料夾")

    @Test fun pdfGoesToDownloads() =
        roundTrip("application/pdf", "pdf", MediaStore.Downloads.EXTERNAL_CONTENT_URI, "「下載」裡的 PetDiary 資料夾")
}
