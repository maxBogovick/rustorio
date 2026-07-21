package com.rustorio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Запись мира в текстовый файл и чтение обратно. Формат — простые строки, по одной на запись,
 * без библиотек сериализации: их код был бы короче, но студент не увидел бы, ЧТО именно и КАК
 * превращается в текст. А нам важно именно это.
 *
 * <pre>
 * # rustorio save v1
 * STATS IRON_ORE 42
 * STATS IRON_PLATE 7
 * MINER 3 5 2
 * CHEST 4 5 12
 * FURNACE 5 5 3 1
 * </pre>
 *
 * <p>Строка здания — {@code <сорт> <x> <y> <состояние>}: сорт и координаты общие для всех, а
 * что после них — решает само здание ({@link Building#save()}). Строка статистики — отдельный
 * тег {@code STATS}, потому что это не здание на карте, а число, живущее в мире целиком.
 */
public final class SaveGame {

    /** Путь по умолчанию — простая игра, один слот сохранения. */
    public static final String DEFAULT_PATH = "rustorio-save.txt";

    private static final String HEADER = "# rustorio save v1";

    private SaveGame() {
    }

    /** Записать мир в файл. Возвращает {@code false}, если диск отказал (например, нет прав). */
    public static boolean save(World world, String path) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Item item : Item.values()) {
            lines.add("STATS " + item.name() + " " + world.stats().total(item));
        }
        world.forEachBuilding((x, y, building) ->
                lines.add(building.type().name() + " " + x + " " + y + " " + building.save()));

        try {
            Files.write(Path.of(path), lines);
            return true;
        } catch (IOException e) {
            System.err.println("Save failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Загрузить мир из файла, ЗАМЕНИВ текущее состояние. Если файла нет или он повреждён на
     * уровне диска — возвращает {@code false}, а текущий мир остаётся нетронутым (мир очищается
     * только после того, как файл успешно прочитан).
     *
     * <p>Формат строки внутри файла мы, в отличие от диска, не подстраховываем: сорт здания
     * ({@code BuildingType.valueOf}) и числа (`Integer.parseInt`) разбираются напрямую. Файл,
     * который написал сам {@link #save}, всегда правильный; специально испорченный — уронит
     * загрузку исключением. Это осознанно (см. урок, задание про испорченный файл).
     */
    public static boolean load(World world, String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            System.err.println("Load failed: " + e.getMessage());
            return false;
        }

        world.clear();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("STATS ")) {
                String[] parts = line.split(" ", 3);
                world.stats().set(Item.valueOf(parts[1]), Long.parseLong(parts[2]));
            } else {
                loadBuilding(world, line);
            }
        }
        return true;
    }

    /** Разобрать строку одного здания и поставить его в мир напрямую, минуя правила постройки. */
    private static void loadBuilding(World world, String line) {
        String[] parts = line.split(" ", 4);
        BuildingType type = BuildingType.valueOf(parts[0]);
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        String data = parts[3];

        Building building = switch (type) {
            case MINER -> Miner.load(data);
            case CHEST -> Chest.load(data);
            case FURNACE -> Furnace.load(data, Recipe.IRON);
            case BELT -> Belt.load(data);
            case SPLITTER -> Splitter.load(data);
            case PRESS -> Furnace.load(data, Recipe.GEAR);
        };
        world.restore(x, y, building);
    }
}
