package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.screens.MaterialBaseScreen
import com.politex.ui.screens.materials.ViewerMode
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class W16PdfReaderModeTest {
    private val composeRule = createComposeRule()

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
                                resetSingleton()
                                ViewerMode.active = false
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
        composeRule.setContent {
            MyApplicationTheme {
                MaterialBaseScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
    }



    private fun waitForNode(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `открытие pdf включает ViewerMode а стрелка назад сбрасывает`() {

        val badPdf = File(context.filesDir, "w16_bad.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x21)) }
        val id = runBlocking {
            db.materialDao().insertItem(
                MaterialItemEntity(
                    kind = "FILE",
                    title = "Лекция W16",
                    ext = "pdf",
                    uriPath = badPdf.absolutePath,
                    fileName = "w16_bad.pdf",
                    subjectName = "ТОЭ",
                    sortOrder = 0,
                ),
            )
        }
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")
        assertFalse(ViewerMode.active)

        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()
        waitForNode("material_item_$id")
        composeRule.onNodeWithTag("material_item_$id").performClick()
        composeRule.waitForIdle()

        waitForNode("pdf_top_bar")
        composeRule.onNodeWithTag("pdf_top_bar_back").assertExists()
        composeRule.onNodeWithTag("corrupted_pdf_plaque").assertExists()
        assertTrue("W16: ViewerMode должен включаться на PDF-просмотрщике", ViewerMode.active)

        composeRule.onNodeWithTag("pdf_top_bar_back").performClick()
        composeRule.waitForIdle()
        assertFalse("W16: ViewerMode обязан сбрасываться при выходе из читалки", ViewerMode.active)
        waitForNode("material_item_$id")
    }

    @Test
    fun `текстовый просмотрщик тоже включает полноэкранный режим`() {
        val id = runBlocking {
            db.materialDao().insertItem(
                MaterialItemEntity(
                    kind = "TEXT",
                    title = "Заметка W16",
                    ext = "txt",
                    subjectName = "ТОЭ",
                    textContent = "текст конспекта",
                    sortOrder = 0,
                ),
            )
        }
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")
        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()
        waitForNode("material_item_$id")
        composeRule.onNodeWithTag("material_item_$id").performClick()
        composeRule.waitForIdle()
        waitForNode("doc_top_bar")

        assertTrue("V7/V8: DocTextViewer обязан включать ViewerMode", ViewerMode.active)

        composeRule.onNodeWithTag("doc_top_bar_back").performClick()
        composeRule.waitForIdle()
        assertFalse("V7/V8: ViewerMode обязан сбрасываться при выходе", ViewerMode.active)

        waitForNode("material_item_$id")
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
