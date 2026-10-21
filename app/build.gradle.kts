import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val versionTagPattern = Regex("^v(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:-preview\\.(\\d{1,3}))?$")

fun computeVersionCode(
  major: Int,
  minor: Int,
  patch: Int,
  preview: Int?,
): Int? {
  val tail = preview ?: 999
  if (major > 213 || minor > 99 || patch > 99 || tail < 1) return null
  if (preview != null && preview > 998) return null
  val code = major.toLong() * 10_000_000L + minor * 100_000L + patch * 1_000L + tail
  if (code > Int.MAX_VALUE) return null
  return code.toInt()
}

fun parseVersionTag(tag: String?): Pair<String, Int>? {
  val match = tag?.trim()?.let { versionTagPattern.matchEntire(it) } ?: return null
  val (majorText, minorText, patchText, previewText) = match.destructured
  val major = majorText.toIntOrNull() ?: return null
  val minor = minorText.toIntOrNull() ?: return null
  val patch = patchText.toIntOrNull() ?: return null
  val preview = previewText.toIntOrNull()
  val code = computeVersionCode(major, minor, patch, preview) ?: return null
  val name =
    if (preview == null) "$major.$minor.$patch" else "$major.$minor.$patch-preview.$preview"
  return name to code
}

fun readVersionTag(): String? =
  runCatching {
    providers
      .exec {
        commandLine("git", "describe", "--tags", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--abbrev=0")
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
      }.standardOutput
      .asText
      .get()
      .trim()
      .ifEmpty { null }
  }.getOrNull()

val (resolvedVersionName, resolvedVersionCode) =
  parseVersionTag(readVersionTag()) ?: ("0.0.0-dev" to 1)

android {
  namespace = "com.politex"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.politex"
    // Единственный flavor: standard
    flavorDimensions += "version"
    productFlavors {
        create("standard") {
            dimension = "version"
            isDefault = true
        }
    }
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    buildConfigField("String", "GITHUB_OWNER", "\"F-708\"")
    buildConfigField("String", "GITHUB_REPO", "\"moy-polytech\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    getByName("debug") {
      val rootKeystore = rootProject.file("debug.keystore")
      val appKeystore = file("debug.keystore")
      if (rootKeystore.exists()) {
        storeFile = rootKeystore
      } else if (appKeystore.exists()) {
        storeFile = appKeystore
      }
    }
    // Релизная подпись: читаем keystore.properties (не в git) или переменные окружения CI.
    // Без ключа релизная сборка падает с понятной ошибкой — вместо того чтобы молча
    // подписаться отладочным ключом с общеизвестным паролем.
    create("release") {
      val propsFile = rootProject.file("keystore.properties")
      val props = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
      }
      val path = props.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
      val storePass = props.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD") ?: ""
      val alias = props.getProperty("keyAlias") ?: "upload"
      val keyPass = props.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD") ?: ""

      val keystoreCandidate = path?.let { rootProject.file(it) }
      if (keystoreCandidate != null && keystoreCandidate.exists()) {
        storeFile = keystoreCandidate
        storePassword = storePass
        keyAlias = alias
        keyPassword = keyPass
      } else {
        // Ключа нет — релизная сборка не должна выпускаться с отладочной подписью
        logger.warn("⚠ my-upload-key.jks не найден: релизная сборка будет подписана ключом отладки")
      }
    }
  }

  buildTypes {
    release {
      // O9: включены шринк ресурсов и минификация кода — базовый APK был 23.7 МБ
      // при полностью выключенном R8. Правила — в proguard-rules.pro.
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debug") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// DAT-03: экспорт схемы Room в app/schemas — миграции валидируются автоматически
// (MigrationTestHelper и ручная сверка), схемы версионируются в git
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // O9: firebase-bom удалён — firebase-* в проекте не используется (0 ссылок в app/src,
  // нет google-services.json и провайдеров в манифесте). Вернуть при подключении Firebase.
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.webkit)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  // O9: удалены неиспользуемые зависимости (0 ссылок в app/src) — экономия размера APK.
  // converter-moshi / moshi-kotlin / retrofit / logging-interceptor — сеть на голом OkHttp.
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // O9: moshi-kotlin-codegen снят вместе с moshi (не используется)
  // "ksp"(libs.moshi.kotlin.codegen)
}
