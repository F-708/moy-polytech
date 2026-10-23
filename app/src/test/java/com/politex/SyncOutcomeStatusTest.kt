package com.politex

import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.checkingStatusFor
import com.politex.data.repository.emptyStateText
import com.politex.data.repository.resolveSyncOutcome
import com.politex.data.repository.statusMessageFor
import com.politex.data.repository.toastFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncOutcomeStatusTest {
    private val date = "06.10.2026"
    private val group = "41О"

    private fun liveDocBytes(): ByteArray = javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `probe 404 - ни документа ни уроков - NotFound с датой`() {
        assertEquals(
            SyncOutcome.NotFound(date),
            resolveSyncOutcome(groupLessons = 0, documentFound = false, targetDate = date),
        )
    }

    @Test
    fun `чужой документ date-mismatch - не принимается - NotFound`() {
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDocBytes(), date))
        assertEquals(
            SyncOutcome.NotFound(date),
            resolveSyncOutcome(groupLessons = 0, documentFound = false, targetDate = date),
        )
    }

    @Test
    fun `документ принят но группы нет - FoundNoGroup с датой`() {
        assertEquals(
            SyncOutcome.FoundNoGroup(date),
            resolveSyncOutcome(groupLessons = 0, documentFound = true, targetDate = date),
        )
    }

    @Test
    fun `документ разобран и группа есть - Ok с числом уроков и датой`() {
        val outcome = resolveSyncOutcome(groupLessons = 5, documentFound = true, targetDate = date)
        assertEquals(SyncOutcome.Ok(5, date), outcome)
        assertEquals(5, outcome.count)
        assertEquals(date, outcome.date)
    }

    @Test
    fun `статус NotFound - ещё не опубликовано, с датой`() {
        assertEquals("Расписание на $date ещё не опубликовано", statusMessageFor(SyncOutcome.NotFound(date)))
    }

    @Test
    fun `статус FoundNoGroup - опубликовано но вашей группы нет`() {
        assertEquals(
            "Расписание на $date опубликовано, но вашей группы в нём пока нет",
            statusMessageFor(SyncOutcome.FoundNoGroup(date)),
        )
    }

    @Test
    fun `статус Ok - загружено N уроков, с датой (склонение)`() {
        assertEquals("Расписание на $date загружено (5 уроков)", statusMessageFor(SyncOutcome.Ok(5, date)))
        assertEquals("Расписание на $date загружено (1 урок)", statusMessageFor(SyncOutcome.Ok(1, date)))
        assertEquals("Расписание на $date загружено (3 урока)", statusMessageFor(SyncOutcome.Ok(3, date)))
    }

    @Test
    fun `промежуточный статус - Проверяю, с датой`() {
        assertEquals("Проверяю расписание на $date…", checkingStatusFor(date))
    }

    @Test
    fun `тост Ok - обновлено N уроков, с датой`() {
        assertEquals(
            "Расписание группы $group на $date обновлено (5 уроков)",
            toastFor(SyncOutcome.Ok(5, date), group),
        )
    }

    @Test
    fun `тост FoundNoGroup - опубликовано но вашей группы нет`() {
        assertEquals(
            "Расписание на $date опубликовано, но вашей группы ($group) в нём пока нет",
            toastFor(SyncOutcome.FoundNoGroup(date), group),
        )
    }

    @Test
    fun `тост NotFound - ещё не опубликовано, с датой`() {
        assertEquals("Расписание на $date ещё не опубликовано", toastFor(SyncOutcome.NotFound(date), group))
    }

    @Test
    fun `единственный промежуточный статус-константа - Проверяю расписание`() {
        assertEquals("Проверяю расписание…", MpkNetworkClient.STATUS_CHECKING)
    }

    @Test
    fun `BB14 - статус про понедельник и empty-state про вторник не противоречат`() {
        val monday = "05.10.2026"
        val tuesday = "06.10.2026"

        val mondayStatus = statusMessageFor(SyncOutcome.Ok(8, monday))
        assertEquals("Расписание на 05.10.2026 загружено (8 уроков)", mondayStatus)
        assertTrue(mondayStatus.contains(monday))
        assertFalse(mondayStatus.contains(tuesday))

        val tuesdayEmpty = emptyStateText(tuesday, monday, SyncOutcome.Ok(8, monday))
        assertEquals("Расписания на этот день нет", tuesdayEmpty)
        assertFalse(tuesdayEmpty.contains(monday))
        assertFalse(tuesdayEmpty.contains("загружено"))
        assertFalse(tuesdayEmpty.contains("базе колледжа"))
    }

    @Test
    fun `BB14 - empty-state про дату свежей загрузки не говорит нет`() {
        assertEquals("Расписание на $date загружено", emptyStateText(date, date, SyncOutcome.Ok(5, date)))
    }

    @Test
    fun `BB14 - empty-state для NotFound и FoundNoGroup этой даты - короткое нет`() {
        assertEquals("Расписания на этот день нет", emptyStateText(date, date, SyncOutcome.NotFound(date)))
        assertEquals("Расписания на этот день нет", emptyStateText(date, date, SyncOutcome.FoundNoGroup(date)))
        assertEquals("Расписания на этот день нет", emptyStateText(date))
    }

    @Test
    fun `ни один финальный статус или тост не обещает скачивание`() {
        val messages = listOf(
            statusMessageFor(SyncOutcome.Ok(5, date)),
            statusMessageFor(SyncOutcome.FoundNoGroup(date)),
            statusMessageFor(SyncOutcome.NotFound(date)),
            toastFor(SyncOutcome.Ok(5, date), group),
            toastFor(SyncOutcome.FoundNoGroup(date), group),
            toastFor(SyncOutcome.NotFound(date), group),
            checkingStatusFor(date),
        )
        messages.forEach {
            assertFalse(it, it.contains("скачиваю", ignoreCase = true))
            assertFalse(it, it.contains("ищу", ignoreCase = true))
            assertFalse(it, it.contains("загружаем", ignoreCase = true))
            assertFalse(it, it.contains("обновляем", ignoreCase = true))
        }
    }

    @Test
    fun `все статусы и тосты содержат дату`() {
        val messages = listOf(
            statusMessageFor(SyncOutcome.Ok(5, date)),
            statusMessageFor(SyncOutcome.FoundNoGroup(date)),
            statusMessageFor(SyncOutcome.NotFound(date)),
            toastFor(SyncOutcome.Ok(5, date), group),
            toastFor(SyncOutcome.FoundNoGroup(date), group),
            toastFor(SyncOutcome.NotFound(date), group),
            checkingStatusFor(date),
        )
        messages.forEach { assertTrue(it, it.contains(date)) }
    }

    @Test
    fun `статусы-обещания удалены из исходников синхронизации`() {
        listOf(
            "app/src/main/java/com/politex/data/repository/ScheduleRepository.kt",
            "app/src/main/java/com/politex/data/network/MpkNetworkClient.kt",
            "app/src/main/java/com/politex/ui/screens/GroupScheduleScreen.kt",
            "app/src/main/java/com/politex/ui/screens/ScheduleScreen.kt",
        ).forEach { rel ->
            val text = sourceText(rel)
            assertFalse(rel, text.contains("скачиваю", ignoreCase = true))
            assertFalse(rel, text.contains("Ищу на портале", ignoreCase = true))
            assertFalse(rel, text.contains("уже загружено", ignoreCase = true))
            assertFalse(rel, text.contains("в базе колледжа", ignoreCase = true))
        }
    }

    private fun sourceText(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, rel).exists()) {
            dir = dir.parentFile ?: error("исходник не найден: $rel")
        }
        return File(dir, rel).readText()
    }
}
