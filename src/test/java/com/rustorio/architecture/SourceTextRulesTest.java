package com.rustorio.architecture;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Closes a finding about the rule files themselves rather than about the code: three style rules
 * carried hand-counted numbers ("3 uses of var", "0 wildcard imports", "don't cite task ids") that
 * nothing verified. An audit found numbers of exactly that kind already wrong elsewhere in the same
 * documents, and the citation ban had been broken again days after it was written down. The point
 * of this test is that those three sentences now fail loudly instead of aging quietly.
 *
 * <p>The first half feeds synthetic snippets to the detectors, so a reader can see what counts and
 * what does not without trusting the regexes; the second half applies them to the real tree. The
 * synthetic half also means the detectors are proven to catch a violation without anyone having to
 * introduce one into a real file to check.
 */
class SourceTextRulesTest {

    private static final Path SRC = Path.of("src");
    private static final Path SRC_MAIN = Path.of("src", "main", "java");

    /**
     * Every citation that exists today, counted once per occurrence — 75 distinct ids and document
     * names, led by three working documents that no longer live in the repository at all.
     * Deliberately not zero: the owner ruled that files written under the old convention stay
     * untouched, so the rule this project can actually enforce is "no new ones", and that is a
     * ratchet. Removing occurrences is welcome — it just has to be recorded here in the same
     * commit, the way the content-coupling ratchet records its own decreases.
     *
     * <p>712 -> 709: three citations left with the comments that carried them, not by a cleanup
     * pass — the windowed game's world assembly and the headless demo's furnace-fuel note were
     * rewritten when both moved onto the shared bootstrap, and {@code Building.prototypeId}'s
     * javadoc was replaced outright when that method stopped having a (wrong) default.
     *
     * <p>709 -> 693: the {@code Tech} enum was deleted when technologies became registered
     * content, taking its own heavily-cited javadoc with it, and {@code Research}/{@code
     * ResearchView} were rewritten around ids in the same move.
     *
     * <p>708 -> 689: the fluids-and-power feature rewrote and replaced a run of domain and mod
     * files (terrain became registered content, the placement rules and world plumbing were
     * reworked), and the task-id citations they had carried went out with the comments that were
     * rewritten — a decrease, not a new one, so it is recorded here rather than fought.
     *
     * <p>689 -> 687: {@code Building.type()} was removed along with all twenty-three of its
     * implementations, and two of the javadoc blocks that went with them cited task ids. Same shape
     * as every decrease above: comments carrying citations were deleted outright, so the number
     * follows them down.
     *
     * <p>687 -> 686: one citation in {@code ContentCouplingRatchetTest}'s own history comment went
     * out while that comment was being rewritten (the two zero baselines it described became a flat
     * prohibition). The smallest possible decrease, and recorded rather than restored: the rule
     * against retroactive cleanup says not to go hunting for these, not to type one back in once the
     * comment around it is being rewritten anyway.
     */
    private static final int DOC_ID_CITATION_BASELINE = 662;

    /**
     * Explicit types are the rule; these are the exceptions that survived review. A new one is not
     * forbidden by nature, but it has to be argued for in review rather than appear unnoticed.
     *
     * <p>3 -> 2: {@code Research.Snapshot}'s {@code var copy = EnumSet.noneOf(...)} went away with
     * the {@code EnumSet} itself, once the unlocked set became a set of ids.
     */
    private static final int VAR_BASELINE = 2;

