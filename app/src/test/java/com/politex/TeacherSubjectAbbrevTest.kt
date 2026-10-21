package com.politex

import com.politex.data.network.TeacherScheduleParser
import com.politex.data.repository.TeacherDayLesson
import com.politex.ui.viewmodel.toLessonEntity
import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherSubjectAbbrevTest {
    private val teacherAbbrevToDisplay =
        mapOf(
            "ФизК" to "Физкультура",
            "СМГр" to "Физкультура",
            "ИнфТ" to "Инф. технол.",
            "Инфо" to "Информатика",
            "Мате" to "Математика",
            "Физи" to "Физика",
            "Хими" to "Химия",
            "Биол" to "Биология",
            "Геог" to "География",
            "Астр" to "Астрономия",
            "Исто" to "История Бел.",
            "ИстБ" to "История Бел.",
            "ОбщВ" to "Обществоведение",
            "Черч" to "Черчение",
            "Допр" to "Допризыв. подг.",
            "ЗащН" to "Защита населения и территорий от чрезвычайных ситуаций",
            "ОхрТ" to "Охр. труда",
            "ОхрО" to "ООС и энергосб.",
            "ОПра" to "Основы права",
            "БеЯз" to "Бел. яз. (проф)",
            "БеПр" to "Бел. яз. (проф)",
            "БелЛ" to "Бел. лит-ра",
            "РусЯ" to "Русский язык",
            "РусЛ" to "Рус. лит-ра",
            "РусП" to "Русский язык",
            "ИноЯ" to "Ин. яз. (проф)",
            "ИнЯз" to "Ин. яз. (проф)",
            "КулД" to "Культура деловой речи",
            "СтБЯ" to "Стилистика деловой речи и редактирование служебных документов на белорусском языке",
            "СтРЯ" to "Стилистика речи",
            "Дело" to "Делопроизводство",
            "Секр" to "Секретарское дело",
            "Архи" to "Архивоведение",
            "Кадр" to "Кадровое делопр.",
            "ДелК" to "Деловые коммуникации",
            "Комм" to "Коммуникационный дизайн (после 11 класса)",
            "УПДе" to "Учебная практика (делопроизводство)",
            "УПМа" to "Учебная практика (маркетинг)",
            "УПИн" to "Учебная практика по информационным технологиям",
            "УПСл" to "Механическая обработка",
            "КУРС" to "Курсовое проектирование",
            "КУРЧ" to "Кур. час",
            "ЭкОр" to "Экон. организ.",
            "ОМен" to "Осн. менеджмента",
            "Стат" to "Статистика",
            "БухУ" to "Бух. учет",
            "ФинО" to "Финансы организаций",
            "Това" to "Товароведение",
            "Марк" to "Основы маркетинга",
            "МаИс" to "Маркет. исслед.",
            "Сист" to "Сист. техн. орг. серв.",
            "СисТ" to "Сист. техн. орг. серв.",
            "ИнжГ" to "Инж. графика",
            "ОИнж" to "Основы инженерной графики",
            "ОТех" to "Основы технической механики",
            "ТехМ" to "Тех. механика",
            "Гидр" to "Гидропривод и гидропневмоавтоматика",
            "Норм" to "Нормирование точности и технические измерения",
            "Стан" to "Стандартизация и качество продукции",
            "ОбрМ" to "Обработка материалов и инструмент",
            "ПрТ" to "Проектирование технологической оснастки",
            "ТехН" to "Техническое нормирование",
            "ЭПри" to "Электропривод и электроавтоматика",
            "ОргМ" to "Организация машиностроительного производства",
            "ОЭкО" to "Основы экономики организации предпринимательской деятельности",
            "Чтен" to "Чтение машиностроительных чертежей",
            "МетР" to "Станки",
            "ТОЭ" to "ТОЭ",
            "ФТТ" to "Физика тв. тела",
            "ЭлТе" to "Электротех. мат.",
            "ЭлИз" to "Электрические измерения",
            "ЭлМа" to "Электр. машины",
            "ЭлАп" to "Электр. аппараты",
            "ЭлПр" to "Электр. приборы",
            "ЭлОб" to "Электрооборудование механических транспортных средств",
            "ЭлСи" to "Электрон. сист. ТС",
            "ЭлСн" to "Электроснабжение организаций городского электрического транспорта",
            "ЭЛОс" to "Электрическое освещение",
            "ЭРЭи" to "Электрорадиоэлементы и устройства функциональной электроники",
            "РелЗ" to "Релейная защита",
            "САУЭ" to "Системы автоматического управления электроприводами",
            "СУЭП" to "Системы автоматического управления электроприводами",
            "СПТ" to "Преобразов. тех.",
            "ОЭлП" to "Основы электропривода",
            "НалД" to "Наладка и эксплуатация электроприводов",
            "ОПрР" to "Основы проектирования и расчета автоматизированных электроприводов промышленных и транспортных установок",
            "ТОиР" to "Техническое обслуживание и ремонт электрооборудования подстанций и тяговых сетей",
            "Диаг" to "Диагностика, техническое обслуживание и ремонт механических транспортных средств",
            "УстМ" to "Устройство механических транспортных средств",
            "Устр" to "Устройство контактной сети",
            "Конс" to "Конструктивная безопасность автомобиля",
            "ПрУс" to "Проект. уч. ТО",
            "ТягЭ" to "Тяговый электропривод",
            "Прои" to "Производство электронной техники",
            "Прое" to "Проектирование микроэлектронных устройств",
            "ОЦиф" to "Основы цифровой техники",
            "ОАлг" to "Основы алгоритмизации и программирования",
            "АнаЦ" to "Аналоговая и цифровая схемотехника",
            "ОМик" to "Микроэлектроника",
            "Обор" to "Оборудование производства микроэлектронных устройств",
            "НнКв" to "Наноэлектроника и приборы на квантовых эффектах",
            "НнМа" to "Наноэлектронные технологии и наноматериалы",
            "ОИмп" to "Основы импульсной и преобразовательной техники",
            "ОАвт" to "Основы автоматики и микропроцессорной техники",
            "ИспК" to "Испытания и контроль качества микроэлектронных устройств",
            "ТехО" to "Технология и оборудование термических вакуумно-элионных и плазмохимических процессов",
            "Техн" to "Технология производства микроэлектронных устройств",
            "ПолС" to "Полупроводниковая схемотехника",
        )

    @Test
    fun `сокращения преподавательского документа дают читаемые имена`() {
        for ((abbr, display) in teacherAbbrevToDisplay) {
            assertEquals("«$abbr»", display, SubjectFormatter.getShortName(abbr))
        }
    }

    @Test
    fun `студенческое сокращение КУРЧАС раскрывается в кураторский час`() {
        assertEquals("Кураторский час", SubjectFormatter.normalize("КУРЧАС"))
        assertEquals("Кур. час", SubjectFormatter.getShortName("КУРЧАС"))
        assertEquals("Кур. час", SubjectFormatter.getShortName("КУРЧ"))
        assertEquals("Кур. час", SubjectFormatter.getShortName("Кураторский час"))
        assertEquals("КУРЧ", SubjectFormatter.getAcronym("Кураторский час"))
    }

    @Test
    fun `после нормализации загрузчиком короткое имя не схлопывается обратно в сокращение`() {
        for (abbr in teacherAbbrevToDisplay.keys) {
            val displayed = SubjectFormatter.getShortName(SubjectFormatter.normalize(abbr))
            assertTrue("«$abbr» → пустое имя", displayed.isNotBlank())
            if (abbr != "ТОЭ") {
                assertNotEquals("«$abbr» → «$displayed»", abbr, displayed)
            }
        }
    }

    @Test
    fun `нормализованный предмет проходит через маппинг переименований`() {
        assertEquals("Физическая культура и здоровье", SubjectFormatter.normalize("ФизК"))
        assertEquals("Физическая культура и здоровье", SubjectFormatter.normalize("СМГр"))
        assertEquals(SubjectFormatter.normalize("ФизКулЗ"), SubjectFormatter.normalize("ФизК"))
    }

    @Test
    fun `неизвестное сокращение показывается как есть`() {
        assertEquals("ОПро", SubjectFormatter.getShortName("ОПро"))
        assertEquals("Прог", SubjectFormatter.getShortName("Прог"))
        assertEquals("ТехП", SubjectFormatter.getShortName("ТехП"))
        assertEquals("УстО", SubjectFormatter.getShortName("УстО"))
        assertEquals("ЫыЫы", SubjectFormatter.getShortName("ЫыЫы"))
    }

    @Test
    fun `маркер выездного занятия не становится предметом`() {
        assertEquals("", SubjectFormatter.normalize("Вые"))
        assertEquals("", SubjectFormatter.getShortName("Вые"))
    }

    @Test
    fun `строка с Вые не перетирает предмет в парсере`() {
        val text =
            "│ 1 НЕДЕЛЯ     ║ ПОНЕДЕЛЬНИК            05.10.2026                         ║\r\n" +
                "├──────────────╫────┼────╢\r\n" +
                "│Деревянко Т.Ф.║32Н │    ║\r\n" +
                "│              ║ТехО│    ║\r\n" +
                "│              ║Вые │    ║\r\n"
        val slots = TeacherScheduleParser.parsePlainText(text).values.flatten()
        assertEquals(1, slots.size)
        assertEquals("ТехО", slots.first().subject)
        assertEquals(listOf("32Н"), slots.first().groups)
        assertEquals("Вые", slots.first().room)
        assertEquals(
            "Технология и оборудование термических вакуумно-элионных и плазмохимических процессов",
            SubjectFormatter.normalize(slots.first().subject),
        )
    }

    @Test
    fun `в live-документе 05_10 нет предмета-маркера Вые`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/05.10.2026-raspisanie-prepodavatelej.doc")!!.readBytes()
        val subjects = TeacherScheduleParser.parse(bytes).values.flatten().map { it.subject }
        assertTrue("«Вые» не должно оставаться предметом", subjects.none { it == "Вые" })
    }

    @Test
    fun `Деревянко 32Н — выездное занятие сохраняет предмет`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/05.10.2026-raspisanie-prepodavatelej.doc")!!.readBytes()
        val parsed = TeacherScheduleParser.parse(bytes)
        val derevyanko = parsed.entries.first { it.key.startsWith("Деревянко") }.value
        val first = derevyanko.first { it.lessonNumber == 1 }
        assertEquals("ТехО", first.subject)
        assertEquals("Вые", first.room)
        assertEquals(
            "Технология и оборудование термических вакуумно-элионных и плазмохимических процессов",
            SubjectFormatter.normalize(first.subject),
        )
    }

    @Test
    fun `все сокращения live-документа 05_10 раскрываются в читаемые имена`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/05.10.2026-raspisanie-prepodavatelej.doc")!!.readBytes()
        val distinct =
            TeacherScheduleParser
                .parse(bytes)
                .values
                .flatten()
                .map { it.subject }
                .filter { it.isNotBlank() }
                .distinct()
        assertTrue("документ должен содержать предметы", distinct.isNotEmpty())
        val shownAsIs = setOf("ТОЭ", "ОПро", "Прог")
        for (raw in distinct) {
            if (raw in shownAsIs) continue
            val display = SubjectFormatter.getShortName(raw)
            assertTrue("«$raw» → «$display» нечитабельно", display.isNotBlank() && display != raw)
        }
    }

    @Test
    fun `все сокращения документа 16_09 раскрываются в читаемые имена`() {
        val bytes = javaClass.getResourceAsStream("/16.09.2026-raspisanie-prepodavatelej.doc")!!.readBytes()
        val distinct =
            TeacherScheduleParser
                .parse(bytes)
                .values
                .flatten()
                .map { it.subject }
                .filter { it.isNotBlank() }
                .distinct()
        assertTrue("документ должен содержать предметы", distinct.isNotEmpty())
        val shownAsIs = setOf("ТОЭ", "ОПро", "Прог", "УстО", "ТехП")
        for (raw in distinct) {
            if (raw in shownAsIs) continue
            val display = SubjectFormatter.getShortName(raw)
            assertTrue("«$raw» → «$display» нечитабельно", display.isNotBlank() && display != raw)
        }
    }

    @Test
    fun `маппинг teacher-урока в карточку сохраняет предмет для getShortName`() {
        val lesson = TeacherDayLesson(2, "ФизК", listOf("32Н"), "150", "09:35", "10:55", 575, 655)
        val entity = lesson.toLessonEntity("05.10.2026")
        assertEquals("ФизК", entity.subjectRaw)
        assertEquals("Физкультура", SubjectFormatter.getShortName(entity.subjectRaw))
    }
}
