# O9: правила для R8 (isMinifyEnabled = true в release).
# Компоненты, инстанцируемые системой по имени из манифеста — без -keep R8 их удалит/переименует.
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-keep public class * extends android.content.ContentProvider

# O9 (важно): имена enum-констант сохраняются в SharedPreferences как строки
# (CountdownMode / WidgetStyle / BellCountdownNotifier.Scope|Theme|Style — .load() ищет по it.name).
# Без -keep R8 переименует константы и сохранённые настройки пользователей перестанут читаться.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries();
    <fields>;
}

# Room: DAO-интерфейсы и entity — кодоген, но имена таблиц/колонок должны уцелеть
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp (consumer-rules обычно есть, дублируем ключевое)
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Сохранить информацию о строках для читаемых стектрейсов в релизе
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile