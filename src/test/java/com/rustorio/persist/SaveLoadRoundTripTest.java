package com.rustorio.persist;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Главная гарантия сохранений — <b>round-trip</b>: снять → в JSON → из JSON → восстановить →
 * снять снова обязано дать РОВНО ТО ЖЕ. Records-DTO дают {@code equals} бесплатно, поэтому
 * равенство снимков — это одновременно и проверка сохранения ПРЕДМЕТОВ: если хоть один груз
 * потерялся или удвоился, снимки разойдутся.
 */
class SaveLoadRoundTripTest {

    private final SaveService saver = new SaveService();
    private final LoadService loader = new LoadService();

    /**
     * Небольшая, но насыщенная фабрика: разные здания, направления, апгрейд и — главное —
     * ПРЕДМЕТЫ во всех местах, где они бывают (буфер печи/сборщика, груз ленты, развилка,
     * труба подземки, выход бура, счётчик ящика).
     */
    private static GameState populatedGame() {
        World world = World.generate(24, 24);
        GameState game = new GameState(world);

        world.place(1, 1, new Miner(Direction.EAST, Item.IRON_ORE));   // готовая руда на выходе
        world.place(2, 1, Building.create(Tool.BELT, Direction.EAST));
        world.place(3, 1, Building.create(Tool.BELT, Direction.EAST));
        world.place(4, 1, new Furnace(Direction.NORTH,
                Map.of(Item.IRON_ORE, 2), List.of(Item.IRON_PLATE)));  // буфер + готовое
        world.place(5, 5, new Assembler(Direction.WEST,
                Map.of(Item.IRON_PLATE, 1), List.of()));
        world.place(6, 6, new Splitter(Direction.SOUTH, Item.IRON_PLATE, 1));
        world.place(7, 7, new UndergroundBelt(Direction.EAST,
                List.of(Item.IRON_ORE, Item.GEAR)));                   // два предмета в трубе
        world.place(8, 8, Building.create(Tool.LAB, Direction.EAST));

        Chest chest = new Chest();
        world.place(9, 9, chest);
        for (int i = 0; i < 42; i++) {
            chest.accept(Item.IRON_PLATE);
        }

        // Груз на ленте — кладём ПОСЛЕ постройки лент (линия уже собрана).
        world.restoreBeltItem(2, 1, 0, Item.IRON_ORE);
        world.restoreBeltItem(3, 1, 1, Item.GEAR);

        game.balance().multiplySpeed(Tool.FURNACE, 1.5f);
        game.balance().setBeltSlotsPerTick(3);
        return game;
    }

    @Test
    void snapshotSurvivesJsonRoundTrip() {
        GameSnapshot original = saver.capture(populatedGame());
        assertEquals(original, loader.fromJson(saver.toJson(original)),
                "снимок после JSON туда-обратно должен совпасть");
    }

    @Test
    void restoreReproducesTheSameSnapshot() {
        GameSnapshot snapshot = saver.capture(populatedGame());

        GameState fresh = new GameState(World.generate(24, 24));
        loader.restore(fresh, snapshot);

        assertEquals(snapshot, saver.capture(fresh),
                "восстановление воспроизводит снимок до последнего предмета");
    }

    @Test
    void restoreOverwritesPreviousWorld() {
        GameState game = new GameState(World.generate(24, 24));
        game.world().place(20, 20, Building.create(Tool.CHEST, Direction.EAST));

        loader.restore(game, saver.capture(populatedGame()));

        assertEquals(9, buildingCount(game.world()), "старое поле стёрто, стоит только загруженное");
        assertInstanceOf(Chest.class, game.world().tile(9, 9).building());
    }

    @Test
    void itemsInBuildingsSurviveRestore() {
        GameState game = new GameState(World.generate(24, 24));
        loader.restore(game, saver.capture(populatedGame()));

        assertEquals(42, ((Chest) game.world().tile(9, 9).building()).items(), "счётчик ящика");
        assertEquals(2, ((Furnace) game.world().tile(4, 1).building())
                .stockSnapshot().get(Item.IRON_ORE), "буфер печи");
        assertEquals(Item.IRON_PLATE, ((Splitter) game.world().tile(6, 6).building()).heldItem(),
                "предмет в развилке");
        assertEquals(2, ((UndergroundBelt) game.world().tile(7, 7).building()).carriedItems().size(),
                "предметы в трубе подземки");
        assertEquals(2, game.world().belts().itemCount(), "груз на лентах вернулся");
        assertEquals(1.5f, game.balance().speed(Tool.FURNACE), "апгрейд пережил загрузку");
    }

    @Test
    void savesToFileAndReadsBack(@TempDir Path dir) throws IOException {
        GameState game = populatedGame();
        Path file = dir.resolve("save.json");

        saver.save(game, file);
        assertTrue(java.nio.file.Files.exists(file), "файл сохранения создан");

        GameState fresh = new GameState(World.generate(24, 24));
        loader.restore(fresh, loader.read(file));
        assertEquals(saver.capture(game), saver.capture(fresh), "через диск состояние совпало");
    }

    /**
     * Обратная совместимость: сейв версии 1 (без полей предметов) грузится кодом версии 2 без
     * ошибок — недостающие поля становятся пустыми. Это и есть миграция аддитивной схемы.
     */
    @Test
    void version1SaveLoadsUnderVersion2() {
        String v1Json = """
                {
                  "version": 1,
                  "width": 24, "height": 24,
                  "balance": { "speed": {}, "beltSlotsPerTick": 2, "undergroundReach": 4 },
                  "research": { "points": 7, "unlocked": ["FAST_BELT"] },
                  "buildings": [
                    { "type": "MINER", "x": 1, "y": 1, "dir": "EAST", "amount": 0 },
                    { "type": "CHEST", "x": 9, "y": 9, "dir": null, "amount": 42 }
                  ]
                }
                """;

        GameState game = new GameState(World.generate(24, 24));
        loader.restore(game, loader.fromJson(v1Json));

        assertEquals(2, buildingCount(game.world()), "оба здания из v1-сейва встали");
        assertEquals(42, ((Chest) game.world().tile(9, 9).building()).items());
        assertEquals(7, game.research().points(), "очки из v1-сейва восстановились");
    }

    private static int buildingCount(World world) {
        int[] count = {0};
        world.forEachBuilding((x, y, b) -> count[0]++);
        return count[0];
    }
}
