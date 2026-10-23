package com.politex

import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.PROBE_NET_FAIL
import com.politex.data.network.ProbeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

private fun cal(y: Int, m: Int, d: Int): Calendar = Calendar.getInstance().apply {
    clear()
    set(y, m - 1, d)
}

private class CachingTestClient(
    private val serve: (String) -> ByteArray?,
) : MpkNetworkClient() {
    val uncachedCalls = AtomicInteger(0)
    val uncachedUrls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var clock = 0L

    override fun nowMillis(): Long = clock

    override fun downloadUncached(url: String): ByteArray? {
        uncachedCalls.incrementAndGet()
        uncachedUrls += url
        return serve(url)
    }
}

private class ProbeTestClient(
    private val head: (String) -> Pair<Int, String>,
) : MpkNetworkClient() {
    val headCalls = AtomicInteger(0)

    override fun headInfo(url: String): Pair<Int, String> {
        headCalls.incrementAndGet()
        return head(url)
    }

    override fun rangeInfo(url: String): Pair<Int, String> = 404 to "text/html"
}

private class OfflineAllRoundClient(
    private val head: (String) -> Pair<Int, String> = { 404 to "text/html" },
) : MpkNetworkClient() {
    val headCalls = AtomicInteger(0)

    @Volatile
    var clock = 0L

    override fun nowMillis(): Long = clock

    override fun headInfo(url: String): Pair<Int, String> {
        headCalls.incrementAndGet()
        return head(url)
    }

    override fun rangeInfo(url: String): Pair<Int, String> = 404 to "text/html"

    override fun downloadUncached(url: String): ByteArray? = null
}

class ScheduleFetchSpeedupTest {

    private val target = cal(2026, 10, 5)
    private val c1 = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.doc"
    private val c2 = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.docx"
    private val c3 = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya-1.doc"
    private val c4 = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya-1.docx"

