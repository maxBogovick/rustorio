package com.rustorio.persistence;

import com.rustorio.domain.OreLayoutId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One entry in a {@link SaveSlots} listing — enough for a menu to show and pick a save without
 * loading it into a {@code World} first. {@code mapLayout} is {@code null} exactly when {@link
 * SaveSlots} couldn't read (or the file didn't have) a {@link WorldSnapshot#oreLayout} header —
 * the same "unknown map, don't check" meaning that field already carries in a save itself.
 */
public record SaveSlotInfo(String name, Instant savedAt, @Nullable OreLayoutId mapLayout) {

    /**
     * A short human label for {@link #mapLayout} — {@code "vanilla"}/{@code "random (seed
     * ...)"}/the authored map's own {@code ContentId}, or {@code "unknown map"} if {@link
     * #mapLayout} is {@code null}. {@link OreLayoutId#kind} is either a fixed tag ({@code "patch"},
     * {@code "random"}) or {@code "authored:" + ContentId} (see {@link
     * com.rustorio.domain.AuthoredOreLayout#id}) — this is the one place that string gets read
     * back apart, everywhere else it's opaque.
     */
    public String mapLabel() {
        if (mapLayout == null) {
            return "unknown map";
        }
        String kind = mapLayout.kind();
        if (kind.equals("patch")) {
            return "vanilla";
        }
        if (kind.equals("random")) {
            return "random (seed " + mapLayout.seed() + ")";
        }
        if (kind.startsWith("authored:")) {
            return kind.substring("authored:".length());
        }
        return kind;
    }
}
