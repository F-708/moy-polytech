package com.politex

import com.politex.ui.screens.SubgroupPaneSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompactSplitSpacingTest {

    private val schedule: String =
        sequenceOf(
            File("src/main/java/com/politex/ui/screens/ScheduleScreen.kt"),
            File("app/src/main/java/com/politex/ui/screens/ScheduleScreen.kt"),
        ).first { it.exists() }.readText()

    private fun block(start: String, end: String): String = schedule.substringAfter(start).substringBefore(end)

    @Test
    fun `MM3 - компактный зазор кабинет-преподаватель вырос на 4-6dp`() {
        assertEquals(9, SubgroupPaneSpacing.CompactRoomTeacherGapDp)
        assertEquals(3, SubgroupPaneSpacing.RegularRoomTeacherGapDp)
        assertTrue(SubgroupPaneSpacing.CompactRoomTeacherGapDp in 8..10)
    }

    @Test
    fun `MM3 - компактный зазор применён и к преподавателю и к группе`() {
        val pane = block("private fun SubgroupPane(", "internal fun emptyStateTitle")
        assertEquals(2, Regex("SubgroupPaneSpacing\\.CompactRoomTeacherGapDp").findAll(pane).count())
        assertEquals(2, Regex("SubgroupPaneSpacing\\.RegularRoomTeacherGapDp").findAll(pane).count())
        assertFalse(pane.contains("if (compact) 4.dp else 3.dp"))
    }

    @Test
    fun `MM3 - merged-compact и обычный compact сплит дышат одинаково`() {
        val compactCard = block("private fun CompactLessonCardContent(", "private fun RegularLessonHeaderRow(")
        val cardSplit = compactCard.substringAfter("if (lesson.isSplit)").substringBefore("} else if")
        assertTrue(cardSplit.contains("LessonSubgroupRow("))
        assertTrue(cardSplit.contains("compact = true"))
        assertTrue(cardSplit.contains("showSubject = false"))

        val compactMerged = block("private fun CompactMergedLessonContent(", "private fun RegularLessonCardContent(")
        val mergedSplit = compactMerged.substringAfter("if (content.split)").substringBefore("} else {")
        assertTrue(mergedSplit.contains("LessonSubgroupRow("))
        assertTrue(mergedSplit.contains("compact = true"))
        assertTrue(mergedSplit.contains("showSubject = false"))

        val row = block("private fun LessonSubgroupRow(", "private fun SubgroupPane(")
        assertEquals(2, Regex("compact = compact").findAll(row).count())
        assertFalse(row.contains("compact = false"))
    }

    @Test
    fun `MM3 - обычный сплит остался на прежнем зазоре`() {
        val regularCard = block("private fun RegularLessonCardContent(", "private fun CompactLessonCardContent(")
        assertTrue(regularCard.contains("if (lesson.isSplit)"))
        assertTrue(regularCard.contains("compact = false"))

        val regularMerged = block("private fun RegularMergedLessonContent(", "private fun CompactMergedLessonContent(")
        assertTrue(regularMerged.contains("if (content.split)"))
        assertTrue(regularMerged.contains("compact = false"))
    }
}
