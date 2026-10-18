package com.zylinn.petdiary.data.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

/**
 * Copies a picked or captured image into the app's [PhotoStore] as a downscaled JPEG,
 * so the original can be deleted from the gallery without breaking the pet's avatar.
 */
class PhotoImporter(
    private val resolver: ContentResolver,
    private val store: PhotoStore,
) {
    /** Returns the stored file name. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri)
        val name = "${UUID.randomUUID()}.jpg"
        try {
            store.file(name).outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "compress failed" }
            }
        } finally {
            bitmap.recycle()
        }
        name
    }

    private fun decodeScaled(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation, so camera photos come out upright.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = MAX_EDGE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("cannot decode image")
        }

    companion object {
        const val MAX_EDGE = 1280
        private const val JPEG_QUALITY = 85
    }
}
