package com.webminer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A fetched response as a handful of lines a player can read at a glance — what {@link Monitor}
 * shows instead of the response body's first few hundred characters.
 *
 * <p>The live report is what those characters actually are. A monitor watching a miner pointed at
 * a web page filled its whole panel with {@code <!doctype html>}, {@code <meta charset>} and three
 * {@code <link rel="icon">}: the least informative part of any HTML document, and six rows of it.
 * Raw-body-first only ever looked reasonable on a JSON API, where the real fields happen to sit at
 * the front.
 *
 * <p>So the body is skimmed by kind rather than quoted from the top. The kind comes from {@code
 * Content-Type} when the server sent one and from the body's first non-blank character when it
 * didn't — servers that mislabel JSON as {@code text/plain} exist, and so do those that send no
 * header at all.
 *
 * <p><b>Everything here is best-effort and must stay that way.</b> These lines are built while a
 * panel is being drawn: there is nowhere sensible above this to catch an exception, so malformed
 * input degrades to a poorer preview and never throws.
 *
 * <p>Text comes out as whatever characters the document holds, typographic ones included. Folding
 * an em dash down to something the HUD font can draw is {@code com.graphics.render.HudText}'s job,
 * not this class's: which glyphs exist is a property of the panel doing the drawing, and a mod
 * that second-guessed it would be wrong the day that font is replaced.
 */
final class ResponsePreview {

    /** How many lines of actual CONTENT follow the summary. The panel wraps each of them and caps the whole thing anyway; more than this is just more to scroll past. */
    private static final int CONTENT_LINES = 4;
    /**
     * Longest a page's readable text is kept in memory per fetched cell. Generous next to the four
     * rows a panel shows, and small next to the page it came from: the point is to survive the body
     * cap, not to hold the document.
     */
    private static final int MAX_DIGEST_CHARS = 2000;
    /** Longest a single JSON value is shown before it is cut — a field holding an entire base64 blob must not eat every content line. */
    private static final int MAX_VALUE_CHARS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponsePreview() {
    }

    /** What a monitor with nothing behind it says — a fact about the building, kept beside the other lines it can show. */
    static final String NOTHING_YET = "(no response seen behind this monitor yet)";

    /** A response on its own — the same thing as an attempt that succeeded, and what a test with one fixture in hand means. */
    static List<String> of(@Nullable FetchResult response) {
        return of(response, response);
    }

    /**
     * What the last fetch did, and what there is to show for it.
     *
     * <p>Two arguments because a failure and a body are separate facts with separate lifetimes. A
     * monitor behind a miner pointed at a mistyped URL used to say "no response seen yet" for as
     * long as the mistake lasted — the same sentence a miner that simply hasn't fetched yet shows,
     * so the one thing the player needed to know was the one thing indistinguishable from normal.
     * Meanwhile the last good body has to survive a failure, because the interpreter in front is
     * still reading fields out of it and a blank panel every flaky minute helps nobody.
     *
     * @param attempt the last fetch to finish, whatever it did
     * @param success the last fetch that brought a body, which may be older than {@code attempt}
     */
    static List<String> of(@Nullable FetchResult attempt, @Nullable FetchResult success) {
        if (attempt == null && success == null) {
            return List.of(NOTHING_YET);
        }
        if (attempt != null && attempt.outcome() == FetchOutcome.ERROR) {
            List<String> lines = new ArrayList<>();
            lines.add(failureLine(attempt));
            if (success == null || success.body() == null) {
                lines.add("(nothing has ever been fetched from this URL)");
                return lines;
            }
            // Labelled, not implied: showing an old body under a fresh failure without saying so
            // is how a player concludes the endpoint is fine.
            lines.add("Last successful response:");
            lines.addAll(describe(success, success.body()));
            return lines;
        }
        FetchResult response = success == null ? attempt : success;
        if (response == null || response.body() == null) {
            return List.of(NOTHING_YET);
        }
        return describe(response, response.body());
    }

