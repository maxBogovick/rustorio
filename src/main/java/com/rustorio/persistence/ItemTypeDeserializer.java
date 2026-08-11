package com.rustorio.persistence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemType;
import java.io.IOException;

/**
 * Inverse of {@link ItemTypeSerializer} — see its javadoc for why this must return the SAME
 * shared instance {@code items} hands out, not a freshly built copy. {@code items} is whichever
 * registry {@link JsonSaveRepository} was constructed with — vanilla-only by default, or a wider
 * one for a game/test running additional (modded) content — so a save naming a non-vanilla
 * {@code ContentId} resolves correctly as long as that content was registered before loading.
 */
final class ItemTypeDeserializer extends JsonDeserializer<ItemType> {

    private final Registry<ItemType> items;

    ItemTypeDeserializer(Registry<ItemType> items) {
        this.items = items;
    }

    @Override
    public ItemType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ContentId id = ContentId.of(p.getValueAsString());
        return items.getOrUnknown(id)
                .orElseThrow(() -> new IllegalStateException("Save references unknown item: " + id));
    }
}
