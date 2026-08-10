package com.webminer;

/**
 * A web page reduced to the two things a {@link Monitor} shows: its title and the start of its
 * readable text.
 *
 * <p>It exists because of WHERE it has to be computed. {@link FetchResult#body} is capped at a few
 * kilobytes so an arbitrary endpoint cannot spend the game's memory, and that cap takes the FIRST
 * few kilobytes — which, on a real page, is the {@code <head>}: meta tags, preload links, inline
 * styles and scripts. A 214 KB page truncated to 8 KB contains no readable text at all, and often
 * not even its own {@code <title>}, so a preview built from the capped body could only ever say
 * "(no readable text in the response)". That is what it did say, on a screenshot, correctly and
 * uselessly.
 *
 * <p>So the skim happens in {@link HttpFetchExecutor}, on the background thread, while the whole
 * document is still in hand — once per fetch, which a miner does at most once a minute — and what
 * survives into memory is this: bounded, small, and already the part worth showing.
 *
 * @param title the page's {@code <title>}, or empty when it has none
 * @param text the visible text, whitespace collapsed, cut to a length worth a few panel rows
 */
record HtmlDigest(String title, String text) {
}
