package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import java.nio.file.Path;
import java.util.List;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import org.junit.jupiter.api.Test;

/**
 * {@link DisplayLabels} — the live bug report: with {@code waterworks} installed the build panel
 * showed two entries reading "Boiler" and two reading "Pipe", one pair vanilla and one modded,
 * with no way at all to tell which was which.
 */
class DisplayLabelsTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path WATERWORKS_MOD_DIR = Path.of("resources", "mods", "waterworks");

    @Test
    void aUniqueLabelIsLeftExactlyAsItsAuthorWroteIt() {
        DisplayLabels labels = DisplayLabels.of(VanillaBuildings.frozen().iterate());

        assertEquals("Miner", labels.of(VanillaBuildings.idFor(com.rustorio.domain.BuildingType.MINER)),
                "nothing collides in a stock game, so nothing gains a namespace — a vanilla install "
                        + "must not read like a debug dump");
        assertEquals("Boiler", labels.of(VanillaBuildings.idFor(com.rustorio.domain.BuildingType.BOILER)));
    }

    /** The collision itself, on real checked-in content rather than a fixture. */
    @Test
    void twoBuildingsSharingALabelBothGainTheirNamespace() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WATERWORKS_MOD_DIR));
        List<BuildingPrototype> all = game.buildings().iterate();
        DisplayLabels labels = DisplayLabels.of(all);

        String vanillaBoiler = labels.of(ContentId.of("rustorio:boiler"));
        String moddedBoiler = labels.of(ContentId.of("waterworks:boiler"));

        assertNotEquals(vanillaBoiler, moddedBoiler,
                "two boilers with identical captions is the defect this class exists for");
        assertEquals("Boiler (rustorio)", vanillaBoiler);
        assertEquals("Boiler (waterworks)", moddedBoiler);
    }

    /** A building whose label is unique is untouched even while a collision exists elsewhere in the same panel. */
    @Test
    void onlyTheCollidingLabelsChangeAndTheRestAreLeftAlone() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WATERWORKS_MOD_DIR));
        DisplayLabels labels = DisplayLabels.of(game.buildings().iterate());

        assertEquals("Miner", labels.of(ContentId.of("rustorio:miner")),
                "the miner collides with nothing and must stay plain");
        assertEquals("Steam engine", labels.of(ContentId.of("waterworks:steam_engine")));
    }

    @Test
    void anIdTheTableNeverSawFallsBackToTheIdItselfRatherThanBlank() {
        DisplayLabels labels = DisplayLabels.of(List.of());

        assertEquals("nosuch:building", labels.of(ContentId.of("nosuch:building")),
                "an unlabelled cell is worse than an ugly one — the player still has to identify it");
    }
}
