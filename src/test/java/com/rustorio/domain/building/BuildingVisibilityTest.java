package com.rustorio.domain.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Research;
import com.rustorio.domain.world.BuildingVisibilityContext;
import com.rustorio.domain.world.PlacementDiscovery;
import com.rustorio.domain.world.ProductionStats;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingVisibilityTest {

    private static final Path RUSTORIO = Path.of("resources", "mods", "rustorio");

    private static LoadedGame loadRustorio() {
        return ModLoader.loadAll(List.of(RUSTORIO));
    }

    private static BuildingPrototype prototype(LoadedGame game, BuildingType type) {
        return game.buildings().get(VanillaBuildings.idFor(type));
    }

    private static BuildingVisibilityContext context(LoadedGame game, ProductionStats stats,
            PlacementDiscovery discovery) {
        return BuildingVisibilityContext.of(stats, new Research(game.techs()), discovery.asSet(), game.items(),
                game.buildings(), game.techs());
    }

    @Test
    void absentRuleMeansAlwaysAvailable() {
        LoadedGame game = loadRustorio();
        BuildingPrototype miner = prototype(game, BuildingType.MINER);
        BuildingVisibilityContext empty = context(game, new ProductionStats(), new PlacementDiscovery());

        assertTrue(BuildingVisibility.isAvailable(miner, empty));
    }

    @Test
    void producedRuleUnlocksAfterTheFirstItem() {
        LoadedGame game = loadRustorio();
        BuildingPrototype press = prototype(game, BuildingType.PRESS);
        ProductionStats stats = new ProductionStats();
        BuildingVisibilityContext before = context(game, stats, new PlacementDiscovery());
        assertFalse(BuildingVisibility.isAvailable(press, before));

        stats.onProduced(0, game.items().get(VanillaItems.IRON_PLATE.id()));
        BuildingVisibilityContext after = context(game, stats, new PlacementDiscovery());
        assertTrue(BuildingVisibility.isAvailable(press, after));
    }

    @Test
    void placedRuleNeedsARecordedPlacement() {
        LoadedGame game = loadRustorio();
        VisibilityRule rule = new VisibilityRule.Placed(ContentId.of("rustorio:generator"));
        BuildingPrototype electric = prototype(game, BuildingType.ELECTRIC_MINER);
        PlacementDiscovery discovery = new PlacementDiscovery();
        BuildingVisibilityContext before = context(game, new ProductionStats(), discovery);
        assertFalse(rule.satisfied(before));
        assertFalse(BuildingVisibility.isAvailable(electric, before));

        discovery.record(ContentId.of("rustorio:generator"));
        BuildingVisibilityContext after = context(game, new ProductionStats(), discovery);
        assertTrue(rule.satisfied(after));
        assertTrue(BuildingVisibility.isAvailable(electric, after));
    }

    @Test
    void allRuleNeedsEveryClause() {
        LoadedGame game = loadRustorio();
        VisibilityRule rule = new VisibilityRule.All(List.of(
                new VisibilityRule.Produced(VanillaItems.IRON_PLATE.id()),
                new VisibilityRule.Placed(ContentId.of("rustorio:pole"))));
        ProductionStats stats = new ProductionStats();
        stats.onProduced(0, game.items().get(VanillaItems.IRON_PLATE.id()));
        PlacementDiscovery discovery = new PlacementDiscovery();
        BuildingVisibilityContext partial = context(game, stats, discovery);
        assertFalse(rule.satisfied(partial));

        discovery.record(ContentId.of("rustorio:pole"));
        assertTrue(rule.satisfied(context(game, stats, discovery)));
    }

    @Test
    void lockedHintNamesTheMissingRequirement() {
        LoadedGame game = loadRustorio();
        BuildingPrototype press = prototype(game, BuildingType.PRESS);
        BuildingVisibilityContext empty = context(game, new ProductionStats(), new PlacementDiscovery());

        String hint = VisibilityHint.lockedMessage(press, empty, "en").orElseThrow();

        assertTrue(hint.startsWith("Locked — "));
        assertTrue(hint.contains("Iron Plate"), hint);
    }
}
