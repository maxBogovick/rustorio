package com.rustorio.api.content.vanilla;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.ItemShape;

/**
 * The game's own built-in items — code standing in for a data file until Phase 3 lets mods supply
 * their own. {@link #frozen()} is the one shared, already-frozen {@link Registry} every
 * vanilla-content consumer (recipes, ore layouts in {@code domain}) reads its {@link ItemType}
 * instances from, so two classes both asking for "iron ore" get the exact same object — needed
 * for the {@code ==} comparisons {@code RecipeBook} already relies on. A frozen registry can't be
 * mutated ({@code register}/{@code update} throw), so sharing this one constant is safe in a way
 * sharing an unfrozen {@code Registry} would not be (that risk — see {@code Registry}'s own
 * javadoc — is about shared MUTABLE state before freeze, not about a read-only result after it).
 *
 * <p>Field values (research grade, color, shape) are transcribed from the current {@code Palette}
 * (colors/shapes) — cross-checked by {@code VanillaItemsTest} against those exact numbers.
 */
public final class VanillaItems {

    // Declared (and therefore initialized) before FROZEN and every public constant below: both
    // read these IDs, and Java runs static field initializers in textual, top-to-bottom order.
    // Also the single source of each item's path string (code review finding R5) — before this,
    // every public ItemType constant retyped its own "rustorio:xxx" literal independently of the
    // bare "xxx" literal registerAll() passed to register() below; a rename in one without the
    // other was NOT a compile error, only a NoSuchElementException wrapped in
    // ExceptionInInitializerError the moment this class was first touched. Now each id is typed
    // exactly once and both the constant and registerAll() read the same object.
    private static final ContentId IRON_ORE_ID = ContentId.of("rustorio:iron_ore");
    private static final ContentId IRON_PLATE_ID = ContentId.of("rustorio:iron_plate");
    private static final ContentId GEAR_ID = ContentId.of("rustorio:gear");
    private static final ContentId BRONZE_ORE_ID = ContentId.of("rustorio:bronze_ore");
    private static final ContentId BRONZE_PLATE_ID = ContentId.of("rustorio:bronze_plate");
    private static final ContentId MECHANISM_ID = ContentId.of("rustorio:mechanism");
    private static final ContentId ENGINE_ID = ContentId.of("rustorio:engine");
    private static final ContentId CHASSIS_ID = ContentId.of("rustorio:chassis");
    private static final ContentId ALLOY_PLATE_ID = ContentId.of("rustorio:alloy_plate");
    private static final ContentId ALLOY_GEAR_ID = ContentId.of("rustorio:alloy_gear");
    private static final ContentId COAL_ID = ContentId.of("rustorio:coal");
    private static final ContentId SAND_ID = ContentId.of("rustorio:sand");
    private static final ContentId GLASS_ID = ContentId.of("rustorio:glass");
    private static final ContentId OIL_ID = ContentId.of("rustorio:oil");
    private static final ContentId PLASTIC_ID = ContentId.of("rustorio:plastic");
    private static final ContentId SILICON_ID = ContentId.of("rustorio:silicon");
    private static final ContentId RESISTOR_ID = ContentId.of("rustorio:resistor");
    private static final ContentId CAPACITOR_ID = ContentId.of("rustorio:capacitor");
    private static final ContentId TRANSISTOR_ID = ContentId.of("rustorio:transistor");
    // Terrain, not cargo: these two are what a TerrainPatch names now that terrain is content
    // rather than a closed enum (see TerrainPatch and OreLayout#terrainAt). They live in the item
    // registry because that is what a patch's reference resolves against — a cell reports the
    // ItemType lying on it, and "nothing lying on it" is plain, buildable ground. Nothing puts
    // them on a belt: no recipe names them, no building's cost is paid in them, and the content
    // editor keeps them out of the ore picker (their own "tool" field says which tool offers them).
    private static final ContentId WATER_ID = ContentId.of("rustorio:water");
    private static final ContentId ROCK_ID = ContentId.of("rustorio:rock");

    // FROZEN must be declared (and therefore initialized) before any constant below that calls
    // frozen() — Java runs static field initializers in textual, top-to-bottom order, and a
    // constant declared above FROZEN would call frozen() while FROZEN still held its default
    // null, throwing a NullPointerException wrapped in ExceptionInInitializerError the moment
    // this class was first touched (caught by World's own static STARTING_INVENTORY field
    // failing to initialize, not by a test that exercises this class directly).
    private static final Registry<ItemType> FROZEN = buildFrozen();

