package com.politex.weather

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Единственная точка входа обновления прогноза.
 *
 * Работает без WorkManager и новых разрешений: вызов из уже существующего
 * жизненного цикла экрана, сеть — только по [WeatherCache.shouldFetch], то есть
 * не чаще раза в час. Ошибка сети не меняет снимок и не показывает сообщений:
 * в приложении остаётся последняя погода, а слой просто рисует прошлое состояние.
 */
object WeatherHub {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchInFlight = AtomicBoolean(false)

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)

    /** Последний известный прогноз: экранный слой читает его как состояние. */
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    /**
     * Поднимает снимок из кэша и, если он протух, тихо обновляет его.
     *
     * Вызывается при каждом появлении главного экрана; лишних запросов нет —
     * [WeatherCache.shouldFetch] отсекает всё, что чаще раза в час.
     */
    fun ensureFresh(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()

        if (_snapshot.value == null) {
            _snapshot.value = WeatherStore.readSnapshot(app)
        }

        val cached = _snapshot.value
        val fetchedAt = WeatherStore.readFetchedAt(app) ?: cached?.fetchedAtMillis
        val lastAttempt = WeatherStore.readLastAttempt(app)
        if (!WeatherCache.shouldFetch(fetchedAt, lastAttempt, now)) return
        if (!fetchInFlight.compareAndSet(false, true)) return

        WeatherStore.writeLastAttempt(app, now)
        scope.launch {
            try {
                val fresh = OpenMeteoClient.fetch(now)
                if (fresh != null) {
                    WeatherStore.writeSnapshot(app, fresh)
                    _snapshot.value = fresh
                    // Виджеты читают тот же кэш: обновляем их сразу, а не до следующего алярма.
                    runCatching { com.politex.widget.WidgetUpdateHelper.updateAllWidgets(app) }
                }
            } catch (_: Exception) {
                // Сеть недоступна — оставляем прошлый снимок, экран молчит.
            } finally {
                fetchInFlight.set(false)
            }
        }
    }

    /** Статичный снимок без сети — для превью и тестов. */
    fun publishForPreview(snapshot: WeatherSnapshot?) {
        _snapshot.value = snapshot
    }

    /**
     * Разрешена ли анимация эмбиент-слоя прямо сейчас.
     *
     * Два стоп-сигнала: пользователь выключил анимации в системе
     * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) и включённый режим
     * энергосбережения. В обоих случаях рисуется один статичный кадр.
     */
    fun animationsAllowed(context: Context): Boolean {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val powerSave = runCatching {
            val manager = context.getSystemService(PowerManager::class.java)
            manager?.isPowerSaveMode == true
        }.getOrDefault(false)
        return effectsAnimationAllowed(
            animatorScaleZero = animatorScale == 0f,
            powerSaveMode = powerSave,
        )
    }
}