package com.graphics;

import com.rustorio.domain.PatchOreLayout;

/**
 * Числовые константы графики: размер окна, клетки, поля, пределы камеры.
 *
 * <p>Раньше эти значения жили в домене ({@code com.rustorio.core.Config}). Графика стала
 * самостоятельной и держала своё собственное {@code GRID_W}/{@code GRID_H} — те же числа, что
 * {@code PatchOreLayout.STANDARD_WIDTH}/{@code STANDARD_HEIGHT}, но не связанные с ними ничем,
 * кроме совпадения. Тот момент, о котором говорил прежний комментарий («когда логика обзаведётся
 * своим размером мира, эти два источника можно будет свести») — это X-04 (DEV_TASKS.md): домен
 * уже свёл размер карты в одну публичную константу, так что графика теперь просто читает её,
 * а не хранит собственную копию, которая могла молча разойтись — числа совпадали только потому,
 * что оба места правили вручную и одновременно; единый источник истины убирает саму возможность
 * когда-нибудь забыть об одном из них.
 */
public final class GfxConfig {

    /** Размер поля в клетках — {@link PatchOreLayout#STANDARD_WIDTH}/{@link PatchOreLayout#STANDARD_HEIGHT}, не копия. */
    public static final int GRID_W = PatchOreLayout.STANDARD_WIDTH;
    public static final int GRID_H = PatchOreLayout.STANDARD_HEIGHT;

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
    // 166, not 112: D-03 added an inventory line, F-01 added an alerts line, and a live bug report
    // (right-click demolish/refund and hand-mining were completely undocumented in the hint text)
    // added a third hint line — see HudRenderer's javadocs — three more 18px text rows than this
    // constant originally budgeted for. Grown here, not by shrinking line spacing, so the same 4px
    // bottom margin the original layout had stays.
    public static final float HUD_TOP_HEIGHT = 166f;
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
