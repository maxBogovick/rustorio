package com.rustorio.architecture;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ratchet: fails if {@link SourceCodeScanner}'s count of content-constant switches,
 * type-pattern switches, or building-{@code instanceof} in {@code src/main/java} ever exceeds the
 * recorded baseline (a regression — content coupling grew), and also fails, with a different
 * message, if a count ever drops below it (baseline gone stale — update it explicitly here rather
 * than letting the ratchet quietly loosen). Classification method and its accepted limitations are
 * documented on {@link SourceCodeScanner} itself; {@link SourceCodeScannerTest} proves that
 * method's behavior on synthetic snippets.
 *
 * <p>Not an {@code ArchUnit} rule ({@link PackageBoundaryRulesTest} covers dependency direction) —
 * this is about what happens INSIDE method bodies, which ArchUnit's bytecode-dependency model
 * doesn't see at all.
 */
class ContentCouplingRatchetTest {

    /** {@code src/main/java}, resolved from the module root ArchUnit/Gradle already run tests from. */
    private static final Path SRC_MAIN = Path.of("src", "main", "java");

    // 8 -> 5: Item became the ItemType prototype, so switch(item) has no case labels to switch
    // on anymore. WorldRenderer.oreColor and both Palette methods (itemColor/itemShape) now read
    // ItemType's own fields directly instead of switching - progress recorded here, not silently
    // absorbed into a new baseline.
    // 5 -> 4: Textures.forSprite's switch (case MINER, CHEST, ...) is gone, replaced by a plain
    // map lookup keyed by ContentId now that sprites are open content, not a closed enum.
    // 4 -> 1: BuildingCost.forType, PlacementRule.forType and Textures.forBuildingType's switches
    // are gone, replaced by reading BuildingPrototype (a registered value, not a case label) —
    // the one remaining switch is BuildingFactory.create/restore's own dispatch (which Java class
    // to build), deliberately left closed until behavior itself opens up.
    // 1 -> 0: BuildingFactory.create/restore's own dispatch switch is gone too, replaced by
    // BuildingPrototype.behavior()/restoreBehavior() (BehaviorFactory/RestoreFactory) — the last
    // content-constant switch in src/main is gone.
    //
    // 2 -> 1: BuildingFactory.clearArrivalMark's switch over Building's sealed hierarchy is gone,
    // replaced by `instanceof SettlesEachTick` (a capability check, not an exhaustive case list) —
    // the one remaining type-pattern switch is BuildingFactory.create/restore's own dispatch
    // (BuildingFactory.java:129), the same one named above, deliberately left closed until
    // behavior itself opens up.
    // 1 -> 0: BuildingFactory.restore no longer pattern-matches a sealed BuildingMemento to find
    // which prototype governs it (codec-based save format) — the save's own envelope names
    // prototypeId directly, so the last type-pattern switch in src/main is gone.
    //
    // Both counts having reached zero is why neither has a baseline constant anymore: see
    // `noSwitchInMainBranchesOnAContentConstant` below for what replaced them and why that is a
    // stronger statement than a baseline of 0.
    //
    // 21 -> 17: World's four instanceof Belt sites (place, the belt-neighbor lookup, removeBuilding,
    // restoreBuilding) became instanceof TransportNode — a capability check, not a concrete-subtype
    // check the scanner's closed building-name list still recognizes, so these four drop out of
    // this particular count (they didn't disappear from the source, they changed KIND).
    // 17 -> 12: UpgradeSpeedAction's five-class instanceof chain (Belt/UndergroundBelt/Inserter/
    // Filter/Splitter) is gone, replaced by reading BuildingPrototype.acceptsSpeedEffects() (a
    // registered value, not a case list) — the same move Phase 4 already made for cost/placement/
    // texture.
    // 11 -> 6: Chest/Furnace/UndergroundBelt/Filter/Splitter inspection text moved onto
    // InspectableBuilding; InspectionPanelLayout no longer names those five concrete classes.
    // 6 -> 8: DepositChestAction (inventory → chest) mirrors GrabChestAction's two Chest
    // instanceof sites — the hand-deposit half of the always-on inventory panel. Not a new
    // content branch: the same concrete buffer Grab already names.
    private static final int BUILDING_INSTANCEOF_BASELINE = 8;

    /**
     * A flat prohibition, not a baseline. Both of these counts reached zero (see the history above),
     * and "zero, and it stays zero" is a strictly stronger statement than "no more than the recorded
     * 0": a baseline invites the next reader to raise it by one with a note explaining why, which is
     * exactly the move this ratchet exists to prevent, whereas a prohibition has no number to edit.
     *
     * <p>The {@code instanceof} count below still has a baseline because it is genuinely not zero
     * yet — a ratchet is the right shape while a number is still coming down, and the wrong shape
     * once it has arrived.
     */
    @Test
    void noSwitchInMainBranchesOnAContentConstant() {
        assertNone("switch по константам контента", scan().contentConstantSwitches());
    }

    @Test
    void noSwitchInMainBranchesOnABuildingTypePattern() {
        assertNone("switch по типам иерархии Building", scan().typePatternSwitches());
    }

    @Test
    void buildingInstanceofCountMatchesRecordedBaseline() {
        List<SourceCodeScanner.CodeLocation> found = scan().buildingInstanceofs();
        assertRatchet("instanceof по конкретным типам зданий", BUILDING_INSTANCEOF_BASELINE, found);
    }

    private static SourceCodeScanner.ScanResult scan() {
        return SourceCodeScanner.scanDirectory(SRC_MAIN,
                SourceCodeScanner.contentConstantNames(), SourceCodeScanner.buildingSubtypeNames());
    }

    /**
     * Fails if the construct exists at all, naming every {@code file:line}. Unlike {@link
     * #assertRatchet}, there is nothing here to update on failure — the fix is to remove the
     * construct, which is the whole point of stating it this way once the count has reached zero.
     */
    private static void assertNone(String label, List<SourceCodeScanner.CodeLocation> found) {
        assertTrue(found.isEmpty(),
                label + " снова появился в src/main (" + found.size() + " шт.) — это закрытость по "
                        + "типу контента, ради снятия которой строится движок: добавление контента "
                        + "должно быть данными, а не правкой кода. Места:" + locationsOf(found));
    }

    /**
     * Fails on any drift from {@code baseline} — growth is a regression (content coupling spread
     * to a new place), shrinkage means the recorded number is stale and must be updated here, in
     * the same commit that removed the place, not silently. Either way the message lists every
     * matching {@code file:line}, not just the two numbers.
     */
    private static void assertRatchet(String label, int baseline, List<SourceCodeScanner.CodeLocation> found) {
        String locations = locationsOf(found);
        if (found.size() > baseline) {
            org.junit.jupiter.api.Assertions.fail(
                    label + " grew from " + baseline + " to " + found.size()
                            + " — this is the regression this ratchet exists to catch. Locations:" + locations);
        } else if (found.size() < baseline) {
            org.junit.jupiter.api.Assertions.fail(
                    label + " dropped from " + baseline + " to " + found.size()
                            + " — update the baseline in ContentCouplingRatchetTest to " + found.size()
                            + " (a decrease is progress, but it must be recorded explicitly, not silently)."
                            + " Locations:" + locations);
        }
        assertTrue(true); // reached only when found.size() == baseline
    }

    /** Every match as {@code file:line}, one per line — a count alone tells nobody where to look. */
    private static String locationsOf(List<SourceCodeScanner.CodeLocation> found) {
        return found.stream()
                .map(SourceCodeScanner.CodeLocation::toString)
                .collect(Collectors.joining("\n  ", "\n  ", ""));
    }
}
