package com.politex.ui.changelog

enum class WhatsNewIcon {
    STOPWATCH,
    APP_UPDATE,
    SPEED,
    SILENT_DATA,
}

data class WhatsNewItem(
    val icon: WhatsNewIcon,
    val text: String,
)

data class WhatsNewEntry(
    val versionCode: Int,
    val versionName: String,
    val items: List<WhatsNewItem>,
)

val WhatsNewEntries: List<WhatsNewEntry> =
    listOf(
        WhatsNewEntry(
            versionCode = 20001999,
            versionName = "2.0.1",
            items =
                listOf(
                    WhatsNewItem(WhatsNewIcon.STOPWATCH, "Виджеты-секундомеры работают без строки «до звонка» и переживают ночь"),
                    WhatsNewItem(WhatsNewIcon.APP_UPDATE, "Обновление приложения прямо из приложения — кнопка «Установить»"),
                    WhatsNewItem(WhatsNewIcon.SPEED, "Расписание загружается в 2–3 раза быстрее"),
                    WhatsNewItem(WhatsNewIcon.SILENT_DATA, "Данные колледжа обновляются автоматически и незаметно"),
                ),
        ),
    )
