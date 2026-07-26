package com.graphics.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
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
 */
public final class GameCamera {

    /**
     * Пол для вьюпорта камеры (в точках). {@code height - HUD_TOP_HEIGHT - HUD_BOTTOM_HEIGHT}
     * уходит в минус, как только окно (оно {@code setResizable(true)}, без
     * {@code setWindowSizeLimits}) становится ниже суммы высот HUD-панелей — тогда
     * {@link com.graphics.render.Renderer} передаёт отрицательную высоту в {@code glViewport}
     * (тот отвечает {@code GL_INVALID_VALUE} и не рисует мир), а {@link #axisClamp} с
     * отрицательным {@code halfView} расширяет допустимый диапазон камеры вместо того, чтобы его
     * ограничивать.
     */
    private static final float MIN_VIEWPORT_HEIGHT = 64f;

    private final OrthographicCamera cam = new OrthographicCamera();
    private final Matrix4 hudMatrix = new Matrix4();
    /** Размер поля в пикселях. */
    private final float worldW;
    private final float worldH;
    private final int gridH;
    private final Vector3 tmp = new Vector3();

    public GameCamera(int gridW, int gridH) {
        this.gridH = gridH;
        this.worldW = gridW * GfxConfig.TILE;
        this.worldH = gridH * GfxConfig.TILE;
        resize(GfxConfig.WINDOW_W, GfxConfig.WINDOW_H);
        // Стартуем над левым верхним углом карты.
        cam.position.set(cam.viewportWidth / 2f, worldH - cam.viewportHeight / 2f, 0);
        clampAndUpdate();
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
        cam.viewportWidth = Math.max(1f, width);
        cam.viewportHeight = Math.max(MIN_VIEWPORT_HEIGHT,
                height - GfxConfig.HUD_TOP_HEIGHT - GfxConfig.HUD_BOTTOM_HEIGHT);
        hudMatrix.setToOrtho2D(0, 0, width, height);
        clampAndUpdate();
    }

    /**
     * Сдвинуть камеру на {@code (dx, dy)} ЭКРАННЫХ пикселей (Y-вверх).
     * Умножение на зум — чтобы драг «прилипал» к миру на любом зуме.
     */
    public void pan(float dx, float dy) {
        cam.position.add(dx * cam.zoom, dy * cam.zoom, 0);
        clampAndUpdate();
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
        unproject(screenX, screenY);
        float anchorX = tmp.x;
        float anchorY = tmp.y;
        float factor = (float) Math.pow(GfxConfig.CAMERA_ZOOM_STEP, steps);
        cam.zoom = clamp(cam.zoom * factor, GfxConfig.CAMERA_ZOOM_MIN, GfxConfig.CAMERA_ZOOM_MAX);
        cam.update();
        unproject(screenX, screenY);
        cam.position.add(anchorX - tmp.x, anchorY - tmp.y, 0);
        clampAndUpdate();
    }

    /**
     * Диапазон клеток, попадающих в кадр (включительно, обрезан по полю).
     * Это culling: рендер обходит ТОЛЬКО эти клетки, а не всё поле.
     */
    public TileRange visibleTiles(int gridW) {
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        int minX = (int) Math.floor((cam.position.x - halfW) / GfxConfig.TILE);
        int maxX = (int) Math.floor((cam.position.x + halfW) / GfxConfig.TILE);
        // Верх экрана (большой мировой Y) — это МАЛЫЙ номер строки: строки считаются сверху.
        int minY = gridH - 1 - (int) Math.floor((cam.position.y + halfH) / GfxConfig.TILE);
        int maxY = gridH - 1 - (int) Math.floor((cam.position.y - halfH) / GfxConfig.TILE);
        return new TileRange(
                Math.max(minX, 0), Math.max(minY, 0),
                Math.min(maxX, gridW - 1), Math.min(maxY, gridH - 1));
    }

    /**
     * Клетка поля под пикселем мыши. Мышь живёт в пикселях окна, клетка — в мире; перевести
     * одно в другое может только камера, ведь она знает, куда смотрит окно и с каким зумом.
     *
     * @param screenX пиксель мыши от левого края окна
     * @param screenY пиксель мыши от ВЕРХА окна (как отдаёт {@code Gdx.input})
     */
    public TilePos pickTile(float screenX, float screenY) {
        unproject(screenX, screenY);
        int gx = (int) Math.floor(tmp.x / GfxConfig.TILE);
        int gy = gridH - 1 - (int) Math.floor(tmp.y / GfxConfig.TILE);
        return new TilePos(gx, gy);
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
     * Пиксель окна → точка мировой плоскости (результат — в {@link #tmp}).
     *
     * <p>Считаем сами, а не через {@code cam.unproject}: тот лезет в глобальный
     * {@code Gdx.graphics} за высотой окна. Для ортокамеры перевод — две строки арифметики.
     *
     * <p>{@code screenY} приходит «сырым» — от {@code Gdx.input}, считая от ВЕРХА ВСЕГО окна.
     * Вьюпорт камеры начинается не с самого верха окна, а с отступом в {@link
     * GfxConfig#HUD_TOP_HEIGHT} (там рисуется верхняя панель) — вычитаем его, чтобы 0 в формуле
     * ниже означал «верх вьюпорта камеры», а не «верх окна».
     */
    private void unproject(float screenX, float screenY) {
        float viewportY = screenY - GfxConfig.HUD_TOP_HEIGHT;
        tmp.set(cam.position.x + (screenX - cam.viewportWidth / 2f) * cam.zoom,
                cam.position.y + (cam.viewportHeight / 2f - viewportY) * cam.zoom, 0);
    }

    /** Не дать укатить камеру в пустоту: центр держится в пределах карты. */
    private void clampAndUpdate() {
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        cam.position.x = axisClamp(cam.position.x, halfW, worldW);
        cam.position.y = axisClamp(cam.position.y, halfH, worldH);
        cam.update();
    }

    private static float axisClamp(float pos, float halfView, float worldSpan) {
        if (halfView * 2 >= worldSpan) {
            return worldSpan / 2f; // вид шире мира — центрируем
        }
        return clamp(pos, halfView, worldSpan - halfView);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
