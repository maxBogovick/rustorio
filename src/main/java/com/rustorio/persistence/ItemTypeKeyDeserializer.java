package com.rustorio.persistence;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;

/**
 * Inverse of {@link ItemTypeKeySerializer}: parses the {@code namespace:path} key back into the
 * SAME {@link ItemType} instance {@code items} hands out everywhere else — not a freshly-built
 * copy — so a restored building's {@code ==} comparisons against {@code RecipeBook}'s recipes keep
 * working exactly like a never-saved one's do. {@code items} is whichever registry {@link
 * JsonSaveRepository} was constructed with.
 */
final class ItemTypeKeyDeserializer extends KeyDeserializer {

    private final Registry<ItemType> items;

    ItemTypeKeyDeserializer(Registry<ItemType> items) {
        this.items = items;
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) {
        ContentId id = ContentId.of(key);
        return items.getOrUnknown(id)
                .orElseThrow(() -> new IllegalStateException("Save references unknown item: " + id));
    }
}
