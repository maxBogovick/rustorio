package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/**
 * Names one optional property a {@link BuildingPrototype} may carry — which fluid it draws, what it
 * asks of the power grid — and carries that property's type, so reading one back needs no cast.
 *
 * <p>Why traits exist at all: those properties used to be components of the prototype record
 * itself. Adding fluids and electricity added three, and each one rippled — a new rung on the
 * record's telescope of constructors, a new rung on {@code VanillaBuildings}' own, a new field in
 * the JSON loader, a new line in the vanilla-as-data parity test — before the property did
 * anything. Traits are what make the NEXT such property (heat, pollution, a circuit connection) one
 * key and one parser, with none of those four files touched.
 *
 * <p>{@code dataKey} is the name this trait takes in a content file. It lives on the key rather
 * than being derived from {@link #id} so that the names already shipped to modders — {@code
 * "fluidInput"}, {@code "power"} — keep working unchanged: a data format is a promise to people
 * who have already written files against it.
 *
 * <p>Identity is {@link #id} alone (a record's generated equality over all three components amounts
 * to the same thing here, since a given id always declares the same name and type). Two mods that
 * declare the same id are talking about the same trait, which is the point — a mod can read a trait
 * another mod defined.
 *
 * @param <T> what a prototype stores under this key
 */
public record TraitKey<T>(ContentId id, String dataKey, Class<T> type) {

    public TraitKey {
        if (dataKey.isBlank()) {
            throw new IllegalArgumentException("trait " + id + " needs a name for content files");
        }
    }

    @Override
    public String toString() {
        return dataKey;
    }
}
