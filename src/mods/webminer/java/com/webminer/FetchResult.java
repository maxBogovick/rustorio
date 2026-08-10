package com.webminer;

import org.jspecify.annotations.Nullable;

/**
 * What {@link FetchExecutor#submit} hands back: {@link #outcome} is what {@link WebMiner} itself
 * acts on (which of the two belt items to produce), {@link #body} is the raw response text —
 * {@code null} on {@link FetchOutcome#ERROR}, and even on {@link FetchOutcome#SUCCESS} only ever
 * held TEMPORARILY by {@code World} (see {@code World#lastResponseBody}'s own javadoc for why it
 * is not carried by the item itself, saved, or kept longer than the next fetch from the same cell
 * overwrites it).
 *
 * <p>{@code body} is capped in length by whoever fills it in ({@link com.webminer.HttpFetchExecutor}
 * today) — an unbounded response held in memory, even briefly, is a real cost a malicious or just
 * enormous endpoint could impose on the game process.
 *
 * @param statusCode the HTTP status, or 0 when there wasn't one (no request was made, or it never
 *                   got far enough to have a status). Used to be checked and thrown away, which
 *                   left a Monitor unable to tell the player anything about the response beyond
 *                   its first few hundred characters
 * @param contentType the {@code Content-Type} header, {@code null} when the server sent none —
 *                    what tells a preview whether it is looking at JSON or at a web page, without
 *                    having to guess from the first character of the body
 * @param fullBodyLength how long the body was BEFORE {@code body} was capped, so a preview can say
 *                       "48 KB, showing 8" instead of reporting the cap as if it were the size
 * @param html the page skimmed from the WHOLE document before {@code body} was capped, or {@code
 *             null} for a response that isn't HTML or came from an executor that doesn't skim.
 *             {@link HtmlDigest} explains why it cannot be computed later: the cap keeps the first
 *             few kilobytes, and on a real page those are the {@code <head>}
 * @param page the same document actually DRAWN, PNG-encoded, or {@code null} when it isn't HTML or
 *             wouldn't render. Produced in the same place and for the same reason as {@code html}:
 *             {@link PageRenderer} needs the whole document, and the whole document exists only on
 *             the fetch thread
 */
public record FetchResult(FetchOutcome outcome, @Nullable String body, int statusCode,
        @Nullable String contentType, int fullBodyLength, @Nullable HtmlDigest html,
        @Nullable RenderedPage page) {

    /**
     * Outcome and body alone, with no HTTP metadata to report — {@code statusCode} 0 and no
     * content type, exactly the "there wasn't one" case the parameter documents. This is what a
     * fake executor in a test constructs, and what any executor that isn't speaking HTTP would.
     */
    public FetchResult(FetchOutcome outcome, @Nullable String body) {
        this(outcome, body, 0, null, body == null ? 0 : body.length(), null, null);
    }

    /** An HTTP response with neither a digest nor a render — the JSON and plain-text case, where the capped body is the whole story. */
    public FetchResult(FetchOutcome outcome, @Nullable String body, int statusCode,
            @Nullable String contentType, int fullBodyLength) {
        this(outcome, body, statusCode, contentType, fullBodyLength, null, null);
    }
}
