package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the sand/oil material and electronics recipes into {@link RecipeBook#standard()} —
 * without these entries the items stay registered but nothing can produce them.
 */
class RecipeBookTest {

    private static final RecipeBook BOOK = RecipeBook.standard();

    @Test
    void glassSmeltsFromSandInTheFurnace() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.FURNACE, VanillaItems.GLASS);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.SAND), recipe.get().ingredients());
        assertEquals(5, recipe.get().time());
        assertEquals(ContentId.of("rustorio:glass"), recipe.get().id());
    }

    @Test
    void plasticCraftsFromOilInTheFurnace() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.FURNACE, VanillaItems.PLASTIC);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.OIL), recipe.get().ingredients());
        assertEquals(8, recipe.get().time());
        assertEquals(ContentId.of("rustorio:plastic"), recipe.get().id());
    }

    @Test
    void siliconPressesFromSandAndCoal() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.PRESS, VanillaItems.SILICON);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.SAND, VanillaItems.COAL), recipe.get().ingredients());
        assertEquals(10, recipe.get().time());
        assertEquals(ContentId.of("rustorio:silicon"), recipe.get().id());
    }

    @Test
    void resistorAssemblesFromIronPlateAndPlastic() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.ASSEMBLER, VanillaItems.RESISTOR);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.IRON_PLATE, VanillaItems.PLASTIC), recipe.get().ingredients());
        assertEquals(8, recipe.get().time());
        assertEquals(ContentId.of("rustorio:resistor"), recipe.get().id());
    }

    @Test
    void capacitorAssemblesFromGlassAndPlastic() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.ASSEMBLER, VanillaItems.CAPACITOR);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.GLASS, VanillaItems.PLASTIC), recipe.get().ingredients());
        assertEquals(10, recipe.get().time());
        assertEquals(ContentId.of("rustorio:capacitor"), recipe.get().id());
    }

    @Test
    void transistorAssemblesFromSiliconAndPlastic() {
        Optional<Recipe> recipe = BOOK.findByOutput(BuildingType.ASSEMBLER, VanillaItems.TRANSISTOR);

        assertTrue(recipe.isPresent());
        assertEquals(List.of(VanillaItems.SILICON, VanillaItems.PLASTIC), recipe.get().ingredients());
        assertEquals(12, recipe.get().time());
        assertEquals(ContentId.of("rustorio:transistor"), recipe.get().id());
    }
}
