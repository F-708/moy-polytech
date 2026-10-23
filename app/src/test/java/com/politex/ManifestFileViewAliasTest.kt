package com.politex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestFileViewAliasTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private fun resolveFile(vararg paths: String): File = paths
        .map(::File)
        .firstOrNull { it.isFile }
        ?: error("Файл не найден: ${paths.joinToString()}")

    private fun manifest(): Element {
        val file = resolveFile("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.childElements(tag: String): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == tag }

    private fun attribute(
        element: Element,
        name: String,
    ): String = element.getAttributeNS(android, name)

    private fun actions(filter: Element): List<String> = filter.childElements("action").map { attribute(it, "name") }

    private fun categories(filter: Element): List<String> = filter.childElements("category").map { attribute(it, "name") }

    private fun application(): Element = manifest().childElements("application").single()

    private fun mainActivity(): Element {
        val activity =
            application().childElements("activity").singleOrNull {
                attribute(it, "name") == ".MainActivity"
            }
        assertNotNull("MainActivity отсутствует", activity)
        return activity!!
    }

    private fun alias(): Element {
        val alias =
            application().childElements("activity-alias").singleOrNull {
                attribute(it, "name") == ".FileViewAlias"
            }
        assertNotNull("activity-alias .FileViewAlias отсутствует", alias)
        return alias!!
    }

    @Test
    fun `alias Просмотреть файл нацелен на MainActivity`() {
        val alias = alias()
        assertEquals(".MainActivity", attribute(alias, "targetActivity"))
        assertEquals("Просмотреть файл", attribute(alias, "label"))
        assertEquals("true", attribute(alias, "exported"))
    }

    @Test
    fun `VIEW content star-star с DEFAULT висит на alias`() {
        val wildcard =
            alias().childElements("intent-filter").firstOrNull { filter ->
                "android.intent.action.VIEW" in actions(filter) &&
                    "android.intent.category.DEFAULT" in categories(filter) &&
                    filter.childElements("data").any { attribute(it, "scheme") == "content" } &&
                    filter.childElements("data").any { attribute(it, "mimeType") == "*/*" } &&
                    filter.childElements("data").none { it.hasAttributeNS(android, "pathPattern") }
            }
        assertNotNull("нет VIEW-фильтра content + */* без pathPattern", wildcard)
    }

    @Test
    fun `file схема не принимается alias`() {
        val schemes =
            alias()
                .childElements("intent-filter")
                .flatMap { it.childElements("data") }
                .map { attribute(it, "scheme") }
        assertFalse("file" in schemes)
    }

    @Test
    fun `на alias остался только VIEW`() {
        val filters = alias().childElements("intent-filter")
        assertTrue(filters.any { "android.intent.action.VIEW" in actions(it) })
        assertFalse(filters.any { "android.intent.action.SEND" in actions(it) })
        assertFalse(filters.any { "android.intent.action.SEND_MULTIPLE" in actions(it) })
    }

    @Test
    fun `SEND и SEND_MULTIPLE объявлены на MainActivity`() {
        val filters = mainActivity().childElements("intent-filter")
        assertTrue(filters.any { "android.intent.action.SEND" in actions(it) })
        assertTrue(
            filters.any {
                "android.intent.action.SEND_MULTIPLE" in actions(it) &&
                    "image/*" in it.childElements("data").map { data -> attribute(data, "mimeType") }
            },
        )
    }

    @Test
    fun `лаунчер остаётся на MainActivity`() {
        val launcher =
            mainActivity().childElements("intent-filter").singleOrNull {
                "android.intent.action.MAIN" in actions(it)
            }
        assertNotNull("лаунчер-фильтр отсутствует", launcher)
        assertTrue("android.intent.category.LAUNCHER" in categories(launcher!!))
        assertTrue("android.intent.action.VIEW" !in actions(launcher))
        assertTrue("android.intent.action.SEND" !in actions(launcher))
    }
}
