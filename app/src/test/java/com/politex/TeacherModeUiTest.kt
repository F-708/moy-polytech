package com.politex

import com.politex.ui.screens.effectiveShowLeads
import com.politex.ui.screens.emptyStateTitle
import com.politex.ui.screens.showSpecialtyMenu
import com.politex.ui.screens.showSubgroupCard
import com.politex.ui.screens.showTeacherModeExit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherModeUiTest {
    @Test
    fun `синхронизация важнее режима - статус проверки одинаковый`() {
        val text =
            emptyStateTitle(
                isSyncing = true,
                hasSyncError = false,
                teacherMode = true,
                viewingToday = true,
                activeDateString = "20.10.2026",
            )
        assertEquals("Проверяю расписание на 20.10.2026…", text)
    }

    @Test
    fun `ошибка синхронизации важнее режима`() {
        val text =
            emptyStateTitle(
                isSyncing = false,
                hasSyncError = true,
                teacherMode = true,
                viewingToday = true,
                activeDateString = "20.10.2026",
            )
        assertEquals("Не удалось обновить расписание", text)
    }

    @Test
    fun `преподаватель смотрит сегодня без уроков - честный empty state`() {
        val text =
            emptyStateTitle(
                isSyncing = false,
                hasSyncError = false,
                teacherMode = true,
                viewingToday = true,
                activeDateString = "20.10.2026",
            )
        assertEquals("Сегодня уроков нет", text)
    }

    @Test
    fun `преподаватель смотрит произвольную дату без уроков - общий текст`() {
        val text =
            emptyStateTitle(
                isSyncing = false,
                hasSyncError = false,
                teacherMode = true,
                viewingToday = false,
                activeDateString = "20.10.2026",
            )
        assertEquals("Расписания на этот день нет", text)
    }

    @Test
    fun `студент сегодня без уроков - прежний текст без изменений`() {
        val text =
            emptyStateTitle(
                isSyncing = false,
                hasSyncError = false,
                teacherMode = false,
                viewingToday = true,
                activeDateString = "20.10.2026",
            )
        assertEquals("Расписания на этот день нет", text)
    }

    @Test
    fun `меню колледжа - Моя специальность скрыта у преподавателя и видна у студента`() {
        assertFalse(showSpecialtyMenu(teacherMode = true))
        assertTrue(showSpecialtyMenu(teacherMode = false))
    }

    @Test
    fun `ведёт у вас - в режиме преподавателя гасится даже если запрошено`() {
        assertFalse(effectiveShowLeads(requested = true, teacherMode = true))
        assertTrue(effectiveShowLeads(requested = true, teacherMode = false))
        assertFalse(effectiveShowLeads(requested = false, teacherMode = false))
    }

    @Test
    fun `настройки - подгруппа скрыта у преподавателя и видна у студента`() {
        assertFalse(showSubgroupCard(teacherMode = true))
        assertTrue(showSubgroupCard(teacherMode = false))
    }

    @Test
    fun `настройки - кнопка выхода из режима только у преподавателя`() {
        assertTrue(showTeacherModeExit(teacherMode = true))
        assertFalse(showTeacherModeExit(teacherMode = false))
    }
}
