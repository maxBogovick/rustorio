package com.webminer;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ResponsePreview} — the lines a {@link Monitor} shows about a fetched response.
 *
 * <p>The report these close is a screenshot of a monitor watching a miner pointed at a web page:
 * the panel's whole body was {@code <!doctype html>}, {@code <meta charset="utf-8">} and three
 * {@code <link rel="icon">}, because the building handed over the response body and the panel
 * quoted it from the top. The head of an HTML document is boilerplate by construction, so quoting
 * from the top can only ever show boilerplate — the fixture below is that same head, trimmed.
 */
class ResponsePreviewTest {

    /** The opening of a real page, in the same shape the screenshot showed — every element here is the kind that used to fill the panel. */
    private static final String HTML_PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8" />
            <link rel="icon" href="./favicon.ico" sizes="any" />
            <link rel="icon" href="./favicon-32x32.png" sizes="32x32" type="image/png" />
            <link rel="apple-touch-icon" href="./apple-touch-icon.png" />
            <title>Example Domain</title>
            <style>body { font-family: sans-serif; color: #333; }</style>
            <script>window.dataLayer = window.dataLayer || [];</script>
            </head>
            <body>
            <h1>Example Domain</h1>
            <p>This domain is for use in illustrative examples in documents. You may use this
            domain in literature without prior coordination or asking for permission.</p>
            </body>
            </html>
            """;

    @Test
    void anHtmlPageShowsItsTitleAndItsReadableTextAndNoneOfItsHead() {
        FetchResult response = new FetchResult(FetchOutcome.SUCCESS, HTML_PAGE, 200, "text/html; charset=utf-8", HTML_PAGE.length());

        List<String> preview = ResponsePreview.of(response);

        assertTrue(preview.getFirst().startsWith("Response: 200 HTML"),
                "the first line must say what came back: " + preview.getFirst());
        assertTrue(preview.contains("Title: Example Domain"), "the page's own title is the one line worth quoting: " + preview);
        assertTrue(String.join(" ", preview).contains("This domain is for use in illustrative examples"),
                "the readable text is what a monitor is for: " + preview);
        for (String line : preview) {
            assertFalse(line.contains("<meta") || line.contains("<link") || line.contains("doctype"),
                    "the document's head is exactly what must never reach the panel: " + line);
            assertFalse(line.contains("font-family") || line.contains("dataLayer"),
                    "style and script bodies are code, not text a player reads: " + line);
        }
    }

    /**
     * Markup that hides where a naive strip cannot see it — inside an attribute value, inside a
     * comment — never reaches the panel, and the entity table is the parser's rather than a
     * hand-picked dozen.
     *
     * <p>This is the test that says what the jsoup dependency bought (owner's decision,
     * 08.08.2026). Each fixture below defeats the skimmer this class had first: {@code onclick}
     * carries a {@code >} that ends its tag early and a {@code <b>} that a tag-stripper would then
     * emit as text, the {@code <link>} is real markup inside a comment, {@code <b>} is never
     * closed, and {@code &frac12;} is outside any table worth maintaining by hand.
     */
    @Test
    void markupHidingInAttributesAndCommentsNeverReachesThePanel() {
        String page = """
                <!doctype html><html><head><title>Broken &amp; Real</title>
                <!-- <link rel="icon" href="./favicon.ico"> --></head>
                <body onclick="if (a > b) { alert('<b>') }">
                <p>An attribute holding a &gt; sign, an unclosed <b>bold run
                <p>and one entity no hand-written table has: &frac12;
                <script>var s = "</p>";</script></body></html>""";

        List<String> preview = ResponsePreview.of(new FetchResult(FetchOutcome.SUCCESS, page, 200, "text/html", page.length()));
        String text = String.join(" ", preview);

        assertEquals("Title: Broken & Real", preview.get(1), "the parser decodes the title's entity: " + preview);
        assertTrue(text.contains("An attribute holding a > sign"), "the page's own text must survive: " + text);
        assertTrue(text.contains("unclosed bold run"), "an unclosed tag must not swallow the text after it: " + text);
        assertTrue(text.contains("½"), "entity decoding is the parser's job, not a hand-picked list: " + text);
        assertFalse(text.contains("alert") || text.contains("var s"),
                "script text and attribute code are not things a player reads: " + text);
        assertFalse(text.contains("favicon"), "markup inside a comment is still markup: " + text);
        assertFalse(text.contains("<") || text.contains(">") && !text.contains("a > sign"),
                "no tag fragment may leak into the panel: " + text);
    }

    /**
     * A page whose readable text begins past the body cap still shows that text.
     *
     * <p>This is the second screenshot, and the second lesson from it. The skimmer was right and
     * the panel said "(no readable text in the response)" on a 214 KB page, because {@code
     * HttpFetchExecutor} keeps the first 8 KB and the first 8 KB of a real page is {@code <head>}:
     * preload links, inline styles, scripts, and on a big enough page not even the {@code <title>}.
     * Skimming after the cap can only ever find head.
     *
     * <p>So the fixture is built the way the executor sees one: a head far larger than the cap, a
     * body after it, and the digest taken from the WHOLE document while the capped body carries
     * only the head — exactly the pair {@code HttpFetchExecutor} now produces.
     */
    @Test
    void aPageWhoseTextStartsBeyondTheBodyCapIsStillReadable() {
        String head = "<link rel=\"preload\" href=\"/static/chunk.js\" as=\"script\" />\n".repeat(200);
        String page = "<!doctype html><html><head><title>Late Title</title>" + head
                + "</head><body><h1>Late Title</h1><p>The text a reader came for.</p></body></html>";
        String capped = page.substring(0, 8192);
        assertFalse(capped.contains("The text a reader came for"),
                "fixture must actually hide its text past the cap, or this test proves nothing");

        List<String> preview = ResponsePreview.of(new FetchResult(FetchOutcome.SUCCESS, capped, 200,
                "text/html", page.length(), ResponsePreview.digestHtml(page), null));

        assertTrue(preview.contains("Title: Late Title"), "the title lives in the head, past the cap: " + preview);
        assertTrue(String.join(" ", preview).contains("The text a reader came for"),
                "the readable text must survive a cap it starts after: " + preview);
    }

    /** Fields, not punctuation — a JSON object is the case that used to look acceptable, and it has to get better rather than merely stay the same. */
    @Test
    void aJsonObjectShowsItsTopLevelFieldsAndNamesTheShapeOfNestedOnes() {
        String body = """
                {"login": "octocat", "id": 583231, "plan": {"name": "pro", "seats": 3},
                 "repos": [1, 2, 3], "type": "User", "site_admin": false}""";
        FetchResult response = new FetchResult(FetchOutcome.SUCCESS, body, 200, "application/json", body.length());

        List<String> preview = ResponsePreview.of(response);

        assertTrue(preview.getFirst().startsWith("Response: 200 JSON"), preview.getFirst());
        assertTrue(preview.contains("  login: octocat"), "top-level fields are the point: " + preview);
        assertTrue(preview.contains("  plan: {2 fields}"), "a nested object is named by its shape, not dumped: " + preview);
        assertTrue(preview.getLast().contains("interpreter"),
                "a body with more fields than fit must point at the building that reads one: " + preview);
    }

    /** A server that sends JSON without saying so is common enough that guessing from the body is worth doing. */
    @Test
    void jsonWithNoContentTypeHeaderIsStillRecognisedFromItsFirstCharacter() {
        String body = "{\"greeting\": \"hello\"}";

        List<String> preview = ResponsePreview.of(new FetchResult(FetchOutcome.SUCCESS, body));

        assertTrue(preview.getFirst().contains("JSON"), "an unlabelled body is judged by its shape: " + preview.getFirst());
        assertTrue(preview.contains("  greeting: hello"), preview.toString());
    }

    /** The summary tells the truth about a capped body: the size is the page's, not the cap's. */
    @Test
    void aCappedBodyReportsTheRealSizeAndSaysHowMuchIsShown() {
        String shown = "x".repeat(8192);

        List<String> preview = ResponsePreview.of(
                new FetchResult(FetchOutcome.SUCCESS, shown, 200, "text/plain", 49_000));

        assertEquals("Response: 200 text, 47 KB (showing 8 KB)", preview.getFirst());
    }

    /**
     * Malformed input degrades, never throws. These lines are built while the panel is being drawn
     * and there is nowhere above this to catch anything — an endpoint returning garbage must cost a
     * duller preview, not a crash in the middle of a frame.
     */
    @Test
    void bodiesThatAreBrokenOrEmptyDegradeInsteadOfThrowing() {
        List<String> brokenJson = ResponsePreview.of(
                new FetchResult(FetchOutcome.SUCCESS, "{\"login\": \"octocat\"", 200, "application/json", 19));
        List<String> unclosedHtml = ResponsePreview.of(
                new FetchResult(FetchOutcome.SUCCESS, "<html><body><script>oops(", 200, "text/html", 25));
        List<String> nothing = ResponsePreview.of(new FetchResult(FetchOutcome.SUCCESS, "", 204, null, 0));

        assertFalse(brokenJson.isEmpty(), "a truncated JSON body still deserves a line");
        assertFalse(unclosedHtml.isEmpty(), "an unclosed script tag must not swallow the preview whole");
        assertFalse(nothing.isEmpty(), "an empty body is itself a fact worth one line");
    }

    /**
     * A failed fetch says so, and says what failed.
     *
     * <p>A miner pointed at a mistyped URL used to leave its monitor reading "no response seen
     * behind this monitor yet" — the same words a miner that simply hasn't fetched yet shows. The
     * one state a player needs to notice was rendered identical to the most ordinary one.
     */
    @Test
    void aFailedFetchIsReportedWithItsStatusInsteadOfLookingLikeSilence() {
        FetchResult notFound = new FetchResult(FetchOutcome.ERROR, null, 404, null, 0);

        List<String> preview = ResponsePreview.of(notFound, null);

        assertEquals("Fetch failed: HTTP 404", preview.getFirst(), preview.toString());
        assertTrue(preview.getLast().contains("nothing has ever been fetched"),
                "a miner that never succeeded must say so rather than imply an old body: " + preview);
    }

    /** Nothing answered at all — no status to quote, so the sentence names the causes a player can act on. */
    @Test
    void aFetchThatNeverReachedAServerSaysThatRatherThanQuotingAStatus() {
        List<String> preview = ResponsePreview.of(new FetchResult(FetchOutcome.ERROR, null), null);

        assertTrue(preview.getFirst().startsWith("Fetch failed: no response"), preview.getFirst());
    }

    /**
     * A failure after a success shows both, and labels which is which. Hiding the old body would
     * blank the panel on every flaky minute; showing it unlabelled under a fresh failure is how a
     * player concludes the endpoint is healthy when it is not.
     */
    @Test
    void aFailureAfterASuccessShowsTheOldBodyButNamesItAsTheOldOne() {
        String body = "{\"greeting\": \"hello\"}";
        FetchResult success = new FetchResult(FetchOutcome.SUCCESS, body, 200, "application/json", body.length());
        FetchResult failure = new FetchResult(FetchOutcome.ERROR, null, 500, null, 0);

        List<String> preview = ResponsePreview.of(failure, success);

        assertEquals("Fetch failed: HTTP 500", preview.getFirst(), preview.toString());
        assertTrue(preview.contains("Last successful response:"),
                "the surviving body must be labelled as the older fact it is: " + preview);
        assertTrue(preview.contains("  greeting: hello"), "and it must still be shown: " + preview);
    }

    /** No response at all is a different sentence from an empty one, and the monitor has always said so. */
    @Test
    void noResponseYetSaysSoRatherThanShowingAnEmptyPanel() {
        assertEquals(List.of(ResponsePreview.NOTHING_YET), ResponsePreview.of(null));
    }
}
