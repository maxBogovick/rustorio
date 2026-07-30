package com.rustorio.persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.rustorio.domain.ItemType;
import java.io.IOException;

/**
 * Writes an {@link ItemType} VALUE (a {@code recipeOutput}/{@code held}/{@code filterItem} field,
 * not a {@code Map} key — see {@link ItemTypeKeySerializer} for that case) as its {@link
 * ItemType#id()} string instead of Jackson's default "serialize every record component". Without
 * this, deserializing rebuilds a NEW {@code ItemType} via the record's canonical constructor —
 * equal by value, but a different object — and {@code RecipeBook}'s {@code ==} comparisons
 * (against {@code VanillaItems}' shared instances) silently fail to match anything, breaking
 * every save that names a recipe by its output item (a live bug caught by {@code
 * JsonSaveRepositoryTest}, not by inspection).
 */
final class ItemTypeSerializer extends JsonSerializer<ItemType> {

    @Override
    public void serialize(ItemType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.id().toString());
    }
}
