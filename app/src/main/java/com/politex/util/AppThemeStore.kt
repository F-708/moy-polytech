package com.politex.util

import android.content.Context
import android.content.res.Configuration
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.AppearanceConfig
import com.politex.ui.theme.MpkPalette
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppearance
import com.politex.ui.theme.effectiveDark
import com.politex.ui.theme.resolvePalette
import kotlinx.coroutines.launch

/**
 * Хранилище настроек внешнего вида.
 *
 * Разделено на два слоя:
 *  - [readPrefs] / [effectivePalette] — чистые чтения (можно звать из виджетов и фоновых потоков);
 *  - [saveTheme] / [saveMode] / [saveTone] — запись + применение + обновление виджетов.
 *
 * Все enum-ы хранятся по строковому `id`, а не по `name`: R8 не может сломать настройки.
 */
object AppThemeStore {
    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_THEME = "app_theme"
    private const val KEY_MODE = "app_mode"
    private const val KEY_TONE = "app_tone"
    private const val LEGACY_KEY_DARK = "app_dark"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Системная тёмная тема по конфигурации ресурсов (без Activity и Compose). */
    fun isSystemDark(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Чистое чтение настройки с ленивой миграцией старого ключа `app_dark`.
     *
     * Миграция ничего не перезаписывает: старый ключ остаётся и работает как зеркало,
     * поэтому откат на предыдущую версию приложения не сбрасывает выбор пользователя.
     */
    fun readPrefs(context: Context): AppearanceConfig {
        val store = prefs(context)
        val mode = store.getString(KEY_MODE, null)?.let(ThemeMode::from)
            ?: when {
                !store.contains(LEGACY_KEY_DARK) -> ThemeMode.SYSTEM
                store.getBoolean(LEGACY_KEY_DARK, false) -> ThemeMode.DARK
                else -> ThemeMode.LIGHT
            }
        return AppearanceConfig(
            theme = AppTheme.from(store.getString(KEY_THEME, null)),
            mode = mode,
            tone = SurfaceTone.from(store.getString(KEY_TONE, null)),
        )
    }

    /**
     * Читает настройку и применяет её к глобальному состоянию.
     *
     * Возвращает пресет — сохранённая сигнатура нужна существующим вызовам
     * (`MainActivity`, экспорт бэкапа, экраны настроек).
     */
    fun load(context: Context): AppTheme {
        val config = readPrefs(context)
        applyAppearance(config.theme, config.mode, config.tone, isSystemDark(context))
        return config.theme
    }

    fun loadMode(context: Context): ThemeMode = readPrefs(context).mode

    fun loadTone(context: Context): SurfaceTone = readPrefs(context).tone

    /** Эффективная палитра без побочных эффектов: режим → тон → пресет. */
    fun effectivePalette(context: Context): MpkPalette {
        val config = readPrefs(context)
        return resolvePalette(config.theme, config.mode, config.tone, isSystemDark(context))
    }

    fun saveTheme(
        context: Context,
        theme: AppTheme,
    ) {
        prefs(context).edit().putString(KEY_THEME, theme.id).apply()
        applyAndRefresh(context)
    }

    fun saveMode(
        context: Context,
        mode: ThemeMode,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_MODE, mode.id)
            // Зеркало для обратной совместимости: старые сборки читают только app_dark.
            .putBoolean(LEGACY_KEY_DARK, mode == ThemeMode.DARK)
            .apply()
        applyAndRefresh(context)
    }

    fun saveTone(
        context: Context,
        tone: SurfaceTone,
    ) {
        prefs(context).edit().putString(KEY_TONE, tone.id).apply()
        applyAndRefresh(context)
    }

    /** Старое имя сохранения темы — оставлено для существующих вызовов. */
    fun save(
        context: Context,
        theme: AppTheme,
    ) = saveTheme(context, theme)

    /** Пишет всё сразу (используется восстановлением бэкапа). */
    fun saveAll(
        context: Context,
        config: AppearanceConfig,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_THEME, config.theme.id)
            .putString(KEY_MODE, config.mode.id)
            .putString(KEY_TONE, config.tone.id)
            .putBoolean(LEGACY_KEY_DARK, config.mode == ThemeMode.DARK)
            .apply()
        applyAndRefresh(context)
    }

    /** Эффективная тьма с учётом режима и системной темы. */
    fun isDark(context: Context): Boolean = effectiveDark(readPrefs(context).mode, isSystemDark(context))

    /**
     * Шим старого API. Явно включает светлую или тёмную тему (режим SYSTEM при этом снимается),
     * обновляет виджеты — раньше это делал только `save`, из-за чего виджеты жили в старой теме.
     */
    fun setDark(
        context: Context,
        dark: Boolean,
    ) {
        saveMode(context, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    private fun applyAndRefresh(context: Context) {
        load(context)
        // Виджеты — побочный эффект: любая ошибка платформы не должна ломать смену темы
        // (в т.ч. в Robolectric-тестах, где AppWidgetManager ведёт себя иначе).
        com.politex.widget.WidgetUpdateHelper.widgetScope.launch {
            runCatching {
                com.politex.widget.WidgetUpdateHelper.updateAllWidgets(context.applicationContext)
            }
        }
    }
}