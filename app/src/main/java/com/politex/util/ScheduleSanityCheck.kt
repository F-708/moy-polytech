package com.politex.util

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.TeacherLessonEntity

/**
 * Защита от «коррумпированных» документов: сайт колледжа временами отдаёт битые .doc (случайные
 * байты вместо текста). Гейт полноты (доля групп каталога) их ловит, но документ, у которого
 * таблица сохранилась, а вот текст — нет, может пройти. Парсер успевает схватить такое, и день в
 * Room ломается полностью: случайные символы и несуществующие кабинеты.
 *
 * Проверка работает ТОЛЬКО там, где кэш уже существует: колледж не знает всех предметов практики,
 * поэтому первый граб чужого/неизвестного дня обязан проходить (иначе ребята на практике остались
 * бы без расписания). А вот «перепроверка уже сохранённого дня» — это дёшево и безопасно: обычно
 * опубликованное расписание не меняется вообще, поэтому новый документ, который выглядит как
 * мусор относительно того, что уже показано пользователю, — повод сохранить старое.
 */

/** Доля уроков с «неизвестным» предметом, выше которой день считается подозрительным. */
internal const val UNKNOWN_SUBJECT_SUSPICIOUS_PERCENT = 40

/** Строка считается текстово коррумпированной, если в ней управляющие символы или много U+FFFD. */
internal fun hasCorruptText(raw: String): Boolean {
    val control = raw.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
    val replacement = raw.count { it == '�' }
    return control > 0 || replacement > 10
}

/** Служебные/спортивные помещения колледжа — не входят в реестр кабинетов карты. */
private val SERVICE_ROOMS = setOf("СТД", "STD", "CTD", "С/З", "СЗ", "СПОРТЗАЛ", "ТИР", "ЗАЛ", "АКТОВЫЙ ЗАЛ", "СЦЕНА", "СТОЛОВАЯ", "СПОРТ", "БАССЕЙН")

private fun cleanRoom(raw: String): String {
    var r = raw.trim().uppercase()
    r = r.removePrefix("КАБ").removePrefix("АУД").trim()
    r = r.trim('.', ' ', '-')
    r = r.replace('А', 'A').replace('В', 'B').replace('С', 'C').replace('Е', 'E')
    r = r.replace('Т', 'T').replace('Д', 'D').replace('З', 'Z').replace('С', 'C')
    return r
}

/**
 * Кабинет «известен», если есть в реестре колледжа, либо это служебное помещение, либо формат
 * подобен кабинету (цифры), но не совпал — формальные кабинеты на практике/спорте существуют.
 */
internal fun roomLooksKnown(
    raw: String,
    knownRooms: Set<String>,
): Boolean {
    val r = cleanRoom(raw)
    if (r.isEmpty()) return true
    if (r in SERVICE_ROOMS) return true
    if (r in knownRooms) return true
    if (r.substringBefore('-') in knownRooms) return true
    if (!r.any { it.isDigit() }) return false
    // Цифровые кабинеты практики/спорта допустимы, даже если не заведены в карте.
    return true
}

/** Предмет «известен», если normalize вернул название из реестра; «практика», «кураторский час»
 * и «физкультура» легальны всегда (вне реестра или в другой формулировке, но реальны). */
internal fun subjectLooksKnown(raw: String): Boolean {
    if (raw.contains("практик", ignoreCase = true)) return true
    if (raw.contains("куратор", ignoreCase = true)) return true
    if (raw.contains("физкультур", ignoreCase = true)) return true
    if (raw.contains("физическая культура", ignoreCase = true)) return true
    val parts = raw.split('/').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) return false
    return parts.all { part ->
        val p = part.trim('-', ' ', '.')
        if (p.length < 3) return@all true
        val norm = SubjectFormatter.normalize(p)
        MpkCurriculum.ALL_SUBJECTS.any { it.equals(norm, ignoreCase = true) }
    }
}

internal fun teacherLooksKnown(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return true
    if (t.length < 2) return false
    val letters = t.count { it.isLetter() }
    if (letters == 0) return false
    return letters.toDouble() / t.length >= 0.5
}

/**
 * Вердикт по строкам одного дня: можно ли перезаписывать уже существующий кэш этими данными.
 * `true` — данные чистые, можно писать; `false` — похожи на коррупцию, оставляем старое.
 */
internal fun sanityVerdict(
    lessons: List<LessonEntity>,
    knownRooms: Set<String>,
): Boolean {
    if (lessons.isEmpty()) return true
    val checked = lessons.filter { it.subjectRaw.isNotBlank() }
    if (checked.isEmpty()) return false
    if (checked.any { hasCorruptText(it.subjectRaw + it.roomFirst + it.roomSecond + it.teacherFirst + it.teacherSecond) }) {
        return false
    }
    val unknownSubjects = checked.count { !subjectLooksKnown(it.subjectRaw) }
    if (unknownSubjects * 100 > checked.size * UNKNOWN_SUBJECT_SUSPICIOUS_PERCENT) return false
    val rooms = checked.flatMap { l -> listOf(l.roomFirst, l.roomSecond) }.filter { it.isNotBlank() }
    if (rooms.any { !roomLooksKnown(it, knownRooms) }) return false
    if (checked.any { !teacherLooksKnown(it.teacherFirst) || !teacherLooksKnown(it.teacherSecond) }) return false
    return true
}

/**
 * То же для строк расписания преподавателей: сайт иногда бьёт тот же документ, преподавательская
 * таблица страдает так же.
 */
internal fun sanityVerdictTeacher(rows: List<TeacherLessonEntity>): Boolean {
    if (rows.isEmpty()) return true
    val checked = rows.filter { it.subject.isNotBlank() }
    if (checked.isEmpty()) return false
    if (checked.any { hasCorruptText(it.subject + it.room + it.teacherName) }) return false
    if (checked.any { !subjectLooksKnown(it.subject) }) return false
    if (checked.any { !teacherLooksKnown(it.teacherName) }) return false
    return true
}
