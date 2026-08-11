# Rustorio mod template — canonical **L2**

A complete, working mod built **outside** the engine's repository. This is the official starting
point for behaviour mods: **ContentDsl + SimpleCrafter**. Copy this directory, or scaffold with
`./gradlew initMod -PmodId=yourid` in the engine checkout.

Walkthrough: engine `docs/hello-building.md`. Where to look for any task: `docs/start-here.md`.

Nothing in the engine's `build.gradle` mentions this project as a compile dependency, and nothing
here mentions the engine's build beyond `mavenLocal()` for `rustorio-api`.

## Build and install

In the engine checkout (whenever the API changes):

```bash
./gradlew publishToMavenLocal
```

Then here:

```bash
./gradlew installMod -PgameDir=/path/to/rustorio
```

Start the game or `./gradlew game` — the mod list should include `template`.
`./gradlew assembleMod` alone puts the installable folder in `build/mod/template/`.

## What is in here

| File | What it is |
|---|---|
| `mod.json` | Identity, dependencies, `entryPoint` |
| `TemplateMod.java` | L2 entry: items, recipe, SimpleCrafter building, map via `ctx.content()` |
| `META-INF/services/…` | `ServiceLoader` registration — must agree with `mod.json` |
| `content/` | Optional L0 JSON (empty in this sample; Java DSL registers everything) |

There is **no** hand-written `Building` / `Codec` / `BuildingPrototype` here. That path is L3.

## Ladder

| Level | Use | Where |
|------|-----|--------|
| L0 Data | JSON only | `docs/first-mod.md` |
| **L2 Behavior** | **this template** | `docs/hello-building.md` |
| L3 Engine-touch | own `Building`, services | `webminer`, petrochem guide, `examples/servicemod` |

## Two traps

**Read the registry with `peek` / `requireItem` during registration** — `get` needs a frozen index.

**Depend on the API as `compileOnly`** — do not bundle engine classes inside your jar.
