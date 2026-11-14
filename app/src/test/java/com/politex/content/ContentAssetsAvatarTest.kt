package com.politex.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Старые сгенерированные «аватарки» (файлы `*_avatar.jpg`) принимались за настоящие
 * фото профиля (Дубровский, Якубецкая, Ефимчик и др.). Заказчик (2026-11-13): вместо
 * них показываем новую заглушку «нет фото».
 */
class ContentAssetsAvatarTest {
    @Test
    fun `старые сгенерированные аватарки распознаются как заглушки`() {
        assertTrue(ContentAssets.isGeneratedAvatar("030_Dubrovskiy_Dmitriy_Viktorovich_avatar.jpg"))
        assertTrue(ContentAssets.isGeneratedAvatar("106_Yakubetskaya_Elena_Viktorovna_avatar.jpg"))
        assertTrue(ContentAssets.isGeneratedAvatar("033_Efimchik_Olga_Vladimirovna_avatar.jpg"))
    }

    @Test
    fun `настоящие фото и пустое имя не считаются аватарками`() {
        assertFalse(ContentAssets.isGeneratedAvatar("001_Avtushko_Natalya_Aleksandrovna.jpg"))
        assertFalse(ContentAssets.isGeneratedAvatar("075_Popovskaya_Natalya_Vyacheslavovna.jpg"))
        assertFalse(ContentAssets.isGeneratedAvatar(""))
        assertFalse(ContentAssets.isGeneratedAvatar("avatar.jpg"))
    }
}
