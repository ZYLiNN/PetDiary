package com.zylinn.petdiary.ui.records

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Getting a photo / PDF out of the app (P11-06): a family member's X-ray is already synced to this phone,
 * "下載" copies it to the phone's own gallery (圖片/PetDiary) or 下載/PetDiary, "分享" hands it to LINE etc.
 */
object AttachmentExport {

    /** MediaStore folders without any storage permission need Android 10+; older phones only get "分享". */
    val canSave get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Copies [file] into the shared gallery / downloads; returns where it went (for the toast) or null on failure. */
    fun save(context: Context, file: File, mimeType: String, displayName: String): String? {
        if (!canSave || !file.exists()) return null
        val image = mimeType.startsWith("image/")
        val folder = (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/PetDiary"
        val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nameWithExtension(displayName, file))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (image) "相簿的 PetDiary 資料夾" else "「下載」裡的 PetDiary 資料夾"
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Opens a PDF (or any file) in whatever viewer the phone has, read-only for this one file. */
    fun open(context: Context, file: File, mimeType: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(view)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The system share sheet, with read access to just this file. */
    fun share(context: Context, file: File, mimeType: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Keeps the user's name but makes sure the gallery / file manager sees the right extension. */
    fun nameWithExtension(displayName: String, file: File): String {
        val ext = file.extension
        return if (ext.isEmpty() || displayName.endsWith(".$ext", ignoreCase = true)) displayName else "$displayName.$ext"
    }
}