    @Test
    fun `повторный download того же URL в пределах TTL не идёт в сеть`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)
        val client = CachingTestClient { if (it == c1) bytes else null }

        assertArrayEquals(bytes, client.downloadFileBytes(c1))
        assertArrayEquals(bytes, client.downloadFileBytes(c1))

        assertEquals(1, client.uncachedCalls.get())
    }

    @Test
    fun `отрицательный результат тоже кэшируется — без повторного запроса`() = runBlocking {
        val client = CachingTestClient { null }

        assertNull(client.downloadFileBytes(c1))
        assertNull(client.downloadFileBytes(c1))

        assertEquals(1, client.uncachedCalls.get())
    }

    @Test
    fun `после истечения TTL документ скачивается заново`() = runBlocking {
        val client = CachingTestClient { byteArrayOf(9) }

        client.downloadFileBytes(c1)
        client.clock += MpkNetworkClient.DOC_CACHE_TTL_MS + 1
        client.downloadFileBytes(c1)

        assertEquals(2, client.uncachedCalls.get())
    }

    @Test
    fun `отрицательный кэш живёт меньше положительного`() = runBlocking {
        val client = CachingTestClient { null }

        client.downloadFileBytes(c1)
        client.clock += MpkNetworkClient.DOC_CACHE_NEG_TTL_MS + 1
        client.downloadFileBytes(c1)

        assertEquals(2, client.uncachedCalls.get())
    }

    @Test
    fun `резолв запоминает найденный URL — повторный вызов той же даты без каскада`() = runBlocking {
        val client = CachingTestClient { null }

        val firstVisited = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            firstVisited += url
            url == c4
        }
        assertTrue("победитель должен быть достигнут каскадом", firstVisited.contains(c4))
        assertTrue("до победителя перебираются кандидаты", firstVisited.size > 1)
        assertEquals(listOf(c1, c2, c3, c4), firstVisited)

        val secondVisited = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            secondVisited += url
            url == c4
        }
        assertEquals("вторая резолюция той же даты идёт сразу в запомненный URL", listOf(c4), secondVisited)
    }

    @Test
    fun `устаревшая запись URL сбрасывается и каскад продолжается`() = runBlocking {
        val client = CachingTestClient { null }

        client.forEachDocumentUrl(target) { it == c4 }

        val visited = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            visited += url
            url == c3
        }
        assertEquals("сначала пробуется запомненный URL", c4, visited.first())
        assertTrue("каскад продолжен до нового победителя", visited.contains(c3))

        val third = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            third += url
            url == c3
        }
        assertEquals(listOf(c3), third)
    }

    @Test
    fun `разные даты и nameBase не пересекаются в запомненных URL`() = runBlocking {
        val client = CachingTestClient { null }

        client.forEachDocumentUrl(target) { it == c4 }

        val otherDate = cal(2026, 10, 6)
        val visited = mutableListOf<String>()
        client.forEachDocumentUrl(otherDate) { url ->
            visited += url
            false
        }
        assertTrue("чужая дата не должна начинаться с URL прошлой даты", visited.first() != c4)
    }

    @Test
    fun `probe находит первый по приоритету документ одним запросом`() = runBlocking {
        val client = ProbeTestClient { url ->
            if (url == c1 || url == c2) 200 to "application/msword" else 404 to "text/html"
        }

        val result = client.probeDocument(target)

        assertTrue(result is ProbeResult.Found)
        assertEquals(c1, (result as ProbeResult.Found).url)
        assertEquals("первый кандидат probing-батчем не требует остальных", 1, client.headCalls.get())
    }

    @Test
    fun `probe при полном отсутствии даёт NotFound и проверяет всех кандидатов`() = runBlocking {
        val client = ProbeTestClient { 404 to "text/html" }

        val result = client.probeDocument(target)

        assertTrue(result is ProbeResult.NotFound)
        assertEquals(404, (result as ProbeResult.NotFound).lastHttpCode)
        val expected = client.generateCandidateDirectUrls(target)
            .map { it.first }
            .count { com.politex.data.network.directUrlHasRequestedDate(it, "05.10.2026") }
        assertEquals(expected, client.headCalls.get())
    }

    @Test
    fun `сетевая ошибка раньше найденного по приоритету даёт NetworkError`() = runBlocking {
        val client = ProbeTestClient { url ->
            when (url) {
                c1, c2 -> 404 to "text/html"
                c3 -> PROBE_NET_FAIL to ""
                else -> 200 to "application/msword"
            }
        }

        val result = client.probeDocument(target)

        assertEquals(ProbeResult.NetworkError, result)
    }

    @Test
    fun `найденный раньше сетевой ошибки по приоритету даёт Found`() = runBlocking {
        val client = ProbeTestClient { url ->
            when (url) {
                c2 -> 200 to "application/msword"
                c3 -> PROBE_NET_FAIL to ""
                else -> 404 to "text/html"
            }
        }

        val result = client.probeDocument(target)

        assertTrue(result is ProbeResult.Found)
        assertEquals(c2, (result as ProbeResult.Found).url)
    }

    @Test
    fun `probe запоминает URL для резолвера`() = runBlocking {
        val probeClient = ProbeTestClient { url -> if (url == c4) 200 to "application/msword" else 404 to "text/html" }
        val probe = probeClient.probeDocument(target)
        assertTrue(probe is ProbeResult.Found)

        val visited = mutableListOf<String>()
        probeClient.forEachDocumentUrl(target) { url ->
            visited += url
            url == c4
        }
        assertEquals("резолвер обязан начать с запомненного probe-URL", c4, visited.first())
    }

    @Test
    fun `предзагрузка батчами не меняет порядок посещения кандидатов`() = runBlocking {
        val client = CachingTestClient { null }

        val visited = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            visited += url
            false
        }

        val direct = client.generateCandidateDirectUrls(target).map { it.first }
        assertEquals(direct, visited.take(direct.size))
    }

    @Test
    fun `probe-miss освобождает резолвер от повторного перебора прямых кандидатов`() = runBlocking {
        val client = OfflineAllRoundClient()

        val probe = client.probeDocument(target)
        assertTrue(probe is ProbeResult.NotFound)
        val headsAfterProbe = client.headCalls.get()
        assertTrue(headsAfterProbe > 0)

        val visited = mutableListOf<String>()
        client.forEachDocumentUrl(target) { url ->
            visited += url
            false
        }
        assertTrue("прямые кандидаты не должны перезапрашиваться после probe-miss", visited.isEmpty())
        assertEquals(headsAfterProbe, client.headCalls.get())
    }

    @Test
    fun `полный провал резолвера запоминается и probe не бьёт сеть в пределах TTL`() = runBlocking {
        val client = OfflineAllRoundClient()

        client.forEachDocumentUrl(target) { false }

        val probe = client.probeDocument(target)
        assertTrue(probe is ProbeResult.NotFound)
        assertEquals(0, client.headCalls.get())
    }

    @Test
    fun `directMiss истекает по TTL и проверка повторяется`() = runBlocking {
        val client = OfflineAllRoundClient()

        client.probeDocument(target)
        val first = client.headCalls.get()
        assertTrue(first > 0)

        client.probeDocument(target)
        assertEquals("в пределах TTL повторный probe без сети", first, client.headCalls.get())

        client.clock += MpkNetworkClient.DIRECT_MISS_TTL_MS + 1
        client.probeDocument(target)
        assertTrue("после TTL probe снова идёт в сеть", client.headCalls.get() > first)
    }
}
