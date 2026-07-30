package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;

/**
 * The game's own built-in items — code standing in for a data file until Phase 3 lets mods supply
 * their own. {@link #frozen()} is the one shared, already-frozen {@link Registry} every
 * vanilla-content class in {@code domain} (recipes, ore layouts) reads its {@link ItemType}
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
    private static final ContentId COPPER_ORE_ID = ContentId.of("rustorio:copper_ore");
    private static final ContentId COPPER_PLATE_ID = ContentId.of("rustorio:copper_plate");
    private static final ContentId MECHANISM_ID = ContentId.of("rustorio:mechanism");
    private static final ContentId ENGINE_ID = ContentId.of("rustorio:engine");
    private static final ContentId CHASSIS_ID = ContentId.of("rustorio:chassis");
    private static final ContentId ALLOY_PLATE_ID = ContentId.of("rustorio:alloy_plate");
    private static final ContentId ALLOY_GEAR_ID = ContentId.of("rustorio:alloy_gear");
    private static final ContentId COAL_ID = ContentId.of("rustorio:coal");
    // Этап 0 — новое сырьё для электронной цепочки
    private static final ContentId QUARTZ_SAND_ID = ContentId.of("rustorio:quartz_sand");
    private static final ContentId TIN_ORE_ID = ContentId.of("rustorio:tin_ore");
    private static final ContentId LEAD_ORE_ID = ContentId.of("rustorio:lead_ore");
    private static final ContentId CRUDE_OIL_ID = ContentId.of("rustorio:crude_oil");
    private static final ContentId GOLD_ORE_ID = ContentId.of("rustorio:gold_ore");

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
    public static final ItemType COPPER_ORE = frozen().get(COPPER_ORE_ID);
    public static final ItemType COPPER_PLATE = frozen().get(COPPER_PLATE_ID);
    public static final ItemType MECHANISM = frozen().get(MECHANISM_ID);
    public static final ItemType ENGINE = frozen().get(ENGINE_ID);
    public static final ItemType CHASSIS = frozen().get(CHASSIS_ID);
    public static final ItemType ALLOY_PLATE = frozen().get(ALLOY_PLATE_ID);
    public static final ItemType ALLOY_GEAR = frozen().get(ALLOY_GEAR_ID);
    public static final ItemType COAL = frozen().get(COAL_ID);
    public static final ItemType QUARTZ_SAND = frozen().get(QUARTZ_SAND_ID);
    public static final ItemType TIN_ORE = frozen().get(TIN_ORE_ID);
    public static final ItemType LEAD_ORE = frozen().get(LEAD_ORE_ID);
    public static final ItemType CRUDE_OIL = frozen().get(CRUDE_OIL_ID);
    public static final ItemType GOLD_ORE = frozen().get(GOLD_ORE_ID);

    private VanillaItems() {
    }

    /** The canonical, already-frozen registry backing the constants above. */
    public static Registry<ItemType> frozen() {
        return FROZEN;
    }

    /** Registers all 16 vanilla items into {@code items}. For tests/custom assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}. */
    public static void registerAll(Registry<ItemType> items) {
        register(items, IRON_ORE_ID, "Iron Ore", false, rgb(105, 100, 95), ItemShape.CIRCLE);
        register(items, IRON_PLATE_ID, "Iron Plate", false, rgb(170, 172, 178), ItemShape.SQUARE);
        register(items, GEAR_ID, "Gear", true, rgb(230, 195, 60), ItemShape.TRIANGLE);
        register(items, COPPER_ORE_ID, "Copper Ore", false, rgb(184, 98, 60), ItemShape.CIRCLE);
        register(items, COPPER_PLATE_ID, "Copper Plate", false, rgb(214, 130, 60), ItemShape.SQUARE);
        register(items, MECHANISM_ID, "Mechanism", true, rgb(163, 68, 40), ItemShape.TRIANGLE);
        register(items, ENGINE_ID, "Engine", true, rgb(90, 170, 90), ItemShape.TRIANGLE);
        register(items, CHASSIS_ID, "Chassis", true, rgb(60, 90, 150), ItemShape.TRIANGLE);
        register(items, ALLOY_PLATE_ID, "Alloy Plate", false, rgb(150, 140, 130), ItemShape.SQUARE);
        register(items, ALLOY_GEAR_ID, "Alloy Gear", true, rgb(190, 170, 90), ItemShape.TRIANGLE);
        register(items, COAL_ID, "Coal", false, rgb(35, 33, 32), ItemShape.CIRCLE);
        register(items, QUARTZ_SAND_ID, "Quartz Sand", false, rgb(194, 178, 128), ItemShape.CIRCLE);
        register(items, TIN_ORE_ID, "Tin Ore", false, rgb(180, 180, 190), ItemShape.CIRCLE);
        register(items, LEAD_ORE_ID, "Lead Ore", false, rgb(90, 90, 100), ItemShape.CIRCLE);
        register(items, CRUDE_OIL_ID, "Crude Oil", false, rgb(25, 20, 18), ItemShape.CIRCLE);
        register(items, GOLD_ORE_ID, "Gold Ore", false, rgb(212, 175, 55), ItemShape.CIRCLE);
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
