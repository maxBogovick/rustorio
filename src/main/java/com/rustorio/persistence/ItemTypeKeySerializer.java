package com.rustorio.persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.rustorio.domain.ItemType;
import java.io.IOException;

/**
 * Jackson can't serialize an arbitrary record as a {@code Map} key on its own the way it could an
 * enum (via {@code name()}). Writes {@link ItemType#id()}'s {@code namespace:path} string; see
 * {@link ItemTypeKeyDeserializer} for the inverse. Whether this exact string is the right choice
 * for the save format long-term, versus something that reproduces the old enum name byte-for-byte,
 * is a separate, still-open question — this class exists so save/load round-trips at all, not as
 * the final word on save format.
 */
final class ItemTypeKeySerializer extends JsonSerializer<ItemType> {

    @Override
    public void serialize(ItemType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName(value.id().toString());
    }
}
