package com.rustorio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.PlayerInventory;
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

    /** Reads/writes {@link ItemType} values against the vanilla-only registry — see {@link #JsonSaveRepository(Path, Registry)} for a game/test running with additional (modded) content. */
    public JsonSaveRepository(Path path) {
        this(path, VanillaItems.frozen());
    }

    /**
     * {@code items} must contain every {@link ItemType} any save this repository reads could name
     * — a save mentioning a {@code ContentId} missing from it fails to load (unknown key), the
     * same as any other malformed save. Passing a registry that only knows the vanilla items (the
     * other constructor's default) is exactly correct for a game running no mods; a game or test
     * with additional registered content needs to pass a registry that includes it too.
     */
    public JsonSaveRepository(Path path, Registry<ItemType> items) {
        this.path = path;
        this.mapper = newMapper(items);
    }

    private static ObjectMapper newMapper(Registry<ItemType> items) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.addMixIn(com.rustorio.domain.building.BuildingMemento.class, BuildingMementoMixin.class);
        // ItemType replaced the Item enum — Jackson serialized an enum
        // both as a plain value and as a Map key via name() for free; a record needs both an
        // explicit (de)serializer AND a key (de)serializer instead (see ItemTypeSerializer's
        // javadoc for why the plain one matters just as much as the key one).
        mapper.registerModule(new SimpleModule()
                .addSerializer(ItemType.class, new ItemTypeSerializer())
                .addDeserializer(ItemType.class, new ItemTypeDeserializer(items))
                .addKeySerializer(ItemType.class, new ItemTypeKeySerializer())
                .addKeyDeserializer(ItemType.class, new ItemTypeKeyDeserializer(items)));
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
        WorldSnapshot snapshot = new WorldSnapshot(WorldSnapshot.CURRENT_VERSION,
                world.stats().snapshot(), world.research().snapshot(), placed,
                world.buildingFactory().oreLayout().id(),
                world.inventory().snapshot().amounts(),
                world.buildingFactory().oreLayout().depletionSnapshot(),
                world.currentTick());

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
     *
     * <p>One more phase-1 check, added late (code review finding): every key in {@code
     * snapshot.oreDepletion()} is validated against the current {@code OreLayout}'s cell count
     * before phase 2 runs at all. {@link com.rustorio.domain.OreLayout#restoreDepletion} itself
     * writes straight into a flat array with no bounds check — left unvalidated, a corrupted index
     * would throw {@code ArrayIndexOutOfBoundsException} AFTER {@code world.clear()}, uncaught,
     * defeating the entire two-phase discipline this javadoc otherwise describes.
     *
     * <p>And one more (same finding): every rebuilt {@link Building}'s footprint, at its saved
     * position, is checked against {@code world.width()}/{@code height()}. {@code
     * World#restoreBuilding} trusts its caller and never checks bounds itself — fine for its two
     * real callers, which only ever hand back an already-validated footprint, but not for a
     * corrupted save, which could otherwise reserve out-of-range cells with no exception at all.
     *
     * <p>Checked before even that, first of everything (D-07, DEV_TASKS.md): {@link
     * WorldSnapshot#version()} must match {@link WorldSnapshot#CURRENT_VERSION} — see that
     * record's own javadoc for why a plain equality check replaces a growing pile of {@code
     * @Nullable} migration fields.
     */
    @Override
    public SaveResult load(World world) {
        WorldSnapshot snapshot;
        try {
            snapshot = mapper.readValue(path.toFile(), WorldSnapshot.class);
        } catch (IOException e) {
            return failure(e);
        }

        if (snapshot.version() != WorldSnapshot.CURRENT_VERSION) {
            return new SaveResult.Failure("save format version " + snapshot.version()
                    + " is not supported (expected " + WorldSnapshot.CURRENT_VERSION + ")");
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

        // Still phase 1 (code review finding): OreLayout#restoreDepletion writes straight into a
        // flat array with no bounds check of its own (see PatchOreLayout#restoreDepletion) — a
        // corrupted or hand-edited save with an out-of-range index would throw
        // ArrayIndexOutOfBoundsException. That call used to happen AFTER world.clear() below, and
        // outside any try/catch, so the exception propagated straight out of load() uncaught —
        // with the world already destroyed. Validating here, before world.clear() runs, keeps the
        // class's own documented contract ("a save that parses but doesn't make sense fails
        // cleanly, with the caller's current world still intact") actually true for this field too.
        OreLayoutId currentLayout = factory.oreLayout().id();
        int cellCount = currentLayout.width() * currentLayout.height();
        for (Integer index : snapshot.oreDepletion().keySet()) {
            if (index == null || index < 0 || index >= cellCount) {
                return new SaveResult.Failure("corrupted ore depletion index: " + index);
            }
        }

        // Also still phase 1 (code review finding): World#restoreBuilding trusts its caller and
        // never checks bounds — deliberately, since the two REAL callers (RotateAction/
        // UpgradeSpeedAction) only ever hand back a footprint that was already validated when the
        // building was first placed. A hand-edited or corrupted save doesn't carry that guarantee:
        // an anchor near the map's edge with a multi-cell footprint (currently only ASSEMBLER)
        // could reach past it, silently reserving out-of-range Coords in World#occupancy with no
        // exception at all — a quiet corruption, not a crash, so nothing downstream would even
        // report it. Checked here, against the actual rebuilt Building's real footprint, not a
        // guess from BuildingType alone.
        for (Map.Entry<PlacedBuilding, Building> entry : rebuilt) {
            PlacedBuilding placed = entry.getKey();
            Building building = entry.getValue();
            int farX = placed.x() + building.footprintWidth() - 1;
            int farY = placed.y() + building.footprintHeight() - 1;
            if (!world.inBounds(placed.x(), placed.y()) || !world.inBounds(farX, farY)) {
                return new SaveResult.Failure(
                        "building at (" + placed.x() + "," + placed.y() + ") falls outside the map");
            }
        }

        world.clear();
        world.restoreStats(snapshot.stats());
        world.restoreResearch(snapshot.research());
        world.restoreInventory(new PlayerInventory.Snapshot(snapshot.inventory()));
        // Before restoring the buildings, and in particular before anything ticks: the stats
        // restored just above are timestamped against this clock (N3, NEW_BUGS_PROGRESS.md).
        world.restoreTickCount(snapshot.tickCount());
        factory.oreLayout().restoreDepletion(snapshot.oreDepletion());
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
