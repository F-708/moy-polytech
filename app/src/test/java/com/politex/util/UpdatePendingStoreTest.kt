package com.politex.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.update.UpdateAsset
import com.politex.update.UpdateInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdatePendingStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val info =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = 20001999,
            notes = "важные правки",
            asset = UpdateAsset("moy-polytech-standard-v2.0.1.apk", "https://example.com/a.apk", 123456L),
            sha256 = "ab12",
        )

    @Before
    fun setUp() = UpdatePendingStore.clear(context)

    @After
    fun tearDown() = UpdatePendingStore.clear(context)

    @Test
    fun `round-trip сохраняет все поля`() {
        UpdatePendingStore.save(context, info)
        assertEquals(info, UpdatePendingStore.load(context))
    }

    @Test
    fun `round-trip с пустыми notes и null sha`() {
        val bare = info.copy(notes = "", sha256 = null)
        UpdatePendingStore.save(context, bare)
        assertEquals(bare, UpdatePendingStore.load(context))
    }

    @Test
    fun `пустое хранилище даёт null`() {
        assertNull(UpdatePendingStore.load(context))
    }

    @Test
    fun `clear стирает pending`() {
        UpdatePendingStore.save(context, info)
        UpdatePendingStore.clear(context)
        assertNull(UpdatePendingStore.load(context))
    }

    @Test
    fun `повреждённая запись без url или кода не восстанавливается`() {
        UpdatePendingStore.save(context, info.copy(asset = info.asset.copy(url = "")))
        assertNull(UpdatePendingStore.load(context))
        UpdatePendingStore.clear(context)
        UpdatePendingStore.save(context, info.copy(versionCode = 0))
        assertNull(UpdatePendingStore.load(context))
    }
}
