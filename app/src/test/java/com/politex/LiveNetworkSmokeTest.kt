package com.politex

import com.politex.data.network.MpkNetworkClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress

class LiveNetworkSmokeTest {
    @Test
    fun fetchScheduleForGroup_41О_сЖивогоСайта() {
        assumeTrue("живой сайт не проверяется в CI", System.getenv("CI").isNullOrBlank())
        assumeTrue("guo-mpk.by недоступен из этого окружения", hostReachable())

        val client = MpkNetworkClient()
        val result = runBlocking { client.fetchScheduleForGroup("41О") }

        result.fold(
            onSuccess = { lessons ->
                val diag = client.diagnosticInfo.value
                println("=== ДИАГНОСТИКА ===")
                println("lastSyncTime  = ${diag.lastSyncTime}")
                println("checkedUrl    = ${diag.checkedUrl}")
                println("httpCode      = ${diag.httpStatusCode}")
                println("receivedBytes = ${diag.receivedBytes}")
                println("lessonsFound  = ${diag.lessonsFound}")
                println("statusMessage = ${diag.statusMessage}")
                println("isSuccess     = ${diag.isSuccess}")
                println("lessons       = ${lessons.map { "${it.lessonNumber}:${it.subjectRaw}" }}")
                assertTrue(
                    "Ожидались уроки 41О (сайт должен отдавать документ на сегодня), " +
                        "диагностика: $diag",
                    lessons.isNotEmpty(),
                )
            },
            onFailure = { e ->
                throw AssertionError("Сетевой запрос упал: ${e.javaClass.simpleName}: ${e.message}", e)
            },
        )
    }

    private fun hostReachable(): Boolean = try {
        InetAddress.getByName("guo-mpk.by")
        true
    } catch (_: Exception) {
        false
    }
}
