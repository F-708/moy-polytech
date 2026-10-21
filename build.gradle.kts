// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  // F3/BSP-07: единый стиль кода автоформатом (заказчик: «подгон под один стиль»).
  // Правило: spotlessApply отдельным коммитом «форматирование, без изменения логики».
  id("com.diffplug.spotless") version "7.0.2"
}

spotless {
  kotlin {
    target("app/src/**/*.kt")
    // F3: max_line_length off — SQL/regex-литералы атомарны, перенос = риск регресса
    ktlint("1.5.0").editorConfigOverride(mapOf("max_line_length" to "off"))
  }
}
