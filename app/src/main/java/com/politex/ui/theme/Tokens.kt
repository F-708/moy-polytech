package com.politex.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Tokens {

    // Минимальный тач-таргет — 48dp по рекомендации Android/Material
    // (было 44dp: на 4dp меньше платформенного минимума, ловилось во всех
    // иконочных кнопках, строках списков и чипах экранов).
    val HitTarget = 48.dp

    val ButtonHeight = 48.dp

    val SearchHeight = 48.dp

    // Чип на экране (фильтры, этапы маршрута, курсы) — тоже 48dp:
    // "height(Tokens.ChipScreen)" ставится как фиксированная высота, т.е. это
    // реальная площадь касания, а не только визуальный размер.
    val ChipScreen = 48.dp

    // Плотный чип внутри форм/диалогов (тип задания, время напоминания,
    // округление оценки): сознательно оставлен компактным, чтобы не ломать
    // плотные строки ввода. Касание расширяет bouncyClickable
    // (minimumInteractiveComponentSize), где это позволяет родитель.
    val ChipForm = 32.dp

    // Чипы-фильтры на списках (тип задания на экране «Задания», этапы маршрута):
    // заказчик попросил вернуть плотность шестого превью — 44dp вместо 48dp.
    // Горизонтальный зазор между чипами остаётся 6dp, вертикальный запас строки
    // даёт сама карточка списка.
    val ChipFilter = 44.dp

    val IconHit = 48.dp

    val ButtonText = 12.sp

    val ChipText = 11.sp

    val SectionHeader = 12.sp
}