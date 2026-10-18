package com.zylinn.petdiary.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.photo.AttachmentImporter
import com.zylinn.petdiary.data.photo.FileStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class AttachmentImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var src: File
    private lateinit var dir: File
    private lateinit var store: FileStore
    private lateinit var importer: AttachmentImporter

    @Before fun setUp() {
        src = File(context.cacheDir, "attach-src").apply { deleteRecursively(); mkdirs() }
        dir = File(context.cacheDir, "attach-store").apply { deleteRecursively(); mkdirs() }
        store = FileStore(dir)
        importer = AttachmentImporter(context.contentResolver, store)
    }

    @After fun tearDown() {
        src.deleteRecursively()
        dir.deleteRecursively()
    }

    private fun pdf(name: String, bytes: Long): File = File(src, name).apply {
        RandomAccessFile(this, "rw").use { it.write("%PDF-1.4\n".toByteArray()); it.setLength(bytes) }
    }

    // P2-15, P2-18
    @Test fun pdfCopiedAndSurvivesOriginalDeletion() = runBlocking {
        val original = pdf("報告.pdf", 200_000)
        val r = importer.import(Uri.fromFile(original)) as AttachmentImporter.Result.Ok
        original.delete()
        assertEquals("application/pdf", r.attachment.mimeType)
        assertEquals("報告.pdf", r.attachment.displayName)
        assertEquals(200_000L, r.attachment.sizeBytes)
        assertTrue(store.exists(r.attachment.fileName))
        assertEquals(200_000L, store.file(r.attachment.fileName).length())
    }

    // P2-17
    @Test fun pdfOverTenMegabytesRejected() = runBlocking {
        val r = importer.import(Uri.fromFile(pdf("big.pdf", AttachmentImporter.MAX_BYTES + 1)))
        assertEquals(AttachmentImporter.Result.TooLarge, r)
        assertEquals("no partial file left behind", 0, dir.listFiles()!!.size)
    }

    @Test fun pdfExactlyTenMegabytesAccepted() = runBlocking {
        val r = importer.import(Uri.fromFile(pdf("edge.pdf", AttachmentImporter.MAX_BYTES)))
        assertTrue(r is AttachmentImporter.Result.Ok)
    }

    // P2-17: photos are compressed first, so a large original still fits
    @Test fun largePhotoIsCompressedUnderLimit() = runBlocking {
        val original = File(src, "xray.png")
        Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
            .let { bmp -> original.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val r = importer.import(Uri.fromFile(original)) as AttachmentImporter.Result.Ok
        assertEquals("image/jpeg", r.attachment.mimeType)
        assertEquals("xray.jpg", r.attachment.displayName)
        assertTrue(r.attachment.sizeBytes <= AttachmentImporter.MAX_BYTES)
    }

    @Test fun otherFileTypesRejected() = runBlocking {
        val txt = File(src, "note.txt").apply { writeText("hello") }
        assertEquals(AttachmentImporter.Result.Unsupported, importer.import(Uri.fromFile(txt)))
    }
}
