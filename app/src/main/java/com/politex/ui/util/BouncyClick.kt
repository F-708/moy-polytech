package com.politex.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Клик с пружинкой — единственный клик-модификатор приложения (~150 вызовов).
 *
 * A11y-контракт:
 * - [indication] больше не `null`: пользователь видит ripple-отклик на нажатие, а не только
 *   масштабирование (раньше кнопка «не отзывалась» — Nielsen #1, видимость статуса).
 * - [role] и [onClickLabel] уходят в семантику: TalkBack объявляет узел как «кнопка» и
 *   подсказывает действие, а не безымянную область (WCAG 4.1.2 Name, Role, Value).
 *   По умолчанию [Role.Button] — так ведёт себя подавляющее большинство вызовов (карточка,
 *   иконка, строка списка). Для переключателей и вкладок передавайте свой role явно:
 *   `Role.Checkbox`, `Role.Switch`, `Role.Tab`, `Role.RadioButton`.
 * - [minTouchTarget] поднимает площадь касания до платформенных 48dp
 *   (`Tokens.HitTarget`), не трогая визуальный размер там, где вызывающий код уже задал
 *   меньшее: модификаторы применяются снаружи внутрь, поэтому `.height(32.dp)` перед
 *   `bouncyClickable` остаётся 32dp, а узел без явного размера (текстовая кнопка, строка)
 *   получает нормальные 48dp.
 *
 * @param enabled выключенный узел не кликается и не анимируется.
 * @param scaleDown до какого масштаба сжимается при нажатии (по умолчанию 0.95).
 * @param role роль для TalkBack; `null` — не объявлять роль.
 * @param onClickLabel что произойдёт по нажатию («Открыть», «Выбрать группу»).
 * @param minTouchTarget гарантировать минимум 48dp касания (см. выше).
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    minTouchTarget: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "BouncyScaleAnimation",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (minTouchTarget) Modifier.minimumInteractiveComponentSize() else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}