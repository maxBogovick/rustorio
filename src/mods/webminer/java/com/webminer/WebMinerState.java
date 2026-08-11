package com.webminer;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * {@link WebMiner}'s own captured state, paired with its own {@link Codec} (see {@code
 * Vanilla belt registry entry). {@code url} is the player's chosen target — free text, set via {@link
 * WebMiner#setUrl} — the same "plain data the player picks" shape {@link FilterState#filterItem}
 * already has, just typed as a string instead of an {@link ItemType}.
 *
 * <p>No {@code pending}/in-flight flag: {@link WebMiner#tick} calls {@link
 * TickContext#requestFetch} every tick it has nothing held (once past cooldown/backoff), and {@link
 * com.rustorio.domain.world.World#requestFetch} is itself the idempotent guard against a duplicate
 * request while one is already outstanding. That is also what makes a save/reload safe with no
 * extra state to restore here: the background job a save captured is gone with the old {@code
 * World} regardless of what this record remembers, so the ONLY correct behavior on the first tick
 * after load is to ask again — which happens automatically, not because this state told it to.
 *
 * <p>{@code intervalTicks}/{@code backoffTicks} are the player's own rate-limit/circuit-breaker
 * settings ({@link WebMiner#setIntervalSeconds}/{@link WebMiner#setBackoffSeconds}) — these MUST
 * survive a save, or reloading would silently reset a deliberately slowed-down miner back to
 * hammering its target. {@code cooldownRemaining}/{@code consecutiveFailures}/{@code
 * backoffRemaining} are the live counters {@link WebMiner#tick} counts down/up — also saved, so a
 * miner mid-backoff at save time doesn't come back and immediately retry a host that was failing.
 */
public record WebMinerState(Direction direction, String url, @Nullable ItemType held, int intervalTicks,
        int backoffTicks, int cooldownRemaining, int consecutiveFailures, int backoffRemaining) {
}
