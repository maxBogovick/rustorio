package com.rustorio.api.mod;

/**
 * The entry point a code mod's own class implements — discovered via {@code ServiceLoader} from
 * the mod's own {@code .jar} (see {@code com.rustorio.mod}'s {@code ModClassLoader}), one instance
 * per mod. A data-only mod (JSON content, no jar) never implements this at all — its content is
 * read directly by {@code com.rustorio.mod.content}'s loaders, without a Java entry point.
 *
 * <p>Every method defaults to a no-op: a mod that only adds data (items/recipes/buildings via
 * {@code content/**}<!---->{@code .json}, still legal even from a jar) overrides none of these, and
 * a mod that only wants to react to events overrides only {@link #subscribeEvents}.
 *
 * <p>The three registration methods run in strict rounds across every loaded mod: every mod's
 * {@link #registerContent} runs (in dependency-resolved order) before any mod's {@link
 * #modifyContent} runs, which in turn all run before any {@link #finalFixes} — never one mod's
 * full sequence before the next mod starts. This lets a mod safely reference another
 * (earlier-loaded) mod's content from its own {@code modifyContent}, without needing a
 * content-level dependency graph on top of the mod-level one.
 */
public interface RustorioMod {

    /** First round: register this mod's own new content. */
    default void registerContent(RegistrationContext context) {
    }

    /** Second round: adjust content — this mod's own or another mod's, already registered. */
    default void modifyContent(RegistrationContext context) {
    }

    /** Third round: last corrections before the registries freeze. */
    default void finalFixes(RegistrationContext context) {
    }

    /** Runs once, after every mod's {@link #finalFixes} and after the registries freeze — subscribe to gameplay events here. */
    default void subscribeEvents(EventBus events) {
    }
}
