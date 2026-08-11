package com.rustorio.mod;

import com.rustorio.api.dsl.ContentDsl;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.registry.RegistryKey;
import com.rustorio.domain.building.ServiceKey;
import com.rustorio.mod.dsl.ContentDslImpl;
import java.util.function.Supplier;

/**
 * Per-mod view of the shared {@link GameRegistrationContext}: same registries and services for
 * every mod in the load, plus {@link #modNamespace()} / {@link #content()} bound to this mod's id.
 *
 * <p>One wrapper per mod round rather than mutating a namespace field on the shared context — a
 * later round for mod B must not see mod A's namespace if a callback retained the context, and a
 * field would make that race a silent data bug instead of an impossible state.
 */
final class ModScopedRegistrationContext implements RegistrationContext {

    private final String modNamespace;
    private final GameRegistrationContext shared;
    private final ContentDsl content;

    ModScopedRegistrationContext(ModId modId, GameRegistrationContext shared) {
        this.modNamespace = modId.value();
        this.shared = shared;
        this.content = new ContentDslImpl(this);
    }

    @Override
    public <T> Registry<T> registry(RegistryKey<T> key) {
        return shared.registry(key);
    }

    @Override
    public <T> void registerService(ServiceKey<T> key, Supplier<T> provider) {
        shared.registerService(key, provider);
    }

    @Override
    public String modNamespace() {
        return modNamespace;
    }

    @Override
    public ContentDsl content() {
        return content;
    }
}
