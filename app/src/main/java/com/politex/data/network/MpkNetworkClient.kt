package com.politex.data.network

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.SyncDiagnosticInfo
import com.politex.util.DateFormats
import com.politex.util.lessonsWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class ProbeResult {
    data class Found(val url: String, val httpCode: Int) : ProbeResult()
    data class NotFound(val lastHttpCode: Int) : ProbeResult()
    object NetworkError : ProbeResult()
}

internal const val PROBE_NET_FAIL = -1

internal data class UrlProbe(val found: Boolean, val code: Int, val networkError: Boolean = false)

internal fun probeUrlOutcome(
    headCode: Int,
    rangeGetCode: Int,
    headIsHtml: Boolean = false,
    rangeIsHtml: Boolean = false,
): UrlProbe = when {
    headCode == PROBE_NET_FAIL -> UrlProbe(found = false, code = PROBE_NET_FAIL, networkError = true)
    headCode in 200..299 && !headIsHtml -> UrlProbe(found = true, code = headCode)
    rangeGetCode == PROBE_NET_FAIL -> UrlProbe(found = false, code = PROBE_NET_FAIL, networkError = true)
    rangeGetCode in 200..299 && !rangeIsHtml -> UrlProbe(found = true, code = rangeGetCode)
    else -> UrlProbe(found = false, code = if (rangeGetCode > 0) rangeGetCode else headCode)
}

internal fun directUrlHasRequestedDate(url: String, dateDot: String): Boolean {
    if (!Regex("""\d{2}\.\d{2}\.\d{4}""").matches(dateDot)) return false
    val underscore = "${dateDot.substring(0, 2)}_${dateDot.substring(3, 5)}_${dateDot.substring(6, 10)}"
    return url.contains(dateDot) || url.contains(underscore)
}

open class MpkNetworkClient {

