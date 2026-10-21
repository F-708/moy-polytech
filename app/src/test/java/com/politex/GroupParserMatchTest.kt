package com.politex

import com.politex.util.GroupParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupParserMatchTest {

    @Test
    fun `точное и обрамлённое совпадение находится`() {
        assertTrue(GroupParser.matchesGroup("11О", "11О"))
        assertTrue(GroupParser.matchesGroup("Список: 11О, 21С", "11О"))
        assertTrue(GroupParser.matchesGroup("группа 11 О", "11О"))
        assertTrue(GroupParser.matchesGroup("11-О", "11О"))
    }

    @Test
    fun `латинские варианты буквы specialty совпадают`() {
        assertTrue(GroupParser.matchesGroup("11O", "11О"))
        assertTrue(GroupParser.matchesGroup("Расписание 11o", "11О"))
    }

    @Test
    fun `встраивание в другой код группы НЕ совпадает (границы)`() {
        assertFalse("211О содержит «11О», но это другая группа", GroupParser.matchesGroup("211О", "11О"))
        assertFalse(GroupParser.matchesGroup("311О", "11О"))
        assertFalse(GroupParser.matchesGroup("11О2", "11О"))
        assertFalse(GroupParser.matchesGroup("11Ок", "11О"))
    }

    @Test
    fun `посторонний текст не совпадает`() {
        assertFalse(GroupParser.matchesGroup("кабинет 205", "11О"))
        assertFalse(GroupParser.matchesGroup("", "11О"))
    }
}
