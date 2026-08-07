package com.graphics.screen;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.List;

/**
 * Dev-mode showcase ({@code --dev} — see {@code com.graphics.Main}): a real, continuously running
 * production chain for EVERY recipe in {@link com.rustorio.domain.RecipeBook#standard()} — not
 * static pre-loaded buffers, actual chests pushing cargo down actual belts into actual
 * furnaces/presses that actually cook it, tick after tick, from the very first frame. Plus one of
 * every other building kind, and one deliberately broken miner for status/alert testing. Not a
 * card in DEV_TASKS.md — a direct request to stop rebuilding the same test rig by hand.
 *
 * <p><b>Owner decision:</b> only the {@code IRON} chain's raw ore comes from a real {@link
 * com.rustorio.domain.building.Miner} standing on a real ore patch — {@code BRONZE_ORE}'s nearest
 * patch is dozens of tiles away, and every recipe past the raw-ore tier needs an intermediate good
 * (a {@code GEAR}, an {@code ENGINE}...) that no single miner produces directly anyway. Every other
 * chain starts from a small pre-stocked {@link Chest} standing in for "delivered from elsewhere" —
 * everything downstream of that chest (the belt, the furnace/press actually cooking, the output
 * accumulating) is exactly as real as if a live mine fed it. A chest this scene stocks with ~30
 * units lasts far longer than anyone will watch it run.
 */
final class DevScene {

    /**
     * The {@code webminer} mod's prototypes, by id. Constants rather than imports on purpose: a
     * mod's content is addressed by {@link ContentId} exactly as a player's save addresses it,
     * and this package must not — and now cannot — depend on a mod's Java.
     */
    private static final ContentId WEB_MINER_ID = ContentId.of("webminer:web_miner");
    private static final ContentId MONITOR_ID = ContentId.of("webminer:monitor");
    private static final ContentId INTERPRETER_ID = ContentId.of("webminer:interpreter");

    /** Plenty for a furnace/press consuming roughly one unit every 5-15 ticks to run for minutes, not seconds. */
    private static final int SUPPLY_STOCK = 30;

    private DevScene() {
    }

    static void build(World world) {
        buildIronChain(world); // IRON_ORE -> IRON_PLATE (real mining)
        buildBronzeChain(world); // BRONZE_ORE -> BRONZE_PLATE
        buildGearChain(world); // IRON_PLATE -> GEAR
        buildMechanismChain(world); // BRONZE_PLATE -> MECHANISM
        buildEngineChain(world); // GEAR + MECHANISM -> ENGINE
        buildChassisChain(world); // ENGINE + GEAR -> CHASSIS
        buildAlloyChain(world); // IRON_PLATE + BRONZE_PLATE -> ALLOY_PLATE
        buildAlloyGearChain(world); // ALLOY_PLATE -> ALLOY_GEAR

        buildSplitterShowcase(world);
        buildTunnelShowcase(world);
        buildLabShowcase(world);
        buildSpeedModuleShowcase(world);
        buildDeliberatelyBrokenMiner(world);
        buildWebMinerShowcase(world);

        world.addResearchPoints(500); // enough to unlock a couple of early techs straight from the tree screen (T)
    }

    /** Real miner -> belt -> furnace (fed real coal) -> belt -> chest — the one chain not sourced from a stand-in chest. */
    private static void buildIronChain(World world) {
        world.place(BuildingType.MINER, 6, 5, Direction.RIGHT); // (6,5) is the standard map's first iron patch — see PatchOreLayout
        world.place(BuildingType.BELT, 7, 5, Direction.RIGHT);
        world.place(BuildingType.FURNACE, 8, 5, Direction.RIGHT);
        world.place(BuildingType.BELT, 9, 5, Direction.RIGHT);
        world.place(BuildingType.CHEST, 10, 5);
        feedCoal(world, 8, 5);
    }

    private static void buildBronzeChain(World world) {
        buildSingleInputChain(world, 8, BuildingType.FURNACE, VanillaItems.BRONZE_ORE);
        feedCoal(world, 8, 8);
    }

    private static void buildGearChain(World world) {
        buildSingleInputChain(world, 11, BuildingType.PRESS, VanillaItems.IRON_PLATE);
    }

    private static void buildMechanismChain(World world) {
        buildSingleInputChain(world, 14, BuildingType.PRESS, VanillaItems.BRONZE_PLATE);
    }

    private static void buildAlloyGearChain(World world) {
        buildSingleInputChain(world, 26, BuildingType.PRESS, VanillaItems.ALLOY_PLATE);
    }