    /** Why the last fetch brought nothing. The status when the server answered with one, and plain language when nothing answered at all. */
    private static String failureLine(FetchResult attempt) {
        return attempt.statusCode() > 0
                ? "Fetch failed: HTTP " + attempt.statusCode()
                : "Fetch failed: no response (bad URL, unreachable host, or timeout)";
    }

    private static List<String> describe(FetchResult response, String body) {
        List<String> lines = new ArrayList<>();
        Kind kind = kindOf(response.contentType(), body);
        lines.add(summary(response, kind, body));
        switch (kind) {
            case JSON -> appendJson(lines, body);
            case HTML -> appendHtml(lines, response, body);
            case TEXT -> appendText(lines, body);
        }
        return lines;
    }

    private enum Kind { JSON, HTML, TEXT }

    /**
     * The header first, the body's own shape second. A {@code Content-Type} is what the server
     * says it sent; the first character is a guess, and only worth making when there is nothing
     * better — but it is worth making, because plenty of endpoints send JSON with no header at all.
     */
    private static Kind kindOf(@Nullable String contentType, String body) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("json")) {
                return Kind.JSON;
            }
            if (lower.contains("html") || lower.contains("xml")) {
                return Kind.HTML;
            }
            if (lower.startsWith("text/")) {
                return Kind.TEXT;
            }
        }
        char first = firstNonBlank(body);
        if (first == '{' || first == '[') {
            return Kind.JSON;
        }
        return first == '<' ? Kind.HTML : Kind.TEXT;
    }

    private static char firstNonBlank(String body) {
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) > ' ') {
                return body.charAt(i);
            }
        }
        return ' ';
    }

    /** The one line that is always there: what came back, how big it was, and whether what follows is all of it. */
    private static String summary(FetchResult response, Kind kind, String body) {
        StringBuilder summary = new StringBuilder("Response: ");
        if (response.statusCode() > 0) {
            summary.append(response.statusCode()).append(' ');
        }
        summary.append(switch (kind) {
            case JSON -> "JSON";
            case HTML -> "HTML";
            case TEXT -> "text";
        });
        summary.append(", ").append(humanSize(response.fullBodyLength()));
        if (response.fullBodyLength() > body.length()) {
            summary.append(" (showing ").append(humanSize(body.length())).append(')');
        }
        return summary.toString();
    }

    private static String humanSize(int chars) {
        return chars < 1024 ? chars + " B" : (chars / 1024) + " KB";
    }

    /**
     * The object's own top-level fields, which is what a player pointed a miner at an API to see.
     * An array reports its length and skims its first element instead — the elements of an array
     * are the interesting part, and the array itself has no fields to name.
     */
    private static void appendJson(List<String> lines, String body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            lines.add("(not valid JSON after all — showing raw text)");
            appendText(lines, body);
            return;
        }
        if (root == null || root.isMissingNode()) {
            appendText(lines, body);
            return;
        }
        if (root.isArray()) {
            lines.add("Array of " + root.size() + ", first element:");
            appendFields(lines, root.isEmpty() ? root : root.get(0));
            return;
        }
        appendFields(lines, root);
    }

    private static void appendFields(List<String> lines, JsonNode node) {
        if (!node.isObject()) {
            lines.add("  " + shorten(node.asText()));
            return;
        }
        int shown = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext() && shown < CONTENT_LINES) {
            Map.Entry<String, JsonNode> field = fields.next();
            lines.add("  " + field.getKey() + ": " + describe(field.getValue()));
            shown++;
        }
        if (fields.hasNext()) {
            lines.add("  ... and more fields (use an interpreter to pick one)");
        }
    }

    /** A nested object or array is named by its shape, not dumped: dumping is how the panel filled up with punctuation in the first place. */
    private static String describe(JsonNode value) {
        if (value.isObject()) {
            return "{" + value.size() + " fields}";
        }
        if (value.isArray()) {
            return "[" + value.size() + "]";
        }
        return shorten(value.asText());
    }

    private static String shorten(String value) {
        return value.length() <= MAX_VALUE_CHARS ? value : value.substring(0, MAX_VALUE_CHARS - 3) + "...";
    }

    /**
     * The page's title, then the text a reader would actually see — {@code Element#text()} already
     * means "visible text", so {@code <head>}, scripts and styles are gone without this class
     * naming them.
     *
     * <p>jsoup, not the hand-rolled skimmer this had first (owner's decision, 08.08.2026). The
     * skimmer worked on the pages it was written against and was a liability on the rest: real
     * pages carry unclosed tags, attributes holding {@code >}, comments wrapping markup and
     * entities beyond the dozen worth hardcoding, and each of those is a way for tag soup to reach
     * a player's panel. jsoup is a parser with an error-recovery model, which is the part that
     * cannot reasonably be written here.
     *
     * <p>{@link Jsoup#parseBodyFragment} rather than {@link Jsoup#parse}: a response body is not
     * always a whole document — an endpoint returning a fragment is ordinary — and the fragment
     * parser handles a complete document just as well.
     */
    private static void appendHtml(List<String> lines, FetchResult response, String body) {
        // The digest, when there is one, was skimmed from the WHOLE document before the body was
        // capped — which is the only way a big page has any readable text to show at all. Parsing
        // the capped body is the fallback for a response that arrived without one (a fake executor
        // in a test, or a fetcher that isn't this mod's HTTP one).
        HtmlDigest digest = response.html() == null ? digestHtml(body) : response.html();
        if (!digest.title().isBlank()) {
            lines.add("Title: " + shorten(digest.title()));
        }
        String visible = digest.text();
        // The title tends to repeat as the page's first heading; showing it twice in four lines
        // wastes the panel on a string the player has just read.
        if (!digest.title().isBlank() && visible.startsWith(digest.title())) {
            visible = visible.substring(digest.title().length());
        }
        lines.add(collapseOrSayEmpty(visible));
    }

    /**
     * A whole HTML document reduced to {@link HtmlDigest}. Called by {@link HttpFetchExecutor} on
     * its background thread with the full body, before any cap is applied — see {@link HtmlDigest}
     * for why that is the only place it can produce anything.
     *
     * <p>{@link Jsoup#parse}, not {@code parseBodyFragment}: this is handed complete documents, and
     * the document parser is the one that puts {@code <title>} where {@link Document#title} finds
     * it. Failure of any kind degrades to an empty digest — the preview then says the response has
     * no readable text, which is a poor answer but a true one, and never throws at a caller with
     * nowhere to catch.
     */
    static HtmlDigest digestHtml(String fullBody) {
        try {
            Document document = Jsoup.parse(fullBody);
            Element body = document.body();
            String text = collapse(body == null ? "" : body.text());
            return new HtmlDigest(document.title().trim(),
                    text.length() <= MAX_DIGEST_CHARS ? text : text.substring(0, MAX_DIGEST_CHARS));
        } catch (RuntimeException e) {
            return new HtmlDigest("", "");
        }
    }

    private static void appendText(List<String> lines, String body) {
        lines.add(collapseOrSayEmpty(body));
    }

    /**
     * The readable text as ONE line, left for the panel to wrap.
     *
     * <p>Chopping it into panel-sized pieces here is what this did first, and it looked worse than
     * doing nothing: the panel re-wraps by measured pixel width, so a chunk cut at sixty characters
     * lands just over a row and spills a word or two onto a nearly empty row of its own. Only one
     * of the two can own the wrapping, and it has to be the one that knows how wide a row is.
     */
    private static String collapseOrSayEmpty(String text) {
        String collapsed = collapse(text);
        return collapsed.isEmpty() ? "(no readable text in the response)" : collapsed;
    }

    /** Runs of whitespace to a single space, trimmed — the panel measures rows in pixels and indentation buys nothing. */
    private static String collapse(String text) {
        StringBuilder collapsed = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= ' ') {
                pendingSpace = !collapsed.isEmpty();
                continue;
            }
            if (pendingSpace) {
                collapsed.append(' ');
                pendingSpace = false;
            }
            collapsed.append(c);
        }
        return collapsed.toString();
    }
}
