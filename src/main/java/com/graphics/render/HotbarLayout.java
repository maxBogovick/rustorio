package com.graphics.render;

import com.rustorio.domain.BuildingType;

/**
 * Геометрия панели построек: где на экране каждый слот, — И для отрисовки ({@link HudRenderer}),
 * И для попадания мышью ({@code com.graphics.input.InputHandler}, другой пакет — поэтому класс
 * публичный). Одна формула на оба потребителя: подвинь слот на экране в этом файле — клик и
 * картинка не разъедутся, потому что второго места с той же арифметикой просто нет.
 */
public final class HotbarLayout {

    /** Сторона одного слота в пикселях экрана. */
    public static final float SLOT_SIZE = 56f;
    /** Зазор между слотами. */
    public static final float SLOT_GAP = 8f;
    /** Отступ панели от нижнего края экрана. */
    public static final float MARGIN_BOTTOM = 14f;

    private HotbarLayout() {
    }

    /** Сколько слотов — по числу сортов построек. */
    public static int count() {
        return BuildingType.values().length;
    }

    /** Суммарная ширина всей панели (слоты + зазоры между ними, без зазора по краям). */
    public static float totalWidth() {
        return count() * SLOT_SIZE + (count() - 1) * SLOT_GAP;
    }

    /** X левого края слота {@code index} — панель отцентрована по ширине окна. */
    public static float slotX(int index, int screenWidth) {
        float startX = (screenWidth - totalWidth()) / 2f;
        return startX + index * (SLOT_SIZE + SLOT_GAP);
    }

    /** Y нижнего края любого слота — все слоты на одной высоте. */
    public static float slotY() {
        return MARGIN_BOTTOM;
    }

    /**
     * Какой слот под пикселем экрана {@code (screenX, screenY)} — координаты как отдаёт
     * {@code Gdx.input} (Y считается от ВЕРХА окна), а слоты рисуются в HUD-координатах (Y от
     * низа) — переворот сделан здесь, чтобы вызывающему не пришлось об этом помнить.
     *
     * @return индекс в {@link BuildingType#values()}, или {@code -1}, если мимо всех слотов
     */
    public static int hitTest(float screenX, float screenY, int screenWidth, int screenHeight) {
        float hudY = screenHeight - screenY;
        if (hudY < slotY() || hudY > slotY() + SLOT_SIZE) {
            return -1;
        }
        for (int i = 0; i < count(); i++) {
            float x = slotX(i, screenWidth);
            if (screenX >= x && screenX <= x + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
