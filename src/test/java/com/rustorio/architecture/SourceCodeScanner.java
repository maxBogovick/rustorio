package com.rustorio.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts three specific anti-patterns in {@code src/main/java} — {@code switch} on a content
 * constant (a bare label like {@code case MINER}), {@code switch} on a sealed-hierarchy type
 * pattern (a two-token label like {@code case Belt belt}), and {@code instanceof} against a
 * concrete {@code Building} subtype — so {@link ContentCouplingRatchetTest} can fail the build the
 * moment one of these grows, before it spreads to ten call sites instead of one.
 *
 * <p><b>Why {@code switch} STATEMENTS, not {@code case} labels.</b> {@code Textures}'s
 * building-sprite lookup alone lists all 12 {@link com.rustorio.domain.BuildingType} constants
 * across its cases — counting labels would put this single method's number in the double digits
 * and hide the real signal, which is "how many separate places in the codebase branch on content
 * identity", not "how exhaustively". Adding a new {@link com.rustorio.domain.BuildingType} constant
 * to an existing multi-label {@code case} therefore does not move this count — only a genuinely
 * new {@code switch} (or {@code instanceof}) does.
 *
 * <p><b>What counts as "content".</b> Reflected, not hardcoded, so the list this class needs can't
 * silently drift from what the domain actually declares:
 * <ul>
 *   <li>Content constants — every name in {@link com.rustorio.domain.BuildingType#values()}
 *   (building kinds). {@link com.rustorio.domain.Direction}, {@code BuildingStatus} and {@code
 *   ItemShape} are deliberately excluded — geometry/runtime-state enums, not moddable content, so
 *   a {@code switch(direction)} doesn't count even though its cases are bare enum constants too.
 *   Neither items nor sprites have constants to list here anymore — {@code ItemType} is a
 *   registry-backed record and sprites are plain {@code ContentId} values, so a switch on either
 *   identity simply can't exist anymore, which is the point.</li>
 *   <li>Building subtypes — every {@linkplain Class#getPermittedSubclasses() permitted subclass}
 *   of the sealed {@code Building} interface (currently 10: Miner, Chest, Furnace, Belt, Splitter,
 *   Filter, Inserter, SpeedModule, Lab, UndergroundBelt).</li>
 * </ul>
 *
 * <p><b>{@code instanceof} counting method.</b> Every {@code instanceof} KEYWORD against a
 * building subtype counts as one place, including each one inside a composite boolean condition
 * like {@code UpgradeSpeedAction}'s {@code bare instanceof Belt || bare instanceof UndergroundBelt
 * || …} (five keywords across two lines, counted as five) — not "one place per {@code if}
 * statement". The alternative (one per statement, ignoring how many types it tests) would give a
 * lower number for this same codebase; this class picks the keyword-count reading because it
 * needs no judgment call about what counts as "one place" and is exactly what a machine can
 * already count without guessing at intent — the point of a ratchet.</p>
 *
 * <p><b>Known limitation (accepted, not a bug).</b> Comment/string stripping is line-oriented and
 * doesn't special-case text blocks ({@code """}) — none exist in {@code src/main} today. A case
 * label's first token is read without full expression parsing, which is enough for every label
 * shape this codebase currently uses ({@code case MINER}, {@code case IRON_ORE, BRONZE_ORE},
 * {@code case Belt belt}, {@code case BuildingMemento.MinerState s}) but not for guarded patterns
 * ({@code case Foo f when …}) — none of those exist here either. This is a fitness-function
 * ratchet, not a general-purpose Java parser.
 */
final class SourceCodeScanner {

    private static final Pattern SWITCH_KEYWORD = Pattern.compile("\\bswitch\\b\\s*\\(");
    private static final Pattern CASE_KEYWORD = Pattern.compile("\\bcase\\b");
    private static final Pattern INSTANCEOF = Pattern.compile(
            "\\binstanceof\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)");
    private static final Pattern CONTENT_CONSTANT_LABEL = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern TYPE_PATTERN_LABEL =
            Pattern.compile("^[A-Z][A-Za-z0-9_]*(?:\\.[A-Z][A-Za-z0-9_]*)*\\s+[a-z_][A-Za-z0-9_]*$");

    private SourceCodeScanner() {
    }

    /** Walks every {@code .java} file under {@code root} and scans each with {@link #scanSource}. */
    static ScanResult scanDirectory(Path root, Set<String> contentConstantNames, Set<String> buildingTypeNames) {
        List<CodeLocation> contentSwitches = new ArrayList<>();
        List<CodeLocation> typeSwitches = new ArrayList<>();
        List<CodeLocation> buildingInstanceofs = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                ScanResult fileResult = scanSource(root.relativize(file).toString(), source,
                        contentConstantNames, buildingTypeNames);
                contentSwitches.addAll(fileResult.contentConstantSwitches());
                typeSwitches.addAll(fileResult.typePatternSwitches());
                buildingInstanceofs.addAll(fileResult.buildingInstanceofs());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk " + root, e);
        }
        return new ScanResult(contentSwitches, typeSwitches, buildingInstanceofs);
    }

    /** Scans one file's already-read source text; split out from {@link #scanDirectory} so unit tests can feed synthetic snippets directly. */
    static ScanResult scanSource(String fileName, String rawSource,
            Set<String> contentConstantNames, Set<String> buildingTypeNames) {
        String source = stripCommentsAndLiterals(rawSource);

        List<CodeLocation> contentSwitches = new ArrayList<>();
        List<CodeLocation> typeSwitches = new ArrayList<>();
        Matcher switchMatcher = SWITCH_KEYWORD.matcher(source);
        while (switchMatcher.find()) {
            int openParenIndex = switchMatcher.end() - 1;
            int closeParenIndex = matchingCloseParen(source, openParenIndex);
            if (closeParenIndex < 0) {
                continue; // malformed/truncated snippet (unit test fragment) — nothing more to classify
            }
            int braceIndex = source.indexOf('{', closeParenIndex);
            if (braceIndex < 0) {
                continue;
            }
            String firstLabel = firstCaseLabel(source, braceIndex);
            if (firstLabel == null) {
                continue;
            }
            int line = lineOf(source, switchMatcher.start());
            if (CONTENT_CONSTANT_LABEL.matcher(firstLabel).matches() && contentConstantNames.contains(firstLabel)) {
                contentSwitches.add(new CodeLocation(fileName, line));
            } else if (TYPE_PATTERN_LABEL.matcher(firstLabel).matches()) {
                typeSwitches.add(new CodeLocation(fileName, line));
            }
        }

        List<CodeLocation> buildingInstanceofs = new ArrayList<>();
        Matcher instanceofMatcher = INSTANCEOF.matcher(source);
        while (instanceofMatcher.find()) {
            String qualifiedName = instanceofMatcher.group(1);
            String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            if (buildingTypeNames.contains(simpleName)) {
                buildingInstanceofs.add(new CodeLocation(fileName, lineOf(source, instanceofMatcher.start())));
            }
        }

        return new ScanResult(contentSwitches, typeSwitches, buildingInstanceofs);
    }

    /** The label text of the first {@code case} after a switch's opening brace, up to its first {@code ->}, {@code :} or {@code ,}. */
    private static String firstCaseLabel(String source, int braceIndex) {
        Matcher caseMatcher = CASE_KEYWORD.matcher(source);
        if (!caseMatcher.find(braceIndex)) {
            return null;
        }
        int labelStart = caseMatcher.end();
        int labelEnd = source.length();
        for (String terminator : List.of("->", ":", ",")) {
            int idx = source.indexOf(terminator, labelStart);
            if (idx >= 0 && idx < labelEnd) {
                labelEnd = idx;
            }
        }
        return source.substring(labelStart, labelEnd).strip();
    }

    /** Index of the {@code )} matching the {@code (} at {@code openParenIndex}, or -1 if unbalanced. */
    private static int matchingCloseParen(String source, int openParenIndex) {
        int depth = 0;
        for (int i = openParenIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int lineOf(String source, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Blanks out {@code //}/{@code /* *}{@code /} comments and the contents of string/char
     * literals with spaces, preserving every newline so line numbers computed on the result still
     * match the original source (D-05-style trap avoidance: a naive strip that collapses lines
     * would silently shift every location this class reports).
     */
    static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append(' ');
                        i++;
                    }
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** A source location, printed as {@code file:line} so ratchet failures name exactly what to look at. */
    record CodeLocation(String file, int line) {
        @Override
        public String toString() {
            return file + ":" + line;
        }
    }

    record ScanResult(List<CodeLocation> contentConstantSwitches, List<CodeLocation> typePatternSwitches,
            List<CodeLocation> buildingInstanceofs) {

        ScanResult {
            contentConstantSwitches = List.copyOf(contentConstantSwitches);
            typePatternSwitches = List.copyOf(typePatternSwitches);
            buildingInstanceofs = List.copyOf(buildingInstanceofs);
        }
    }

    /** Names of every {@link com.rustorio.domain.BuildingType} constant — reflected so this list can't drift from the domain itself. */
    static Set<String> contentConstantNames() {
        Set<String> names = new LinkedHashSet<>();
        for (com.rustorio.domain.BuildingType type : com.rustorio.domain.BuildingType.values()) {
            names.add(type.name());
        }
        return Set.copyOf(names);
    }

    /** Simple names of every concrete {@code Building} subtype, via the sealed interface's own permits list. */
    static Set<String> buildingSubtypeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> subtype : com.rustorio.domain.building.Building.class.getPermittedSubclasses()) {
            names.add(subtype.getSimpleName());
        }
        return Set.copyOf(names);
    }
}
