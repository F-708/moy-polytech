package com.politex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

class WidgetRegistrationTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private val remoteViewsAllowedTags = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "TextView", "ImageView", "Button", "ImageButton", "ProgressBar",
        "Chronometer", "AnalogClock", "ViewFlipper", "ListView", "GridView",
        "StackView", "AdapterViewFlipper",
    )

    private fun resolveFile(vararg paths: String): File = paths
        .map(::File)
        .firstOrNull { it.isFile }
        ?: error("Файл не найден: ${paths.joinToString()}")

    private fun parse(file: File): Element {
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

    private fun mainApplication(): Element =
        parse(resolveFile("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml"))
            .childElements("application").single()

    private fun teacherApplication(): Element =
        parse(resolveFile("src/teacher/AndroidManifest.xml", "app/src/teacher/AndroidManifest.xml"))
            .childElements("application").single()

    private fun widgetReceivers(app: Element): List<Element> = app.childElements("receiver").filter { rcv ->
        rcv.childElements("intent-filter").any { filter ->
            filter.childElements("action").any { attribute(it, "name") == "android.appwidget.action.APPWIDGET_UPDATE" }
        }
    }

    private fun providerMetaResource(rcv: Element): String? = rcv.childElements("meta-data")
        .firstOrNull { attribute(it, "name") == "android.appwidget.provider" }
        ?.let { attribute(it, "resource") }

    private fun allRegisteredReceivers(): List<Pair<String, Element>> =
        widgetReceivers(mainApplication()).map { attribute(it, "name") to it } +
            widgetReceivers(teacherApplication()).map { attribute(it, "name") to it }

    private fun infoXmlFile(resourceRef: String): File {
        assertTrue("meta-data resource должен указывать на @xml/: $resourceRef", resourceRef.startsWith("@xml/"))
        val name = resourceRef.removePrefix("@xml/")
        return resolveFile("src/main/res/xml/$name.xml", "app/src/main/res/xml/$name.xml")
    }

    private fun infoXml(resourceRef: String): Element = parse(infoXmlFile(resourceRef))

    private fun cellsFromDp(dp: Int): Int = ceil((dp + 30) / 70.0).toInt()

    private fun dpValue(info: Element, attr: String): Int? = attribute(info, attr)
        .takeIf { it.endsWith("dp") }
        ?.removeSuffix("dp")
        ?.toIntOrNull()

    private fun layoutFile(ref: String): File {
        assertTrue("ссылка должна быть @layout/: $ref", ref.startsWith("@layout/"))
        val name = ref.removePrefix("@layout/")
        return resolveFile("src/main/res/layout/$name.xml", "app/src/main/res/layout/$name.xml")
    }

    private fun layoutTags(file: File): List<String> = file.readText()
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .let { text ->
            Regex("<([A-Za-z][A-Za-z0-9_.]*)").findAll(text).map { it.groupValues[1] }.toList()
        }

    private fun drawableExists(ref: String): Boolean {
        if (!ref.startsWith("@drawable/") && !ref.startsWith("@mipmap/")) return false
        val (type, name) = ref.removePrefix("@").split("/", limit = 2)
        return File("src/main/res").listFiles()?.any { dir ->
            dir.isDirectory && dir.name.startsWith(type) &&
                dir.listFiles()?.any { it.nameWithoutExtension == name } == true
        } == true ||
            File("app/src/main/res").listFiles()?.any { dir ->
                dir.isDirectory && dir.name.startsWith(type) &&
                    dir.listFiles()?.any { it.nameWithoutExtension == name } == true
            } == true
    }

    @Test
    fun `все виджет-ресиверы зарегистрированы с exported true`() {
        val receivers = allRegisteredReceivers()
        assertTrue("не найдено ни одного widget-ресивера", receivers.isNotEmpty())
        receivers.forEach { (name, rcv) ->
            assertEquals(
                "$name: без exported=true пикер лаунчера (особенно MIUI на A12+) не видит провайдера",
                "true",
                attribute(rcv, "exported"),
            )
        }
    }

    @Test
    fun `у каждого виджет-ресивера есть валидная meta-data appwidget provider`() {
        allRegisteredReceivers().forEach { (name, rcv) ->
            val resource = providerMetaResource(rcv)
            assertNotNull("$name: нет meta-data android.appwidget.provider с android:resource", resource)
            assertTrue("$name: info-xml не найден: $resource", infoXmlFile(resource!!).isFile)
        }
    }

    @Test
    fun `в каждом info-xml есть initialLayout указывающий на существующий layout`() {
        allRegisteredReceivers().forEach { (name, rcv) ->
            val info = infoXml(providerMetaResource(rcv)!!)
            val initial = attribute(info, "initialLayout")
            assertTrue("$name: initialLayout обязателен и должен быть @layout/", initial.startsWith("@layout/"))
            assertTrue("$name: initialLayout $initial не найден", layoutFile(initial).isFile)
        }
    }

    @Test
    fun `layout виджетов содержат только классы разрешённые в RemoteViews`() {
        val layouts = allRegisteredReceivers().flatMap { (_, rcv) ->
            val info = infoXml(providerMetaResource(rcv)!!)
            listOfNotNull(attribute(info, "initialLayout"), attribute(info, "previewLayout").ifBlank { null })
        }.distinct()
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { ref ->
            val tags = layoutTags(layoutFile(ref))
            val bad = tags.filter { it !in remoteViewsAllowedTags }
            assertTrue("$ref содержит запрещённые в RemoteViews теги: $bad", bad.isEmpty())
        }
        val rowTags = listOf("src/main/res/layout/widget_row_list.xml", "src/main/res/layout/widget_row_bell.xml", "src/main/res/layout/widget_fallback.xml")
            .flatMap { layoutTags(resolveFile(it, "app/$it")) }
        assertTrue("row/fallback layout'ы вне разрешённого набора", rowTags.all { it in remoteViewsAllowedTags })
    }

    @Test
    fun `previewImage у всех провайдеров существует`() {
        allRegisteredReceivers().forEach { (name, rcv) ->
            val info = infoXml(providerMetaResource(rcv)!!)
            val preview = attribute(info, "previewImage")
            assertTrue("$name: previewImage обязателен для пикера", preview.isNotBlank())
            assertTrue("$name: previewImage $preview не найден в res", drawableExists(preview))
        }
    }

    @Test
    fun `config-активити экспортирована с APPWIDGET_CONFIGURE и совпадает с android-configure`() {
        val activity = mainApplication().childElements("activity").singleOrNull {
            attribute(it, "name") == ".widget.WidgetConfigActivity"
        }
        assertNotNull("WidgetConfigActivity не объявлена", activity)
        assertEquals("true", attribute(activity!!, "exported"))
        assertTrue(
            activity.childElements("intent-filter").any { filter ->
                filter.childElements("action").any { attribute(it, "name") == "android.appwidget.action.APPWIDGET_CONFIGURE" }
            },
        )
        allRegisteredReceivers().forEach { (_, rcv) ->
            val configure = attribute(infoXml(providerMetaResource(rcv)!!), "configure")
            if (configure.isNotBlank()) {
                assertEquals("com.politex.widget.WidgetConfigActivity", configure)
            }
        }
    }

    @Test
    fun `геометрия info-xml помещается в 4-колоночную сетку и согласована с targetCell`() {
        allRegisteredReceivers().forEach { (name, rcv) ->
            val info = infoXml(providerMetaResource(rcv)!!)
            val targetW = attribute(info, "targetCellWidth").toIntOrNull()
            val targetH = attribute(info, "targetCellHeight").toIntOrNull()
            if (targetW != null) {
                assertTrue(
                    "$name: targetCellWidth=$targetW не помещается в 4-колоночную сетку (MIUI/AOSP смартфоны)",
                    targetW <= 4,
                )
            }
            dpValue(info, "minWidth")?.let { dp ->
                assertEquals("$name: minWidth=${dp}dp не совпадает с targetCellWidth=$targetW", targetW, cellsFromDp(dp))
            }
            dpValue(info, "minHeight")?.let { dp ->
                assertEquals("$name: minHeight=${dp}dp не совпадает с targetCellHeight=$targetH", targetH, cellsFromDp(dp))
            }
            assertTrue("$name: updatePeriodMillis обязателен", attribute(info, "updatePeriodMillis").toLongOrNull() != null)
            assertTrue("$name: widgetCategory обязателен", attribute(info, "widgetCategory").isNotBlank())
        }
    }

    @Test
    fun `каждый PendingIntent в виджет-зоне создан с явным флагом мутабельности`() {
        val sources = File("src/main/java/com/politex").let { if (it.isDirectory) it else File("app/src/main/java/com/politex") }
        val files = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                it.path.replace('\\', '/').let { p -> p.contains("/widget/") || p.endsWith("/util/BellTimerService.kt") }
            }
            .toList()
        assertTrue("исходники виджет-зоны не найдены", files.isNotEmpty())
        val calls = mutableListOf<String>()
        files.forEach { file ->
            val text = file.readText()
            Regex("PendingIntent\\.(getActivity|getBroadcast|getService|getForegroundService)\\s*\\(").findAll(text).forEach { m ->
                var depth = 0
                var end = m.range.last
                for (i in m.range.last until text.length) {
                    when (text[i]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
                val call = text.substring(m.range.first, end + 1)
                assertTrue(
                    "${file.name}: PendingIntent без FLAG_IMMUTABLE/FLAG_MUTABLE (S+ — SecurityException):\n$call",
                    call.contains("FLAG_IMMUTABLE") || call.contains("FLAG_MUTABLE"),
                )
                calls.add(file.name)
            }
        }
        assertTrue("в виджет-зоне не найдено ни одного PendingIntent — проверка выродилась", calls.isNotEmpty())
    }
}
