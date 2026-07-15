package com.rustorio.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.core.Tech;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Belt;
import com.rustorio.model.BeltSegment;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Lab;
import com.rustorio.model.Miner;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Снимает состояние игры в {@link GameSnapshot} и пишет его в JSON-файл.
 *
 * <p><b>Роль в паттерне Memento.</b> Игра — «создатель снимка» (originator), {@code
 * GameSnapshot} — снимок (memento), а этот сервис вместе с {@link LoadService} — «хранитель»
 * (caretaker): он умеет достать снимок и убрать его на диск, но НЕ лезет в приватную кухню
 * домена — только через его открытые геттеры. Так формат файла отвязан от внутреннего
 * устройства игры.
 *
 * <p>Файловый ввод-вывод здесь на {@code java.nio}, без libGDX: слой persist остаётся
 * тестируемым без окна, а определять путь к файлу — забота вызывающего (слоя ввода).
 */
public final class SaveService {

    /** {@link ObjectMapper} потокобезопасен после настройки — один на всё приложение. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Снять состояние игры в плоский снимок (ничего не пишет на диск). */
    public GameSnapshot capture(GameState game) {
        World world = game.world();

        Map<Tool, Float> speed = new EnumMap<>(Tool.class);
        for (Tool tool : Tool.values()) {
            speed.put(tool, game.balance().speed(tool));
        }
        BalanceDto balance = new BalanceDto(
                speed, game.balance().beltSlotsPerTick(), game.balance().undergroundReach());

        // Открытые технологии — по фиксированному порядку (ordinal), чтобы JSON был стабилен.
        List<Tech> unlocked = new ArrayList<>(game.research().unlocked());
        unlocked.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        ResearchDto research = new ResearchDto(game.research().points(), unlocked);

        List<BuildingDto> buildings = new ArrayList<>();
        world.forEachBuilding((x, y, building) -> buildings.add(toDto(x, y, building)));

        return new GameSnapshot(GameSnapshot.SCHEMA_VERSION,
                world.width(), world.height(), balance, research, buildings,
                captureBeltItems(world));
    }

    /**
     * Груз всех транспортных линий как плоский список «клетка + позиция + предмет».
     *
     * <p>Слот внутри линии глобален (от её хвоста); клетку и позицию в ней выводим делением
     * на число слотов в клетке. Так предмет привяжется к абсолютной клетке и переживёт
     * пересборку линий при загрузке.
     */
    private static List<BeltItemDto> captureBeltItems(World world) {
        int slots = Config.SLOTS_PER_TILE;
        List<BeltItemDto> out = new ArrayList<>();
        for (BeltSegment segment : world.belts().segments()) {
            List<Cell> tiles = segment.tiles();
            for (Map.Entry<Integer, Item> entry : segment.itemSlots()) {
                int slot = entry.getKey();
                Cell cell = tiles.get(slot / slots);
                out.add(new BeltItemDto(cell.x(), cell.y(), slot % slots, entry.getValue()));
            }
        }
        return out;
    }

    /**
     * Один здание → его снимок. {@code switch} исчерпывающий по sealed-типу {@link Building}:
     * добавишь новое здание — компилятор потребует ветку и здесь, забыть его в сохранении
     * будет нельзя.
     */
    private static BuildingDto toDto(int x, int y, Building building) {
        return switch (building) {
            case Miner m -> new BuildingDto(Tool.MINER, x, y, m.dir(),
                    0, null, null, null, m.outputItem().orElse(null));
            case Belt b -> new BuildingDto(Tool.BELT, x, y, b.dir(),
                    0, null, null, null, null); // груз ленты — в общем списке beltItems
            case Furnace f -> new BuildingDto(Tool.FURNACE, x, y, f.dir(),
                    0, new MachineDto(f.stockSnapshot(), f.readySnapshot()), null, null, null);
            case Chest c -> new BuildingDto(Tool.CHEST, x, y, null,
                    c.items(), null, null, null, null);
            case Assembler a -> new BuildingDto(Tool.ASSEMBLER, x, y, a.dir(),
                    0, new MachineDto(a.stockSnapshot(), a.readySnapshot()), null, null, null);
            case Splitter s -> new BuildingDto(Tool.SPLITTER, x, y, s.dir(),
                    0, null, new SplitterDto(s.heldItem(), s.nextOutput()), null, null);
            case UndergroundBelt u -> new BuildingDto(Tool.UNDERGROUND, x, y, u.dir(),
                    0, null, null, u.carriedItems(), null);
            case Lab l -> new BuildingDto(Tool.LAB, x, y, null,
                    l.points(), new MachineDto(l.stockSnapshot(), l.readySnapshot()), null, null, null);
        };
    }

    /** Снимок → человекочитаемый JSON (с отступами: сейв можно открыть и прочитать глазами). */
    public String toJson(GameSnapshot snapshot) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось сериализовать снимок", e);
        }
    }

    /** Снять и записать игру в файл одним вызовом. */
    public void save(GameState game, Path path) throws IOException {
        Files.writeString(path, toJson(capture(game)));
    }
}
