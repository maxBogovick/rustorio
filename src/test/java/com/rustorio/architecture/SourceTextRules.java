package com.rustorio.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Text-level rules that no compiler can express: they are about what the source LOOKS like, not
 * about what it does. Three of them existed only as prose in the project's rule files, where each
 * carried a hand-counted number ("3 uses of var", "0 wildcard imports") that nothing kept honest —
 * and an audit found several such numbers had already drifted from the code they described. A
 * number that a test asserts stays true by construction; a number in prose is a promise nobody
 * checks.
 *
 * <p>The third rule is the one that motivated this class. Citing a task/ADR id or a working
 * document's file name in a comment was banned after the owner called it noise, and the ban was
 * broken again in the very session that introduced it — a reminder proved not to be enforcement.
 * Existing occurrences are deliberately kept (removing them was explicitly ruled out), so the rule
 * is a ratchet over their count, not a zero.
 *
 * <p>Scanning is regex over text, like {@link SourceCodeScanner}: good enough for a ratchet, not a
 * Java parser. Accepted limitations are named on each method.
 */
final class SourceTextRules {

    /** {@code import a.b.*;} — the project has none and wants none. */
    private static final Pattern WILDCARD_IMPORT = Pattern.compile("^\\s*import\\s+[^;]*\\*\\s*;");

    /** A {@code var} declaration: the keyword followed by an identifier, not part of a longer name. */
    private static final Pattern VAR_DECLARATION =
            Pattern.compile("(?<![A-Za-z0-9_.])var\\s+[A-Za-z_][A-Za-z0-9_]*\\s*[=:]");

    /** {@code (ADR-4)}, {@code (E6-05, ...)}, {@code X-02} — a task/decision id used as a citation. */
    private static final Pattern DOC_ID = Pattern.compile("\\b(?:ADR-\\d+|[A-Z]{1,2}\\d?-\\d{1,2})\\b");

    /** A capitalized markdown file name, e.g. {@code DEV_TASKS.md} — a working document being cited. */
    private static final Pattern DOC_FILE = Pattern.compile("\\b[A-Z][A-Z0-9_]*\\.md\\b");

    /**
     * Permanent, versioned rule files. Naming one of these in a comment is a pointer to something
     * that will still exist next year, not a citation of a one-off card — the practice the ban is
     * about. Keeping this list short is the point: everything else in the repository root is
     * disposable by the owner's own decision.
     */
    private static final Set<String> PERMANENT_DOCS = Set.of("AGENTS.md", "CLAUDE.md", "GDD.md", "README.md");

    private SourceTextRules() {
    }

    /** One flagged place: file, 1-based line, and the offending text, so a failure names it exactly. */
    record TextFinding(String file, int line, String text) {
        @Override
        public String toString() {
            return file + ":" + line + "  " + text;
        }
    }

    /** Walks every {@code .java} file under {@code root} and applies {@code rule} to each. */
    static List<TextFinding> scanTree(Path root, Rule rule) {
        List<TextFinding> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                found.addAll(rule.apply(file.toString(), source));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk " + root, e);
        }
        return found;
    }

    /** A single text rule, so {@link #scanTree} stays one method instead of three near-copies. */
    @FunctionalInterface
    interface Rule {
        List<TextFinding> apply(String fileName, String source);
    }

    /**
     * Wildcard imports. Read line by line rather than from the comment-stripped source: an import
     * line can't hide inside a string literal, and keeping the raw text preserves line numbers.
     */
    static List<TextFinding> wildcardImports(String fileName, String source) {
        List<TextFinding> found = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (WILDCARD_IMPORT.matcher(lines[i]).find()) {
                found.add(new TextFinding(fileName, i + 1, lines[i].strip()));
            }
        }
        return found;
    }

    /**
     * {@code var} declarations in real code. Comments and string literals are stripped first
     * ({@link SourceCodeScanner#stripCommentsAndLiterals}), so prose about {@code var} — of which
     * this repository's javadoc has plenty — is not counted as a use of it.
     */
    static List<TextFinding> varDeclarations(String fileName, String source) {
        return matchesIn(fileName, SourceCodeScanner.stripCommentsAndLiterals(source), VAR_DECLARATION);
    }

    /**
     * Citations of task ids and working-document names — and only inside comments, because that is
     * where the practice lives; an identifier or a string that happens to look like an id is not
     * what the ban is about. Mentions of the permanent rule files ({@link #PERMANENT_DOCS}) are
     * allowed: pointing at `AGENTS.md` is a pointer to something that outlives the code.
     */
    static List<TextFinding> docIdCitations(String fileName, String source) {
        List<TextFinding> found = new ArrayList<>();
        for (TextFinding comment : commentsOf(fileName, source)) {
            Matcher ids = DOC_ID.matcher(comment.text());
            while (ids.find()) {
                found.add(new TextFinding(fileName, comment.line(), ids.group()));
            }
            Matcher docs = DOC_FILE.matcher(comment.text());
            while (docs.find()) {
                if (!PERMANENT_DOCS.contains(docs.group())) {
                    found.add(new TextFinding(fileName, comment.line(), docs.group()));
                }
            }
        }
        return found;
    }

    /**
     * Comment text with line numbers. A hand-rolled scanner rather than a regex, because three
     * things a regex gets wrong all occur here: a block comment spans lines, a {@code //} inside a
     * string is not a comment, and a text block spans lines while holding text that looks like
     * code. The text-block case is not hypothetical — fourteen files use them, mostly as JSON
     * fixtures for the mod loader, and an earlier version of this scanner assumed the codebase had
     * none and would have read their contents as comments.
     *
     * <p>What it deliberately does not handle: a comment marker inside a character literal
     * ({@code '"'}), which no file here contains. This feeds a ratchet, not a compiler.
     */
    static List<TextFinding> commentsOf(String fileName, String source) {
        List<TextFinding> comments = new ArrayList<>();
        boolean inBlock = false;
        boolean inTextBlock = false;
        boolean inString = false;
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            StringBuilder commentText = new StringBuilder();
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                boolean hasNext = c + 1 < line.length();
                if (inBlock) {
                    if (ch == '*' && hasNext && line.charAt(c + 1) == '/') {
                        inBlock = false;
                        c++;
                    } else {
                        commentText.append(ch);
                    }
                } else if (inTextBlock) {
                    if (line.startsWith("\"\"\"", c)) {
                        inTextBlock = false;
                        c += 2;
                    }
                } else if (inString) {
                    if (ch == '\\') {
                        c++; // escaped character — never ends the literal
                    } else if (ch == '"') {
                        inString = false;
                    }
                } else if (line.startsWith("\"\"\"", c)) {
                    inTextBlock = true;
                    c += 2;
                } else if (ch == '"') {
                    inString = true;
                } else if (ch == '/' && hasNext && line.charAt(c + 1) == '/') {
                    commentText.append(line, c + 2, line.length());
                    break;
                } else if (ch == '/' && hasNext && line.charAt(c + 1) == '*') {
                    inBlock = true;
                    c++;
                }
            }
            inString = false; // an ordinary literal cannot span lines; a text block can, and its state carries over
            if (!commentText.isEmpty()) {
                comments.add(new TextFinding(fileName, i + 1, commentText.toString()));
            }
        }
        return comments;
    }

    private static List<TextFinding> matchesIn(String fileName, String source, Pattern pattern) {
        List<TextFinding> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            found.add(new TextFinding(fileName, lineOf(source, matcher.start()), matcher.group().strip()));
        }
        return found;
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
}
