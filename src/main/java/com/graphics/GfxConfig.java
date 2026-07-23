package com.graphics;

/**
 * Числовые константы графики: размер окна, клетки, поля, пределы камеры.
 *
 * <p>Раньше эти значения жили в домене ({@code com.rustorio.core.Config}). Графика теперь
 * самостоятельна и не зависит от логики игры, поэтому нужные ей числа держит у себя. Когда
 * логика будет отстроена заново и обзаведётся своим размером мира, эти два источника при
 * желании можно будет свести — но графике для запуска чужой код больше не нужен.
 */
public final class GfxConfig {

    /** Размер поля в клетках — пока просто «сколько сетки рисуем». */
    public static final int GRID_W = 96;
    public static final int GRID_H = 64;

    /** Сторона клетки в пикселях мировой плоскости. */
    public static final float TILE = 34f;

    /** Размер окна по умолчанию. */
    public static final int WINDOW_W = 1280;
    public static final int WINDOW_H = 800;

    /**
     * Высота полос HUD сверху и снизу окна — И для отрисовки панелей ({@code HudRenderer}), И
     * для того, чтобы камера НЕ показывала мир под ними ({@code GameCamera}). Раньше камера
     * занимала окно целиком, а панели просто рисовались ПОВЕРХ карты — верхние клетки поля
     * оказывались навсегда закрыты подложкой панели, хоть построить там технически было можно
     * (клик всё равно проходил в мир, просто игрок не видел, куда кликает). Теперь у мира
     * меньше окно (см. {@link com.graphics.render.GameCamera#resize}), а панели рисуются в
     * освободившихся полосах — карта нигде не спрятана за интерфейсом.
     */
    public static final float HUD_TOP_HEIGHT = 112f;
    public static final float HUD_BOTTOM_HEIGHT = 106f;

    /** Скорость скролла камеры (пикселей окна в секунду). */
    public static final float CAMERA_PAN_SPEED = 700f;

    /** Пределы и шаг зума. */
    public static final float CAMERA_ZOOM_MIN = 0.4f;
    public static final float CAMERA_ZOOM_MAX = 3.5f;
    public static final float CAMERA_ZOOM_STEP = 1.15f;

    private GfxConfig() {
    }
}
