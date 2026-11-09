package com.politex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Гейт манифеста: приложение не должно объявлять себя обработчиком файлов,
 * которые не умеет открыть (жалоба заказчика: «принимает абсолютно все файлы, в том числе APK»).
 */
class ManifestFileViewAliasTest {

    private val android = "http://schemas.android.com/apk/res/android"

    /** MIME, которые приложение не умеет открывать: объявлять их нельзя. */
    private val bannedMimeTypes =
        setOf(
            "application/vnd.android.package-archive",
            "application/zip",
            "application/x-zip-compressed",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/rtf",
            "text/rtf",
            "audio/*",
            "video/*",
        )

    /** Расширения, которые приложение не умеет открывать: pathPattern'ов на них быть не должно. */
    private val bannedExtensions = setOf(".apk", ".zip", ".doc", ".xls", ".xlsx", ".mp3", ".mp4", ".pptx", ".odt")

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

    private fun aliasData(): List<Element> = alias().childElements("intent-filter").flatMap { it.childElements("data") }

    private fun aliasMimeTypes(): List<String> = aliasData().map { attribute(it, "mimeType") }.filter { it.isNotEmpty() }

    private fun aliasPaths(): List<String> = aliasData().map { attribute(it, "pathPattern") }.filter { it.isNotEmpty() }

    @Test
    fun `alias Просмотреть файл нацелен на MainActivity`() {
        val alias = alias()
        assertEquals(".MainActivity", attribute(alias, "targetActivity"))
        assertEquals("Просмотреть файл", attribute(alias, "label"))
        assertEquals("true", attribute(alias, "exported"))
    }

    @Test
    fun `alias не объявляет wildcard mimeType`() {
        assertTrue(
            "wildcard */* снова открывает приём любых файлов",
            aliasMimeTypes().none { it == "*/*" },
        )
    }

    @Test
    fun `в фильтрах alias нет голого data без mimeType и pathPattern`() {
        // <data android:scheme="content"/> сам по себе матчит ЛЮБОЙ content-uri:
        // именно из-за него в «Открыть с помощью» попадали APK и архивы.
        val orphans =
            aliasData().filter { data ->
                attribute(data, "mimeType").isEmpty() && attribute(data, "pathPattern").isEmpty()
            }
        assertTrue(
            "элементы <data> без mimeType/pathPattern матчат любой content-uri: ${orphans.map { attribute(it, "scheme") }}",
            orphans.isEmpty(),
        )
    }

    @Test
    fun `alias не объявляет приём архивов, таблиц, аудио, видео и apk`() {
        val declared = aliasMimeTypes()
        bannedMimeTypes.forEach { banned ->
            assertFalse("фильтр alias не должен объявлять $banned", banned in declared)
        }
    }

    @Test
    fun `alias не объявляет расширений неподдерживаемых форматов`() {
        val paths = aliasPaths()
        bannedExtensions.forEach { extension ->
            val hit = paths.filter { it.lowercase().endsWith(extension) }
            assertTrue("pathPattern под $extension не должен объявляться: $hit", hit.isEmpty())
        }
    }

    @Test
    fun `все VIEW-фильтры alias требуют DEFAULT и ограничены типом или путём`() {
        val viewFilters = alias().childElements("intent-filter").filter { "android.intent.action.VIEW" in actions(it) }
        assertTrue("alias без VIEW-фильтров", viewFilters.isNotEmpty())
        viewFilters.forEach { filter ->
            assertTrue("VIEW-фильтр без DEFAULT не получит неявный интент", "android.intent.category.DEFAULT" in categories(filter))
            val data = filter.childElements("data")
            assertTrue("VIEW-фильтр без <data> принимает всё", data.isNotEmpty())
            data.forEach { element ->
                assertTrue(
                    "в <data> должен быть scheme вместе с mimeType или pathPattern",
                    attribute(element, "scheme") == "content" &&
                        (attribute(element, "mimeType").isNotEmpty() || attribute(element, "pathPattern").isNotEmpty()),
                )
            }
        }
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
    fun `SEND не объявляет таблицы и старый Word`() {
        val sendFilter = mainActivity().childElements("intent-filter").single { "android.intent.action.SEND" in actions(it) }
        val declared = sendFilter.childElements("data").map { attribute(it, "mimeType") }
        assertFalse("application/msword" in declared)
        assertFalse("application/vnd.ms-excel" in declared)
        assertFalse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" in declared)
        assertTrue("application/pdf" in declared)
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