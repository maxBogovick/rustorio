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
 */
public record FetchResult(FetchOutcome outcome, @Nullable String body) {
}
