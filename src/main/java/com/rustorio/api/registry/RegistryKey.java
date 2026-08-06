package com.rustorio.api.registry;

/**
 * Names one KIND of content registry — items, fluids, buildings — and carries what that registry
 * holds in its type parameter, so {@code registry(RegistryKeys.ITEMS)} hands back a {@code
 * Registry<ItemType>} and not an {@code Object} the caller has to cast.
 *
 * <p>Why this exists: every registry used to be its own hand-written accessor, and adding one meant
 * editing the context interface, its implementation, the loaded-game record, the mod loader's
 * freeze list, its log line and the bootstrap that passes it on — six or seven files of pure
 * mechanism before the new content kind did anything at all. Keyed lookup makes a new kind one
 * constant.
 *
 * <p>{@code name} is identity: two keys with the same name ARE the same registry, which is what
 * lets a key be declared next to the content it describes rather than all in one list. It is also
 * what appears in a load-summary line, so it is a plain lowercase word ({@code "items"}), not a
 * class name.
 *
 * @param <T> what the registry under this key holds
 */
public record RegistryKey<T>(String name) {

    public RegistryKey {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a registry key needs a name");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
