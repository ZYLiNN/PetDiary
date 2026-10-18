package com.zylinn.petdiary.data.photo

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.zylinn.petdiary.data.record.NewAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Copies a picked photo or PDF into the attachment store (P2-15 / P2-18).
 * Photos are downscaled first, so a large camera photo usually ends up well under the limit;
 * PDFs are copied as-is and rejected above [MAX_BYTES] (P2-17).
 */
class AttachmentImporter(
    private val resolver: ContentResolver,
    private val store: FileStore,
) {
    sealed interface Result {
        data class Ok(val attachment: NewAttachment) : Result
        data object TooLarge : Result
        data object Unsupported : Result
        data object Failed : Result
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        runCatching {
            val mime = mimeOf(uri)
            val name = displayNameOf(uri)
            when {
                mime.startsWith("image/") -> importImage(uri, name)
                mime == PDF -> importPdf(uri, name)
                else -> Result.Unsupported
            }
        }.getOrElse { Result.Failed }
    }

    private suspend fun importImage(uri: Uri, displayName: String): Result {
        val fileName = PhotoImporter(resolver, store).import(uri)
        val size = store.file(fileName).length()
        if (size > MAX_BYTES) {
            store.delete(fileName)
            return Result.TooLarge
        }
        val shownName = displayName.substringBeforeLast('.') + ".jpg"
        return Result.Ok(NewAttachment(fileName, "image/jpeg", shownName, size))
    }

    private fun importPdf(uri: Uri, displayName: String): Result {
        sizeOf(uri)?.let { if (it > MAX_BYTES) return Result.TooLarge }
        val fileName = "${UUID.randomUUID()}.pdf"
        val target = store.file(fileName)
        var copied = 0L
        resolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    copied += n
                    if (copied > MAX_BYTES) {
                        output.close()
                        store.delete(fileName)
                        return Result.TooLarge
                    }
                    output.write(buffer, 0, n)
                }
            }
        }
        return Result.Ok(NewAttachment(fileName, PDF, displayName, copied))
    }

    private fun mimeOf(uri: Uri): String =
        resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase())
            ?: ""

    private fun displayNameOf(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "附件"

    private fun sizeOf(uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        } ?: runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize }?.takeIf { it >= 0 } }.getOrNull()

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        const val PDF = "application/pdf"
    }
}
