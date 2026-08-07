package com.webminer;

/**
 * What a {@link WebMiner}'s outstanding request resolved to, once {@link FetchExecutor} calls
 * back — deliberately not the response body itself. The engine's items are counted by {@link
 * com.rustorio.domain.ItemType} kind, not carried per-instance (see {@link Chest#contents}); a
 * real HTTP response is unique on every fetch and has nowhere to live in that model yet. Until a
 * later card adds instance-carrying items, a fetch resolves to one of two kinds of ordinary item
 * ({@code WEB_OK}/{@code WEB_ERROR}, registered by content, not this enum) — visible on the belt,
 * not silently dropped.
 */
public enum FetchOutcome {
    SUCCESS,
    ERROR
}
