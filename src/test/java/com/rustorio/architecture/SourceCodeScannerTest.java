package com.rustorio.architecture;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link SourceCodeScanner}'s classification on synthetic snippets — not on real
 * {@code src/main} files, so these assertions can't be satisfied by accident just because today's
 * code happens to look a certain way: a new {@code case MINER} anywhere must grow the count, and
 * removing an {@code instanceof} must shrink it, demonstrated directly without mutating and
 * reverting real production files to prove it.
 */
class SourceCodeScannerTest {

    private static final Set<String> CONTENT = Set.of("MINER", "IRON_ORE", "BELT");
    private static final Set<String> BUILDING_TYPES = Set.of("Belt", "Miner");

    @Test
    void switchOnABareContentConstantIsCountedOnce() {
        String source = """
                class Sample {
                    Object pick(Object type) {
                        return switch (type) {
                            case MINER -> "a";
                            case IRON_ORE, BELT -> "b";
                            default -> "c";
                        };
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertEquals(1, result.contentConstantSwitches().size(),
                "one switch statement counts once regardless of how many case labels it has");
        assertTrue(result.typePatternSwitches().isEmpty());
    }

    @Test
    void switchOnASealedTypePatternIsCountedSeparatelyFromConstants() {
        String source = """
                class Sample {
                    void clear(Object building) {
                        switch (building) {
                            case Belt belt -> belt.clear();
                            case Object ignored -> { }
                        }
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertEquals(1, result.typePatternSwitches().size());
        assertTrue(result.contentConstantSwitches().isEmpty(),
                "a type-pattern switch must not also be counted as a constant switch — the two are counted separately");
    }

    @Test
    void switchOnAnUnrelatedEnumIsNotCountedAtAll() {
        // Same "bare identifier" case-label shape as a content switch, but WORKING isn't in
        // CONTENT — mirrors Palette's switch(status) in the real codebase, deliberately excluded.
        String source = """
                class Sample {
                    Object describe(Object status) {
                        return switch (status) {
                            case WORKING -> "ok";
                            default -> "bad";
                        };
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertTrue(result.contentConstantSwitches().isEmpty());
        assertTrue(result.typePatternSwitches().isEmpty());
    }

    @Test
    void addingASecondContentSwitchToAFileIncreasesTheCount() {
        String before = """
                class Sample {
                    Object pick(Object type) {
                        return switch (type) {
                            case MINER -> "a";
                            default -> "b";
                        };
                    }
                }
                """;
        String after = """
                class Sample {
                    Object pick(Object type) {
                        return switch (type) {
                            case MINER -> "a";
                            default -> "b";
                        };
                    }

                    Object pickAgain(Object type) {
                        return switch (type) {
                            case BELT -> "a";
                            default -> "b";
                        };
                    }
                }
                """;

        int countBefore = SourceCodeScanner.scanSource("Sample.java", before, CONTENT, BUILDING_TYPES)
                .contentConstantSwitches().size();
        int countAfter = SourceCodeScanner.scanSource("Sample.java", after, CONTENT, BUILDING_TYPES)
                .contentConstantSwitches().size();

        assertTrue(countAfter > countBefore,
                "a genuinely new switch-by-content-constant must grow the count — this is what "
                        + "ContentCouplingRatchetTest turns into a build failure");
    }

    @Test
    void removingAnInstanceofLowersTheCount() {
        String before = """
                class Sample {
                    boolean isBelt(Object building) {
                        return building instanceof Belt || building instanceof Miner;
                    }
                }
                """;
        String after = """
                class Sample {
                    boolean isBelt(Object building) {
                        return building instanceof Belt;
                    }
                }
                """;

        int countBefore = SourceCodeScanner.scanSource("Sample.java", before, CONTENT, BUILDING_TYPES)
                .buildingInstanceofs().size();
        int countAfter = SourceCodeScanner.scanSource("Sample.java", after, CONTENT, BUILDING_TYPES)
                .buildingInstanceofs().size();

        assertEquals(2, countBefore);
        assertEquals(1, countAfter);
        assertTrue(countAfter < countBefore,
                "a removed instanceof must lower the count — ContentCouplingRatchetTest turns this "
                        + "into a build failure demanding the baseline be updated explicitly, not silently");
    }

    @Test
    void instanceofAgainstAnUnrelatedTypeIsNotCounted() {
        String source = """
                class Sample {
                    boolean isFailure(Object result) {
                        return result instanceof SaveResult.Failure failure;
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertTrue(result.buildingInstanceofs().isEmpty());
    }

    @Test
    void commentsMentioningCaseOrInstanceofAreNotMistakenForCode() {
        String source = """
                class Sample {
                    // this used to switch (type) { case MINER -> ...; } before the refactor
                    /** an instanceof Belt check used to live here */
                    void noop() {
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertTrue(result.contentConstantSwitches().isEmpty());
        assertTrue(result.typePatternSwitches().isEmpty());
        assertTrue(result.buildingInstanceofs().isEmpty());
    }

    @Test
    void locationsReportTheOriginalLineNumberAcrossMultilineComments() {
        String source = """
                class Sample {
                    /*
                     * a multi-line block comment
                     * pushing real code further down
                     */
                    Object pick(Object type) {
                        return switch (type) {
                            case MINER -> "a";
                            default -> "b";
                        };
                    }
                }
                """;

        var result = SourceCodeScanner.scanSource("Sample.java", source, CONTENT, BUILDING_TYPES);

        assertEquals(List.of(new SourceCodeScanner.CodeLocation("Sample.java", 7)),
                result.contentConstantSwitches(),
                "the switch keyword is on line 7 in the original source; stripping the block "
                        + "comment above it must not shift that line number");
    }
}
