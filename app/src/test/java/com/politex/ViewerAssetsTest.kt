package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.materials.ViewerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewerAssetsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun assetText(path: String): String = ctx.assets.open(path).bufferedReader().use { it.readText() }

    private fun exists(path: String): Boolean = runCatching { ctx.assets.open(path).use {} }.isSuccess

    @Test
    fun `все viewer-ассеты pdf и docx присутствуют`() {
        listOf(
            "viewer/pdf.html", "viewer/docx.html", "viewer/app.js", "viewer/app.css",
            "viewer/pdf.mjs", "viewer/pdf-setup.js", "viewer/pdf-nomodule.js",
            "viewer/docx.js", "viewer/find.js", "viewer/mpk-page.js",
            "viewer/lib/pdf.min.mjs", "viewer/lib/pdf.worker.min.mjs",
            "viewer/lib/docx-preview.min.js", "viewer/lib/jszip3.min.js",
            "viewer/lib/wasm/openjpeg.wasm", "viewer/lib/wasm/jbig2.wasm",
            "viewer-licences.md",
        ).forEach { assertTrue("нет ассета $it", exists(it)) }

        val cmaps = ctx.assets.list("viewer/lib/cmaps").orEmpty()
        assertTrue("cmaps пуст", cmaps.isNotEmpty())
    }

    @Test
    fun `CSP в meta pdf и docx дословно равен ViewerPolicy`() {
        assertTrue(assetText("viewer/pdf.html").contains(ViewerPolicy.CSP))
        assertTrue(assetText("viewer/docx.html").contains(ViewerPolicy.CSP))
    }

    @Test
    fun `mpk-page js подключён к docx, рисование удалено везде`() {
        assertTrue(assetText("viewer/docx.html").contains("mpk-page.js"))

        assertFalse(assetText("viewer/pdf.html").contains("mpk-ink.js"))
        assertFalse(assetText("viewer/docx.html").contains("mpk-ink.js"))
        assertFalse(exists("viewer/mpk-ink.js"))
    }
}
