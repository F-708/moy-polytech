package com.politex

import com.politex.util.FuzzySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun `расстояние считает замены вставки и удаления`() {
        assertEquals(0, FuzzySearch.damerauLevenshtein("abc", "abc"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "abd"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "ab"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "abcd"))
        assertEquals(3, FuzzySearch.damerauLevenshtein("kitten", "sitting"))
        assertEquals(3, FuzzySearch.damerauLevenshtein("", "abc"))
    }

    @Test
    fun `перестановка соседних символов стоит единицу`() {

        assertEquals(1, FuzzySearch.damerauLevenshtein("физика", "фиизка"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("ab", "ba"))
    }

    @Test
    fun `лимит даёт ранний выход и возвращает лимит плюс один`() {

        assertEquals(3, FuzzySearch.damerauLevenshtein("ранета", "дерево", limit = 2))
        assertEquals(3, FuzzySearch.damerauLevenshtein("abcdef", "uvwxyz", limit = 2))

        assertEquals(3, FuzzySearch.damerauLevenshtein("а", "абвгд", limit = 2))
    }

    @Test
    fun `опечатка в длинном слове считается совпадением`() {
        assertTrue(FuzzySearch.wordMatches("математика", "матиматика"))
        assertTrue(FuzzySearch.wordMatches("программирование", "програмирование"))
        assertTrue(FuzzySearch.wordMatches("физика", "фиизка"))
    }

    @Test
    fun `две опечатки ещё совпадают, три уже нет`() {
        assertTrue(FuzzySearch.wordMatches("электротехника", "эликтратехника"))
        assertFalse(FuzzySearch.wordMatches("электротехника", "аликтратахника"))
    }

    @Test
    fun `совпадение по префиксу от четырёх символов`() {
        assertTrue(FuzzySearch.wordMatches("программирование", "программиров"))
        assertTrue(FuzzySearch.wordMatches("сопротивление", "сопр"))

        assertFalse(FuzzySearch.wordMatches("сопротивление", "сом"))
    }

    @Test
    fun `короткие слова ищутся только точно`() {
        assertTrue(FuzzySearch.wordMatches("да", "да"))

        assertFalse(FuzzySearch.wordMatches("до", "да"))
        assertFalse(FuzzySearch.wordMatches("сон", "сын"))
    }

    @Test
    fun `ё приводится к е`() {
        assertEquals("учеба", FuzzySearch.normalize("учёба"))
        assertEquals("Ё→е работает и в регистр-чувствительном режиме", "Учеба", FuzzySearch.normalize("Учёба", caseSensitive = true))
        assertTrue("запрос с «е» находит текст с «ё»", FuzzySearch.score("Учёба в политехе", "учеба") > 0)
        assertTrue("и наоборот", FuzzySearch.score("Учеба в политехе", "учёба") > 0)
    }

    @Test
    fun `регистр по умолчанию не важен`() {
        assertEquals(1, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "лекция"))
        assertEquals(1, FuzzySearch.score("лекция по тоэ", "ЛЕКЦИЯ"))
    }

    @Test
    fun `тогл Aa включает регистр-чувствительность`() {
        assertEquals(1, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "ЛЕКЦИЯ", caseSensitive = true))
        assertEquals("с включённым Aa другой регистр не совпадает", 0, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "лекция", caseSensitive = true))
    }

    @Test
    fun `считаются только найденные слова запроса`() {
        val text = "Методичка по математике, лекция 3, пределы"
        assertEquals(3, FuzzySearch.score(text, "математика лекция пределы"))

        assertEquals(3, FuzzySearch.score(text, "математика лекция пределы физика"))
        assertEquals("ни одно слово не найдено — материал не подходит", 0, FuzzySearch.score(text, "физика химия"))
    }

    @Test
    fun `словоформа находится по префиксу`() {
        assertEquals(1, FuzzySearch.score("Методичка по математике", "математик"))
    }

    @Test
    fun `число совпадений возвращается для подсветки`() {
        assertEquals(2, FuzzySearch.score("ТОЭ лабораторная работа 4", "тоэ лабараторная"))
        assertEquals(2, FuzzySearch.queryWordCount("тоэ лабараторная"))
    }

    @Test
    fun `повторяющиеся слова запроса считаются один раз`() {
        assertEquals(1, FuzzySearch.queryWordCount("лекция лекция ЛЕКЦИЯ"))
        assertEquals(1, FuzzySearch.score("первая лекция", "лекция лекция"))
    }

    @Test
    fun `пустой запрос и пустой текст дают ноль`() {
        assertEquals(0, FuzzySearch.score("какой-то текст", ""))
        assertEquals(0, FuzzySearch.score("какой-то текст", "   "))
        assertEquals(0, FuzzySearch.score("", "запрос"))
    }

    @Test
    fun `разбивка на слова режет пунктуацию и одиночные буквы`() {
        val words = FuzzySearch.wordsOf("ТОЭ, лаба №4 — сдал! я и a")
        assertTrue(words.contains("тоэ"))
        assertTrue(words.contains("лаба"))
        assertTrue(words.contains("сдал"))
        assertTrue("цифры — часть слова", FuzzySearch.wordsOf("лаба 12").contains("12"))
        assertFalse("одиночная цифра — шум", FuzzySearch.wordsOf("лаба №4").contains("4"))
        assertFalse("одиночная буква — шум", words.contains("я"))
        assertFalse("одиночная латинская буква — шум", words.contains("a"))
        assertFalse("знаки препинания не слова", words.contains("—"))
    }

    @Test
    fun `поиск по длинному тексту с опечаткой в середине`() {
        val text =
            buildString {
                repeat(200) { append("обычный текст конспекта номер $it. ") }
                append("Электротехника и электроника — раздел про цепи")
            }
        assertTrue(FuzzySearch.score(text, "эликтротехника цепи") > 0)
    }
}
