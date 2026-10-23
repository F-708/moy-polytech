package com.politex.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OwnerPhotoResizerDecodeTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `resizeJpeg уменьшает крупное изображение до 512 по длинной стороне`() {
        val src = File(context.cacheDir, "resize_src.png")
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        src.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val dest = File(context.cacheDir, "resize_out/photo.jpg")
        val ok = OwnerPhotoResizer.resizeJpeg(open = { src.inputStream() }, dest = dest)
        assertTrue(ok)
        assertTrue(dest.isFile)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        dest.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals(512, bounds.outWidth)
        assertEquals(341, bounds.outHeight)
    }

    @Test
    fun `resizeJpeg возвращает false на недоступном потоке`() {
        val dest = File(context.cacheDir, "resize_fail.jpg")
        assertFalse(OwnerPhotoResizer.resizeJpeg(open = { null }, dest = dest))
        assertFalse(dest.exists())
    }
}
