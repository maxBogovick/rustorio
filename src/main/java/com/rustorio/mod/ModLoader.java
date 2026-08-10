package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.EngineVersion;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.registry.RegistryKey;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

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

    private static final System.Logger LOGGER = System.getLogger(ModLoader.class.getName());

    /**
     * Loads every mod, skipping any single mod that fails on its own account and reporting it in
     * {@link LoadedGame#skippedMods()} instead of refusing to start the game at all. A mod that
     * depended on a skipped one is skipped in turn, because dependency resolution then fails and
     * names it (see {@link DependencyResolver}).
     *
     * <p>Skipping is implemented by loading the whole set again without the culprit, rather than by
     * undoing what it registered. A mod that fails halfway has already written into shared
     * registries — some of it possibly {@code update}s of another mod's content — and there is no
     * honest way to take that back; starting over costs a re-parse of a handful of small JSON files
     * and is exactly correct instead. Each attempt drops at least one mod, so the loop runs at most
     * once per mod.
     *
     * @throws ModLoadException for a failure no single mod can be blamed for — see {@link
     *                          ModLoadException#culprit()}.
     */
    public static LoadedGame loadAll(List<Path> modDirectories) {
        List<Path> sortedDirectories = modDirectories.stream()
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        List<SkippedMod> skipped = new ArrayList<>();
        Set<ModId> excluded = new LinkedHashSet<>();
        while (true) {
            try {
                return attemptLoad(sortedDirectories, excluded, skipped);
            } catch (ModLoadException e) {
                ModId culprit = e.culprit();
                if (culprit == null || !excluded.add(culprit)) {
                    // No single mod to blame, or the one named is already excluded — the latter
                    // would spin forever, and means the attribution itself is wrong rather than
                    // that another mod needs dropping.
                    throw e;
                }
                LOGGER.log(System.Logger.Level.WARNING, "Skipping mod ''{0}'': {1}", culprit, e.getMessage());
                skipped.add(new SkippedMod(culprit, messageOf(e)));
            }
        }
    }

    /** One full load of every directory whose mod is not in {@code excluded} — see {@link #loadAll} for why this is retried rather than rolled back. */
    private static LoadedGame attemptLoad(List<Path> sortedDirectories, Set<ModId> excluded, List<SkippedMod> skipped) {
        ModJsonReader jsonReader = new ModJsonReader();
        Map<ModId, Path> directoryById = new LinkedHashMap<>();
        List<ModDescriptor> descriptors = new ArrayList<>();
        for (Path directory : sortedDirectories) {
            // Checked BEFORE reading, by directory name: a mod skipped BECAUSE its mod.json can't
            // be read is only identifiable by its folder, and re-reading it every attempt would
            // throw the same unattributable failure forever.
            if (excluded.contains(modIdFromDirectoryName(directory))) {
                continue;
            }
            ModDescriptor descriptor = readDescriptor(jsonReader, directory);
            if (excluded.contains(descriptor.id())) {
                continue;
            }
            requireEngineNewEnough(descriptor, directory);
            if (directoryById.put(descriptor.id(), directory) != null) {
                // Not attributable: both directories claim the same id, and dropping either one
                // silently is a guess about which copy the player meant to install.
                throw new ModLoadException("duplicate mod id across mod directories: '" + descriptor.id() + "'");
            }
            descriptors.add(descriptor);
        }

        List<ModDescriptor> loadOrder = DependencyResolver.resolve(descriptors);
        LOGGER.log(System.Logger.Level.DEBUG, "Load order: {0}", describeLoadOrder(loadOrder));
        GameRegistrationContext context = new GameRegistrationContext();
        Map<ModId, RustorioMod> entryPoints = runRegisterRound(loadOrder, directoryById, context);
        runRound(loadOrder, entryPoints, RustorioMod::modifyContent, context);
        runRound(loadOrder, entryPoints, RustorioMod::finalFixes, context);

        // Every registry the context holds, in its fixed declaration order — written this way so a
        // content kind added under a NEW key freezes with the rest instead of staying writable
        // after loading ends. Today every such key is the engine's own: a mod has no way to add one
        // (see RegistrationContext#registry), so this loop currently walks exactly RegistryKeys.VANILLA.
        for (RegistryKey<?> key : context.keys()) {
            context.registry(key).freeze();
        }
        RecipeBook recipeBook = buildRecipeBook(context.recipes().iterate());
        validateContent(context.items(), context.buildings(), context.kinds(), context.techs(), recipeBook);

        SimpleEventBus events = new SimpleEventBus();
        for (ModDescriptor mod : loadOrder) {
            RustorioMod entryPoint = entryPoints.get(mod.id());
            if (entryPoint != null) {
                attributeTo(mod.id(), "subscribing to events", () -> entryPoint.subscribeEvents(events));
            }
        }

        logLoadSummary(loadOrder, context, recipeBook, skipped);
        return new LoadedGame(context.items(), context.fluids(), context.buildings(), context.kinds(), context.maps(),
                context.techs(), recipeBook, events,
                mergeRenames(loadOrder), List.copyOf(skipped), context.serviceProviders());
    }

    /**
     * Refuses a mod that declares a {@code minEngineVersion} newer than this engine. Attributable to
     * that mod, so it is skipped and reported like any other single-mod failure: the alternative is
     * loading it anyway and failing later somewhere inside its own code, with a message about a
     * missing method rather than about an out-of-date game.
     */
    private static void requireEngineNewEnough(ModDescriptor descriptor, Path directory) {
        SemVer required = descriptor.minEngineVersion();
        if (required == null) {
            return;
        }
        SemVer engine = SemVer.parse(EngineVersion.CURRENT);
        if (engine.compareTo(required) < 0) {
            throw new ModLoadException(directory + ": mod '" + descriptor.id() + "' needs engine "
                    + required + " or newer, but this is " + engine + " — update the game, or install "
                    + "an older release of the mod", descriptor.id());
        }
    }

    /**
     * Reads one {@code mod.json}, blaming the directory's own name when the file itself is
     * unreadable: the id inside is exactly what couldn't be read, so the only identity available is
     * the folder the player would delete. A directory whose name isn't a legal {@link ModId} leaves
     * even that unavailable, and the failure stays fatal.
     */
    private static ModDescriptor readDescriptor(ModJsonReader jsonReader, Path directory) {
        try {
            return jsonReader.read(directory.resolve("mod.json"));
        } catch (ModLoadException e) {
            throw new ModLoadException(messageOf(e), modIdFromDirectoryName(directory), e);
        }
    }

    private static @Nullable ModId modIdFromDirectoryName(Path directory) {
        try {
            return new ModId(directory.getFileName().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Runs {@code stage} and re-throws anything it fails with as a {@link ModLoadException} blaming
     * {@code modId}. Catches {@link RuntimeException}, not just {@code ModLoadException}: a code
     * mod's own entry point is arbitrary third-party code, and its {@code NullPointerException} is
     * just as much "this mod is broken" as a malformed JSON file is.
     */
    private static void attributeTo(ModId modId, String what, Runnable stage) {
        try {
            stage.run();
        } catch (ModLoadException e) {
            throw e.culprit() != null ? e : new ModLoadException(messageOf(e), modId, e);
        } catch (RuntimeException e) {
            throw new ModLoadException(
                    "mod '" + modId + "' failed while " + what + ": " + e, modId, e);
        }
    }

    /** {@link Throwable#getMessage()} without the {@code null} case — every {@link ModLoadException} this package builds carries a message, but the compiler cannot know that. */
    private static String messageOf(Throwable e) {
        String message = e.getMessage();
        return message == null ? e.toString() : message;
    }

    private static String describeLoadOrder(List<ModDescriptor> loadOrder) {
        return loadOrder.stream()
                .map(mod -> mod.id() + " " + mod.version())
                .collect(Collectors.joining(", "));
    }

    /**
     * One INFO line a modder can paste into a bug report, plus a WARNING per skipped mod and per
     * overwritten content id. The overwrite lines are what {@link Registry#updateLog()} was
     * collecting for and nothing read: "my item looks wrong and I don't know which mod changed it"
     * is otherwise only answerable by reading someone else's source.
     */
    private static void logLoadSummary(List<ModDescriptor> loadOrder, GameRegistrationContext context,
            RecipeBook recipeBook, List<SkippedMod> skipped) {
        // Counts are assembled from the keys rather than spelled out: the old line carried seven
        // hand-numbered placeholders, so a new content kind meant renumbering the tail of it, and a
        // mod's own kind could never appear in the summary at all.
        StringBuilder counts = new StringBuilder();
        for (RegistryKey<?> key : context.keys()) {
            counts.append(counts.isEmpty() ? "" : ", ")
                    .append(context.registry(key).size()).append(' ').append(key.name());
        }
        LOGGER.log(System.Logger.Level.INFO, "Loaded {0} mod(s) [{1}]: {2}",
                loadOrder.size(), describeLoadOrder(loadOrder), counts);
        for (SkippedMod mod : skipped) {
            LOGGER.log(System.Logger.Level.WARNING, "Mod ''{0}'' was skipped: {1}", mod.id(), mod.reason());
        }
        for (RegistryKey<?> key : context.keys()) {
            logOverwrites(key.name(), context.registry(key).updateLog());
        }
    }

    private static void logOverwrites(String what, List<ContentId> overwritten) {
        for (ContentId id : overwritten) {
            LOGGER.log(System.Logger.Level.INFO, "A later mod overwrote the {0} ''{1}''", what, id);
        }
    }

    /**
     * Every loaded mod's own {@code renames} declaration, merged in load order. Two mods claiming a
     * rename for the SAME old id is refused rather than resolved by load order: only one of the two
     * targets can win, the loser's content silently disappears from a loaded save, and no ordering
     * rule makes that choice defensible. Renaming INTO an id another mod also renames into is fine
     * and not checked — two dead ids collapsing onto one live prototype is a legitimate merge.
     *
     * <p>{@link LinkedHashMap}: the map decides what a save's rows are rewritten to, and the
     * conflict message below names the first collision found, so both have to be stable across JVM
     * runs.
     */
    private static Map<ContentId, ContentId> mergeRenames(List<ModDescriptor> loadOrder) {
        Map<ContentId, ContentId> merged = new LinkedHashMap<>();
        Map<ContentId, ModId> declaredBy = new LinkedHashMap<>();
        for (ModDescriptor mod : loadOrder) {
            for (Map.Entry<ContentId, ContentId> rename : mod.prototypeRenames().entrySet()) {
                ModId earlier = declaredBy.putIfAbsent(rename.getKey(), mod.id());
                if (earlier != null) {
                    throw new ModLoadException("mods '" + earlier + "' and '" + mod.id()
                            + "' both declare a rename for '" + rename.getKey()
                            + "' — only one of them can own it, so remove the rename from the mod that no longer does");
                }
                merged.put(rename.getKey(), rename.getValue());
            }
        }
        return merged;
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
            attributeTo(mod.id(), "reading its content/ JSON",
                    () -> ContentJsonLoader.loadInto(directory, mod.id(), context));
            String entryPointClassName = mod.entryPoint();
            if (entryPointClassName != null) {
                Path jarFile = directory.resolve(mod.id().value() + ".jar");
                attributeTo(mod.id(), "loading and registering its jar", () -> {
                    RustorioMod entryPoint = ModJarLoader.loadEntryPoint(
                            jarFile, entryPointClassName, ModLoader.class.getClassLoader());
                    entryPoints.put(mod.id(), entryPoint);
                    entryPoint.registerContent(context);
                });
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
                    attributeTo(mod.id(), "running a content round", () -> round.run(entryPoint, context));
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
     * item registry; every recipe's own pool and every building's own {@code recipeKind} must
     * resolve to a REAL kind (see {@link #knownKinds}).
     *
     * <p>Every message names the mod at fault, and every failure is attributed to it so the loader
     * can skip that mod instead of refusing to start. For an item or a building prototype the owner
     * is simply its {@link ContentId}'s own namespace — that IS the mod id for anything a mod
     * registered under its own name, recipes included now that they carry an id of their own.
     * JSON content ({@link
     * ItemJsonLoader}/{@link RecipeJsonLoader}/{@link
     * BuildingJsonLoader}/{@link RecipeKindJsonLoader}) already validates its OWN references
     * immediately, file-named, at load time — this pass exists for what THOSE can't catch: a code
     * mod calling {@code addRecipe}/{@code buildings().register} directly with a reference nothing
     * registered, OR a reference that only resolves correctly once EVERY mod's content — kinds
     * included — has finished loading (which is why kind checking couldn't live in {@code
     * RecipeJsonLoader}/{@code BuildingJsonLoader} themselves; see those two's own {@code
     * resolveKind} javadoc).
     */
    private static void validateContent(Registry<ItemType> items, Registry<BuildingPrototype> buildings,
            Registry<RecipeKind> kinds, Registry<TechType> techs, RecipeBook recipes) {
        for (Recipe recipe : recipes.all()) {
            ModId owner = ownerOf(recipe.id());
            for (ItemType ingredient : recipe.ingredients()) {
                requireRegistered(items, ingredient, owner, "recipe ingredient");
            }
            requireRegistered(items, recipe.output(), owner, "recipe output");
        }
        for (BuildingPrototype prototype : buildings.iterate()) {
            requireRegistered(items, prototype.cost().item(), ownerOf(prototype.id()),
                    "building '" + prototype.id() + "'s cost item");
        }

        validateTechs(techs);

        Set<ContentId> knownKinds = knownKinds(buildings, kinds);
        for (Recipe recipe : recipes.all()) {
            if (!knownKinds.contains(recipe.type())) {
                ModId owner = ownerOf(recipe.id());
                throw new ModLoadException("mod '" + owner + "': recipe '" + recipe + "'s kind '" + recipe.type()
                        + "' doesn't match any BuildingType, registered kind, or building id — check for a typo",
                        owner);
            }
        }
        for (BuildingPrototype prototype : buildings.iterate()) {
            if (!knownKinds.contains(prototype.recipeKind())) {
                ModId owner = ownerOf(prototype.id());
                throw new ModLoadException("mod '" + owner + "': building '" + prototype.id() + "'s recipe kind '"
                        + prototype.recipeKind()
                        + "' doesn't match any BuildingType, registered kind, or building id — check for a typo",
                        owner);
            }
        }
    }

    /**
     * Every technology's prerequisites must name a registered technology, and the graph they form
     * must be acyclic. Neither was possible to get wrong while technologies were {@code enum}
     * constants — a constant can only name earlier constants, so a dangling or circular
     * prerequisite could not be written down. Registered content can be written down either way,
     * so it is checked here instead of being assumed.
     */
    private static void validateTechs(Registry<TechType> techs) {
        for (TechType tech : techs.iterate()) {
            for (ContentId prerequisite : tech.prerequisites()) {
                if (techs.getOrUnknown(prerequisite).isEmpty()) {
                    ModId owner = ownerOf(tech.id());
                    throw new ModLoadException("mod '" + owner + "': technology '" + tech.id()
                            + "' requires '" + prerequisite + "', which no mod registered", owner);
                }
            }
        }
        for (TechType tech : techs.iterate()) {
            Set<ContentId> seen = new LinkedHashSet<>();
            if (reachesItself(techs, tech.id(), tech.id(), seen)) {
                ModId owner = ownerOf(tech.id());
                throw new ModLoadException("mod '" + owner + "': technology '" + tech.id()
                        + "' is its own prerequisite, directly or through " + seen
                        + " — a cycle no player could ever unlock", owner);
            }
        }
    }

    /** Depth-first walk up {@code from}'s prerequisites looking for {@code target}; {@code seen} collects the path for the message. */
    private static boolean reachesItself(Registry<TechType> techs, ContentId target, ContentId from,
            Set<ContentId> seen) {
        for (ContentId prerequisite : techs.get(from).prerequisites()) {
            if (prerequisite.equals(target)) {
                return true;
            }
            if (seen.add(prerequisite) && reachesItself(techs, target, prerequisite, seen)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The mod that owns {@code id}, read off its namespace. Correct for everything registered under
     * a mod's own name, which is every JSON-defined item and building by construction ({@code
     * ItemJsonLoader} and friends build the id from the mod id) and the convention a code mod is
     * expected to follow. A code mod registering under someone else's namespace is misattributed
     * here — an acceptable cost for a message, and a thing that mod should not be doing anyway.
     */
    private static ModId ownerOf(ContentId id) {
        return new ModId(id.namespace());
    }

    /**
     * Every {@link ContentId} a recipe or a building's own {@code recipeKind} is allowed to name:
     * the 12 vanilla {@link BuildingType} pools (a defensive fallback for content built through
     * {@code VanillaBuildings.frozen()}/{@code BuildingFactory.standard()} directly, which many
     * tests still do, rather than through this JSON path), every explicitly-registered {@link
     * RecipeKind}, and every building's own {@link BuildingPrototype#id()} — deliberately {@code
     * id()}, NOT {@code recipeKind()}: checking a building's OWN {@code recipeKind()} against a set
     * that already includes every building's {@code recipeKind()} would be tautological and let
     * ANY typo through, defeating the entire point of this check (caught during design review —
     * see the plan). A building's DEFAULT {@code recipeKind()} (nothing explicit in its own JSON)
     * always equals its own {@code id()} by construction, so it's automatically valid here with no
     * separate registration needed — only an EXPLICIT reference (a building or recipe opting into
     * someone else's pool) is actually checked against real ids.
     */
    private static Set<ContentId> knownKinds(Registry<BuildingPrototype> buildings, Registry<RecipeKind> kinds) {
        Set<ContentId> knownKinds = new HashSet<>();
        for (BuildingType type : BuildingType.values()) {
            knownKinds.add(type.contentId());
        }
        for (RecipeKind kind : kinds.iterate()) {
            knownKinds.add(kind.id());
        }
        for (BuildingPrototype prototype : buildings.iterate()) {
            knownKinds.add(prototype.id());
        }
        return knownKinds;
    }

    private static void requireRegistered(Registry<ItemType> items, ItemType item, ModId owner, String role) {
        ContentId id = item.id();
        if (items.getOrUnknown(id).isEmpty()) {
            throw new ModLoadException("mod '" + owner + "': " + role + " '" + id + "' is not a registered item", owner);
        }
    }
}
