package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.screens.MaterialBaseScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import com.politex.util.MaterialImporter
import com.politex.util.PendingMaterial
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class MaterialTextRoutingTest {
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

        context.deleteDatabase(com.politex.data.local.MpkDatabase.DATABASE_NAME)
        db = MpkDatabase.getInstance(context)
        composeRule.setContent {
            MyApplicationTheme {
                MaterialBaseScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
    }



    private fun insertItem(item: MaterialItemEntity): Long = runBlocking {
        db.materialDao().insertItem(item)
    }

    private fun waitForNode(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `текстовый материал проходит путь импорт предмет тап и показывает текст`() {

        val textId = runBlocking {
            MaterialImporter.importMaterial(
                context,
                db.materialDao(),
                "ТОЭ",
                PendingMaterial.Text("Первая строка конспекта\nВторая строка конспекта"),
            )
        }
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")

        composeRule.onNodeWithTag("material_subject_ТОЭ").assertExists()
        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("material_item_$textId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("doc_top_bar").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_list").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_0").assertExists()
        composeRule.onNodeWithTag("doc_paragraph_1").assertExists()

        val stored = runBlocking { db.materialDao().getItemById(textId) }
        assertEquals("Первая строка конспекта", stored?.title)
        assertTrue(stored?.textContent?.contains("Вторая строка конспекта") == true)
    }

    @Test
    fun `архивный материал виден только в Архиве и возвращается оттуда`() {
        val id = insertItem(
            MaterialItemEntity(
                kind = "FILE",
                title = "Старая лекция",
                ext = "pdf",
                subjectName = "ТОЭ",
                archived = true,
                sortOrder = 0,
            ),
        )
        insertItem(
            MaterialItemEntity(kind = "FILE", title = "Лекция 1", ext = "pdf", subjectName = "ТОЭ", sortOrder = 0),
        )
        composeRule.waitForIdle()
        waitForNode("material_subject_ТОЭ")

        composeRule.onNodeWithTag("material_subject_ТОЭ").assertExists()
        composeRule.onNodeWithTag("material_archive_section").assertExists()

        composeRule.onNodeWithTag("material_subject_ТОЭ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("material_item_${id}").assertDoesNotExist()
        composeRule.onNodeWithTag("subject_back_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("material_archive_section").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("material_item_${id}").assertExists()
        composeRule.onNodeWithTag("material_restore_${id}").assertExists()
    }

    @Test
    fun `поиск находит текстовый материал по содержимому`() {
        insertItem(
            MaterialItemEntity(
                kind = "TEXT",
                title = "Заметка",
                ext = "txt",
                subjectName = "Математика",
                textContent = "интеграл по частям формула",
                sortOrder = 0,
            ),
        )
        composeRule.waitForIdle()
        waitForNode("material_search_field")

        composeRule.onNodeWithTag("material_search_field").performTextInput("интеграл")
        waitForNode("material_search_list")

        composeRule.onNodeWithTag("material_search_list").assertExists()
        composeRule.onNodeWithTag("material_item_1").assertExists()

        composeRule.onNodeWithTag("pdf_search_hint").assertDoesNotExist()
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