    /**
     * Common shape for every single-input recipe: {@code Chest(supply) -> belt -> furnace/press ->
     * belt -> Chest(output)}, all facing {@code RIGHT} in one row at {@code y}.
     */
    private static void buildSingleInputChain(World world, int y, BuildingType kind, ItemType supply) {
        world.place(BuildingType.CHEST, 6, y, Direction.RIGHT);
        world.place(BuildingType.BELT, 7, y, Direction.RIGHT);
        world.place(kind, 8, y, Direction.RIGHT);
        world.place(BuildingType.BELT, 9, y, Direction.RIGHT);
        world.place(BuildingType.CHEST, 10, y);
        stock(world, 6, y, supply);
    }

    /**
     * {@code GEAR + MECHANISM -> ENGINE}: {@code MECHANISM} is fed first (from directly north — no
     * belt needed for one hop) because it's unambiguous for {@code PRESS} (only {@code ENGINE}
     * uses it), so it auto-commits the recipe before the (otherwise ambiguous — see {@code
     * Furnace#pickRecipe}) {@code GEAR} belt from the west ever needs to be told which recipe was
     * meant; a {@code GEAR} that arrives before {@code MECHANISM} just waits on the belt, same
     * "hold until delivered" discipline every producer already follows.
     */
    private static void buildEngineChain(World world) {
        world.place(BuildingType.CHEST, 6, 17, Direction.RIGHT); // GEAR supply
        world.place(BuildingType.BELT, 7, 17, Direction.RIGHT);
        world.place(BuildingType.PRESS, 8, 17, Direction.RIGHT);
        world.place(BuildingType.BELT, 9, 17, Direction.RIGHT);
        world.place(BuildingType.CHEST, 10, 17);
        world.place(BuildingType.CHEST, 8, 16, Direction.DOWN); // MECHANISM supply, feeds straight down
        stock(world, 6, 17, VanillaItems.GEAR);
        stock(world, 8, 16, VanillaItems.MECHANISM);
    }

    /** {@code ENGINE + GEAR -> CHASSIS}: same trick as {@link #buildEngineChain} — {@code ENGINE} is unique to this recipe, fed first. */
    private static void buildChassisChain(World world) {
        world.place(BuildingType.CHEST, 6, 20, Direction.RIGHT); // ENGINE supply
        world.place(BuildingType.BELT, 7, 20, Direction.RIGHT);
        world.place(BuildingType.PRESS, 8, 20, Direction.RIGHT);
        world.place(BuildingType.BELT, 9, 20, Direction.RIGHT);
        world.place(BuildingType.CHEST, 10, 20);
        world.place(BuildingType.CHEST, 8, 19, Direction.DOWN); // GEAR supply, feeds straight down
        stock(world, 6, 20, VanillaItems.ENGINE);
        stock(world, 8, 19, VanillaItems.GEAR);
    }

    /**
     * {@code IRON_PLATE + BRONZE_PLATE -> ALLOY_PLATE}: unlike the two chains above, EITHER input
     * is already unambiguous on its own for {@code FURNACE} kind (only {@code ALLOY} takes either
     * as an ingredient), so there's no first-vs-second feeding order to get right — plus the real
     * coal a {@code FURNACE} always needs, regardless of which recipe it's running.
     */
    private static void buildAlloyChain(World world) {
        world.place(BuildingType.CHEST, 6, 23, Direction.RIGHT); // IRON_PLATE supply
        world.place(BuildingType.BELT, 7, 23, Direction.RIGHT);
        world.place(BuildingType.FURNACE, 8, 23, Direction.RIGHT);
        world.place(BuildingType.BELT, 9, 23, Direction.RIGHT);
        world.place(BuildingType.CHEST, 10, 23);
        world.place(BuildingType.CHEST, 8, 22, Direction.DOWN); // BRONZE_PLATE supply, feeds straight down (north side)
        world.place(BuildingType.CHEST, 8, 24, Direction.UP); // COAL supply, feeds straight up (south side — north is already taken above)
        stock(world, 6, 23, VanillaItems.IRON_PLATE);
        stock(world, 8, 22, VanillaItems.BRONZE_PLATE);
        stock(world, 8, 24, VanillaItems.COAL);
    }

    /** A small chest directly NORTH of {@code (x, y)}, feeding real {@code COAL} straight down into whatever stands there. */
    private static void feedCoal(World world, int x, int y) {
        world.place(BuildingType.CHEST, x, y - 1, Direction.DOWN);
        stock(world, x, y - 1, VanillaItems.COAL);
    }

    private static void stock(World world, int x, int y, ItemType item) {
        Chest chest = (Chest) world.peek(x, y).orElseThrow();
        for (int i = 0; i < SUPPLY_STOCK; i++) {
            chest.accept(world, item);
        }
    }