    @Test
    void wildcardImportIsDetected() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.wildcardImports("A.java", "import java.util.*;\nclass A {}\n");
        assertEquals(1, found.size(), "an import ending in .* is exactly what this rule forbids");
    }

    @Test
    void ordinaryImportIsNotMistakenForAWildcardOne() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.wildcardImports("A.java", "import java.util.List;\nclass A {}\n");
        assertTrue(found.isEmpty(), "a named import is the form the project wants, not a violation");
    }

    @Test
    void varDeclarationInCodeIsDetected() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.varDeclarations("A.java", "class A { void m() { var x = 1; } }");
        assertEquals(1, found.size(), "an inferred local type is the thing being counted");
    }

    @Test
    void theWordVarInsideACommentIsNotCountedAsAUse() {
        String source = "/** Explains why var is avoided here. */\nclass A { int x = 1; }";
        assertTrue(SourceTextRules.varDeclarations("A.java", source).isEmpty(),
                "javadoc discussing the rule must not inflate the count the rule is about");
    }

    @Test
    void taskIdCitationInACommentIsDetected() {
        String source = "/** Bumped again (E6-05, ENGINE_TASKS.md) to add the field. */\nclass A {}";
        List<SourceTextRules.TextFinding> found = SourceTextRules.docIdCitations("A.java", source);
        assertEquals(2, found.size(),
                "both halves of the practice are citations: the card id and the document's name");
    }

    @Test
    void mentionOfAPermanentRuleFileIsNotACitation() {
        String source = "// The boundary rule this enforces is stated in AGENTS.md.\nclass A {}";
        assertTrue(SourceTextRules.docIdCitations("A.java", source).isEmpty(),
                "pointing at a versioned rule file is allowed; pointing at a disposable card is not");
    }

    @Test
    void anIdInsideAStringLiteralIsNotACitation() {
        String source = "class A { String s = \"ADR-4\"; }";
        assertTrue(SourceTextRules.docIdCitations("A.java", source).isEmpty(),
                "the ban is about comments; code that legitimately carries such text is not the target");
    }

    @Test
    void textBlockContentIsNotReadAsAComment() {
        String source = "class A { String json = \"\"\"\n"
                + "        { \"note\": \"see ADR-9 // still data\" }\n"
                + "        \"\"\"; }";
        assertTrue(SourceTextRules.docIdCitations("A.java", source).isEmpty(),
                "a fixture whose text merely looks like a comment is data — this repository has "
                        + "fourteen files with text blocks, so getting this wrong would inflate the ratchet");
    }

    @Test
    void blockCommentSpanningLinesIsReadWhole() {
        String source = "/*\n * see ADR-7\n */\nclass A {}";
        assertEquals(1, SourceTextRules.docIdCitations("A.java", source).size(),
                "a citation on the second line of a block comment must not escape the scan");
    }

    @Test
    void noWildcardImportsAnywhereInSources() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.scanTree(SRC, SourceTextRules::wildcardImports);
        assertTrue(found.isEmpty(),
                "wildcard imports hide which type a name refers to; the project has zero" + list(found));
    }

    @Test
    void varUsageStaysAtItsRecordedBaseline() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.scanTree(SRC_MAIN, SourceTextRules::varDeclarations);
        assertRatchet("var declarations in src/main", VAR_BASELINE, found);
    }

    @Test
    void docIdCitationsDoNotGrow() {
        List<SourceTextRules.TextFinding> found =
                SourceTextRules.scanTree(SRC, SourceTextRules::docIdCitations);
        assertRatchet("citations of task ids and working documents", DOC_ID_CITATION_BASELINE, found);
    }

    /**
     * Fails on any drift, in either direction, the same way the content-coupling ratchet does:
     * growth is the regression being guarded against, and shrinkage means the recorded number is
     * stale and must be lowered here, in the commit that removed the occurrence, rather than
     * loosening the guard silently.
     */
    private static void assertRatchet(String label, int baseline, List<SourceTextRules.TextFinding> found) {
        if (found.size() > baseline) {
            fail(label + " grew from " + baseline + " to " + found.size()
                    + " — this is the regression this ratchet exists to catch." + list(found));
        } else if (found.size() < baseline) {
            fail(label + " dropped from " + baseline + " to " + found.size()
                    + " — lower the baseline in SourceTextRulesTest to " + found.size()
                    + " in the same commit, so the guard is never quietly loosened." + list(found));
        }
        assertTrue(true); // reached only when the count matches exactly
    }

    private static String list(List<SourceTextRules.TextFinding> found) {
        if (found.isEmpty()) {
            return "";
        }
        return found.stream()
                .map(SourceTextRules.TextFinding::toString)
                .collect(Collectors.joining("\n  ", "\n  ", ""));
    }
}