    public static final ItemType IRON_ORE = frozen().get(IRON_ORE_ID);
    public static final ItemType IRON_PLATE = frozen().get(IRON_PLATE_ID);
    public static final ItemType GEAR = frozen().get(GEAR_ID);
    public static final ItemType BRONZE_ORE = frozen().get(BRONZE_ORE_ID);
    public static final ItemType BRONZE_PLATE = frozen().get(BRONZE_PLATE_ID);
    public static final ItemType MECHANISM = frozen().get(MECHANISM_ID);
    public static final ItemType ENGINE = frozen().get(ENGINE_ID);
    public static final ItemType CHASSIS = frozen().get(CHASSIS_ID);
    public static final ItemType ALLOY_PLATE = frozen().get(ALLOY_PLATE_ID);
    public static final ItemType ALLOY_GEAR = frozen().get(ALLOY_GEAR_ID);
    public static final ItemType COAL = frozen().get(COAL_ID);
    public static final ItemType SAND = frozen().get(SAND_ID);
    public static final ItemType GLASS = frozen().get(GLASS_ID);
    public static final ItemType OIL = frozen().get(OIL_ID);
    public static final ItemType PLASTIC = frozen().get(PLASTIC_ID);
    public static final ItemType SILICON = frozen().get(SILICON_ID);
    public static final ItemType RESISTOR = frozen().get(RESISTOR_ID);
    public static final ItemType CAPACITOR = frozen().get(CAPACITOR_ID);
    public static final ItemType TRANSISTOR = frozen().get(TRANSISTOR_ID);
    public static final ItemType WATER = frozen().get(WATER_ID);
    public static final ItemType ROCK = frozen().get(ROCK_ID);

    private VanillaItems() {
    }

    /**
     * Whether {@code item} is one of the two vanilla items that exist as terrain rather than as
     * cargo ({@link #WATER}, {@link #ROCK}) — the obstacle kinds that used to be constants of a
     * closed {@code Terrain} enum before a terrain patch started naming content like an ore patch
     * always did. Nothing produces them, nothing consumes them, and no recipe reaches them, so the
     * places that reason about "every item a player can end up holding" have to be able to say so.
     *
     * <p>Deliberately scoped to VANILLA, and named that way: a mod's own terrain item is not
     * listed here and cannot be — this answers "do I have hand-drawn art / a recipe path for this
     * one", not "is this terrain". What a patch DOES is decided by which array it sits in ({@link
     * com.rustorio.api.content.model.AuthoredMap}), never by asking an item what it is.
     */
    public static boolean isVanillaTerrain(ItemType item) {
        return WATER.equals(item) || ROCK.equals(item);
    }

    /** The canonical, already-frozen registry backing the constants above. */
    public static Registry<ItemType> frozen() {
        return FROZEN;
    }

    /** Registers all 21 vanilla items into {@code items}. For tests/custom assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}. */
    public static void registerAll(Registry<ItemType> items) {
        register(items, IRON_ORE_ID, "Iron Ore", false, rgb(105, 100, 95), ItemShape.CIRCLE);
        register(items, IRON_PLATE_ID, "Iron Plate", false, rgb(170, 172, 178), ItemShape.SQUARE);
        register(items, GEAR_ID, "Gear", true, rgb(230, 195, 60), ItemShape.TRIANGLE);
        register(items, BRONZE_ORE_ID, "Bronze Ore", false, rgb(110, 80, 60), ItemShape.CIRCLE);
        register(items, BRONZE_PLATE_ID, "Bronze Plate", false, rgb(214, 122, 44), ItemShape.SQUARE);
        register(items, MECHANISM_ID, "Mechanism", true, rgb(163, 68, 40), ItemShape.TRIANGLE);
        register(items, ENGINE_ID, "Engine", true, rgb(90, 170, 90), ItemShape.TRIANGLE);
        register(items, CHASSIS_ID, "Chassis", true, rgb(60, 90, 150), ItemShape.TRIANGLE);
        register(items, ALLOY_PLATE_ID, "Alloy Plate", false, rgb(150, 140, 130), ItemShape.SQUARE);
        register(items, ALLOY_GEAR_ID, "Alloy Gear", true, rgb(190, 170, 90), ItemShape.TRIANGLE);
        register(items, COAL_ID, "Coal", false, rgb(35, 33, 32), ItemShape.CIRCLE);
        register(items, SAND_ID, "Sand", false, rgb(194, 178, 128), ItemShape.CIRCLE);
        register(items, GLASS_ID, "Glass", false, rgb(168, 212, 230), ItemShape.SQUARE);
        register(items, OIL_ID, "Oil", false, rgb(43, 33, 24), ItemShape.CIRCLE);
        register(items, PLASTIC_ID, "Plastic", false, rgb(226, 222, 211), ItemShape.SQUARE);
        register(items, SILICON_ID, "Silicon", false, rgb(120, 130, 140), ItemShape.SQUARE);
        register(items, RESISTOR_ID, "Resistor", true, rgb(180, 100, 70), ItemShape.TRIANGLE);
        register(items, CAPACITOR_ID, "Capacitor", true, rgb(70, 120, 180), ItemShape.TRIANGLE);
        register(items, TRANSISTOR_ID, "Transistor", true, rgb(50, 80, 60), ItemShape.TRIANGLE);
        // Colors match the map tiles these two draw as (Textures#terrainWater/terrainRock), so a
        // renderer or editor without art for them still shows water as water.
        register(items, WATER_ID, "Water", false, rgb(58, 110, 165), ItemShape.SQUARE);
        register(items, ROCK_ID, "Rock", false, rgb(122, 122, 118), ItemShape.SQUARE);
    }

    private static Registry<ItemType> buildFrozen() {
        Registry<ItemType> items = new Registry<>();
        registerAll(items);
        items.freeze();
        return items;
    }

    private static void register(Registry<ItemType> items, ContentId id, String label,
            boolean researchGrade, int colorRgb, ItemShape shape) {
        items.register(id, new ItemType(id, label, researchGrade, colorRgb, shape));
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