    companion object {

        const val STATUS_CHECKING = "Проверяю расписание…"
        const val SCHEDULE_PORTAL_URL = "https://guo-mpk.by/raspisanie/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val BROWSER_USER_AGENT = USER_AGENT
        const val BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,application/msword," +
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document,*/*;q=0.8"

        internal const val DOC_CACHE_TTL_MS = 60_000L
        internal const val DOC_CACHE_NEG_TTL_MS = 15_000L
        internal const val DOC_CACHE_MAX_ENTRIES = 16
        internal const val URL_MEMO_TTL_MS = 6L * 60 * 60 * 1000
        internal const val URL_MEMO_MAX_ENTRIES = 32
        internal const val DIRECT_MISS_TTL_MS = 60_000L
        internal const val PREFETCH_BATCH = 6
        internal const val PROBE_BATCH = 6

        private val WS_REGEX = Regex("\\s+")

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8000, TimeUnit.MILLISECONDS)
                .readTimeout(8000, TimeUnit.MILLISECONDS)
                .writeTimeout(8000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun buildDirectScheduleUrl(calendar: Calendar = Calendar.getInstance()): String {
            val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
            val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
            return "https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/${DateFormats.dotDate(calendar)}-raspisanie-uchashhihsya.doc"
        }
    }

    private class DocCacheEntry(
        val bytes: ByteArray?,
        val at: Long,
    )

    private class UrlMemoEntry(
        val url: String,
        val at: Long,
    )

    private class DirectMissEntry(
        val code: Int,
        val at: Long,
    )

    private val docCache = ConcurrentHashMap<String, DocCacheEntry>()
    private val urlMemo = ConcurrentHashMap<String, UrlMemoEntry>()
    private val directMiss = ConcurrentHashMap<String, DirectMissEntry>()

    internal open fun nowMillis(): Long = System.currentTimeMillis()

    private fun docCacheTtl(entry: DocCacheEntry): Long = if (entry.bytes == null) DOC_CACHE_NEG_TTL_MS else DOC_CACHE_TTL_MS

    private fun freshUrlMemo(key: String): String? {
        val entry = urlMemo[key] ?: return null
        if (nowMillis() - entry.at >= URL_MEMO_TTL_MS) {
            urlMemo.remove(key)
            return null
        }
        return entry.url
    }

    private fun rememberUrlMemo(key: String, url: String) {
        val now = nowMillis()
        if (urlMemo.size >= URL_MEMO_MAX_ENTRIES) {
            urlMemo.entries.removeAll { now - it.value.at >= URL_MEMO_TTL_MS }
            while (urlMemo.size >= URL_MEMO_MAX_ENTRIES) {
                val oldest = urlMemo.entries.minByOrNull { it.value.at } ?: break
                urlMemo.remove(oldest.key)
            }
        }
        urlMemo[key] = UrlMemoEntry(url, now)
    }

    private fun freshDirectMiss(key: String): DirectMissEntry? {
        val entry = directMiss[key] ?: return null
        if (nowMillis() - entry.at >= DIRECT_MISS_TTL_MS) {
            directMiss.remove(key)
            return null
        }
        return entry
    }

    private fun rememberDirectMiss(key: String, code: Int) {
        directMiss[key] = DirectMissEntry(code, nowMillis())
    }

    private fun clearDirectMiss(key: String) {
        directMiss.remove(key)
    }

    internal fun urlMemoKey(calendar: Calendar, nameBase: String): String = "$nameBase|${DateFormats.dotDate(calendar)}"

    private fun evictDocCache(now: Long) {
        if (docCache.size < DOC_CACHE_MAX_ENTRIES) return
        docCache.entries.removeAll { now - it.value.at >= docCacheTtl(it.value) }
        while (docCache.size >= DOC_CACHE_MAX_ENTRIES) {
            val oldest = docCache.entries.minByOrNull { it.value.at } ?: break
            docCache.remove(oldest.key)
        }
    }

    private val _diagnosticInfo = MutableStateFlow(SyncDiagnosticInfo())
    val diagnosticInfo: StateFlow<SyncDiagnosticInfo> = _diagnosticInfo.asStateFlow()

    fun generateCandidateDirectUrls(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya",
    ): List<Pair<String, String>> {
        val ddMmYyyy = DateFormats.dotDate(calendar)
        val dd = ddMmYyyy.substring(0, 2)
        val prevMonth = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

        val folders = listOf(
            SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time) to
                SimpleDateFormat("MM", Locale.ROOT).format(calendar.time),
            SimpleDateFormat("yyyy", Locale.ROOT).format(prevMonth.time) to
                SimpleDateFormat("MM", Locale.ROOT).format(prevMonth.time),
        )

        val result = mutableListOf<Pair<String, String>>()
        fun add(yyyy: String, mm: String, name: String, suffix: String, ext: String) {
            val url = "https://guo-mpk.by/wp-content/uploads/$yyyy/$mm/$name-$nameBase$suffix.$ext"
            if (result.none { it.first == url }) result.add(Pair(url, ddMmYyyy))
        }

        for ((yyyy, mm) in folders) {
            for (name in listOf(ddMmYyyy, "${dd}_${mm}_$yyyy")) {
                for (suffix in listOf("", "-1", "-2")) {
                    for (ext in listOf("doc", "docx")) {
                        add(yyyy, mm, name, suffix, ext)
                    }
                }
            }
        }
        return result
    }

    fun generateCandidateEventUrls(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya",
    ): List<Pair<String, String>> {
        val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
        val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
        val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
        val dateStr = "$dd-$mm-$yyyy"
        val occStr = "$yyyy-$mm-$dd"
        val slugs = if (nameBase.endsWith("prepodavatelej")) listOf("", "-2") else listOf("")
        return slugs.flatMap { slugSuffix ->
            listOf(
                Pair("https://guo-mpk.by/raspisanie/$dateStr-$nameBase$slugSuffix/?occurrence=$occStr", "$dd.$mm.$yyyy"),
                Pair("https://guo-mpk.by/$dateStr-$nameBase$slugSuffix/", "$dd.$mm.$yyyy"),
            )
        }
    }

    fun extractEventPageUrls(html: String, dateStr: String): List<String> = Regex(
        "(?i)https?://guo-mpk\\.by/(?:raspisanie/)?" + Regex.escape(dateStr) +
            "-raspisanie-(?:uchashhihsya|prepodavatelej)[a-z0-9-]*/?",
    )
        .findAll(html)
        .map { it.value }
        .distinct()
        .toList()

