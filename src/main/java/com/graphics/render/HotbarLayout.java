package com.graphics.render;

/**
 * Геометрия панели построек: где на экране каждый слот, — И для отрисовки ({@link HudRenderer}),
 * И для попадания мышью ({@code com.graphics.input.InputHandler}, другой пакет — поэтому класс
 * публичный). Одна формула на оба потребителя: подвинь слот на экране в этом файле — клик и
 * картинка не разъедутся, потому что второго места с той же арифметикой просто нет.
 *
 * <p>С Фазы 8 число слотов — параметр каждого метода, а не {@code BuildingType.values().length}:
 * хотбар теперь несёт НАСТРАИВАЕМЫЙ список закреплённых прототипов ({@code
 * com.graphics.input.InputHandler}'s собственное состояние), а не фиксированную панель на все
 * зарегистрированные здания сразу — при 60+ прототипах она физически не влезла бы. Полный список
 * — в {@link BuildMenuRenderer}.
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

    /** Суммарная ширина всей панели (слоты + зазоры между ними, без зазора по краям) при {@code slotCount} слотах. */
    public static float totalWidth(int slotCount) {
        return slotCount * SLOT_SIZE + (slotCount - 1) * SLOT_GAP;
    }

    /** X левого края слота {@code index} — панель отцентрована по ширине окна. */
    public static float slotX(int index, int screenWidth, int slotCount) {
        float startX = (screenWidth - totalWidth(slotCount)) / 2f;
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
     * @return индекс слота (0..{@code slotCount}-1), или {@code -1}, если мимо всех слотов
     */
    public static int hitTest(float screenX, float screenY, int screenWidth, int screenHeight, int slotCount) {
        float hudY = screenHeight - screenY;
        if (hudY < slotY() || hudY > slotY() + SLOT_SIZE) {
            return -1;
        }
        for (int i = 0; i < slotCount; i++) {
            float x = slotX(i, screenWidth, slotCount);
            if (screenX >= x && screenX <= x + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
