package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.BuildingPrototype;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The mod loader's entry point: {@link #loadAll} turns a set of mod directories into a fully
 * loaded, frozen {@link LoadedGame} — parsing every {@code mod.json}, resolving load order,
 * running every mod's {@code content/} JSON and (if it has one) its {@code RustorioMod} through
 * the register/modify/finalFixes rounds, then validating and freezing.
 *
 * <p>{@code modDirectories} is sorted by directory name BEFORE anything else happens — the one
 * place a filesystem listing's unspecified order becomes the order {@link DependencyResolver} (and
 * therefore everything downstream: {@code rawId} assignment, the merged recipe list) sees. See
 * {@code ModLoaderTest}'s determinism test.
 */
public final class ModLoader {

    private ModLoader() {
    }

    /** @throws ModLoadException on anything that stops loading — see the individual stages' own javadoc for what each can throw. */
    public static LoadedGame loadAll(List<Path> modDirectories) {
        List<Path> sortedDirectories = modDirectories.stream()
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();

        ModJsonReader jsonReader = new ModJsonReader();
        Map<ModId, Path> directoryById = new LinkedHashMap<>();
        List<ModDescriptor> descriptors = new ArrayList<>();
        for (Path directory : sortedDirectories) {
            ModDescriptor descriptor = jsonReader.read(directory.resolve("mod.json"));
            if (directoryById.put(descriptor.id(), directory) != null) {
                throw new ModLoadException("duplicate mod id across mod directories: '" + descriptor.id() + "'");
            }
            descriptors.add(descriptor);
        }

        List<ModDescriptor> loadOrder = DependencyResolver.resolve(descriptors);
        GameRegistrationContext context = new GameRegistrationContext();
        Map<ModId, RustorioMod> entryPoints = runRegisterRound(loadOrder, directoryById, context);
        runRound(loadOrder, entryPoints, RustorioMod::modifyContent, context);
        runRound(loadOrder, entryPoints, RustorioMod::finalFixes, context);

        context.items().freeze();
        context.buildings().freeze();
        RecipeBook recipeBook = buildRecipeBook(context.recipes());
        validateContent(context.items(), context.buildings(), recipeBook);

        SimpleEventBus events = new SimpleEventBus();
        for (ModDescriptor mod : loadOrder) {
            RustorioMod entryPoint = entryPoints.get(mod.id());
            if (entryPoint != null) {
                entryPoint.subscribeEvents(events);
            }
        }

        return new LoadedGame(context.items(), context.buildings(), recipeBook, events);
    }

    /**
     * Round 1: for each mod, in load order, its {@code content/} JSON THEN (if it has one) its own
     * {@code registerContent} — both are "register content", and a mod's code may itself depend on
     * its own JSON content already being registered (e.g. a recipe its code wants to look up).
     */
    private static Map<ModId, RustorioMod> runRegisterRound(
            List<ModDescriptor> loadOrder, Map<ModId, Path> directoryById, GameRegistrationContext context) {
        Map<ModId, RustorioMod> entryPoints = new LinkedHashMap<>();
        for (ModDescriptor mod : loadOrder) {
            Path directory = Objects.requireNonNull(directoryById.get(mod.id()));
            ContentJsonLoader.loadInto(directory, mod.id(), context);
            String entryPointClassName = mod.entryPoint();
            if (entryPointClassName != null) {
                Path jarFile = directory.resolve(mod.id().value() + ".jar");
                RustorioMod entryPoint = ModJarLoader.loadEntryPoint(jarFile, entryPointClassName, ModLoader.class.getClassLoader());
                entryPoints.put(mod.id(), entryPoint);
                entryPoint.registerContent(context);
            }
        }
        return entryPoints;
    }

    private interface Round {
        void run(RustorioMod mod, GameRegistrationContext context);
    }

    private static void runRound(List<ModDescriptor> loadOrder, Map<ModId, RustorioMod> entryPoints, Round round, GameRegistrationContext context) {
        for (ModDescriptor mod : loadOrder) {
            RustorioMod entryPoint = entryPoints.get(mod.id());
            if (entryPoint != null) {
                round.run(entryPoint, context);
            }
        }
    }

    private static RecipeBook buildRecipeBook(List<Recipe> recipes) {
        try {
            return new RecipeBook(recipes);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException("recipe conflict while building the merged recipe book: " + e.getMessage(), e);
        }
    }

    /**
     * Content-wide validation pass, run once at the end of loading: every recipe's
     * ingredients/output, and every building prototype's cost item, must resolve in the now-frozen
     * item registry. Names the offending content id precisely; does NOT name which mod registered
     * it — {@link Registry} carries no per-entry provenance, and adding one to attribute a handful
     * of error messages is a bigger change than this narrow validation pass needs. JSON content
     * ({@link ItemJsonLoader}/{@link RecipeJsonLoader}/{@link BuildingJsonLoader}) already validates
     * its OWN references immediately, file-named, at load time — this pass exists for what THOSE
     * can't catch: a code mod calling {@code addRecipe}/{@code buildings().register} directly with
     * a reference nothing registered.
     */
    private static void validateContent(Registry<ItemType> items, Registry<BuildingPrototype> buildings, RecipeBook recipes) {
        for (Recipe recipe : recipes.all()) {
            for (ItemType ingredient : recipe.ingredients()) {
                requireRegistered(items, ingredient, "recipe ingredient");
            }
            requireRegistered(items, recipe.output(), "recipe output");
        }
        for (BuildingPrototype prototype : buildings.iterate()) {
            requireRegistered(items, prototype.cost().item(), "building '" + prototype.id() + "'s cost item");
        }
    }

    private static void requireRegistered(Registry<ItemType> items, ItemType item, String role) {
        ContentId id = item.id();
        if (items.getOrUnknown(id).isEmpty()) {
            throw new ModLoadException(role + " '" + id + "' is not a registered item");
        }
    }
}
