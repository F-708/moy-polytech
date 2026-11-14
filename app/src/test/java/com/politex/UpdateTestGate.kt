package com.politex

import org.junit.Assume

/**
 * Гейт update-тестов по флейвору.
 *
 * Тесты автообновления написаны под standard: там секция обновлений включена
 * (`UPDATE_ENABLED_FLAVORS`) и applicationId без суффикса. В admin/adminplus
 * обновления отключены намеренно — там эти тесты не «падают», а неприменимы,
 * поэтому пропускаем их через [Assume], а не чиним хардкоды под каждый флейвор.
 */
fun assumeUpdatesFlavor() {
    Assume.assumeTrue(
        "в этом флейворе обновления отключены — тест неприменим",
        com.politex.update.AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR),
    )
}
