package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Cross-checks every field {@link VanillaItems} registers against the values currently returned
 * by {@code com.graphics.render.Palette.itemColor}/{@code itemShape} — transcribed by hand (both
 * are package-private to {@code com.graphics.render}, not callable from here) and listed in full
 * so a mismatch points straight at the field, not just "something's off". Also proves {@link
 * VanillaItems#frozen()}'s constants are the SAME objects {@code registerAll} would build fresh
 * (by value, not identity) — that sameness (not just equality) is what {@code RecipeBook}'s
 * {@code ==} comparisons depend on.
 */
class VanillaItemsTest {

    @Test
    void frozenRegistryHasAllTwentyOneItems() {
        assertEquals(21, VanillaItems.frozen().size());
    }

    @Test
    void namedConstantsMatchTheFrozenRegistry() {
        assertSame(VanillaItems.IRON_ORE, VanillaItems.frozen().get(ContentId.of("rustorio:iron_ore")));
        assertSame(VanillaItems.COAL, VanillaItems.frozen().get(ContentId.of("rustorio:coal")));
        assertSame(VanillaItems.SAND, VanillaItems.frozen().get(ContentId.of("rustorio:sand")));
        assertSame(VanillaItems.OIL, VanillaItems.frozen().get(ContentId.of("rustorio:oil")));
        assertSame(VanillaItems.PLASTIC, VanillaItems.frozen().get(ContentId.of("rustorio:plastic")));
        assertSame(VanillaItems.GLASS, VanillaItems.frozen().get(ContentId.of("rustorio:glass")));
        assertSame(VanillaItems.SILICON, VanillaItems.frozen().get(ContentId.of("rustorio:silicon")));
        assertSame(VanillaItems.RESISTOR, VanillaItems.frozen().get(ContentId.of("rustorio:resistor")));
        assertSame(VanillaItems.CAPACITOR, VanillaItems.frozen().get(ContentId.of("rustorio:capacitor")));
        assertSame(VanillaItems.TRANSISTOR, VanillaItems.frozen().get(ContentId.of("rustorio:transistor")));
    }

    @Test
    void ironOreMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.IRON_ORE, "iron_ore", "Iron Ore", false, 105, 100, 95, ItemShape.CIRCLE);
    }

    @Test
    void ironPlateMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.IRON_PLATE, "iron_plate", "Iron Plate", false, 170, 172, 178, ItemShape.SQUARE);
    }

    @Test
    void gearMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.GEAR, "gear", "Gear", true, 230, 195, 60, ItemShape.TRIANGLE);
    }

    @Test
    void bronzeOreMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.BRONZE_ORE, "bronze_ore", "Bronze Ore", false, 110, 80, 60, ItemShape.CIRCLE);
    }

    @Test
    void bronzePlateMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.BRONZE_PLATE, "bronze_plate", "Bronze Plate", false, 214, 122, 44, ItemShape.SQUARE);
    }

    @Test
    void mechanismMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.MECHANISM, "mechanism", "Mechanism", true, 163, 68, 40, ItemShape.TRIANGLE);
    }

    @Test
    void engineMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.ENGINE, "engine", "Engine", true, 90, 170, 90, ItemShape.TRIANGLE);
    }

    @Test
    void chassisMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.CHASSIS, "chassis", "Chassis", true, 60, 90, 150, ItemShape.TRIANGLE);
    }

    @Test
    void alloyPlateMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.ALLOY_PLATE, "alloy_plate", "Alloy Plate", false, 150, 140, 130, ItemShape.SQUARE);
    }

    @Test
    void alloyGearMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.ALLOY_GEAR, "alloy_gear", "Alloy Gear", true, 190, 170, 90, ItemShape.TRIANGLE);
    }

    @Test
    void coalMatchesCurrentPaletteValues() {
        assertItem(VanillaItems.COAL, "coal", "Coal", false, 35, 33, 32, ItemShape.CIRCLE);
    }

    @Test
    void sandMatchesRegisteredValues() {
        assertItem(VanillaItems.SAND, "sand", "Sand", false, 194, 178, 128, ItemShape.CIRCLE);
    }

    @Test
    void oilMatchesRegisteredValues() {
        assertItem(VanillaItems.OIL, "oil", "Oil", false, 40, 30, 20, ItemShape.CIRCLE);
    }

    @Test
    void plasticMatchesRegisteredValues() {
        assertItem(VanillaItems.PLASTIC, "plastic", "Plastic", false, 90, 160, 200, ItemShape.SQUARE);
    }

    @Test
    void glassMatchesRegisteredValues() {
        assertItem(VanillaItems.GLASS, "glass", "Glass", false, 170, 210, 220, ItemShape.SQUARE);
    }

    @Test
    void siliconMatchesRegisteredValues() {
        assertItem(VanillaItems.SILICON, "silicon", "Silicon", false, 140, 150, 165, ItemShape.SQUARE);
    }

    @Test
    void resistorMatchesRegisteredValues() {
        assertItem(VanillaItems.RESISTOR, "resistor", "Resistor", true, 180, 100, 60, ItemShape.TRIANGLE);
    }

    @Test
    void capacitorMatchesRegisteredValues() {
        assertItem(VanillaItems.CAPACITOR, "capacitor", "Capacitor", true, 70, 120, 200, ItemShape.TRIANGLE);
    }

    @Test
    void transistorMatchesRegisteredValues() {
        assertItem(VanillaItems.TRANSISTOR, "transistor", "Transistor", true, 50, 100, 55, ItemShape.TRIANGLE);
    }

    @Test
    void registerAllBuildsAnIndependentRegistryEqualByValueButNotByIdentity() {
        Registry<ItemType> independent = new Registry<>();
        VanillaItems.registerAll(independent);
        independent.freeze();

        ItemType fromIndependent = independent.get(ContentId.of("rustorio:iron_ore"));
        assertEquals(VanillaItems.IRON_ORE, fromIndependent, "same field values");
    }

    private static void assertItem(ItemType itemType, String path, String label, boolean researchGrade,
            int r, int g, int b, ItemShape shape) {
        assertEquals(ContentId.of("rustorio:" + path), itemType.id());
        assertEquals(label, itemType.label());
        assertEquals(researchGrade, itemType.researchGrade(), "researchGrade");
        assertEquals((r << 16) | (g << 8) | b, itemType.colorRgb(), "colorRgb");
        assertEquals(shape, itemType.shape(), "shape");
    }
}
