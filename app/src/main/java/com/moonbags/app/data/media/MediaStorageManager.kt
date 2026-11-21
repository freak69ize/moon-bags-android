package com.moonbags.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class StoredMedia(val path: String, val mime: String)

/**
 * Copies picked PNG/JPG/GIF files into internal storage so they survive reboots
 * without persisted URI permissions. Widget bitmaps are downsampled.
 */
class MediaStorageManager(private val context: Context) {
    fun import(uri: Uri, slot: String): StoredMedia {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val dest = File(dir, "$slot-${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not read the picked image" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return StoredMedia(dest.absolutePath, mime)
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        File(path).delete()
    }

    /** First GIF frame on API 28+, otherwise a sampled still. Capped so RemoteViews stays small. */
    fun decodeForWidget(path: String?, maxPx: Int = 640): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val decoded = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largest = max(info.size.width, info.size.height).coerceAtLeast(1)
                    val scale = minOf(1f, maxPx / largest.toFloat())
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                decodeSampled(file, maxPx)
            }
            val software = if (decoded != null && decoded.config == Bitmap.Config.HARDWARE) decoded.copy(Bitmap.Config.ARGB_8888, false) else decoded
            software ?: decodeSampled(file, maxPx)
        } catch (_: Exception) {
            decodeSampled(file, maxPx)
        }
    }

    private fun decodeSampled(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        }
        return BitmapFactory.decodeFile(file.absolutePath, sample)
    }

    private fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        var sample = 1
        while (width / sample > maxPx * 2 || height / sample > maxPx * 2) sample *= 2
        return sample
    }
}
