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
    private static final int CONTENT_CONSTANT_SWITCH_BASELINE = 1;
    private static final int TYPE_PATTERN_SWITCH_BASELINE = 2;
    private static final int BUILDING_INSTANCEOF_BASELINE = 21;

    @Test
    void contentConstantSwitchCountMatchesRecordedBaseline() {
        List<SourceCodeScanner.CodeLocation> found = scan().contentConstantSwitches();
        assertRatchet("switch по константам контента", CONTENT_CONSTANT_SWITCH_BASELINE, found);
    }

    @Test
    void typePatternSwitchCountMatchesRecordedBaseline() {
        List<SourceCodeScanner.CodeLocation> found = scan().typePatternSwitches();
        assertRatchet("switch по типам sealed-иерархии", TYPE_PATTERN_SWITCH_BASELINE, found);
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
     * Fails on any drift from {@code baseline} — growth is a regression (content coupling spread
     * to a new place), shrinkage means the recorded number is stale and must be updated here, in
     * the same commit that removed the place, not silently. Either way the message lists every
     * matching {@code file:line}, not just the two numbers.
     */
    private static void assertRatchet(String label, int baseline, List<SourceCodeScanner.CodeLocation> found) {
        String locations = found.stream()
                .map(SourceCodeScanner.CodeLocation::toString)
                .collect(Collectors.joining("\n  ", "\n  ", ""));
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
}
