package com.politex

import com.politex.util.BellCountdownNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BellCountdownThemeTest {
    @Test
    fun `JJ6 в темах строки «До звонка» остались только системная и как в приложении`() {
        assertEquals(
            listOf(
                BellCountdownNotifier.Theme.SYSTEM,
                BellCountdownNotifier.Theme.APP,
            ),
            BellCountdownNotifier.Theme.entries,
        )
        assertEquals("Системная", BellCountdownNotifier.Theme.SYSTEM.title)
        assertEquals("Как в приложении", BellCountdownNotifier.Theme.APP.title)
    }

    @Test
    fun `JJ6 Theme from возвращает существующие id как есть`() {
        BellCountdownNotifier.Theme.entries.forEach { theme ->
            assertEquals(theme, BellCountdownNotifier.Theme.from(theme.id))
        }
    }

    @Test
    fun `JJ6 Theme from мягко мапит удалённые id в системную`() {
        listOf("brand", "sky", "dark").forEach { removedId ->
            assertEquals(
                "удалённая тема '$removedId' должна падать в системную",
                BellCountdownNotifier.Theme.SYSTEM,
                BellCountdownNotifier.Theme.from(removedId),
            )
        }
    }

    @Test
    fun `JJ6 Theme from не падает на пустых и неизвестных id`() {
        assertEquals(BellCountdownNotifier.Theme.SYSTEM, BellCountdownNotifier.Theme.from(null))
        assertEquals(BellCountdownNotifier.Theme.SYSTEM, BellCountdownNotifier.Theme.from(""))
        assertEquals(BellCountdownNotifier.Theme.SYSTEM, BellCountdownNotifier.Theme.from("no_such_theme"))
    }

    @Test
    fun `JJ6 id тем уникальны`() {
        val ids = BellCountdownNotifier.Theme.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `JJ6 системная тема без акцента и принудительной заливки`() {
        assertEquals(0, BellCountdownNotifier.Theme.SYSTEM.accent)
        assertTrue(!BellCountdownNotifier.Theme.SYSTEM.colorize)
    }
}
