package com.graphics.render;

import com.rustorio.domain.building.BuildingImage;

/**
 * Что рисует {@link PageViewRenderer}: картинка, которую отдало здание, и на сколько исходных
 * пикселей она прокручена. {@code null} в {@link HudState#pageView()} значит «просмотр закрыт».
 *
 * <p>Одна запись, а не два поля в {@link HudState}, по той же причине, по которой {@code
 * SettingsModalView} — одна: открытость просмотра и его содержимое всегда меняются вместе, и
 * разложенные по отдельным компонентам они позволяли бы описать состояние, которого не бывает,
 * — прокрутку без картинки.
 *
 * @param title подпись окна — здание называет себя само, движок про страницы ничего не знает
 * @param image пиксели, отданные зданием; та же ссылка между кадрами, пока картинка не сменилась
 * @param scroll прокрутка в пикселях ИСХОДНОЙ картинки (см. {@link PageViewLayout})
 */
public record PageView(String title, BuildingImage image, int scroll) {
}
