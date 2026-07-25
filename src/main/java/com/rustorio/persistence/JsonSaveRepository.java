package com.rustorio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SaveRepository} backed by a human-readable JSON file, via the Jackson databind dependency
 * this project already declares.
 *
 * <p>Replaces the previous hand-rolled, position-based text format (one space-separated line per
 * building, each building parsing and re-parsing its own fields) with typed {@link
 * com.rustorio.domain.building.BuildingMemento} records that Jackson serializes directly — a
 * malformed save now fails with a precise Jackson exception naming the field and building index
 * that didn't match, instead of an {@code ArrayIndexOutOfBoundsException} from a hand-split line.
 */
public final class JsonSaveRepository implements SaveRepository {

    /** Default save location — a simple game, one save slot. */
    public static final Path DEFAULT_PATH = Path.of("rustorio-save.json");

    private final Path path;
    private final ObjectMapper mapper;

    public JsonSaveRepository() {
        this(DEFAULT_PATH);
    }

    public JsonSaveRepository(Path path) {
        this.path = path;
        this.mapper = newMapper();
    }

    private static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.addMixIn(com.rustorio.domain.building.BuildingMemento.class, BuildingMementoMixin.class);
        return mapper;
    }

    @Override
    public SaveResult save(World world) {
        List<PlacedBuilding> placed = new ArrayList<>();
        world.forEachBuilding((x, y, building) ->
                placed.add(new PlacedBuilding(x, y, building.speedLevel(), building.memento())));
        WorldSnapshot snapshot = new WorldSnapshot(world.stats().snapshot(), world.research().snapshot(), placed);

        try {
            mapper.writeValue(path.toFile(), snapshot);
            return new SaveResult.Success();
        } catch (IOException e) {
            return new SaveResult.Failure(e.getMessage());
        }
    }

    @Override
    public SaveResult load(World world) {
        WorldSnapshot snapshot;
        try {
            snapshot = mapper.readValue(path.toFile(), WorldSnapshot.class);
        } catch (IOException e) {
            return new SaveResult.Failure(e.getMessage());
        }

        world.clear();
        world.restoreStats(snapshot.stats());
        world.restoreResearch(snapshot.research());

        BuildingFactory factory = world.buildingFactory();
        for (PlacedBuilding placed : snapshot.buildings()) {
            Building building = factory.restore(placed.state(), placed.speedLevel());
            world.restoreBuilding(placed.x(), placed.y(), building);
        }
        return new SaveResult.Success();
    }
}
