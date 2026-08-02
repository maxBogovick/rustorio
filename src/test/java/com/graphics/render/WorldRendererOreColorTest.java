package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * {@link WorldRenderer#oreColor} regression: a map loaded through the mod pipeline resolves its
 * ore {@link ItemType}s from that map's own item {@code Registry} — a DIFFERENT instance than
 * {@link VanillaItems}'s constants even for the very same vanilla id (every item coming out of a
 * loaded mod, including the vanilla mod itself, is a freshly constructed {@link ItemType}).
 * {@code oreColor} used to compare with {@code ==}, so it never recognized bronze or coal loaded
 * this way and drew them with the same flat default tint as iron ({@code bronzeOre…} below was red
 * on that code). It also never looked at a non-vanilla ore's own color at all, so two different
 * modded ores drew identically too ({@code aModdedOre…} below was red as well). The iron-ore test
 * does not reproduce either failure — the old default tint happened to already be iron's color —
 * it only guards the new explicit {@code IRON_ORE} branch against a future regression.
 */
class WorldRendererOreColorTest {

    /** Stand-in for the separate instance a mod-loaded Registry hands back for the same id. */
    private static final ItemType RELOADED_IRON_ORE = new ItemType(
            VanillaItems.IRON_ORE.id(), VanillaItems.IRON_ORE.label(), VanillaItems.IRON_ORE.researchGrade(),
            VanillaItems.IRON_ORE.colorRgb(), VanillaItems.IRON_ORE.shape());

    @Test
    void ironOreFromADifferentRegistryInstanceStillGetsIronOresColor() {
        assertNotSame(VanillaItems.IRON_ORE, RELOADED_IRON_ORE,
                "the fixture must reproduce a genuinely different instance, or this test proves nothing");

        assertEquals(WorldRenderer.oreColor(VanillaItems.IRON_ORE), WorldRenderer.oreColor(RELOADED_IRON_ORE),
                "an ore loaded from a map's own item registry must be colored the same as the vanilla constant sharing its id");
    }

    @Test
    void bronzeOreFromADifferentRegistryInstanceStillGetsBronzeOresColor() {
        ItemType reloadedBronzeOre = new ItemType(
                VanillaItems.BRONZE_ORE.id(), VanillaItems.BRONZE_ORE.label(), VanillaItems.BRONZE_ORE.researchGrade(),
                VanillaItems.BRONZE_ORE.colorRgb(), VanillaItems.BRONZE_ORE.shape());

        assertEquals(WorldRenderer.oreColor(VanillaItems.BRONZE_ORE), WorldRenderer.oreColor(reloadedBronzeOre));
        assertNotEquals(WorldRenderer.oreColor(VanillaItems.IRON_ORE), WorldRenderer.oreColor(reloadedBronzeOre),
                "iron and bronze must still read as different colors once loaded from a map's own registry");
    }

    @Test
    void aModdedOreNotAmongTheVanillaThreeGetsItsOwnColorInsteadOfTheFlatDefault() {
        ItemType copperOre = new ItemType(ContentId.of("examplemod:copper_ore"), "Copper Ore", false, 0x00FF00, ItemShape.CIRCLE);
        ItemType tinOre = new ItemType(ContentId.of("examplemod:tin_ore"), "Tin Ore", false, 0xFF00FF, ItemShape.CIRCLE);

        Color copperTint = WorldRenderer.oreColor(copperOre);
        Color tinTint = WorldRenderer.oreColor(tinOre);

        assertNotEquals(copperTint, tinTint, "two different modded ores with different colors must not render identically");
        assertNotEquals(WorldRenderer.oreColor(VanillaItems.IRON_ORE), copperTint,
                "a modded ore must not fall back to the vanilla iron-ore default tint");
    }
}
