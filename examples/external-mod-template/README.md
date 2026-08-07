# Rustorio mod template

A complete, working mod built **outside** the engine's repository. Copy this directory anywhere,
rename it, and start from it.

Nothing in the engine's `build.gradle` mentions this project, and nothing here mentions the
engine's build. A mod is a folder you drop into `resources/mods/` — the same arrangement Factorio,
Fabric and RimWorld use.

## Build and install

In the engine's checkout — publishes the API this project compiles against. Re-run it whenever the
engine changes, or you will be compiling against a stale copy and get errors that describe an API
that no longer exists:

```bash
./gradlew publishToMavenLocal
```

Then, here:

```bash
./gradlew installMod -PgameDir=/path/to/rustorio
```

That is all. Start the game and the mod is loaded; `./gradlew game` prints the mod list on startup.
`./gradlew assembleMod` alone puts the installable folder in `build/mod/template/` if you would
rather copy it yourself.

## What is in here

| File | What it is |
|---|---|
| `mod.json` | Identity, dependencies, and `entryPoint` — the class the loader instantiates |
| `content/items/spark.json` | An item, as pure data. No Java needed for content |
| `src/main/resources/META-INF/services/…` | Standard `ServiceLoader` registration; must agree with `mod.json` |
| `TemplateMod.java` | The entry point: registers the building and its save codec |
| `SparkGenerator.java` | The building's actual behaviour |

## The four things a mod can do

1. **Add content as data** — items, recipes, buildings that reuse existing behaviour, maps,
   technologies. JSON files under `content/`, no Java at all.
2. **Add new behaviour** — a Java class implementing `Building`, registered with a
   `BuildingPrototype`. That is `SparkGenerator` here.
3. **Add a capability** — something the engine has never heard of (a network client, a clock),
   registered with `context.registerService(key, service)` and reached from a building through
   `TickContext.service(key)`. Not shown here; see the engine's own `com.webminer` mod.
4. **Ship art** — put `.png` files in a `textures/` directory beside `mod.json`. The game packs
   every mod's textures into its atlas at startup, under your mod's own namespace.

## Two traps worth knowing before you hit them

**Read the registry with `peek` during registration.** `registerContent` runs while registration is
still open; `get` and `getOrUnknown` both need the frozen id index and throw. Getting this wrong is
not loud — your mod is skipped and the only trace is a `WARNING` line in the log.

**Depend on the API as `compileOnly`.** The running game has already loaded those classes. Bundling
a copy inside your jar gives you types that are not the game's types, and every registry lookup and
`instanceof` fails in ways that look like nonsense.
