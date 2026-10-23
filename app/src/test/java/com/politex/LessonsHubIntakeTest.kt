package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.ui.screens.MaterialBaseScreen
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import com.politex.util.PendingMaterial
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h720dp")
class LessonsHubIntakeTest {
    private val composeRule = createComposeRule()
    private val consumed = AtomicInteger(0)

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(
                TestRule { base, _ ->
                    object : Statement() {
                        override fun evaluate() {
                            try {
                                base.evaluate()
                            } finally {
                                if (::db.isInitialized) db.close()
                                drainIntake()
                                resetSingleton()
                            }
                        }
                    }
                },
            ).around(composeRule)

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!
    private lateinit var db: MpkDatabase

    @Before
    fun setUp() {
        resetSingleton()
        context.deleteDatabase(MpkDatabase.DATABASE_NAME)
        db = MpkDatabase.getInstance(context)
    }

    @After
    fun tearDown() {
        drainIntake()
    }

    private fun drainIntake() {
        while (ExternalMaterialIntake.hasWork) {
            ExternalMaterialIntake.claim()
            val current = ExternalMaterialIntake.active ?: break
            ExternalMaterialIntake.finish(current)
        }
    }

    private fun renderMaterials() {
        composeRule.setContent {
            MyApplicationTheme {
                MaterialBaseScreen(
                    groupInfo = group,
                    onExternalMaterialConsumed = { consumed.incrementAndGet() },
                )
            }
        }
    }

    private fun hasTag(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hasTag(tag) }
    }

    @Test
    fun `входящий материал показывает окно куда положить и удерживает intake`() {
        val material = PendingMaterial.Text("Конспект по ТОЭ")
        ExternalMaterialIntake.offer(material)
        ExternalMaterialIntake.claim()

        renderMaterials()
        awaitTag("dest_confirm_btn")

        assertTrue(hasTag("dest_cancel_btn"))
        assertEquals(1, consumed.get())
        assertNull(ExternalMaterialIntake.pending)
        assertSame(material, ExternalMaterialIntake.active)
    }

    @Test
    fun `материал приходит в уже открытый экран материалов и забирается ровно один раз`() {
        renderMaterials()
        awaitTag("material_search_field")
        assertNull(ExternalMaterialIntake.pending)
        assertEquals(0, consumed.get())

        val material = PendingMaterial.Text("Внезапная лекция")
        ExternalMaterialIntake.offer(material)
        ExternalMaterialIntake.claim()
        awaitTag("dest_confirm_btn")

        assertTrue(hasTag("dest_cancel_btn"))
        assertEquals(1, consumed.get())
        assertNull(ExternalMaterialIntake.pending)
        assertSame(material, ExternalMaterialIntake.active)

        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        assertEquals(1, consumed.get())
        assertSame(material, ExternalMaterialIntake.active)
    }

    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }
}
