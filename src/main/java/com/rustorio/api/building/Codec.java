package com.rustorio.api.building;

/**
 * Facade for {@link com.rustorio.domain.building.Codec} — prefer this import in new mod code.
 *
 * @param <S> state type encoded/decoded by this codec
 */
public interface Codec<S> extends com.rustorio.domain.building.Codec<S> {
}