    /** A splitter with something real to route, not just sitting empty. */
    private static void buildSplitterShowcase(World world) {
        world.place(BuildingType.CHEST, 4, 29, Direction.RIGHT);
        world.place(BuildingType.BELT, 5, 29, Direction.RIGHT);
        world.place(BuildingType.SPLITTER, 6, 29, Direction.RIGHT);
        world.place(BuildingType.BELT, 7, 29, Direction.RIGHT);
        world.place(BuildingType.BELT, 6, 30, Direction.DOWN);
        stock(world, 4, 29, VanillaItems.IRON_ORE);
    }

    /** A tunnel pair well within range, actually relaying cargo from a feeder chest. */
    private static void buildTunnelShowcase(World world) {
        world.place(BuildingType.CHEST, 5, 32, Direction.RIGHT);
        world.place(BuildingType.UNDERGROUND_IN, 6, 32, Direction.RIGHT);
        world.place(BuildingType.UNDERGROUND_OUT, 9, 32, Direction.RIGHT); // 3 tiles ahead — inside MAX_RANGE even un-teched
        world.place(BuildingType.BELT, 10, 32, Direction.RIGHT);
        stock(world, 5, 32, VanillaItems.IRON_ORE);
    }

    /** A lab continuously fed research-grade goods. */
    private static void buildLabShowcase(World world) {
        world.place(BuildingType.CHEST, 6, 35, Direction.RIGHT);
        world.place(BuildingType.BELT, 7, 35, Direction.RIGHT);
        world.place(BuildingType.LAB, 8, 35);
        stock(world, 6, 35, VanillaItems.GEAR);
    }

    /** A chest sped up via {@link Building#withSpeedLevel} — the same upgrade {@code UpgradeSpeedAction} applies, done directly — pushing cargo down the belt twice as fast as an unupgraded chest would. */
    private static void buildSpeedModuleShowcase(World world) {
        world.place(BuildingType.CHEST, 6, 38, Direction.RIGHT);
        world.place(BuildingType.BELT, 7, 38, Direction.RIGHT);
        world.place(BuildingType.CHEST, 8, 38);
        Building source = world.removeBuilding(6, 38).orElseThrow();
        world.restoreBuilding(6, 38, source.withSpeedLevel(1));
        stock(world, 6, 38, VanillaItems.IRON_ORE);
    }

    /**
     * A miner off any ore patch — {@code World.place} would refuse this outright ({@link
     * com.rustorio.domain.building.PlacementRule#NEEDS_ORE}), so this goes straight through {@link
     * World#restoreBuilding} instead, same as loading a save does. Permanently {@code NO_ORE} —
     * the one broken building the alerts line and the per-cell status marker should always have
     * something to report from the very first frame.
     */
    private static void buildDeliberatelyBrokenMiner(World world) {
        Building strandedMiner = world.buildingFactory().create(BuildingType.MINER, Direction.RIGHT);
        world.restoreBuilding(1, 1, strandedMiner); // far from every ore patch in PatchOreLayout.standard()
    }

    /**
     * The {@code webminer} mod's own chain — miner, monitor, interpreter, belt, chest — on the map
     * from the very first frame: a genuine outbound HTTP GET whose result rides the belt like ore,
     * the same "as real as if a live mine fed it" standard every other chain here follows. Click
     * the monitor for the raw body, the interpreter for {@code current_user_url} pulled out of it
     * (a field the default endpoint's response always has).
     *
     * <p>Everything here is addressed the way a scene in ANY mod would have to address another
     * mod's content: prototypes by {@link ContentId}, configuration through {@link
     * EditableBuilding} — never by importing the mod's Java classes, which this package is now
     * forbidden to do (see {@code PackageBoundaryRulesTest}) and could not do anyway, since that
     * mod compiles into its own jar. A silent skip when the mod is absent, for the same reason a
     * dev scene must not be the one thing that stops the game booting without it.
     */
    private static void buildWebMinerShowcase(World world) {
        if (world.buildingFactory().buildings().peek(WEB_MINER_ID).isEmpty()) {
            return;
        }
        world.place(WEB_MINER_ID, 1, 41, Direction.RIGHT);
        world.place(MONITOR_ID, 2, 41, Direction.RIGHT);
        world.place(INTERPRETER_ID, 3, 41, Direction.RIGHT);
        world.place(BuildingType.BELT, 4, 41, Direction.RIGHT);
        world.place(BuildingType.CHEST, 5, 41);
        // The same call the settings modal makes when a player types into it — this scene needs no
        // more access to a mod's building than a player has.
        Building interpreter = world.peek(3, 41).orElseThrow();
        ((EditableBuilding) interpreter).applyEdits(List.of("current_user_url"));
    }
}
