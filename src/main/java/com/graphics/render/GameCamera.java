package com.graphics.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.graphics.GfxConfig;

/**
 * Камера над полем: скролл, зум, видимый диапазон клеток.
 *
 * <p><b>Зачем она вообще.</b> Поле больше окна во много раз — без камеры видно только угол.
 * Камера делает окно «окошком» над большой картой.
 *
 * <p><b>Две системы координат.</b> Поле лежит в непрерывной пиксельной плоскости (Y-вверх,
 * клетка {@code (0,0)} — в ЛЕВОМ ВЕРХНЕМ углу); камера ездит по ней. HUD прибит к стеклу:
 * рисуется отдельной матрицей {@link #hudMatrix()}, которую зум и скролл не трогают.
 *
 * <p>Камера — «через что смотрим», а не «что в мире», поэтому логике игры она не нужна и про
 * неё не знает. Двигает её слой ввода.
 *
 * <p><b>Тонкая обёртка над {@link CameraViewport} (A1, CODE_REVIEW_2026-07-28.md).</b> Вся
 * арифметика (зум/скролл/{@link #pickTile}/{@link #visibleTiles}) живёт в {@link CameraViewport},
 * классе без единого типа libGDX — сюда вынесена ровно затем, чтобы её можно было гонять в
 * headless-тесте. Этот класс лишь синхронизирует {@link CameraViewport}'а с реальной {@link
 * OrthographicCamera} после каждой мутации, чтобы {@link #combined()}/{@link #hudMatrix()}
 * отдавали актуальные матрицы для отрисовки — то единственное, ради чего здесь вообще нужен
 * libGDX (и что делает ЭТОТ класс непроверяемым headless: {@code OrthographicCamera.update()}
 * зовёт нативный {@code Matrix4.prj}, которого нет вне настоящего оконного запуска).
 */
public final class GameCamera {

    private final OrthographicCamera cam = new OrthographicCamera();
    private final Matrix4 hudMatrix = new Matrix4();
    private final CameraViewport viewport;

    public GameCamera(int gridW, int gridH) {
        this.viewport = new CameraViewport(gridW, gridH);
        sync();
        hudMatrix.setToOrtho2D(0, 0, GfxConfig.WINDOW_W, GfxConfig.WINDOW_H);
    }

    /**
     * Подстроиться под новый размер окна (вид не «расплющивается», HUD не съезжает).
     *
     * <p>Вьюпорт КАМЕРЫ (то, что видит игрок как «мир») уже полного окна на высоту HUD сверху и
     * снизу ({@link GfxConfig#HUD_TOP_HEIGHT}/{@link GfxConfig#HUD_BOTTOM_HEIGHT}) — раньше
     * камера занимала окно целиком, а панели HUD просто рисовались ПОВЕРХ карты: верхние клетки
     * поля были навсегда закрыты подложкой панели, хоть построить там технически было можно
     * (клик проходил в мир вслепую). {@link com.graphics.render.Renderer} рисует мир в этот
     * узкий вьюпорт через {@code glViewport}, а HUD — по-прежнему в полный {@link #hudMatrix},
     * который insets не касаются.
     */
    public void resize(int width, int height) {
        viewport.resize(width, height);
        hudMatrix.setToOrtho2D(0, 0, width, height);
        sync();
    }

    /**
     * Сдвинуть камеру на {@code (dx, dy)} ЭКРАННЫХ пикселей (Y-вверх).
     * Умножение на зум — чтобы драг «прилипал» к миру на любом зуме.
     */
    public void pan(float dx, float dy) {
        viewport.pan(dx, dy);
        sync();
    }

    /**
     * Зум на {@code steps} щелчков колеса, ЯКОРЕМ в точке под курсором:
     * точка под мышью остаётся под мышью — так зумят все карты.
     *
     * @param screenX пиксель мыши от левого края окна
     * @param screenY пиксель мыши от ВЕРХА окна
     * @param steps   положительное — отдалить, отрицательное — приблизить
     */
    public void zoomAt(float screenX, float screenY, float steps) {
        viewport.zoomAt(screenX, screenY, steps);
        sync();
    }

    /**
     * Диапазон клеток, попадающих в кадр (включительно, обрезан по полю).
     * Это culling: рендер обходит ТОЛЬКО эти клетки, а не всё поле.
     */
    public TileRange visibleTiles(int gridW) {
        return viewport.visibleTiles(gridW);
    }

    /**
     * Клетка поля под пикселем мыши. Мышь живёт в пикселях окна, клетка — в мире; перевести
     * одно в другое может только камера, ведь она знает, куда смотрит окно и с каким зумом.
     *
     * @param screenX пиксель мыши от левого края окна
     * @param screenY пиксель мыши от ВЕРХА окна (как отдаёт {@code Gdx.input})
     */
    public TilePos pickTile(float screenX, float screenY) {
        return viewport.pickTile(screenX, screenY);
    }

    /** Матрица «мир → экран» для проходов отрисовки поля. */
    public Matrix4 combined() {
        return cam.combined;
    }

    /** Матрица для HUD: обычные оконные пиксели, зум и скролл её не трогают. */
    public Matrix4 hudMatrix() {
        return hudMatrix;
    }

    /**
     * Переносит текущее состояние {@link #viewport} (позиция/зум/размер вьюпорта — вся
     * арифметика посчитана ТАМ) в реальную {@link OrthographicCamera} и просит её пересчитать
     * матрицы. Единственное место, где этот класс трогает {@code cam} напрямую.
     */
    private void sync() {
        cam.viewportWidth = viewport.viewportWidth();
        cam.viewportHeight = viewport.viewportHeight();
        cam.zoom = viewport.zoom();
        cam.position.set(viewport.x(), viewport.y(), 0);
        cam.update();
    }
}
