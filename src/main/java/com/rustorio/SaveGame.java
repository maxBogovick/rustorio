package com.rustorio;

import com.rustorio.core.Item;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SaveGame {
    public static boolean save(World world, String path) {
        List<String> lines = new ArrayList<>();
        for (Item item : Item.values()) {
            lines.add("STATS " + item.name() + " " + world.stats().total(item));
        }
        world.forEachBuilding((x, y, building) ->
                lines.add(building.type().name() + " " + x + " " + y + " " + building.save()));

        try {
            Files.write(Path.of(path), lines);
            return true;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    public static boolean load(World world, String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        }
        catch (IOException e) {
            System.err.println("Load failed: " + e.getMessage());
            return false;
        }

        world.clear();

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (line.startsWith("STATS ")) {
                String[] parts = line.split(" ", 3);
                world.stats().set(Item.valueOf(parts[1]), Long.parseLong(parts[2]));
            }else {
                loadBuilding(world, line);
            }
        }
        return true;
    }

    private static void loadBuilding(World world, String line) {
        String[] parts = line.split(" ", 4);
        BuildingType type = BuildingType.valueOf(parts[0]);
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        String data = parts[3];
        Building building = switch (type) {
            case MINER   -> Miner.load(data);
            case CHEST   -> Chest.load(data);
            case FURNACE -> Furnace.load(data);
        };
        world.restore(x, y, building);
    }
}
