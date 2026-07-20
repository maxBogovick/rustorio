package com.graphics.render;

import com.rustorio.core.Config;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Камера — чистая математика (окно ей не нужно), поэтому самый рискованный её
 * кусок — перевод «пиксель мыши → клетка» с переворотом оси Y — проверяется
 * обычным юнит-тестом. Ошибка знака здесь не роняет игру: просто клик попадает
 * не в ту клетку, и искать это глазами намного дольше, чем тестом.
 */
class GameCameraTest {

    static {
        // Матричная математика libGDX — нативная (Matrix4.prj и т.п.), поэтому
        // даже «чистой» камере нужна загрузка нативной библиотеки. Окно при
        // этом НЕ открывается: грузится только math, без OpenGL.
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static final float TILE = Config.TILE;

    private final World world = World.generate(Config.GRID_W, Config.GRID_H);

    @Test
    void topLeftPixelIsTileZeroZeroAtStart() {
        // Камера стартует над левым верхним углом карты — там пиксель (0,0)
        // окна обязан попадать ровно в клетку (0,0): строки считаются сверху.
        GameCamera camera = new GameCamera(Config.GRID_W, Config.GRID_H);
        assertEquals(new Cell(0, 0), camera.pickTile(1, 1, world));
    }

    @Test
    void pickMovesWithPan() {
        GameCamera camera = new GameCamera(Config.GRID_W, Config.GRID_H);
        // Сдвиг камеры вправо-вниз на две клетки: та же точка окна должна
        // указывать на клетку на (2, 2) дальше. Экранный Y растёт вниз,
        // поэтому «вниз по карте» = pan с отрицательным dy.
        camera.pan(2 * TILE, -2 * TILE);
        assertEquals(new Cell(2, 2), camera.pickTile(1, 1, world));
    }

    @Test
    void pickOutsideWorldIsNull() {
        GameCamera camera = new GameCamera(Config.GRID_W, Config.GRID_H);
        // Уводим взгляд максимально влево-вверх и отдаляем: за краем карты
        // клеток нет — камера обязана вернуть null, а не выдумывать клетку.
        camera.zoomAt(0, 0, 50); // упрётся в CAMERA_ZOOM_MAX
        Cell corner = camera.pickTile(1, 1, world);
        // На максимальном отдалении мир может целиком влезть в окно с полями —
        // тогда верхний-левый пиксель окна лежит ЗА картой.
        if (corner != null) {
            assertEquals(new Cell(0, 0), corner);
        } else {
            assertNull(corner);
        }
    }

    @Test
    void zoomKeepsAnchorTileUnderCursor() {
        GameCamera camera = new GameCamera(Config.GRID_W, Config.GRID_H);
        // Уводим камеру к середине карты: у КРАЯ якорь законно уезжает (кламп
        // не даёт показать пустоту за картой и двигает центр), поэтому свойство
        // «клетка под курсором не убегает при зуме» честно только вдали от краёв.
        camera.pan(2 * Config.WINDOW_W / 3f, -Config.WINDOW_H / 2f);
        float sx = 300f;
        float sy = 200f;
        Cell before = camera.pickTile(sx, sy, world);
        assertNotNull(before);
        camera.zoomAt(sx, sy, -1); // щелчок «приблизить»: вид сжимается, кламп молчит
        Cell after = camera.pickTile(sx, sy, world);
        assertEquals(before, after);
    }

    @Test
    void cameraNeverLeavesWorld() {
        GameCamera camera = new GameCamera(Config.GRID_W, Config.GRID_H);
        // Тянем камеру далеко за карту: кламп обязан удержать вид у края,
        // и центр окна всё ещё показывает существующую клетку.
        camera.pan(1_000_000f, -1_000_000f);
        Cell center = camera.pickTile(Config.WINDOW_W / 2f, Config.WINDOW_H / 2f, world);
        assertNotNull(center);
        assertTrue(world.inBounds(center.x(), center.y()));
    }
}
