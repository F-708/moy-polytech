package com.politex.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object OwnerPhotoResizer {
    const val MAX_SIDE = 512
    const val QUALITY = 80

    fun resizePlan(
        srcWidth: Int,
        srcHeight: Int,
        maxSide: Int = MAX_SIDE,
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0 || maxSide <= 0) return 0 to 0
        val longest = max(srcWidth, srcHeight)
        if (longest <= maxSide) return srcWidth to srcHeight
        val scale = maxSide.toFloat() / longest.toFloat()
        val w = max(1, (srcWidth * scale).roundToInt())
        val h = max(1, (srcHeight * scale).roundToInt())
        return w to h
    }

    fun sampleSize(
        srcWidth: Int,
        srcHeight: Int,
        maxSide: Int = MAX_SIDE,
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxSide <= 0) return 1
        var sample = 1
        while (srcHeight / (sample * 2) >= maxSide && srcWidth / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample
    }

    fun resizeJpeg(
        open: () -> InputStream?,
        dest: File,
        maxSide: Int = MAX_SIDE,
        quality: Int = QUALITY,
    ): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val opts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            val (w, h) = resizePlan(bounds.outWidth, bounds.outHeight, maxSide)
            if (w <= 0 || h <= 0) {
                decoded.recycle()
                return false
            }
            val scaled =
                if (decoded.width == w && decoded.height == h) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, w, h, true)
                }
            dest.parentFile?.mkdirs()
            val ok =
                dest.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
            if (scaled !== decoded) {
                scaled.recycle()
                decoded.recycle()
            }
            ok && dest.isFile && dest.length() > 0
        } catch (_: Exception) {
            false
        }
    }
}
