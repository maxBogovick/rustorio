package com.rustorio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /**
     * Writes to a temp file next to the target and atomically renames it into place, so a crash
     * mid-write can never leave the single save slot half-written — see P1-04 in
     * BUG_FIX_PROGRESS.md. The old save stays exactly as it was until the rename succeeds.
     */
    @Override
    public SaveResult save(World world) {
        List<PlacedBuilding> placed = new ArrayList<>();
        world.forEachBuilding((x, y, building) ->
                placed.add(new PlacedBuilding(x, y, building.speedLevel(), building.memento())));
        WorldSnapshot snapshot = new WorldSnapshot(world.stats().snapshot(), world.research().snapshot(), placed,
                world.buildingFactory().oreLayout().id());

        Path tmp = null;
        try {
            tmp = Files.createTempFile(path.toAbsolutePath().getParent(), "rustorio-save", ".tmp");
            mapper.writeValue(tmp.toFile(), snapshot);
            moveIntoPlace(tmp, path);
            return new SaveResult.Success();
        } catch (IOException e) {
            return failure(e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup — a leftover temp file next to the save is harmless
                }
            }
        }
    }

    private static void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Two phases, deliberately not interleaved. Phase 1 only reads: deserialize the snapshot and
     * rebuild every {@link Building} through {@link BuildingFactory#restore}, which can throw
     * (e.g. a schema-valid but semantically impossible recipe output — see
     * {@code BuildingFactory#restore}'s {@code IllegalStateException}). None of that touches
     * {@code world} yet. Only once every building has been rebuilt successfully does phase 2 run:
     * {@code world.clear()} and the actual restore. This way a save that parses but doesn't make
     * sense fails cleanly, with the caller's current world still intact — see P1-03 in
     * BUG_FIX_PROGRESS.md.
     *
     * <p>Also part of phase 1: if the snapshot names an {@code OreLayoutId} and it doesn't match
     * the world being loaded into, fail rather than silently placing miners built for one ore map
     * onto another (see P2-01, owner decision A, in BUG_FIX_PROGRESS.md). A {@code null} {@code
     * oreLayout} — a save written before this field existed — means "unknown, don't check."
     */
    @Override
    public SaveResult load(World world) {
        WorldSnapshot snapshot;
        try {
            snapshot = mapper.readValue(path.toFile(), WorldSnapshot.class);
        } catch (IOException e) {
            return failure(e);
        }

        BuildingFactory factory = world.buildingFactory();
        OreLayoutId savedLayout = snapshot.oreLayout();
        if (savedLayout != null && !savedLayout.equals(factory.oreLayout().id())) {
            return new SaveResult.Failure("save was made on a different map (" + savedLayout + ")");
        }

        List<Map.Entry<PlacedBuilding, Building>> rebuilt = new ArrayList<>();
        try {
            for (PlacedBuilding placed : snapshot.buildings()) {
                Building building = factory.restore(placed.state(), placed.speedLevel());
                rebuilt.add(new AbstractMap.SimpleEntry<>(placed, building));
            }
        } catch (RuntimeException e) {
            return failure(e);
        }

        world.clear();
        world.restoreStats(snapshot.stats());
        world.restoreResearch(snapshot.research());
        for (Map.Entry<PlacedBuilding, Building> entry : rebuilt) {
            world.restoreBuilding(entry.getKey().x(), entry.getKey().y(), entry.getValue());
        }
        return new SaveResult.Success();
    }

    /**
     * {@code e.getMessage()} is frequently {@code null} (a {@code NoSuchFileException} carries only
     * the path, nothing else) — fold in the exception's class name so the reason is never silently
     * empty.
     */
    private static SaveResult.Failure failure(Exception e) {
        return new SaveResult.Failure(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
