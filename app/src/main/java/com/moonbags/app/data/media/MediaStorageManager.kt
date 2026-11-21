package com.moonbags.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    fun importWidgetPlate(uri: Uri, slot: String): StoredMedia {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val dest = File(dir, "$slot.png")
        val decoded = decodeStream(uri, 480) ?: error("Could not read the picked image")
        val scaled = scaleDown(decoded, 480)
        dest.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return StoredMedia(dest.absolutePath, "image/png")
    }

    /** First GIF frame on API 28+, otherwise a sampled still. Capped so RemoteViews stays small. */
    fun decodeForWidget(path: String?, maxPx: Int = 480): Bitmap? {
        val ready = ensurePlate(path, maxPx) ?: return null
        return decodeSampled(ready, maxPx)
    }

    private fun ensurePlate(path: String?, maxPx: Int): File? {
        if (path.isNullOrBlank()) return null
        val source = File(path)
        if (!source.exists()) return null
        val cached = File(source.parentFile, source.nameWithoutExtension + "-plate.png")
        if (cached.exists() && cached.lastModified() >= source.lastModified() && cached.length() > 0L) return cached
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth in 1..maxPx && bounds.outHeight in 1..maxPx && source.length() < 220_000) return source
        val decoded = decodeSampled(source, maxPx) ?: return source
        val scaled = scaleDown(decoded, maxPx)
        cached.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return cached
    }

    private fun decodeStream(uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sample) } ?: return null
        return scaleDown(decoded, maxPx)
    }

    private fun scaleDown(bitmap: Bitmap, maxPx: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxPx) return bitmap
        val scale = maxPx / largest.toFloat()
        val next = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        if (next !== bitmap) bitmap.recycle()
        return next
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
