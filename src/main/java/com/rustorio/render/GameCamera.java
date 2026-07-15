package com.rustorio.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.rustorio.core.Config;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import org.jspecify.annotations.Nullable;

/**
 * Камера над миром: скролл, зум, перевод «пиксель мыши → клетка».
 *
 * <p><b>Зачем она вообще.</b> Мир чанковый и больше окна во много раз — без камеры
 * игрок видит только угол поля. Камера делает окно «окошком» над большой картой.
 *
 * <p><b>Две системы координат.</b> Мир лежит в непрерывной пиксельной плоскости
 * (Y-вверх, клетка {@code (0,0)} — в ЛЕВОМ ВЕРХНЕМ углу карты, как и раньше); камера
 * ездит по этой плоскости. HUD же прибит к стеклу: он рисуется отдельной матрицей
 * {@link #hudMatrix()}, которую зум и скролл не трогают.
 *
 * <p><b>Кто ею управляет.</b> Состояние камеры — «через что смотрим», а не «что
 * происходит в мире», поэтому она живёт в {@code render} и НЕ входит в
 * {@link com.rustorio.game.GameState}: симуляция и тесты про неё не знают. Двигает её
 * слой ввода (ему можно знать про рендер — стрелка зависимостей смотрит вниз).
 */
public final class GameCamera {

    private final OrthographicCamera cam = new OrthographicCamera();
    private final Matrix4 hudMatrix = new Matrix4();
    /** Размер мира в пикселях мировой плоскости. */
    private final float worldW;
    private final float worldH;
    private final int gridH;
    private final Vector3 tmp = new Vector3();

    public GameCamera(int gridW, int gridH) {
        this.gridH = gridH;
        this.worldW = gridW * Config.TILE;
        this.worldH = gridH * Config.TILE;
        resize(Config.WINDOW_W, Config.WINDOW_H);
        // Стартуем над левым верхним углом карты — там стартовые залежи руды.
        cam.position.set(cam.viewportWidth / 2f, worldH - cam.viewportHeight / 2f, 0);
        clampAndUpdate();
    }

    /** Подстроиться под новый размер окна (вид не «расплющивается», HUD не съезжает). */
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        hudMatrix.setToOrtho2D(0, 0, width, height);
        clampAndUpdate();
    }

    /**
     * Сдвинуть камеру на {@code (dx, dy)} ЭКРАННЫХ пикселей (Y-вверх).
     * Умножение на зум — чтобы драг «прилипал» к миру: на любом зуме точка мира
     * следует за курсором один в один.
     */
    public void pan(float dx, float dy) {
        cam.position.add(dx * cam.zoom, dy * cam.zoom, 0);
        clampAndUpdate();
    }

    /**
     * Зум на {@code steps} щелчков колеса, ЯКОРЕМ в точке под курсором:
     * клетка под мышью остаётся под мышью — так зумят все карты, и рука это ждёт.
     *
     * @param screenX пиксель мыши от левого края окна
     * @param screenY пиксель мыши от ВЕРХА окна (как отдаёт {@code Gdx.input})
     * @param steps   положительное — отдалить, отрицательное — приблизить
     */
    public void zoomAt(float screenX, float screenY, float steps) {
        unproject(screenX, screenY);
        float anchorX = tmp.x;
        float anchorY = tmp.y;
        float factor = (float) Math.pow(Config.CAMERA_ZOOM_STEP, steps);
        cam.zoom = clamp(cam.zoom * factor, Config.CAMERA_ZOOM_MIN, Config.CAMERA_ZOOM_MAX);
        cam.update();
        unproject(screenX, screenY);
        cam.position.add(anchorX - tmp.x, anchorY - tmp.y, 0);
        clampAndUpdate();
    }

    /** Клетка мира под пикселем мыши, если курсор над полем. */
    public @Nullable Cell pickTile(float screenX, float screenY, World world) {
        unproject(screenX, screenY);
        int gx = (int) Math.floor(tmp.x / Config.TILE);
        int gy = gridH - 1 - (int) Math.floor(tmp.y / Config.TILE);
        return world.inBounds(gx, gy) ? new Cell(gx, gy) : null;
    }

    /**
     * Диапазон клеток, попадающих в кадр (включительно, уже обрезан по полю).
     * Это и есть culling: рендер обходит ТОЛЬКО эти клетки, а не всё поле —
     * на большой карте разница в десятки раз.
     */
    public TileRange visibleTiles(int gridW) {
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        int minX = (int) Math.floor((cam.position.x - halfW) / Config.TILE);
        int maxX = (int) Math.floor((cam.position.x + halfW) / Config.TILE);
        // Верх экрана (большой мировой Y) — это МАЛЫЙ номер строки: строки считаются сверху.
        int minY = gridH - 1 - (int) Math.floor((cam.position.y + halfH) / Config.TILE);
        int maxY = gridH - 1 - (int) Math.floor((cam.position.y - halfH) / Config.TILE);
        return new TileRange(
                Math.max(minX, 0), Math.max(minY, 0),
                Math.min(maxX, gridW - 1), Math.min(maxY, gridH - 1));
    }

    /** Матрица «мир → экран» для проходов отрисовки мира. */
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
     * <p>Считаем сами, а не через {@code cam.unproject}: тот в ЛЮБОЙ перегрузке лезет
     * в глобальный {@code Gdx.graphics} за высотой окна, которого нет в тестах без
     * окна. Для ортографической камеры перевод — две строки арифметики: пиксель
     * относительно центра окна, умноженный на зум, плюс позиция камеры. Мышиный Y
     * растёт вниз, мировой — вверх, отсюда {@code (vh/2 - screenY)}.
     */
    private void unproject(float screenX, float screenY) {
        tmp.set(cam.position.x + (screenX - cam.viewportWidth / 2f) * cam.zoom,
                cam.position.y + (cam.viewportHeight / 2f - screenY) * cam.zoom, 0);
    }

    /**
     * Не дать укатить камеру в пустоту: центр держится в пределах карты.
     * Если по оси видно больше, чем вся карта, — карта просто центрируется.
     */
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