    open suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya",
        preferredUrl: String? = null,
        visit: suspend (String) -> Boolean,
    ) = withContext(Dispatchers.IO) {
        val tried = LinkedHashSet<String>()
        val memoKey = urlMemoKey(calendar, nameBase)

        suspend fun phaseUrls(urls: List<String>): String? {
            for (url in urls) {
                if (!tried.add(url)) continue
                if (visit(url)) return url
            }
            return null
        }

        suspend fun prefetch(urls: List<String>) {
            val fresh = urls.filterNot { it in tried }
            if (fresh.isEmpty()) return
            coroutineScope { fresh.map { async { downloadFileBytes(it) } }.awaitAll() }
        }

        suspend fun phaseUrlsBatched(urls: List<String>): String? {
            if (urls.isEmpty()) return null
            phaseUrls(urls.take(1))?.let { return it }
            for (batch in urls.drop(1).chunked(PREFETCH_BATCH)) {
                prefetch(batch)
                phaseUrls(batch)?.let { return it }
            }
            return null
        }

        suspend fun phaseEventPages(pages: List<String>): String? {
            for (batch in pages.chunked(PREFETCH_BATCH)) {
                prefetch(batch)
                for (page in batch) {
                    if (!tried.add(page)) continue
                    val html = downloadFileBytes(page) ?: continue
                    phaseUrls(MpkScheduleParser.extractDocumentUrls(String(html, Charsets.UTF_8), page))?.let { return it }
                }
            }
            return null
        }

        var winner: String? = null

        if (preferredUrl != null) winner = phaseUrls(listOf(preferredUrl))
        if (winner == null) freshUrlMemo(memoKey)?.let { winner = phaseUrls(listOf(it)) }
        if (winner == null && freshDirectMiss(memoKey) == null) {
            winner = phaseUrlsBatched(generateCandidateDirectUrls(calendar, nameBase).map { it.first })
            if (winner == null) rememberDirectMiss(memoKey, 0)
        }

        if (winner == null) {
            val dd = SimpleDateFormat("dd", Locale.ROOT).format(calendar.time)
            val mm = SimpleDateFormat("MM", Locale.ROOT).format(calendar.time)
            val yyyy = SimpleDateFormat("yyyy", Locale.ROOT).format(calendar.time)
            val portalDocs = mutableListOf<String>()
            val portalEventPages = mutableListOf<String>()
            downloadFileBytes(SCHEDULE_PORTAL_URL)?.let { bytes ->
                val html = String(bytes, Charsets.UTF_8)
                portalDocs += MpkScheduleParser.extractDocumentUrls(html, SCHEDULE_PORTAL_URL)
                portalEventPages += extractEventPageUrls(html, "$dd-$mm-$yyyy")
            }

            winner = phaseEventPages(
                generateCandidateEventUrls(calendar, nameBase).map { it.first } + portalEventPages,
            )
            if (winner == null) winner = phaseUrls(portalDocs)
        }

        if (winner != null) {
            clearDirectMiss(memoKey)
            rememberUrlMemo(memoKey, winner)
        }
    }

    open suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar = calculateTargetCalendar(),
        maxRetries: Int = 2,
        preferredUrl: String? = null,
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        val timeFormat = SimpleDateFormat("HH:mm:ss (dd.MM.yyyy)", Locale.ROOT)
        val nowStr = timeFormat.format(Date())
        val dateDotStr = DateFormats.dotDate(targetCalendar)

        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            isSyncing = true,
            statusMessage = STATUS_CHECKING,
        )

        var lastCheckedUrl = buildDirectScheduleUrl(targetCalendar)
        var lastHttpCode = 0
        var totalBytesReceived = 0L
        var lastException: Throwable? = null

        val allLessons = mutableListOf<LessonEntity>()
        var lastResponsePreview = ""
        var lastParseFormat = ""
        var lastParseStrategy = ""
        var lastParseRuns = 0
        var groupWasFound = false
        var lastParseError = ""
        var lastTextPreview = ""

        fun rememberResponsePreview(bytes: ByteArray) {
            lastResponsePreview = String(bytes.copyOfRange(0, minOf(100, bytes.size)), Charsets.UTF_8)
                .replace(WS_REGEX, " ")
                .take(100)
        }

        fun rememberParseStats() {
            val stats = MpkScheduleParser.statsSnapshot()
            lastParseFormat = stats.format
            lastParseStrategy = stats.strategy
            lastParseRuns = stats.runsExtracted
            groupWasFound = groupWasFound || stats.groupFound
            lastParseError = stats.parseError
            lastTextPreview = stats.textPreview
        }

        for (attempt in 1..maxRetries) {
            try {
                forEachDocumentUrl(targetCalendar, preferredUrl = preferredUrl) { url ->
                    lastCheckedUrl = url
                    val docBytes = downloadFileBytes(url)
                    if (docBytes == null || docBytes.isEmpty()) {
                        false
                    } else if (!MpkScheduleParser.documentMatchesDate(docBytes, dateDotStr)) {
                        false
                    } else {
                        totalBytesReceived += docBytes.size
                        lastHttpCode = 200
                        rememberResponsePreview(docBytes)
                        val parsed = MpkScheduleParser.parseFile(docBytes, targetGroup, dateDotStr)
                        rememberParseStats()
                        if (parsed.isNotEmpty()) {
                            allLessons += parsed
                            true
                        } else {
                            false
                        }
                    }
                }

                val distinctLessons = allLessons.distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
                val isSuccess = lastHttpCode in 200..299 || distinctLessons.isNotEmpty()

                _diagnosticInfo.value = SyncDiagnosticInfo(
                    lastSyncTime = nowStr,
                    checkedUrl = lastCheckedUrl,
                    httpStatusCode = if (lastHttpCode == 0 && isSuccess) 200 else lastHttpCode,
                    receivedBytes = totalBytesReceived,
                    lessonsFound = distinctLessons.size,
                    statusMessage = when {
                        distinctLessons.isNotEmpty() ->
                            "Успешно: загружено ${distinctLessons.size} ${lessonsWord(distinctLessons.size)}" +
                                (if (lastParseError.isNotBlank()) " (с ошибкой разбора: $lastParseError)" else "")

                        totalBytesReceived > 0L && lastParseError.isNotBlank() ->
                            "Документ получен (${totalBytesReceived / 1024} КБ), ошибка разбора: $lastParseError"
                        totalBytesReceived > 0L -> "Документ получен (${totalBytesReceived / 1024} КБ), но группа $targetGroup в нём не найдена"
                        else -> "Расписание группы $targetGroup пока не опубликовано"
                    },
                    isSuccess = isSuccess,
                    isSyncing = false,
                    responsePreview = lastResponsePreview,
                    parseFormat = lastParseFormat,
                    parseStrategy = lastParseStrategy,
                    parseRuns = lastParseRuns,
                    groupFound = groupWasFound,
                    parseError = lastParseError,
                    textPreview = lastTextPreview,
                )

                return@withContext Result.success(distinctLessons)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) delay(300)
            }
        }

        val errMsg = lastException?.message ?: "Не удалось загрузить расписание"
        _diagnosticInfo.value = SyncDiagnosticInfo(
            lastSyncTime = nowStr,
            checkedUrl = lastCheckedUrl,
            httpStatusCode = lastHttpCode,
            receivedBytes = totalBytesReceived,
            lessonsFound = 0,
            statusMessage = "Ошибка: ${lastException?.javaClass?.simpleName ?: ""} $errMsg".trim(),
            isSuccess = false,
            isSyncing = false,
            responsePreview = lastResponsePreview,
            parseFormat = lastParseFormat,
            parseStrategy = lastParseStrategy,
            parseRuns = lastParseRuns,
            groupFound = groupWasFound,
            parseError = lastParseError,
            textPreview = lastTextPreview,
        )

        Result.failure(lastException ?: IOException(errMsg))
    }

    private fun calculateTargetCalendar(): Calendar {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        if ((dayOfWeek == Calendar.FRIDAY && hour >= 15) || dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            val daysUntilMon = if (dayOfWeek == Calendar.SUNDAY) 1 else (Calendar.SATURDAY - dayOfWeek + 2) % 7
            cal.add(Calendar.DAY_OF_YEAR, daysUntilMon)
        }
        return cal
    }

    open suspend fun downloadFileBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val now = nowMillis()
        val cached = docCache[url]
        if (cached != null) {
            if (now - cached.at < docCacheTtl(cached)) return@withContext cached.bytes
            docCache.remove(url)
        }
        val bytes = downloadUncached(url)
        evictDocCache(now)
        docCache[url] = DocCacheEntry(bytes, now)
        bytes
    }

    internal open fun downloadUncached(url: String): ByteArray? {
        try {
            executeRequest(url).use { response ->
                if (response.isSuccessful) {
                    return response.body?.bytes()
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {}
        return null
    }

    open suspend fun fetchTeacherSchedule(
        targetCalendar: Calendar = Calendar.getInstance(),
    ): Result<Map<String, List<TeacherSlot>>> = withContext(Dispatchers.IO) {
        val ddMmYyyy = DateFormats.dotDate(targetCalendar)
        var lastUrl = ""
        var lastMismatch: String? = null

        try {
            var accepted: Map<String, List<TeacherSlot>>? = null

            forEachDocumentUrl(targetCalendar, "raspisanie-prepodavatelej") { url ->
                lastUrl = url
                val bytes = downloadFileBytes(url)
                if (bytes == null || bytes.isEmpty()) return@forEachDocumentUrl false

                val text = TeacherScheduleParser.extractText(bytes)
                if (text.isNotBlank() && !TeacherScheduleParser.matchesRequestedDate(text, targetCalendar)) {
                    val inDoc = TeacherScheduleParser.dateFromHeaderText(
                        TeacherScheduleParser.findHeaderLine(text).orEmpty(),
                    )
                    lastMismatch = if (inDoc == null) {
                        "Дата не найдена в документе: $url"
                    } else {
                        "Документ за $inDoc вместо $ddMmYyyy: $url"
                    }
                    return@forEachDocumentUrl false
                }

                val parsed = TeacherScheduleParser.parse(bytes)
                if (parsed.isEmpty()) {
                    lastMismatch = "Не удалось разобрать расписание преподавателей"
                    false
                } else {
                    accepted = parsed
                    true
                }
            }

            when {
                accepted != null -> Result.success(accepted!!)
                lastMismatch != null -> Result.failure(IOException(lastMismatch!!))
                else -> Result.failure(IOException("Документ не найден: ${lastUrl.ifBlank { ddMmYyyy }}"))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeRequest(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .build()
        return client.newCall(request).execute()
    }

    private fun executeHead(url: String): Response = client.newCall(
        Request.Builder().url(url).head()
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .build(),
    ).execute()

    private fun executeRangeGet(url: String): Response = client.newCall(
        Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", BROWSER_ACCEPT)
            .header("Range", "bytes=0-0")
            .build(),
    ).execute()

    private fun safeHeadInfo(block: () -> Response): Pair<Int, String> = try {
        block().use { it.code to (it.header("Content-Type") ?: "") }
    } catch (_: IOException) {
        PROBE_NET_FAIL to ""
    }

    internal open fun headInfo(url: String): Pair<Int, String> = safeHeadInfo { executeHead(url) }

    internal open fun rangeInfo(url: String): Pair<Int, String> = safeHeadInfo { executeRangeGet(url) }

    open suspend fun probeDocument(
        calendar: Calendar,
        nameBase: String = "raspisanie-uchashhihsya",
    ): ProbeResult = withContext(Dispatchers.IO) {
        val dateDot = DateFormats.dotDate(calendar)
        val memoKey = urlMemoKey(calendar, nameBase)

        freshDirectMiss(memoKey)?.let { return@withContext ProbeResult.NotFound(it.code) }

        val candidates = generateCandidateDirectUrls(calendar, nameBase)
            .map { it.first }
            .filter { directUrlHasRequestedDate(it, dateDot) }
        val memo = freshUrlMemo(memoKey)?.takeIf { directUrlHasRequestedDate(it, dateDot) }
        val ordered = if (memo != null) listOf(memo) + candidates.filter { it != memo } else candidates

        var lastCode = 0
        val batches = listOf(ordered.take(1)) + ordered.drop(1).chunked(PROBE_BATCH)
        for (batch in batches) {
            if (batch.isEmpty()) continue
            val infos = coroutineScope { batch.map { async { headInfo(it) } }.awaitAll() }
            for ((index, url) in batch.withIndex()) {
                val (head, headType) = infos[index]
                val (range, rangeType) =
                    if (head == PROBE_NET_FAIL || head in 200..299) 0 to "" else rangeInfo(url)
                val outcome = probeUrlOutcome(
                    headCode = head,
                    rangeGetCode = range,
                    headIsHtml = headType.startsWith("text/html", ignoreCase = true),
                    rangeIsHtml = rangeType.startsWith("text/html", ignoreCase = true),
                )
                when {
                    outcome.networkError -> return@withContext ProbeResult.NetworkError
                    outcome.found -> {
                        clearDirectMiss(memoKey)
                        rememberUrlMemo(memoKey, url)
                        return@withContext ProbeResult.Found(url, outcome.code)
                    }
                    else -> lastCode = outcome.code
                }
            }
        }
        rememberDirectMiss(memoKey, lastCode)
        ProbeResult.NotFound(lastCode)
    }

    open fun publishStatus(statusMessage: String? = null, isSyncing: Boolean? = null) {
        _diagnosticInfo.update { current ->
            current.copy(
                statusMessage = statusMessage ?: current.statusMessage,
                isSyncing = isSyncing ?: current.isSyncing,
            )
        }
    }
}
