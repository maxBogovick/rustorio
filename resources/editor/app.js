"use strict";

/* ============================================================
   fetch wrapper
   ============================================================ */

/** Endpoints that read/write one mod's own content — every request against these carries {@code ?mod=} (see withModParam); {@code /api/validate}, {@code /api/relaunch}, {@code /api/mods} and {@code /api/vanilla-map-template} are deliberately NOT here — they're cross-mod or mod-independent by design. */
const MOD_SCOPED_PREFIXES = ["/api/items", "/api/buildings", "/api/kinds", "/api/maps", "/api/recipes", "/api/textures"];

function withModParam(path, modId) {
  if (!MOD_SCOPED_PREFIXES.some((p) => path === p || path.startsWith(p + "/") || path.startsWith(p + "?"))) {
    return path;
  }
  return path + (path.includes("?") ? "&" : "?") + "mod=" + encodeURIComponent(modId || state.mod);
}

/** {@code modId} — which mod the request targets; defaults to {@link state}.mod (the switcher) when omitted. Editing/deleting an EXISTING entry must pass that entry's own {@code __mod} explicitly (see entryKey/splitEntryKey) — every tab now lists content from every mod, not just the active one, so "the active mod" is only ever right for a brand-new entry. */
async function api(method, path, body, modId) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(withModParam(path, modId), opts);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error((data && data.error) || `${res.status} ${res.statusText}`);
  }
  return data;
}

/* ============================================================
   toasts + confirm modal
   ============================================================ */

function toast(message, isError = false) {
  const stack = document.getElementById("toast-stack");
  const el = document.createElement("div");
  el.className = "toast" + (isError ? " error" : "");
  el.innerHTML = `<span class="toast-icon">${icon(isError ? "alert" : "check", 16)}</span><span class="toast-msg"></span>`;
  el.querySelector(".toast-msg").textContent = message;
  stack.appendChild(el);
  setTimeout(() => {
    el.style.transition = "opacity 0.2s ease";
    el.style.opacity = "0";
    setTimeout(() => el.remove(), 200);
  }, isError ? 6000 : 3200);
}

function confirmModal(title, body) {
  return new Promise((resolve) => {
    const backdrop = document.getElementById("modal-backdrop");
    document.getElementById("modal-title").textContent = title;
    document.getElementById("modal-body").textContent = body;
    backdrop.classList.remove("hidden");
    const cancelBtn = document.getElementById("modal-cancel");
    const confirmBtn = document.getElementById("modal-confirm");
    const cleanup = (result) => {
      backdrop.classList.add("hidden");
      cancelBtn.removeEventListener("click", onCancel);
      confirmBtn.removeEventListener("click", onConfirm);
      backdrop.removeEventListener("click", onBackdrop);
      resolve(result);
    };
    const onCancel = () => cleanup(false);
    const onConfirm = () => cleanup(true);
    const onBackdrop = (e) => { if (e.target === backdrop) cleanup(false); };
    cancelBtn.addEventListener("click", onCancel);
    confirmBtn.addEventListener("click", onConfirm);
    backdrop.addEventListener("click", onBackdrop);
  });
}

/** Makes a plain {@code <div>}/{@code <li>} with a click handler operable from the keyboard too —
 * Tab to reach it, Enter/Space to activate it, same as a real {@code <button>} gets for free. Every
 * OTHER control in this app already is a real button/input/select; the item/texture pickers and the
 * entry/usage/search-result rows are the exception, built as divs because they need free-form
 * thumb+label markup a {@code <button>} can hold too, but historically never got the keyboard half
 * of that — reachable to look at, but only clickable with a mouse. Call this right after adding the
 * element's own "click" listener; it re-dispatches a click rather than duplicating that listener's
 * logic, so the two can never drift apart. */
function makeKeyboardClickable(el) {
  el.tabIndex = 0;
  el.setAttribute("role", "button");
  el.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault(); // Space must not also scroll the list it's inside
      el.click();
    }
  });
}

/* ============================================================
   app state
   ============================================================ */

/** A map's 256x256 cells match {@code PatchOreLayout.STANDARD_WIDTH/HEIGHT} (Java) — see AuthoredMap's own javadoc for why this isn't a per-map field. */
const MAP_SIZE = 256;

const MOD_STORAGE_KEY = "rustorio-editor-mod";

const state = {
  mod: localStorage.getItem(MOD_STORAGE_KEY) || "rustorio",
  mods: [],
  // Every tab lists (and lets you edit/delete) content from EVERY installed mod, not just the
  // active one — each entry below is tagged with its own __mod. "The active mod" (the switcher)
  // only decides where a brand-new entry gets created; editing an EXISTING entry always writes
  // back to ITS OWN mod (see entryKey/splitEntryKey and every submit/delete handler below).
  items: [],
  recipes: [],
  buildings: [],
  kinds: [],
  maps: [],
  textures: { vanilla: [], mod: [] },
  selected: { items: null, recipes: null, buildings: null, kinds: null, maps: null },
  dirty: { items: false, recipes: false, buildings: false, kinds: false, maps: false },
  jsonMode: { items: false, recipes: false, buildings: false, kinds: false, maps: false },
};

/** A stable, collision-free list/selection identity for an entry that now might share a bare {@code path} with a same-named entry from a DIFFERENT mod — always {@code "modId:path"}, unlike {@link refValueFor} (which stays bare for the active mod, because that's what belongs in a saved FILE). */
function entryKey(entry) {
  return `${entry.__mod}:${entry.path}`;
}

/** The inverse of {@link entryKey}. */
function splitEntryKey(key) {
  const i = key.indexOf(":");
  return { mod: key.slice(0, i), path: key.slice(i + 1) };
}

/** A "kind" reference (a building/recipe's own {@code kind} field) always resolves bare in the mod that OWNS the file naming it — {@code ownerMod}, NOT the currently-active switcher mod (unlike an item reference, which the picker itself writes out already-qualified at authoring time). Always returns a fully-qualified {@code "modId:path"}, used as the kind-usage index's stable grouping id — see computeKindUsageIndex. */
function resolveKindRef(ref, ownerMod) {
  return ref.includes(":") ? ref : `${ownerMod}:${ref}`;
}

/** Re-expresses a reference written INSIDE {@code ownerMod}'s own file (bare or namespaced) in the form correct for a file about to be saved into the CURRENTLY active mod instead — bare if that turns out to be the same mod, {@code "modId:path"} otherwise. Needed wherever content gets copied across the mod boundary (duplicating a map, importing the vanilla layout template) — a bare ref that meant "this mod" in the SOURCE file means something else entirely once it lands in a different one. */
function requalifyRef(ref, ownerMod) {
  const resolved = resolveKindRef(ref, ownerMod);
  const colon = resolved.indexOf(":");
  const mod = resolved.slice(0, colon);
  return mod === state.mod ? resolved.slice(colon + 1) : resolved;
}

/** An entry's own reference string for use INSIDE another file's JSON (a building's cost item, a map's ore patch, …) — bare (matches every existing file's convention) when it belongs to the mod you're CURRENTLY creating/editing content in, {@code "modId:path"} otherwise, resolved server-side the identical way {@code BuildingJsonLoader.resolveRef}/{@code MapJsonLoader.resolveRef} already resolve any other content reference. */
function refValueFor(entry) {
  return entry.__mod === state.mod ? entry.path : `${entry.__mod}:${entry.path}`;
}

/** " (modId)" for an entry from a DIFFERENT mod than the one you're currently creating/editing content in, "" for your own — appended to display text so a cross-mod pick/listing is never mistaken for a local one. */
function originSuffix(entry) {
  return entry.__mod === state.mod ? "" : ` (${entry.__mod})`;
}

/** Resolves a stored item reference (bare — meaning the ACTIVE mod — or {@code "modId:path"}) back to its {@code state.items} entry, {@code null} if nothing matches (a stale/broken reference — {@code /api/validate} is the real check for that, this is just display). */
function resolveItemRef(ref) {
  if (!ref) return null;
  const colon = ref.indexOf(":");
  const modId = colon >= 0 ? ref.slice(0, colon) : state.mod;
  const path = colon >= 0 ? ref.slice(colon + 1) : ref;
  return state.items.find((i) => i.__mod === modId && i.path === path) || null;
}

async function fetchJson(path) {
  const res = await fetch(path);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error((data && data.error) || `${res.status} ${res.statusText}`);
  }
  return data;
}

let recipeIngredients = [];
let recipeOutput = null;
let buildingCostItem = null;
/** The building's own "Fuel item" picker state — null means no fuel requirement at all (the field is genuinely optional, unlike Cost). */
let buildingFuelItem = null;

/** Cross-mod ({@link state}.textures — every tab is cross-mod now, see its own comment), not just the active mod's own list — a building's texture (like its cost item) is very often a DIFFERENT mod's, most commonly a vanilla one. */
function textureAssetUrl(id) {
  const all = [...state.textures.vanilla, ...state.textures.mod];
  const found = all.find((t) => t.id === id);
  return found ? found.assetUrl : null;
}

/**
 * `label` on an item/building loaded from JSON is either a plain string (every mod-created entry —
 * see the "New item"/"New building" forms below) or a localized object like `{ "en": "...", "ru":
 * "..." }` (every vanilla entry — see e.g. resources/mods/rustorio/content/items/iron_ore.json).
 * Every place that shows a label must go through here, or a localized one renders as the object's
 * own `toString()`, i.e. the literal text "[object Object]".
 */
function labelText(label) {
  if (typeof label === "string") return label;
  if (label && typeof label === "object") {
    return label.ru || label.en || Object.values(label)[0] || "";
  }
  return "";
}

/** Fetches every content type from EVERY installed mod (not just the active one) and merges them, each entry tagged with its own {@code __mod} — see the {@code state} object's own comment for why. Uses {@link fetchJson} directly rather than {@link api} so each request's own explicit {@code ?mod=} is the only one on the URL, instead of {@link withModParam} silently appending a second, conflicting one for the ACTIVE mod. */
async function loadAll() {
  const results = await Promise.all(state.mods.map(async (modId) => {
    const [items, recipes, buildings, kinds, maps, textures] = await Promise.all([
      fetchJson(`/api/items?mod=${encodeURIComponent(modId)}`),
      fetchJson(`/api/recipes?mod=${encodeURIComponent(modId)}`),
      fetchJson(`/api/buildings?mod=${encodeURIComponent(modId)}`),
      fetchJson(`/api/kinds?mod=${encodeURIComponent(modId)}`),
      fetchJson(`/api/maps?mod=${encodeURIComponent(modId)}`),
      fetchJson(`/api/textures?mod=${encodeURIComponent(modId)}`),
    ]);
    return { modId, items, recipes, buildings, kinds, maps, textures };
  }));
  const tag = (list, modId) => list.map((entry) => ({ ...entry, __mod: modId }));
  state.items = results.flatMap((r) => tag(r.items, r.modId));
  state.recipes = results.flatMap((r) => tag(r.recipes, r.modId));
  state.buildings = results.flatMap((r) => tag(r.buildings, r.modId));
  state.kinds = results.flatMap((r) => tag(r.kinds, r.modId));
  state.maps = results.flatMap((r) => tag(r.maps, r.modId));
  state.textures = {
    vanilla: results.length ? results[0].textures.vanilla : [],
    mod: results.flatMap((r) => r.textures.mod),
  };
  renderItemsList();
  renderRecipesList();
  renderBuildingsList();
  renderKindsList();
  renderMapsList();
  renderTextureGrids();
  refreshContentStatus();

  // These pickers reflect the CURRENTLY open form (new-entry or editing), not just the entry
  // that was last explicitly selected — re-render them against the freshly loaded item/texture
  // lists too, otherwise a freshly opened "New recipe"/"New building" form (or the very first
  // paint, before anything is ever selected) shows empty picker boxes until the user clicks
  // something.
  updateItemGlyphPreview();
  renderRecipeIngredientsUI();
  renderRecipeOutputUI();
  renderBuildingCostUI();
  renderBuildingFuelUI();
  renderKindSelectOptions();
  updateBuildingRecipeFieldsVisibility();
  renderMapOreItemSelect();
  renderPatchInspectorOreOptions();
  renderMapEditor();
}

/* ============================================================
   mod switcher — everything above (loadAll) reads/writes state.mod's
   own content; this section is what changes state.mod in the first place
   ============================================================ */

async function loadMods() {
  const mods = await api("GET", "/api/mods");
  state.mods = mods;
  const select = document.getElementById("mod-select");
  select.innerHTML = mods.map((m) => `<option value="${m}">${m}</option>`).join("");
  // The server (EditorHttp#modId) is the source of truth for "does this mod actually exist" — a
  // stale localStorage value from a mod that got removed since falls back to the first one on the
  // list rather than sending requests the backend would 400 on forever.
  state.mod = mods.includes(state.mod) ? state.mod : (mods[0] || "rustorio");
  select.value = state.mod;
  localStorage.setItem(MOD_STORAGE_KEY, state.mod);
}

// Switching the mod switcher does NOT reload or hide anything — every tab already lists every
// mod's content regardless of which one is active (see the state object's own comment). All the
// switcher changes is (a) where a brand-new entry gets created and (b) whether an ALREADY-open
// picker offers a just-picked entry bare or as "modId:path" (refValueFor/originSuffix both read
// state.mod) — so a switch only needs to re-render the pickers, never re-fetch anything.
document.getElementById("mod-select").addEventListener("change", (e) => {
  state.mod = e.target.value;
  localStorage.setItem(MOD_STORAGE_KEY, state.mod);
  renderRecipeIngredientsUI();
  renderRecipeOutputUI();
  renderBuildingCostUI();
  renderBuildingFuelUI();
  renderKindSelectOptions();
  renderMapOreItemSelect();
  renderPatchInspectorOreOptions();
  renderMapEditor();
  toast(`New entries will now be created in "${state.mod}"`);
});

/**
 * Populates BOTH kind <select> fields (a recipe's own "Made in", a building's own "Recipe kind")
 * from things that PROVABLY exist right now — every registered building's own path (its default
 * private-pool kind, per BuildingJsonLoader) and every declared entry on the Kinds tab — nothing
 * hardcoded or free-typed, so neither field can ever hold a value that doesn't resolve to
 * something real (the whole point — see ModLoader#validateContent for the same guarantee enforced
 * server-side too, for content edited outside this UI).
 *
 * <p>Deliberately does NOT also list the vanilla BuildingType names ("FURNACE" etc.) as separate
 * options: resources/mods/rustorio/content/buildings/furnace.json (etc.) already exists and IS
 * exactly that same pool under its own real (lowercase) path — a second, differently-spelled
 * option resolving to the identical id would just be confusing.
 */
// Mirrors RecipeJsonLoader#resolveKind / BuildingJsonLoader#resolveKind (Java): an UPPERCASE
// BuildingType name is a legacy shortcut for that vanilla building's own id (e.g. "PRESS" resolves
// to rustorio:press, same ContentId as the lowercase path "press"). All of gear.json/alloy_gear.json
// /etc still store this uppercase form. Without normalizing it, the <select> below has no matching
// option, so opening one of these recipes shows an unselected dropdown — and saving without
// deliberately re-picking a value would silently write kind: "" over a working recipe.
const LEGACY_ARCHETYPE_KIND_NAMES = new Set([
  "MINER", "CHEST", "FURNACE", "BELT", "SPLITTER", "PRESS",
  "UNDERGROUND_IN", "UNDERGROUND_OUT", "LAB", "FILTER", "INSERTER", "ASSEMBLER",
]);
function normalizeLegacyKind(rawKind) {
  return LEGACY_ARCHETYPE_KIND_NAMES.has(rawKind) ? rawKind.toLowerCase() : rawKind;
}

function renderKindSelectOptions() {
  const options = [
    ...state.buildings.map((b) => ({ value: refValueFor(b), text: `${refValueFor(b)} (building)` })),
    ...state.kinds.map((k) => ({ value: refValueFor(k), text: `${refValueFor(k)} (kind)` })),
  ].sort((a, b) => a.value.localeCompare(b.value));

  const recipeSelect = document.getElementById("recipe-kind-select");
  const previousRecipeValue = recipeSelect.value;
  recipeSelect.innerHTML = options.map((o) => `<option value="${o.value}">${o.text}</option>`).join("");
  recipeSelect.value = previousRecipeValue;

  const buildingSelect = document.getElementById("building-kind-select");
  const previousBuildingValue = buildingSelect.value;
  buildingSelect.innerHTML = '<option value="">— private (this building\'s own id) —</option>'
      + options.map((o) => `<option value="${o.value}">${o.text}</option>`).join("");
  buildingSelect.value = previousBuildingValue;
}

/** FURNACE/PRESS/ASSEMBLER are the only archetypes {@code Furnace} (java) reads Recipe kind/Fuel item from — hide that whole field group for every other archetype so it isn't noise. */
const RECIPE_ARCHETYPES = new Set(["FURNACE", "PRESS", "ASSEMBLER"]);

function updateBuildingRecipeFieldsVisibility() {
  const archetype = document.getElementById("buildings-form").archetype.value;
  document.getElementById("building-recipe-fields").classList.toggle("hidden", !RECIPE_ARCHETYPES.has(archetype));
}

/* ============================================================
   static chrome: icons, nav, tabs
   ============================================================ */

document.getElementById("nav-items").innerHTML = `${icon("box")}<span>Items</span><span class="count" id="count-items">0</span>`;
document.getElementById("nav-recipes").innerHTML = `${icon("flask")}<span>Recipes</span><span class="count" id="count-recipes">0</span>`;
document.getElementById("nav-buildings").innerHTML = `${icon("factory")}<span>Buildings</span><span class="count" id="count-buildings">0</span>`;
document.getElementById("nav-kinds").innerHTML = `${icon("tag")}<span>Kinds</span><span class="count" id="count-kinds">0</span>`;
document.getElementById("nav-maps").innerHTML = `${icon("map")}<span>Maps</span><span class="count" id="count-maps">0</span>`;
document.getElementById("nav-textures").innerHTML = `${icon("image")}<span>Textures</span><span class="count" id="count-textures">0</span>`;
document.getElementById("topbar-search-icon").innerHTML = icon("search", 15);
document.querySelectorAll(".mini-search-icon").forEach((el) => (el.innerHTML = icon("search", 14)));
document.querySelectorAll('[data-new]').forEach((btn) => (btn.innerHTML = icon("plus", 16)));
document.getElementById("map-help").innerHTML = icon("help", 15);
document.querySelectorAll('[data-view-toggle]').forEach((btn) => (btn.innerHTML = `${icon("code", 13)} JSON`));
document.getElementById("play-btn").innerHTML = `${icon("play", 15)} Play`;
document.querySelector('#texture-upload-form button[type=submit]').innerHTML = `${icon("upload", 15)} Upload`;
document.getElementById("buildings-glyph-fallback").innerHTML = icon("factory", 26);

/** True if the CURRENTLY OPEN form has unsaved edits a caller is about to throw away (switching
 * tabs, selecting a different entry, starting a new one) — asks the user first (native confirm(),
 * not confirmModal: this fires from a plain synchronous click handler, and every call site needs
 * a yes/no answer before it can decide whether to proceed at all) and returns whether the caller
 * should abort. Previously nothing asked at all — clicking a different row, a different tab, or
 * even just closing the browser tab silently discarded whatever was typed but not yet Saved. */
function blockedByUnsavedChanges() {
  const tab = activeTab();
  if (tab === "textures" || !state.dirty[tab]) return false;
  return !confirm(`Discard unsaved changes to this ${tab.slice(0, -1)}?`);
}

window.addEventListener("beforeunload", (e) => {
  if (Object.values(state.dirty).some(Boolean)) {
    e.preventDefault();
    e.returnValue = ""; // Chrome requires returnValue to be set; the actual text shown is browser-chosen, not this string
  }
});

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    if (btn.dataset.tab === activeTab()) return; // already here — nothing would actually be discarded
    if (blockedByUnsavedChanges()) return;
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".workspace").forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(`panel-${btn.dataset.tab}`).classList.add("active");
    // The maps canvas can only measure its available width once its panel is actually laid out
    // (a `display:none` ancestor collapses clientWidth to 0) — resize it right as it becomes visible,
    // not just once at boot, since "items" (not "maps") is the tab active on first load.
    if (btn.dataset.tab === "maps") resizeMapCanvas();
  });
});

function activeTab() {
  return document.querySelector(".nav-item.active").dataset.tab;
}

/** Switches tabs through the SAME guarded click handler above (so a bare click on the nav and a
 * programmatic switchToTab() can never disagree about whether unsaved changes get discarded) and
 * reports back whether it actually happened — a caller that also wants to select something in the
 * destination tab (kind-usage links, global search results) needs to know NOT to do that when the
 * user chose to keep editing instead. */
function switchToTab(tab) {
  if (tab === activeTab()) return true;
  document.querySelector(`.nav-item[data-tab="${tab}"]`).click();
  return activeTab() === tab;
}

// The "Kinds tab" links inside the Recipes/Buildings forms' explanatory .field-hint text.
document.querySelectorAll("[data-goto-tab]").forEach((link) => {
  link.addEventListener("click", (e) => {
    e.preventDefault();
    switchToTab(link.dataset.gotoTab);
  });
});

/* ============================================================
   generic list rendering
   ============================================================ */

function renderList(kind, entries, rowInfo, onSelect) {
  const ul = document.getElementById(`${kind}-list`);
  const empty = document.getElementById(`${kind}-empty`);
  const filterInput = document.getElementById(`filter-${kind}`);
  const query = (filterInput.value || "").trim().toLowerCase();
  const rows = entries.map((e) => ({ entry: e, info: rowInfo(e) }));
  const filtered = query ? rows.filter((r) => r.info.search.toLowerCase().includes(query)) : rows;

  ul.innerHTML = "";
  for (const { entry, info } of filtered) {
    const li = document.createElement("li");
    li.dataset.key = info.key;
    if (state.selected[kind] === info.key) li.classList.add("selected");
    li.innerHTML = `<div class="row-thumb">${info.thumb}</div><div class="row-text"><div class="row-title"></div><div class="row-sub"></div></div>`;
    li.querySelector(".row-title").textContent = info.title;
    li.querySelector(".row-sub").textContent = info.sub;
    li.addEventListener("click", () => {
      if (blockedByUnsavedChanges()) return;
      onSelect(entry);
    });
    makeKeyboardClickable(li);
    ul.appendChild(li);
  }
  document.getElementById(`count-${kind}`).textContent = entries.length;

  if (filtered.length === 0) {
    empty.classList.remove("hidden");
    empty.textContent = entries.length === 0 ? emptyMessage(kind) : `No matches for "${query}"`;
  } else {
    empty.classList.add("hidden");
  }
}

function emptyMessage(kind) {
  return {
    items: "No items yet — click + New item to add the game's first custom item.",
    recipes: "No recipes yet — add ingredients and an output above.",
    buildings: "No buildings yet — every one reuses an existing archetype's behavior.",
    kinds: "No recipe pools in use yet — a FURNACE/PRESS/ASSEMBLER building's own pool shows up here automatically once it has a recipe, no declaring needed (see the hint above).",
    maps: "No maps yet — click + New map, then click the canvas to place your first ore or terrain patch.",
  }[kind];
}

/* ============================================================
   dirty tracking + JSON view toggle (shared across the 3 kinds)
   ============================================================ */

function markDirty(kind) {
  state.dirty[kind] = true;
  document.getElementById(`${kind}-dirty`).classList.remove("hidden");
}

function clearDirty(kind) {
  state.dirty[kind] = false;
  document.getElementById(`${kind}-dirty`).classList.add("hidden");
}

function wireDirtyTracking(kind, formEl) {
  formEl.addEventListener("input", () => markDirty(kind));
  formEl.addEventListener("change", () => markDirty(kind));
}

const collectors = {}; // kind -> () => body object
const fillers = {}; // kind -> (body) => void, repopulates the form

function wireJsonToggle(kind) {
  const btn = document.querySelector(`[data-view-toggle="${kind}"]`);
  const formEl = document.getElementById(`${kind}-form`);
  const jsonEl = document.getElementById(`${kind}-json`);
  btn.addEventListener("click", () => {
    const toJson = !state.jsonMode[kind];
    if (toJson) {
      jsonEl.value = JSON.stringify(collectors[kind](), null, 2);
      formEl.classList.add("hidden");
      jsonEl.classList.remove("hidden");
    } else {
      let body;
      try {
        body = JSON.parse(jsonEl.value);
      } catch (err) {
        toast(`Invalid JSON: ${err.message}`, true);
        return;
      }
      fillers[kind](body);
      formEl.classList.remove("hidden");
      jsonEl.classList.add("hidden");
      // The maps form was just hidden (JSON mode) — resizeMapCanvas measures its clientWidth, which
      // was 0 the whole time, so it needs a fresh measurement now that the form is visible again,
      // not just on tab-switch/window-resize (fillMap's own resetView doesn't re-measure the canvas).
      if (kind === "maps") resizeMapCanvas();
    }
    state.jsonMode[kind] = toJson;
    btn.classList.toggle("active", toJson);
  });
  jsonEl.addEventListener("input", () => markDirty(kind));
}

function currentBody(kind) {
  const jsonEl = document.getElementById(`${kind}-json`);
  if (state.jsonMode[kind]) {
    return JSON.parse(jsonEl.value); // throws — caller catches
  }
  return collectors[kind]();
}

/* ============================================================
   item picker (chip picker) — shared widget for ingredients / output / cost item
   ============================================================ */

/** {@code selectedRefs} and each option's own {@code dataset.path} are {@link refValueFor} strings (bare for the active mod's own items, {@code "modId:path"} for every other mod's) — see the {@code state} object's own comment for why this picker draws from the cross-mod pool rather than just {@code state.items}. */
function renderItemOptionsInto(container, selectedRefs) {
  container.innerHTML = "";
  for (const item of state.items) {
    const ref = refValueFor(item);
    const opt = document.createElement("div");
    opt.className = "option" + (selectedRefs.includes(ref) ? " picked" : "");
    opt.dataset.path = ref;
    const thumb = document.createElement("span");
    thumb.className = "thumb";
    thumb.innerHTML = itemGlyph(item.colorRgb, item.shape);
    opt.appendChild(thumb);
    opt.appendChild(document.createTextNode(labelText(item.label) + originSuffix(item)));
    makeKeyboardClickable(opt); // click listener wired by each of this function's own callers, below
    container.appendChild(opt);
  }
  if (state.items.length === 0) {
    container.innerHTML = '<span style="color:var(--muted-2);font-size:12px;padding:4px;">Add some items first.</span>';
  }
}

/* ================= ITEMS ================= */

function itemRowInfo(item) {
  const label = labelText(item.label);
  return {
    key: entryKey(item),
    title: label,
    sub: `${item.__mod}:${item.path}`,
    search: `${label} ${item.path} ${item.__mod}`,
    thumb: itemGlyph(item.colorRgb, item.shape),
  };
}

function renderItemsList() {
  renderList("items", state.items, itemRowInfo, selectItem);
}

function updateItemGlyphPreview() {
  const f = document.getElementById("items-form");
  document.getElementById("items-glyph-preview").innerHTML = itemGlyph(f.colorRgb.value, f.shape.value);
}

function selectItem(item) {
  fillItem(item);
  document.getElementById("items-form-title").textContent = `Edit "${entryKey(item)}"`;
  formError("items", "");
  state.selected.items = entryKey(item);
  clearDirty("items");
  renderItemsList();
}

// The raw `label` (string OR localized {en,ru,...} object) of whatever's currently loaded into the
// items form — see collectItem()'s own comment for why this is kept around instead of just reading
// the text input back.
let editingItemLabel;

function collectItem() {
  const f = document.getElementById("items-form");
  const typed = f.label.value.trim();
  // The Label field is a single plain-text input — there's no per-language UI here. If it still
  // reads exactly what a localized label displays as, the player never touched it: save the
  // ORIGINAL {en,ru,...} object back untouched, not a flattened single-language string that would
  // silently discard every OTHER language the next time this vanilla entry loads. Only once they
  // actually edit the field does it become a plain string, same as any brand-new item.
  const label = editingItemLabel && typeof editingItemLabel === "object" && typed === labelText(editingItemLabel)
      ? editingItemLabel
      : typed;
  const body = {
    path: f.path.value.trim(),
    label,
    colorRgb: f.colorRgb.value,
    shape: f.shape.value,
    researchGrade: f.researchGrade.checked,
  };
  // Only written out when the author actually picked something. "Auto" is the ABSENCE of the
  // field, not a third value stored in it (see oreItemPool): every item file authored before this
  // field existed means auto, so auto has to be spelled the same way they already spell it, or
  // the two would be different states that behave identically.
  if (f.tool.value) {
    body.tool = f.tool.value;
  }
  return body;
}
collectors.items = collectItem;

function fillItem(body) {
  const f = document.getElementById("items-form");
  editingItemLabel = body.label;
  f.path.value = body.path || "";
  f.label.value = labelText(body.label);
  f.colorRgb.value = body.colorRgb || "#aaaaaa";
  f.shape.value = body.shape || "CIRCLE";
  f.tool.value = body.tool || ""; // "" is the Auto option — see collectItem on why absence is the auto state
  f.researchGrade.checked = !!body.researchGrade;
  updateItemGlyphPreview();
}
fillers.items = fillItem;

document.querySelector('[data-new="items"]').addEventListener("click", () => {
  if (blockedByUnsavedChanges()) return;
  fillItem({});
  document.getElementById("items-form-title").textContent = "New item";
  formError("items", "");
  state.selected.items = null;
  clearDirty("items");
  renderItemsList();
});

document.getElementById("items-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  let body;
  try {
    body = currentBody("items");
  } catch (err) {
    formError("items", `Invalid JSON: ${err.message}`);
    return;
  }
  // Editing an existing item writes back to ITS OWN mod, not whatever the switcher currently
  // shows — only a brand-new item (state.selected.items === null) goes to the active mod.
  const targetMod = state.selected.items ? splitEntryKey(state.selected.items).mod : state.mod;
  try {
    if (state.selected.items) {
      const { path } = splitEntryKey(state.selected.items);
      await api("PUT", `/api/items/${path}`, body, targetMod);
    } else {
      await api("POST", "/api/items", body, targetMod);
    }
    formError("items", "");
    toast(`Saved "${targetMod}:${body.path}"`);
    await loadAll();
    selectItem({ ...body, __mod: targetMod });
  } catch (err) {
    formError("items", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="items"]').addEventListener("click", async () => {
  const key = state.selected.items;
  if (!key) return;
  const { mod, path } = splitEntryKey(key);
  if (!(await confirmModal("Delete item?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/items/${path}`, undefined, mod);
    toast(`Deleted "${key}"`);
    await loadAll();
    fillItem({});
    document.getElementById("items-form-title").textContent = "New item";
    state.selected.items = null;
    clearDirty("items");
    renderItemsList();
  } catch (err) {
    formError("items", err.message);
    toast(err.message, true);
  }
});

wireDirtyTracking("items", document.getElementById("items-form"));
wireJsonToggle("items");
document.getElementById("items-form").colorRgb.addEventListener("input", updateItemGlyphPreview);
document.getElementById("items-form").shape.addEventListener("change", updateItemGlyphPreview);

/* ================= RECIPES ================= */

/** {@code recipe.file} plays the role {@code path} plays for every other content kind — a recipe has no field of its own naming it (see RecipesHandler's own javadoc), just this API-assigned filename. */
function recipeKey(recipe) {
  return `${recipe.__mod}:${recipe.file}`;
}

function recipeRowInfo(recipe) {
  const outputItem = resolveItemRef(recipe.output);
  return {
    key: recipeKey(recipe),
    title: recipe.file,
    sub: `${recipe.__mod} · ${recipe.ingredients.join(" + ")} → ${recipe.output}`,
    search: `${recipe.file} ${recipe.ingredients.join(" ")} ${recipe.output} ${recipe.__mod}`,
    thumb: outputItem ? itemGlyph(outputItem.colorRgb, outputItem.shape) : icon("flask", 16),
  };
}

function renderRecipesList() {
  renderList("recipes", state.recipes, recipeRowInfo, selectRecipe);
}

function renderRecipeIngredientsUI() {
  const chips = document.getElementById("recipe-ingredients-chips");
  chips.innerHTML = "";
  for (const path of recipeIngredients) {
    const item = resolveItemRef(path);
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.innerHTML = `<span class="chip-thumb">${item ? itemGlyph(item.colorRgb, item.shape) : ""}</span>`;
    chip.appendChild(document.createTextNode(item ? labelText(item.label) + originSuffix(item) : path));
    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.innerHTML = icon("close", 12);
    removeBtn.addEventListener("click", () => {
      recipeIngredients = recipeIngredients.filter((p) => p !== path);
      markDirty("recipes");
      renderRecipeIngredientsUI();
    });
    chip.appendChild(removeBtn);
    chips.appendChild(chip);
  }
  if (recipeIngredients.length === 0) {
    chips.innerHTML = '<span style="color:var(--muted-2);font-size:12px;">none selected</span>';
  }

  const picker = document.getElementById("recipe-ingredients-picker");
  renderItemOptionsInto(picker, recipeIngredients);
  picker.querySelectorAll(".option").forEach((opt) => {
    opt.addEventListener("click", () => {
      const path = opt.dataset.path;
      const idx = recipeIngredients.indexOf(path);
      if (idx >= 0) recipeIngredients.splice(idx, 1);
      else recipeIngredients.push(path);
      markDirty("recipes");
      renderRecipeIngredientsUI();
    });
  });
}

function renderRecipeOutputUI() {
  const picker = document.getElementById("recipe-output-picker");
  renderItemOptionsInto(picker, recipeOutput ? [recipeOutput] : []);
  picker.querySelectorAll(".option").forEach((opt) => {
    opt.addEventListener("click", () => {
      recipeOutput = opt.dataset.path;
      document.getElementById("recipe-output-value").value = recipeOutput;
      markDirty("recipes");
      renderRecipeOutputUI();
    });
  });
}

function selectRecipe(recipe) {
  fillRecipe(recipe);
  document.getElementById("recipes-form").file.disabled = true;
  document.getElementById("recipes-form-title").textContent = `Edit "${recipeKey(recipe)}"`;
  formError("recipes", "");
  state.selected.recipes = recipeKey(recipe);
  clearDirty("recipes");
  renderRecipesList();
}

function collectRecipe() {
  const f = document.getElementById("recipes-form");
  return {
    ingredients: [...recipeIngredients],
    output: recipeOutput,
    time: Number(f.time.value),
    kind: f.kind.value,
  };
}
collectors.recipes = collectRecipe;

function fillRecipe(body) {
  const f = document.getElementById("recipes-form");
  f.file.value = body.file || "";
  recipeIngredients = [...(body.ingredients || [])];
  recipeOutput = body.output || null;
  document.getElementById("recipe-output-value").value = recipeOutput || "";
  f.time.value = body.time || 5;
  // No hardcoded fallback (the old free-text field defaulted to the literal string "FURNACE") —
  // the <select>'s own options are the only valid values now; an empty/unmatched body.kind just
  // leaves nothing selected. normalizeLegacyKind handles the one common legacy case (uppercase
  // archetype-name shortcuts already used by the shipped vanilla recipes).
  f.kind.value = normalizeLegacyKind(body.kind || "");
  renderRecipeIngredientsUI();
  renderRecipeOutputUI();
}
fillers.recipes = fillRecipe;

document.querySelector('[data-new="recipes"]').addEventListener("click", () => {
  if (blockedByUnsavedChanges()) return;
  fillRecipe({});
  document.getElementById("recipes-form").file.disabled = false;
  document.getElementById("recipes-form-title").textContent = "New recipe";
  formError("recipes", "");
  state.selected.recipes = null;
  clearDirty("recipes");
  renderRecipesList();
});

document.getElementById("recipes-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  let body;
  try {
    body = currentBody("recipes");
  } catch (err) {
    formError("recipes", `Invalid JSON: ${err.message}`);
    return;
  }
  const fileName = document.getElementById("recipes-form").file.value.trim();
  const targetMod = state.selected.recipes ? splitEntryKey(state.selected.recipes).mod : state.mod;
  try {
    if (state.selected.recipes) {
      const { path: file } = splitEntryKey(state.selected.recipes);
      await api("PUT", `/api/recipes/${file}`, body, targetMod);
    } else {
      await api("POST", `/api/recipes?file=${encodeURIComponent(fileName)}`, body, targetMod);
    }
    formError("recipes", "");
    toast(`Saved "${targetMod}:${fileName}"`);
    await loadAll();
    selectRecipe({ ...body, file: fileName, __mod: targetMod });
  } catch (err) {
    formError("recipes", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="recipes"]').addEventListener("click", async () => {
  const key = state.selected.recipes;
  if (!key) return;
  const { mod, path: file } = splitEntryKey(key);
  if (!(await confirmModal("Delete recipe?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/recipes/${file}`, undefined, mod);
    toast(`Deleted "${key}"`);
    await loadAll();
    fillRecipe({});
    document.getElementById("recipes-form").file.disabled = false;
    document.getElementById("recipes-form-title").textContent = "New recipe";
    state.selected.recipes = null;
    clearDirty("recipes");
    renderRecipesList();
  } catch (err) {
    formError("recipes", err.message);
    toast(err.message, true);
  }
});

wireDirtyTracking("recipes", document.getElementById("recipes-form"));
wireJsonToggle("recipes");

/* ================= BUILDINGS ================= */

function buildingRowInfo(b) {
  const assetUrl = textureAssetUrl(b.texture);
  const label = labelText(b.label);
  return {
    key: entryKey(b),
    title: label,
    sub: `${b.archetype} · ${b.__mod}:${b.path}`,
    search: `${label} ${b.path} ${b.archetype} ${b.__mod}`,
    thumb: assetUrl ? `<img src="${assetUrl}" alt="">` : icon("factory", 16),
  };
}

function renderBuildingsList() {
  renderList("buildings", state.buildings, buildingRowInfo, selectBuilding);
}

function updateBuildingGlyphPreview() {
  const preview = document.getElementById("buildings-glyph-preview");
  const assetUrl = textureAssetUrl(document.getElementById("building-texture-value").value);
  preview.innerHTML = assetUrl ? `<img src="${assetUrl}" alt="">` : `<span>${icon("factory", 26)}</span>`;
}

function renderBuildingCostUI() {
  const picker = document.getElementById("building-cost-picker");
  renderItemOptionsInto(picker, buildingCostItem ? [buildingCostItem] : []);
  picker.querySelectorAll(".option").forEach((opt) => {
    opt.addEventListener("click", () => {
      buildingCostItem = opt.dataset.path;
      document.getElementById("building-cost-item-value").value = buildingCostItem;
      markDirty("buildings");
      renderBuildingCostUI();
    });
  });
}

function renderBuildingFuelUI() {
  const picker = document.getElementById("building-fuel-picker");
  renderItemOptionsInto(picker, buildingFuelItem ? [buildingFuelItem] : []);
  picker.querySelectorAll(".option").forEach((opt) => {
    opt.addEventListener("click", () => {
      buildingFuelItem = opt.dataset.path;
      document.getElementById("building-fuel-value").value = buildingFuelItem;
      markDirty("buildings");
      renderBuildingFuelUI();
    });
  });
}

document.getElementById("building-fuel-clear").addEventListener("click", () => {
  buildingFuelItem = null;
  document.getElementById("building-fuel-value").value = "";
  markDirty("buildings");
  renderBuildingFuelUI();
});

function renderBuildingTexturePicker() {
  const picker = document.getElementById("building-texture-picker");
  picker.innerHTML = "";
  const all = [...state.textures.vanilla, ...state.textures.mod];
  const selected = document.getElementById("building-texture-value").value;
  for (const t of all) {
    const div = document.createElement("div");
    div.className = "swatch" + (t.id === selected ? " selected" : "");
    div.title = t.id;
    div.innerHTML = `<img src="${t.assetUrl}" alt="${t.id}">`;
    div.addEventListener("click", () => {
      document.getElementById("building-texture-value").value = t.id;
      markDirty("buildings");
      renderBuildingTexturePicker();
      updateBuildingGlyphPreview();
    });
    makeKeyboardClickable(div);
    picker.appendChild(div);
  }
}

function selectBuilding(b) {
  fillBuilding(b);
  document.getElementById("buildings-form-title").textContent = `Edit "${entryKey(b)}"`;
  formError("buildings", "");
  state.selected.buildings = entryKey(b);
  clearDirty("buildings");
  renderBuildingsList();
}

// Same "don't clobber a localized label the player never touched" reasoning as editingItemLabel —
// see collectItem()'s own comment.
let editingBuildingLabel;

function collectBuilding() {
  const f = document.getElementById("buildings-form");
  const typed = f.label.value.trim();
  const label = editingBuildingLabel && typeof editingBuildingLabel === "object" && typed === labelText(editingBuildingLabel)
      ? editingBuildingLabel
      : typed;
  // kind/fuel are genuinely optional — an empty string / null becomes `undefined` here so
  // JSON.stringify drops the key entirely (matching "omitted" in the saved JSON file), instead of
  // writing an empty string BuildingJsonLoader would then have to special-case.
  const kind = f.kind.value.trim();
  return {
    path: f.path.value.trim(),
    label,
    archetype: f.archetype.value,
    kind: kind || undefined,
    fuel: buildingFuelItem || undefined,
    cost: { item: buildingCostItem, amount: Number(f.costAmount.value) },
    placement: f.placement.value,
    texture: document.getElementById("building-texture-value").value,
    footprintWidth: Number(f.footprintWidth.value),
    footprintHeight: Number(f.footprintHeight.value),
    bufferMax: Number(f.bufferMax.value),
    speedMultiplier: Number(f.speedMultiplier.value),
    acceptsSpeedEffects: f.acceptsSpeedEffects.checked,
  };
}
collectors.buildings = collectBuilding;

function fillBuilding(b) {
  const f = document.getElementById("buildings-form");
  editingBuildingLabel = b.label;
  f.path.value = b.path || "";
  f.label.value = labelText(b.label);
  f.archetype.value = b.archetype || "CHEST";
  f.kind.value = normalizeLegacyKind(b.kind || "");
  buildingFuelItem = b.fuel || null;
  document.getElementById("building-fuel-value").value = buildingFuelItem || "";
  f.placement.value = b.placement || "ALWAYS";
  buildingCostItem = (b.cost && b.cost.item) || null;
  f.costAmount.value = (b.cost && b.cost.amount) || 1;
  document.getElementById("building-texture-value").value = b.texture || "";
  f.footprintWidth.value = b.footprintWidth || 1;
  f.footprintHeight.value = b.footprintHeight || 1;
  f.bufferMax.value = b.bufferMax || 0;
  f.speedMultiplier.value = b.speedMultiplier || 1;
  f.acceptsSpeedEffects.checked = !!b.acceptsSpeedEffects;
  renderBuildingCostUI();
  renderBuildingFuelUI();
  renderBuildingTexturePicker();
  updateBuildingGlyphPreview();
  updateBuildingRecipeFieldsVisibility();
}
fillers.buildings = fillBuilding;

document.querySelector('[data-new="buildings"]').addEventListener("click", () => {
  if (blockedByUnsavedChanges()) return;
  fillBuilding({ footprintWidth: 1, footprintHeight: 1, bufferMax: 0, speedMultiplier: 1 });
  document.getElementById("buildings-form-title").textContent = "New building";
  formError("buildings", "");
  state.selected.buildings = null;
  clearDirty("buildings");
  renderBuildingsList();
});

document.getElementById("buildings-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  let body;
  try {
    body = currentBody("buildings");
  } catch (err) {
    formError("buildings", `Invalid JSON: ${err.message}`);
    return;
  }
  const targetMod = state.selected.buildings ? splitEntryKey(state.selected.buildings).mod : state.mod;
  try {
    if (state.selected.buildings) {
      const { path } = splitEntryKey(state.selected.buildings);
      await api("PUT", `/api/buildings/${path}`, body, targetMod);
    } else {
      await api("POST", "/api/buildings", body, targetMod);
    }
    formError("buildings", "");
    toast(`Saved "${targetMod}:${body.path}"`);
    await loadAll();
    selectBuilding({ ...body, __mod: targetMod });
  } catch (err) {
    formError("buildings", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="buildings"]').addEventListener("click", async () => {
  const key = state.selected.buildings;
  if (!key) return;
  const { mod, path } = splitEntryKey(key);
  if (!(await confirmModal("Delete building?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/buildings/${path}`, undefined, mod);
    toast(`Deleted "${key}"`);
    await loadAll();
    fillBuilding({ footprintWidth: 1, footprintHeight: 1, bufferMax: 0, speedMultiplier: 1 });
    document.getElementById("buildings-form-title").textContent = "New building";
    state.selected.buildings = null;
    clearDirty("buildings");
    renderBuildingsList();
  } catch (err) {
    formError("buildings", err.message);
    toast(err.message, true);
  }
});

wireDirtyTracking("buildings", document.getElementById("buildings-form"));
wireJsonToggle("buildings");
document.getElementById("buildings-form").archetype.addEventListener("change", updateBuildingRecipeFieldsVisibility);

function formError(kind, message) {
  document.getElementById(`${kind}-form-error`).textContent = message || "";
}

/* ================= KINDS ================= */

/**
 * One row per recipe pool actually in play — not just the declared ones. A pool "exists" here if
 * ANY of these is true: (a) it has its own {@code content/kinds/*.json} file, (b) it's a
 * FURNACE/PRESS/ASSEMBLER building's own path (its default/self-referencing private pool — kind is
 * inert on every other archetype, see {@code updateBuildingRecipeFieldsVisibility}), or (c) some
 * recipe/building explicitly points at it, even if that target turns out not to exist (an
 * "orphaned" reference — surfaced here deliberately instead of hidden, since spotting exactly this
 * kind of broken reference is the whole point of this tab; {@code /api/validate} catches it too,
 * but this is where a modder would come looking to understand WHY).
 */
/** {@code e.id} is always a fully-qualified {@code "modId:path"} (via {@link resolveKindRef}/{@link entryKey}) — with content from every mod in play now, grouping by the bare path alone would wrongly merge two different mods' same-named pools, or wrongly split one shared pool a building references bare (resolving in ITS OWN mod) from how the declaring Kind's own entry got grouped. */
function computeKindUsageIndex() {
  const byId = new Map();
  function entryFor(id) {
    if (!byId.has(id)) byId.set(id, { id, label: id, declared: null, buildings: [], recipes: [] });
    return byId.get(id);
  }

  for (const k of state.kinds) {
    const e = entryFor(entryKey(k));
    e.declared = k;
    e.label = labelText(k.label);
  }
  for (const b of state.buildings) {
    if (!RECIPE_ARCHETYPES.has(b.archetype)) continue; // kind is never read outside these 3
    const id = resolveKindRef(normalizeLegacyKind(b.kind || "") || b.path, b.__mod);
    const e = entryFor(id);
    e.buildings.push(b);
    if (id === entryKey(b) && !e.declared) e.label = labelText(b.label);
  }
  for (const r of state.recipes) {
    const raw = normalizeLegacyKind(r.kind || "");
    if (raw) entryFor(resolveKindRef(raw, r.__mod)).recipes.push(r);
  }

  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

/** "declared" has its own kinds/*.json file; "owned" is a real building's own default pool with no file; "orphaned" is referenced by something but backed by neither — a broken reference. */
function kindEntryStatus(e) {
  if (e.declared) return "declared";
  if (e.buildings.some((b) => entryKey(b) === e.id)) return "owned";
  return "orphaned";
}

function kindRowInfo(e) {
  const status = kindEntryStatus(e);
  const badge = status === "declared" ? "" : status === "owned" ? " (implicit)" : " (broken)";
  const count = `${e.recipes.length} recipe${e.recipes.length === 1 ? "" : "s"}, ${e.buildings.length} building${e.buildings.length === 1 ? "" : "s"}`;
  return {
    key: e.id,
    title: e.label + badge,
    sub: `${e.id} · ${count}`,
    search: `${e.label} ${e.id}`,
    thumb: icon(status === "orphaned" ? "alert" : "flask", 16),
  };
}

function renderKindsList() {
  state.kindsIndex = computeKindUsageIndex();
  renderList("kinds", state.kindsIndex, kindRowInfo, selectKindEntry);
}

function renderUsageList(elementId, rows, describe, onOpen) {
  const el = document.getElementById(elementId);
  el.innerHTML = "";
  if (rows.length === 0) {
    const li = document.createElement("li");
    li.className = "empty";
    li.textContent = "— none —";
    el.appendChild(li);
    return;
  }
  for (const row of rows) {
    const li = document.createElement("li");
    li.textContent = describe(row);
    li.addEventListener("click", () => onOpen(row));
    makeKeyboardClickable(li);
    el.appendChild(li);
  }
}

function renderKindUsagePanel(e) {
  document.getElementById("kinds-usage-panel").classList.remove("hidden");
  renderUsageList("kind-usage-buildings", e.buildings,
      (b) => `${labelText(b.label)} (${entryKey(b)})${entryKey(b) === e.id ? " — own pool" : " — shares this pool"}`,
      (b) => { if (switchToTab("buildings")) selectBuilding(b); });
  renderUsageList("kind-usage-recipes", e.recipes,
      (r) => `${r.file} — ${r.ingredients.join(" + ")} → ${r.output}`,
      (r) => { if (switchToTab("recipes")) selectRecipe(r); });
  const outputPaths = [...new Set(e.recipes.map((r) => r.output))];
  const outputItems = outputPaths.map((path) => resolveItemRef(path)).filter(Boolean);
  renderUsageList("kind-usage-items", outputItems,
      (item) => labelText(item.label) + originSuffix(item),
      (item) => { if (switchToTab("items")) selectItem(item); });
}

function hideKindUsagePanel() {
  document.getElementById("kinds-usage-panel").classList.add("hidden");
}

function selectKindEntry(e) {
  state.selected.kinds = e.id;
  formError("kinds", "");
  clearDirty("kinds");
  const status = kindEntryStatus(e);
  const form = document.getElementById("kinds-form");
  const info = document.getElementById("kinds-implicit-info");
  const jsonToggleBtn = document.querySelector('[data-view-toggle="kinds"]');
  const jsonEl = document.getElementById("kinds-json");
  if (status === "declared") {
    fillKind(e.declared);
    document.getElementById("kinds-form-title").textContent = `Edit "${e.id}"`;
    form.classList.remove("hidden");
    info.classList.add("hidden");
    jsonToggleBtn.classList.remove("hidden");
  } else {
    document.getElementById("kinds-form-title").textContent = `"${e.id}"`;
    form.classList.add("hidden");
    info.classList.remove("hidden");
    // No file backs an implicit/orphaned entry — there's no JSON body to show or edit for it.
    jsonToggleBtn.classList.add("hidden");
    jsonToggleBtn.classList.remove("active");
    state.jsonMode.kinds = false;
    jsonEl.classList.add("hidden");
    info.classList.toggle("warn", status === "orphaned");
    const goto = document.getElementById("kinds-implicit-goto-building");
    if (status === "owned") {
      const owner = e.buildings.find((b) => entryKey(b) === e.id);
      document.getElementById("kinds-implicit-text").textContent =
          `This isn't a declared Kind — it's the "${labelText(owner.label)}" building's own default recipe pool, named after the building itself. There's no separate file to edit: rename the building on the Buildings tab if you need to change this id, or click below to declare it as a real Kind instead (only needed if another building should start sharing this same pool).`;
      goto.textContent = "Edit the building →";
      goto.classList.remove("hidden");
      goto.onclick = () => { switchToTab("buildings"); selectBuilding(owner); };
    } else {
      document.getElementById("kinds-implicit-text").textContent =
          `Nothing actually provides this pool — no declared Kind and no building whose own id is "${e.id}", yet ${e.recipes.length + e.buildings.length} entr${e.recipes.length + e.buildings.length === 1 ? "y" : "ies"} below still point at it. This is a broken reference (a typo, or something that got renamed/deleted) — Validate will refuse to load it as-is. Repoint the entries below, or declare a Kind named "${e.id}" to make the reference real.`;
      goto.classList.add("hidden");
    }
  }
  renderKindUsagePanel(e);
  renderKindsList();
}

// Same "don't clobber a localized label the player never touched" reasoning as editingItemLabel.
let editingKindLabel;

function collectKind() {
  const f = document.getElementById("kinds-form");
  const typed = f.label.value.trim();
  const label = editingKindLabel && typeof editingKindLabel === "object" && typed === labelText(editingKindLabel)
      ? editingKindLabel
      : typed;
  return { path: f.path.value.trim(), label };
}
collectors.kinds = collectKind;

function fillKind(body) {
  const f = document.getElementById("kinds-form");
  editingKindLabel = body.label;
  f.path.value = body.path || "";
  f.label.value = labelText(body.label);
}
fillers.kinds = fillKind;

document.querySelector('[data-new="kinds"]').addEventListener("click", () => {
  if (blockedByUnsavedChanges()) return;
  fillKind({});
  document.getElementById("kinds-form-title").textContent = "New kind";
  formError("kinds", "");
  state.selected.kinds = null;
  clearDirty("kinds");
  document.getElementById("kinds-form").classList.remove("hidden");
  document.getElementById("kinds-implicit-info").classList.add("hidden");
  document.querySelector('[data-view-toggle="kinds"]').classList.remove("hidden");
  hideKindUsagePanel();
  renderKindsList();
});

document.getElementById("kinds-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  let body;
  try {
    body = currentBody("kinds");
  } catch (err) {
    formError("kinds", `Invalid JSON: ${err.message}`);
    return;
  }
  const oldKey = state.selected.kinds;
  const targetMod = oldKey ? splitEntryKey(oldKey).mod : state.mod;
  try {
    // Renaming a declared kind's path doesn't touch any file that still points at the OLD id — the
    // API has no way to know those references exist. Warn before orphaning them; same reasoning as
    // the delete confirm below.
    if (oldKey && body.path !== splitEntryKey(oldKey).path) {
      const current = (state.kindsIndex || []).find((k) => k.id === oldKey);
      const usageCount = current ? current.buildings.length + current.recipes.length : 0;
      if (usageCount > 0) {
        const proceed = await confirmModal("Rename this kind?",
            `${usageCount} building/recipe entr${usageCount === 1 ? "y" : "ies"} still point at "${oldKey}". They will NOT be updated automatically and will fail to load until repointed to "${targetMod}:${body.path}". Rename anyway?`);
        if (!proceed) return;
      }
    }
    if (oldKey) {
      const { path } = splitEntryKey(oldKey);
      await api("PUT", `/api/kinds/${path}`, body, targetMod);
    } else {
      await api("POST", "/api/kinds", body, targetMod);
    }
    formError("kinds", "");
    toast(`Saved "${targetMod}:${body.path}"`);
    await loadAll();
    const savedKey = `${targetMod}:${body.path}`;
    const saved = (state.kindsIndex || []).find((k) => k.id === savedKey);
    if (saved) selectKindEntry(saved);
  } catch (err) {
    formError("kinds", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="kinds"]').addEventListener("click", async () => {
  const key = state.selected.kinds;
  if (!key) return;
  const { mod, path } = splitEntryKey(key);
  const current = (state.kindsIndex || []).find((k) => k.id === key);
  const usageCount = current ? current.buildings.length + current.recipes.length : 0;
  const usageWarning = usageCount > 0
      ? ` ${usageCount} building/recipe entr${usageCount === 1 ? "y" : "ies"} currently point at it and will fail to load until repointed.`
      : " Nothing currently points at it, so this is safe.";
  if (!(await confirmModal("Delete kind?", `"${key}" will be removed permanently.${usageWarning}`))) return;
  try {
    await api("DELETE", `/api/kinds/${path}`, undefined, mod);
    toast(`Deleted "${key}"`);
    await loadAll();
    fillKind({});
    document.getElementById("kinds-form-title").textContent = "New kind";
    state.selected.kinds = null;
    clearDirty("kinds");
    document.getElementById("kinds-form").classList.remove("hidden");
    document.getElementById("kinds-implicit-info").classList.add("hidden");
    document.querySelector('[data-view-toggle="kinds"]').classList.remove("hidden");
    hideKindUsagePanel();
    renderKindsList();
  } catch (err) {
    formError("kinds", err.message);
    toast(err.message, true);
  }
});

wireDirtyTracking("kinds", document.getElementById("kinds-form"));
wireJsonToggle("kinds");

/* ================= MAPS ================= */

let mapOrePatches = [];
let mapTerrainPatches = [];

/** The two spellings terrain had before it became content, and the colors they drew as. A patch
 * saved by an older editor still says {@code "WATER"} / {@code "ROCK"} instead of an item
 * reference, and the game still loads those (MapJsonLoader keeps the same legacy shortcut) — so
 * the canvas has to keep drawing them, or an existing map would open full of gray blanks. New
 * patches never write these: they carry whichever item the tool's own picker offered. */
const LEGACY_TERRAIN_COLORS = { WATER: "#3a6ea5", ROCK: "#7a7a76" };

/** The map tools that place something, and the layer each one places into. Terrain is no longer a
 * closed pair of enum names — these are TOOL names now, and what a patch of that kind actually
 * contains is an item picked from {@link toolItemPool}, exactly like ore already worked. */
const PLACING_TOOLS = { ore: "ore", water: "terrain", rock: "terrain" };

/**
 * One-click multi-patch drops — "как обычно сделано" for a lake/ore-vein instead of placing every
 * circle by hand. {@code offsets} are cell deltas from the click point; {@code radiusDelta} adjusts
 * the toolbar radius field per stamp (a seam reads thinner, a lake reads bigger). Still ordinary
 * {@link OrePatch}/{@link TerrainPatch} entries once dropped — no new domain concept, just several
 * pushed in one gesture.
 */
const STAMPS = {
  "stamp-ore-cluster": { kind: "ore", radiusDelta: 0, offsets: [[0, 0], [3, 1], [-2, 2], [2, -2], [-3, -1]] },
  "stamp-ore-seam": { kind: "ore", radiusDelta: -1, offsets: [[-4, 0], [-1, 1], [2, 0], [5, -1]] },
  "stamp-lake": { kind: "terrain", tool: "water", radiusDelta: 3, offsets: [[0, 0], [2, 1], [-2, 1], [0, -2]] },
  "stamp-ridge": { kind: "terrain", tool: "rock", radiusDelta: 0, offsets: [[-5, -2], [-2, 0], [1, 1], [4, 2]] },
};

/** Which layer (see mapLayerVisible) a given #map-tool value would actually draw into — "ore" for
 * the Ore tool and the two ore-flavored stamps, "terrain" for Water/Rock and the two
 * terrain-flavored stamps. Used to refuse placing into a currently-hidden layer (mousedown). */
function toolLayerKind(tool) {
  if (STAMPS[tool]) return STAMPS[tool].kind;
  return PLACING_TOOLS[tool] || "terrain";
}

/** The plain tool a value stands for — a stamp answers with the tool it paints as ({@code "ore"},
 * {@code "water"}, {@code "rock"}), everything else with itself. One place that knows the mapping,
 * so the item picker, the preview color and the commit all read the same pool. */
function baseToolOf(tool) {
  return STAMPS[tool] ? (STAMPS[tool].kind === "ore" ? "ore" : STAMPS[tool].tool) : tool;
}

/** The canvas element's own drawing-buffer resolution, recomputed by {@link resizeMapCanvas} to
 * fill the ENTIRE available panel width AND height (not just whichever is smaller) instead of the
 * fixed 512px square box this used to be hardcoded to. Deliberately NOT forced square: a square
 * canvas centered inside a wider (or taller) wrap left a margin on the long axis that was painted
 * the exact same background color as the canvas itself — visually indistinguishable from map, but
 * dead: mouse listeners are bound to {@code #map-canvas} itself, not its wrapper, so clicking that
 * margin silently did nothing. Two numbers, not one, because the map area is no longer guaranteed
 * square once it fills a real (usually landscape) panel. {@link mapMinScale}/{@link mapMaxScale}
 * derive from these instead of from fixed constants, so zoom bounds stay correct at whatever size
 * the canvas actually ends up. */
let mapCanvasWidth = 512;
let mapCanvasHeight = 512;

/** The whole 256x256 map fits on its OWN tighter axis — can't zoom out further than this. The
 * other (longer) axis then shows MORE than 256 world units at this scale, same as any ordinary 2D
 * camera whose viewport isn't the same aspect ratio as the level it's pointed at: not a distortion
 * (one uniform scale for both axes — see {@link worldToScreen}), just a wider field of view. */
function mapMinScale() {
  return Math.min(mapCanvasWidth, mapCanvasHeight) / MAP_SIZE;
}

/** 16 cells visible on the tighter axis — individually clickable, same ratio the editor has always used. */
function mapMaxScale() {
  return Math.min(mapCanvasWidth, mapCanvasHeight) / 16;
}

/** The zoom a map opens at, as a multiple of {@link mapMinScale} — i.e. exactly what the HUD reads
 * as 200%. "Fit whole map" (100%) shows all 256x256 cells at once, which is right for finding your
 * way around but too small to place anything precisely; twice that still shows a quarter of the map
 * while cells are big enough to aim at. Owner's choice, 04.08.2026. Deliberately expressed as a
 * multiple of the fit scale rather than an absolute one: the fit scale itself depends on the canvas
 * size (see mapCanvasWidth), so a hardcoded number would mean a different zoom on every panel size. */
const DEFAULT_ZOOM = 2;

/** Pan/zoom camera over the 256x256 world — this mapping changes on wheel/shift-drag; the canvas element's OWN size is {@link mapCanvasWidth}/{@link mapCanvasHeight}, changed separately by {@link resizeMapCanvas}. */
let mapView = { scale: mapMinScale() * DEFAULT_ZOOM, offsetX: 0, offsetY: 0 };
/** Every currently selected patch, as {@code {list, patch}} pairs — keyed by the patch OBJECT
 * itself, not its index: an index goes stale the moment any OTHER row in the same list is deleted
 * or drag-reordered, silently "selecting" whatever patch happens to have slid into that slot. A
 * single click replaces this with a one-element array; Shift-click toggles one pair in or out;
 * rubber-band (the "Select" tool) replaces it with everything the box enclosed. Most call sites
 * that only make sense for exactly one patch (resize, the numeric X/Y/radius/item fields) check
 * {@code mapSelectedSet.length === 1} rather than having a separate single-selection variable. */
let mapSelectedSet = [];
/** In-progress mouse interaction — {@code null} when idle. See the canvas {@code mousedown} handler for the shapes this takes. */
let mapDrag = null;
/** The not-yet-committed patches a "place" drag has painted so far — drawn as translucent ghosts,
 * pushed into the real array together on mouseup. An array rather than the single patch it used to
 * be: dragging now stamps copies along the path (see {@link extendPlaceStroke}) instead of resizing
 * one patch. Empty, not null, when idle — one shape to test everywhere. */
let mapPendingPatches = [];
/** World coordinates under the cursor right now (idle hover, not dragging) — drives the coordinate readout and the placement preview ghost. */
let mapHoverWorld = null;
/** {@link hitTestPatch}'s own result for the CURRENT idle hover — {@code null} off the map, over
 * empty space, or mid-drag. Drives {@link renderHoverToolbar}'s quick Duplicate/Delete pill; kept
 * as its own variable rather than re-hit-testing inside that render function so a render triggered
 * by something OTHER than a mousemove (a keyboard nudge, a save) doesn't have to re-run hit-testing
 * against a cursor position that hasn't actually changed. */
let mapHoverPatch = null;
/** Set by the keydown arrow-nudge branch, consumed by the keyup listener right below it — coalesces a whole key-repeat sequence into one undo step, the same way a mouse drag only pushes history on mouseup. */
let mapNudgePending = false;
/** Per-layer visibility, toggled from the tools panel — a display-only affordance for a dense map
 * where ore and terrain patches bury each other, NOT part of the saved map data (collectMap never
 * reads this). Hiding a layer also takes it out of hit-testing/rubber-band/the patch list, not
 * just off the canvas — otherwise clicking or box-selecting through an "invisible" layer would
 * still catch patches you can't see, which is more confusing than genuinely being out of the way. */
let mapLayerVisible = { ore: true, terrain: true };

let mapHistory = [];
let mapHistoryIndex = -1;
const MAP_HISTORY_LIMIT = 50;

function mapRowInfo(map) {
  const label = labelText(map.label);
  const oreCount = (map.orePatches || []).length;
  const terrainCount = (map.terrainPatches || []).length;
  return {
    key: entryKey(map),
    title: label,
    sub: `${map.__mod}:${map.path} · ${oreCount} ore patch${oreCount === 1 ? "" : "es"}, ${terrainCount} terrain patch${terrainCount === 1 ? "" : "es"}`,
    search: `${label} ${map.path} ${map.__mod}`,
    thumb: icon("map", 16),
  };
}

function renderMapsList() {
  renderList("maps", state.maps, mapRowInfo, selectMap);
  renderMapsPicker();
}

/** The Maps tab has no {@code .list-panel} (see {@code #panel-maps.workspace.active} in style.css)
 * — the map editor is the whole page, so switching which map you're editing lives in this compact
 * dropdown inside the floating tools panel instead of a permanent 300px file-list column eating
 * into the canvas. Kept in sync with the (still-rendered, just off-screen) {@code #maps-list} by
 * always being called from {@link renderMapsList} rather than tracking state.maps separately. */
function renderMapsPicker() {
  const select = document.getElementById("maps-picker");
  const previousKey = state.selected.maps;
  const query = (document.getElementById("maps-picker-filter").value || "").trim().toLowerCase();
  // Same search string mapRowInfo already computes for the (hidden, but still-live) #maps-list —
  // one definition of "matches the filter," not a second one that could quietly disagree with it.
  let matches = query ? state.maps.filter((m) => mapRowInfo(m).search.toLowerCase().includes(query)) : state.maps;
  // The map actually open stays in the list even if it doesn't match what's currently typed — losing
  // it from the dropdown mid-search would orphan the selection (or silently jump to a different map).
  const current = state.maps.find((m) => entryKey(m) === previousKey);
  if (current && !matches.includes(current)) {
    matches = [current, ...matches];
  }
  select.innerHTML = matches.map((m) => `<option value="${entryKey(m)}"></option>`).join("");
  [...select.options].forEach((opt, idx) => (opt.textContent = `${labelText(matches[idx].label)} (${entryKey(matches[idx])})`));
  if (previousKey && matches.some((m) => entryKey(m) === previousKey)) {
    select.value = previousKey;
  } else {
    select.selectedIndex = -1; // a new/unsaved map matches nothing in the list — don't pretend it's whatever happens to be first
  }
}

/**
 * Which items an ore patch may actually name. An item's own {@code tool} field decides it — {@code
 * "ore"} means this tool lists it, {@code "none"} means no map tool ever offers it — and an item
 * WITHOUT that field (every item authored before the field existed, vanilla included) falls back
 * to the rule that used to apply to all of them: a raw resource is one that no recipe produces.
 * Absence is the fallback rather than a stored {@code "auto"} value on purpose — see collectItem.
 *
 * <p>Nothing in the GAME's item format says "this one is an ore" (ItemJsonLoader reads path,
 * label, researchGrade, colorRgb, shape, and ignores the rest), so {@code tool} is authoring
 * metadata: it steers this editor's tool lists, and the game neither reads it nor cares.
 *
 * <p>Every listed item stays cross-mod, same as the full list was — the pool is narrowed by what
 * items ARE, never by which mod owns them.
 */
function toolItemPool(tool) {
  if (tool === "ore") return oreItemPool();
  // A terrain tool's list is exactly the items that declared themselves for it. No derivation to
  // fall back on here, unlike ore: "nothing crafts it" says an item comes out of the ground, not
  // that it IS ground, so a terrain tool only ever offers what an author explicitly put in it.
  return state.items.filter((i) => i.tool === tool);
}

function oreItemPool() {
  // A recipe's `output` is bare when it means the recipe's OWN mod — the same rule resolveKindRef
  // implements for any reference stored inside a mod file. Deliberately NOT resolveItemRef here:
  // that one resolves a bare ref against the ACTIVE mod, so with sandbox active, vanilla's bare
  // "iron_plate" would resolve to a sandbox item that doesn't exist — and iron_plate, produced by
  // a recipe, would show up in the ore list as if nothing made it.
  const crafted = new Set(state.recipes.map((r) => resolveKindRef(r.output, r.__mod)));
  return state.items.filter((i) => (i.tool ? i.tool === "ore" : !crafted.has(entryKey(i))));
}

/** Fills a map <select> with {@link oreItemPool}, plus {@code keep} (a ref an already-saved patch
 * holds) when the pool doesn't contain it: maps authored before the pool was narrowed can name a
 * crafted item as their ore, and dropping that option would show the patch's real item as an empty
 * box. Returns the refs actually listed, in listed order. */
function fillOreSelect(select, keep, tool) {
  const pool = toolItemPool(tool || "ore");
  const options = pool.map((i) => ({ value: refValueFor(i), text: labelText(i.label) + originSuffix(i) }));
  if (keep && !options.some((o) => o.value === keep)) {
    // itemLabelByPath falls back to the raw ref for an item that no longer exists at all, which is
    // exactly what a stale reference should read as here too.
    // Includes the pre-content spellings ("WATER"/"ROCK"), which resolve to no item at all and so
    // read as their own raw text — accurate: that is exactly what the file still says.
    options.push({ value: keep, text: `${itemLabelByPath(keep)} — not in this tool's list` });
  }
  select.innerHTML = options.map((o) => `<option value="${o.value}"></option>`).join("");
  [...select.options].forEach((opt, idx) => (opt.textContent = options[idx].text));
  return options.map((o) => o.value);
}

/** The item <select> the currently selected tool reads from — ore for the Ore tool and its stamps,
 * that terrain tool's own items for Water/Rock and theirs. Refreshed whenever the item list or the
 * tool changes, same reasoning as renderKindSelectOptions.
 *
 * <p>One picker for every placing tool rather than one per tool: what changes between them is only
 * which pool it lists, and a second <select> would be a second place to keep in sync with it. The
 * value it holds per tool is remembered (see mapToolItemChoice) so switching Ore -> Water -> Ore
 * doesn't quietly reset what you were painting with. */
function renderMapOreItemSelect() {
  const select = document.getElementById("map-ore-item");
  const tool = baseToolOf(document.getElementById("map-tool").value);
  const previous = mapToolItemChoice[tool] || select.value;
  const refs = fillOreSelect(select, null, tool);
  select.value = refs.includes(previous) ? previous : (refs[0] || "");
  mapToolItemChoice[tool] = select.value;
}

/** What each tool was last painting with — see renderMapOreItemSelect. Keyed by base tool name. */
const mapToolItemChoice = {};

/** The selected-patch inspector's OWN ore <select> — same item pool as {@link renderMapOreItemSelect}
 * but refreshed separately from {@link renderPatchInspector} (called on every render, including every
 * mousemove while dragging): rebuilding a <select>'s full option list is real DOM work, so it only
 * happens here, when the item list itself actually changed, not on every frame. Also rebuilt from
 * {@link renderPatchInspector} whenever the selected patch's own ore isn't in the pool — see
 * {@link fillOreSelect}. */
function renderPatchInspectorOreOptions() {
  fillOreSelect(document.getElementById("patch-ore-item"), selectionOre(), selectionType() || "ore");
}

function updateMapToolFieldVisibility() {
  const tool = document.getElementById("map-tool").value;
  // Every placing tool has an item to choose now, terrain included — only Select has nothing to
  // pick. Re-filled here, not just shown: Water's list is not Ore's.
  const needsItem = Boolean(PLACING_TOOLS[baseToolOf(tool)]);
  document.getElementById("map-ore-item-field").classList.toggle("hidden", !needsItem);
  if (needsItem) {
    renderMapOreItemSelect();
  }
  updateMapCursor();
}

/** The canvas's own cursor — three states, checked in priority order: actively panning beats
 * holding-Space-ready-to-pan beats whatever the selected tool would otherwise show. Re-evaluated
 * from every place that can change ANY of those three inputs (tool switch, Space down/up, a pan
 * drag starting/ending) rather than each of those places poking the style directly — one function
 * that knows the whole priority order instead of N call sites each hoping they got it right. Looked
 * up fresh rather than through the module-level mapCanvasEl const: this function's very first call
 * (right below updateMapToolFieldVisibility's own definition) runs before that const's declaration
 * line does. */
function updateMapCursor() {
  const canvas = document.getElementById("map-canvas");
  if (mapDrag && mapDrag.mode === "pan") {
    canvas.style.cursor = "grabbing";
    return;
  }
  if (mapDrag && mapDrag.mode === "scale") {
    canvas.style.cursor = "nwse-resize";
    return;
  }
  if (spaceHeld) {
    canvas.style.cursor = "grab";
    return;
  }
  // The selection frame's corner handles are the only discoverability cue they get — nothing in the
  // panel says "this area can be scaled," so the cursor has to say it on approach. Diagonal
  // direction per corner, the convention every editor's own resize handles use.
  if (mapHoverWorld) {
    const handle = selectionHandleAt(
        (mapHoverWorld.x - mapView.offsetX) * mapView.scale,
        (mapHoverWorld.y - mapView.offsetY) * mapView.scale);
    if (handle) {
      canvas.style.cursor = handle.id === "nw" || handle.id === "se" ? "nwse-resize" : "nesw-resize";
      return;
    }
  }
  // Crosshair reads as "about to draw"; Select isn't drawing anything, so it gets the ordinary
  // pointer instead — a small cue for which mode a freshly-opened map defaults into.
  const tool = document.getElementById("map-tool").value;
  canvas.style.cursor = tool === "select" ? "default" : "crosshair";
}

function itemLabelByPath(ref) {
  const item = resolveItemRef(ref);
  return item ? labelText(item.label) + originSuffix(item) : ref;
}

function oreColorFor(ref) {
  const item = resolveItemRef(ref);
  return item ? item.colorRgb : "#999999";
}

/** A terrain patch's color. Its {@code terrain} is an item reference now, exactly like an ore
 * patch's {@code ore} — with the two pre-content spellings still understood, since map files
 * written before the change (including the ones shipped in this repository) still hold them. */
function terrainColorFor(ref) {
  const item = resolveItemRef(ref);
  if (item) return item.colorRgb;
  return LEGACY_TERRAIN_COLORS[ref] || "#7a7a76";
}

/** Every placing tool now paints whatever its own picker holds, so all of them — ore, terrain and
 * both flavors of stamp — resolve their preview color the same way: from that item. Select, which
 * places nothing, is the only value left without one. */
function previewColorForTool(tool) {
  const base = baseToolOf(tool);
  if (!PLACING_TOOLS[base]) return "#ffffff";
  const ref = document.getElementById("map-ore-item").value;
  return base === "ore" ? oreColorFor(ref) : terrainColorFor(ref);
}

function currentRadius() {
  return Math.max(1, Number(document.getElementById("map-radius").value) || 3);
}

function clampCell(v) {
  return Math.max(0, Math.min(MAP_SIZE - 1, v));
}

function isSelectedPatch(list, patch) {
  return mapSelectedSet.some((s) => s.list === list && s.patch === patch);
}

/** Replaces the WHOLE selection with just this one patch — the plain-click behavior. See {@link
 * toggleSelected} for Shift-click (add/remove one patch without disturbing the rest). */
function selectPatch(list, patch) {
  mapSelectedSet = [{ list, patch }];
  renderMapEditor();
}

/** Shift-click: adds the patch to the selection if it wasn't there, removes it if it was — the
 * usual way to build up a multi-selection one patch at a time, alongside rubber-band (the
 * "Select" tool) for grabbing many at once. */
function toggleSelected(list, patch) {
  const idx = mapSelectedSet.findIndex((s) => s.list === list && s.patch === patch);
  if (idx >= 0) {
    mapSelectedSet.splice(idx, 1);
  } else {
    mapSelectedSet.push({ list, patch });
  }
  renderMapEditor();
}

function deselectPatch() {
  if (mapSelectedSet.length > 0) {
    mapSelectedSet = [];
    renderMapEditor();
  }
}

/**
 * Turns everything selected into {@code type} — one of the {@link PLACING_TOOLS} names — so an
 * area drag-boxed with the Select tool can become water, rock or ore in one go, instead of being
 * deleted and redrawn with the right tool. What each converted patch ends up CONTAINING is that
 * tool's own current pick, the same item a fresh patch drawn with it would get.
 *
 * <p>Patches ALREADY of that type are left exactly where they are: re-inserting an ore patch as
 * "ore" would overwrite its own item with whatever the toolbar happens to hold and shuffle it to
 * the front of the paint order, and water becoming rock only needs its own field rewritten in
 * place. Only patches that actually cross between {@code mapOrePatches} and {@code
 * mapTerrainPatches} are rebuilt — those two hold differently shaped objects ({@code ore} vs
 * {@code terrain}), so a crossing patch is a new object, not a mutated one.
 */
function convertSelectionTo(type) {
  if (mapSelectedSet.length === 0) return;
  const toOre = type === "ore";
  const layer = toOre ? "ore" : "terrain";
  // Same contract placement already honors (see the mousedown handler): a patch that exists in the
  // data while being invisible, unclickable and absent from the list is a worse surprise than a
  // refusal. Converting INTO a hidden layer would do exactly that to the whole selection at once.
  if (!mapLayerVisible[layer]) {
    toast(`The ${layer} layer is hidden — show it before converting into it`, true);
    return;
  }
  // What a crossing patch becomes: the target tool's own pick. Read from that tool's pool rather
  // than from whatever the picker happens to be showing — converting to Water while the Ore tool
  // is selected must not fill the patches with iron ore.
  const pool = toolItemPool(type);
  const fill = (mapToolItemChoice[type] && pool.some((i) => refValueFor(i) === mapToolItemChoice[type]))
      ? mapToolItemChoice[type]
      : (pool[0] ? refValueFor(pool[0]) : "");
  const crossesOver = mapSelectedSet.some((s) => (s.list === mapOrePatches) !== toOre);
  if (crossesOver && !fill) {
    toast(`Nothing for the ${type} tool to place — add an item for it on the Items tab first`, true);
    return;
  }
  const crossing = [];
  for (const selected of mapSelectedSet) {
    const isOre = selected.list === mapOrePatches;
    if (toOre === isOre) {
      if (!toOre) {
        selected.patch.terrain = fill; // water <-> rock: one field, same list, paint order untouched
      }
      continue;
    }
    crossing.push(selected);
  }
  if (crossing.length > 0) {
    const doomed = new Set(crossing.map((s) => s.patch));
    const shape = (p) => ({ cx: p.cx, cy: p.cy, radius: p.radius });
    const rebuilt = crossing.map((s) => (toOre
        ? { ...shape(s.patch), ore: fill }
        : { ...shape(s.patch), terrain: fill }));
    mapOrePatches = mapOrePatches.filter((p) => !doomed.has(p));
    mapTerrainPatches = mapTerrainPatches.filter((p) => !doomed.has(p));
    const target = toOre ? mapOrePatches : mapTerrainPatches;
    // One unshift for the whole batch, front of the list (same "just placed wins an overlap"
    // priority as anything freshly drawn) — one unshift per patch would reverse their order, the
    // same trap duplicateSelectedPatches' own comment documents.
    target.unshift(...rebuilt);
    // Every {list, patch} pair the selection holds went stale, not just the crossing ones: those
    // patches are new objects in the other array, and the filters above REPLACED both arrays, so
    // even an untouched entry is left pointing at a dead copy (see liveListOf). Rebuilt in the same
    // order, so the selection the user sees doesn't reshuffle under them.
    const replacement = new Map();
    crossing.forEach((s, index) => replacement.set(s.patch, rebuilt[index]));
    mapSelectedSet = mapSelectedSet.map((s) => {
      const patch = replacement.get(s.patch) || s.patch;
      return { list: liveListOf(patch), patch };
    });
  }
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Applies one radius to every selected patch — the "изменить размер" half of a selection-wide
 * edit, next to {@link convertSelectionTo}. Dragging an edge still resizes a single patch. */
function resizeSelectedPatches(radius) {
  if (mapSelectedSet.length === 0) return;
  for (const selected of mapSelectedSet) {
    selected.patch.radius = radius;
  }
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Applies one item to every selected ORE patch, leaving terrain in the selection alone (water has
 * no item to set) — convert it to ore first if that's what was meant. */
function setSelectedPatchesOre(ore) {
  if (mapSelectedSet.length === 0) return;
  for (const selected of mapSelectedSet) {
    if (selected.list === mapOrePatches) {
      selected.patch.ore = ore;
    } else {
      selected.patch.terrain = ore; // terrain names an item too — same field, different array
    }
  }
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

function deleteSelectedPatches() {
  if (mapSelectedSet.length === 0) return;
  // ⌘D/Delete are reachable via keydown WHILE a mouse drag is still held (mapDrag.group holds the
  // very objects about to be removed here) — dropping the in-progress drag avoids two problems at
  // once: mousemove would keep dragging now-deleted (or, for duplicate, now-stale) patch objects
  // around invisibly, and mouseup would then push a second, redundant history snapshot on top of
  // the one this function already pushes.
  mapDrag = null;
  const doomed = new Set(mapSelectedSet.map((s) => s.patch));
  mapOrePatches = mapOrePatches.filter((p) => !doomed.has(p));
  mapTerrainPatches = mapTerrainPatches.filter((p) => !doomed.has(p));
  deselectPatch();
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Offsets every copy a few cells over (clamped) so none lands exactly on its original — front of
 * its own list, same "just placed" priority as any freshly drawn patch (see commitPendingPatches).
 * The new copies become the selection, same as a single duplicate did before multi-select existed.
 * Copies for the SAME list are batched into one unshift(...copies) at the end, preserving their
 * relative order — one unshift per COPY (in a loop) would reverse that order, same trap
 * placeStamp's own comment already documents for a stamp's own sub-patches. */
function duplicateSelectedPatches() {
  if (mapSelectedSet.length === 0) return;
  mapDrag = null; // see deleteSelectedPatches' own comment — reachable mid-drag via ⌘D
  const copiesByList = new Map();
  const newSelection = [];
  for (const { list, patch } of mapSelectedSet) {
    const copy = { ...patch, cx: clampCell(patch.cx + 5), cy: clampCell(patch.cy + 5) };
    if (!copiesByList.has(list)) copiesByList.set(list, []);
    copiesByList.get(list).push(copy);
    newSelection.push({ list, patch: copy });
  }
  for (const [list, copies] of copiesByList) {
    list.unshift(...copies);
  }
  mapSelectedSet = newSelection;
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Exact-value editing for whichever patch is selected — dragging on the canvas is the only other
 * way to move/resize a patch, and there was previously no way at all to type a precise coordinate
 * or reassign an ore patch's item short of deleting it and drawing a fresh one. Only makes sense
 * for EXACTLY one patch (typing one X into three differently-positioned patches has no single
 * right answer) — a multi-selection gets a plain "N patches selected" summary instead, with
 * Duplicate/Delete still acting on all of them. Cheap on purpose: called on every {@link
 * renderMapEditor} (including every mousemove while idle), so it only ever sets values/toggles
 * visibility — the ore <select>'s own <option> list is rebuilt separately, by {@link
 * renderPatchInspectorOreOptions}, only when the item list itself changes. */
/**
 * The world-space box covering every patch in {@code patches}, in the same coordinates {@code
 * drawPatch} draws them (a patch's circle is centered on the MIDDLE of its cell, {@code cx + 0.5},
 * with {@code radius} in world units) — so the frame this produces lands exactly on the circles
 * rather than half a cell off them. {@code null} for an empty list.
 *
 * <p>One helper for both users on purpose: the selection frame drawn on the canvas and the floating
 * Duplicate/Delete pill anchored to it are the same box, and computing it twice is how the pill
 * ends up visibly detached from the frame it is supposed to be attached to.
 */
function patchesBounds(patches) {
  if (patches.length === 0) return null;
  let x1 = Infinity;
  let y1 = Infinity;
  let x2 = -Infinity;
  let y2 = -Infinity;
  for (const patch of patches) {
    x1 = Math.min(x1, patch.cx + 0.5 - patch.radius);
    y1 = Math.min(y1, patch.cy + 0.5 - patch.radius);
    x2 = Math.max(x2, patch.cx + 0.5 + patch.radius);
    y2 = Math.max(y2, patch.cy + 0.5 + patch.radius);
  }
  return { x1, y1, x2, y2 };
}

/** {@link patchesBounds} of whatever is selected right now, {@code null} with nothing selected. */
function selectionBounds() {
  return patchesBounds(mapSelectedSet.map((s) => s.patch));
}

/** Half the side of a selection-frame corner handle, in screen pixels — the frame is canvas-drawn,
 * so its handles are too, and everything about them lives in screen space. */
const SELECTION_HANDLE = 5;

/** Where the selection frame sits on screen, in canvas pixels, padding included — the one
 * definition of that rectangle, shared by the drawing, the corner-handle hit test and the cursor,
 * so a handle can't end up somewhere the frame isn't. {@code null} with nothing selected. */
function selectionFrameRect() {
  const bounds = selectionBounds();
  if (!bounds) return null;
  const topLeft = worldToScreen(bounds.x1, bounds.y1);
  const bottomRight = worldToScreen(bounds.x2, bounds.y2);
  const pad = 3; // a hair of air, so the frame reads as around the patches rather than clipping them
  return { x: topLeft.x - pad, y: topLeft.y - pad,
    w: bottomRight.x - topLeft.x + pad * 2, h: bottomRight.y - topLeft.y + pad * 2 };
}

/** The four corners of {@link selectionFrameRect}, each with the corner OPPOSITE it — dragging a
 * handle scales the selection around that opposite corner, which is what makes the far side of the
 * frame stay put while the grabbed one follows the cursor. */
function selectionHandles() {
  const rect = selectionFrameRect();
  if (!rect) return [];
  const left = rect.x;
  const right = rect.x + rect.w;
  const top = rect.y;
  const bottom = rect.y + rect.h;
  return [
    { id: "nw", x: left, y: top, anchorX: right, anchorY: bottom },
    { id: "ne", x: right, y: top, anchorX: left, anchorY: bottom },
    { id: "sw", x: left, y: bottom, anchorX: right, anchorY: top },
    { id: "se", x: right, y: bottom, anchorX: left, anchorY: top },
  ];
}

/** Which corner handle is under a canvas-space point, or null. The grab area is a little larger
 * than the handle is drawn — a 10px square is hard to hit exactly, and being slightly generous
 * costs nothing here since the handles sit outside the patches they belong to. */
function selectionHandleAt(px, py) {
  if (mapSelectedSet.length < 2) return null; // handles are drawn with the frame, and the frame needs two patches
  const grab = SELECTION_HANDLE + 3;
  return selectionHandles().find((h) => Math.abs(px - h.x) <= grab && Math.abs(py - h.y) <= grab) || null;
}

/**
 * Scales the whole selection by {@code factor} around the world point {@code anchor}, reading every
 * patch's size and position from {@code drag.start} rather than from where it currently sits.
 *
 * <p>Recomputed from the originals on every mousemove, never applied incrementally: centers and
 * radii are whole cells, so an incremental version would round on every single event and the area
 * would visibly crawl away from the cursor over one drag (the same reasoning behind the move drag's
 * own {@code startCx + dx}).
 *
 * <p>One factor for both axes because a patch is a circle: this map format has no way to express an
 * ellipse, so a frame that stretched on one axis would be showing something that can't be saved.
 */
function applySelectionScale(drag, factor) {
  for (const start of drag.start) {
    // The circle's real center is the MIDDLE of its cell (see drawPatch), so it's the middle that
    // gets scaled — scaling the raw index and rounding back would drift half a cell per operation.
    const centerX = drag.anchor.x + (start.cx + 0.5 - drag.anchor.x) * factor;
    const centerY = drag.anchor.y + (start.cy + 0.5 - drag.anchor.y) * factor;
    start.patch.cx = clampCell(Math.round(centerX - 0.5));
    start.patch.cy = clampCell(Math.round(centerY - 0.5));
    start.patch.radius = Math.max(1, Math.round(start.radius * factor));
  }
}

/** The largest factor that still leaves the whole scaled frame on the map. Clamps the FACTOR, not
 * each patch: clamping patch by patch lets whichever one reaches the edge first stop while the rest
 * keep growing, quietly deforming the area's own layout — the trap the group-move drag documents. */
function maxSelectionScale(drag) {
  let limit = Infinity;
  const edges = [
    { value: drag.bounds.x1, anchor: drag.anchor.x },
    { value: drag.bounds.x2, anchor: drag.anchor.x },
    { value: drag.bounds.y1, anchor: drag.anchor.y },
    { value: drag.bounds.y2, anchor: drag.anchor.y },
  ];
  for (const edge of edges) {
    const reach = edge.value - edge.anchor;
    if (reach > 0) limit = Math.min(limit, (MAP_SIZE - edge.anchor) / reach);
    if (reach < 0) limit = Math.min(limit, edge.anchor / -reach);
  }
  return limit;
}

/**
 * Whichever of the two live arrays holds {@code patch} right now, or null if neither does any more.
 *
 * <p>Looked up instead of remembered because {@link deleteSelectedPatches} and {@link
 * convertSelectionTo} REPLACE {@code mapOrePatches}/{@code mapTerrainPatches} (filter, not splice):
 * a {@code {list, patch}} pair captured before either ran can hold an array that is no longer the
 * one the editor draws from. That stale array still contains the patch, so every {@code
 * list.includes(patch)} check on it answers "still there" — and anything that then WRITES through
 * that reference (a duplicate unshifted into it) writes into a copy nothing renders.
 */
function liveListOf(patch) {
  if (mapOrePatches.includes(patch)) return mapOrePatches;
  if (mapTerrainPatches.includes(patch)) return mapTerrainPatches;
  return null;
}

/** The value {@code read} returns for every selected patch, or {@code null} when they disagree —
 * what a field shared by the whole selection has to show. "Mixed" is deliberately not a value of
 * its own: the caller renders it as an empty field, so committing one only ever writes something
 * the user actually typed. */
function commonAcrossSelection(read) {
  if (mapSelectedSet.length === 0) return null;
  const first = read(mapSelectedSet[0]);
  return mapSelectedSet.every((s) => read(s) === first) ? first : null;
}

/** The type every selected patch shares — {@code "ore"}, {@code "WATER"}, {@code "ROCK"}, or null
 * for a mixed selection. The same three values the {@code #patch-type} select and {@link
 * convertSelectionTo} speak, so nothing has to translate between "which list is it in" and "what
 * does the dropdown call it" more than once. */
function selectionType() {
  return commonAcrossSelection((s) => (s.list === mapOrePatches ? "ore" : terrainToolOf(s.patch)));
}

/** Which terrain TOOL a terrain patch belongs to — the tool its item declared itself for, with the
 * two pre-content spellings mapped onto the tools that replaced them. Null for a patch whose item
 * declares no tool at all (a hand-edited file, or an item whose tool was changed afterwards): the
 * inspector shows that as a mixed/unset Type rather than guessing one. */
function terrainToolOf(patch) {
  const legacy = { WATER: "water", ROCK: "rock" }[patch.terrain];
  if (legacy) return legacy;
  const item = resolveItemRef(patch.terrain);
  return item && PLACING_TOOLS[item.tool] ? item.tool : null;
}

/** The ore reference every selected ore patch shares, or null (mixed, or nothing ore is selected). */
function selectionOre() {
  if (mapSelectedSet.length === 0) return null;
  // Ore patches and terrain patches both carry an item reference now, just under their own field
  // name — so the Item field means something for either kind, and a selection of one kind reads
  // the same way whichever kind it is.
  return commonAcrossSelection((s) => (s.list === mapOrePatches ? s.patch.ore : s.patch.terrain));
}

function renderPatchInspector() {
  const panel = document.getElementById("map-patch-inspector");
  const position = document.getElementById("patch-inspector-position");
  const multi = document.getElementById("patch-inspector-multi");
  if (mapSelectedSet.length === 0) {
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  const many = mapSelectedSet.length > 1;
  // X/Y are the only single-patch fields left: one exact coordinate typed into several
  // differently-placed patches has no single right answer, whereas one radius, one type and one
  // item all do. Moving a whole selection is the canvas drag and the arrow keys instead.
  position.classList.toggle("hidden", many);
  multi.classList.toggle("hidden", !many);
  if (many) {
    document.getElementById("patch-inspector-count").textContent = `${mapSelectedSet.length} patches selected`;
  }
  // This runs on every renderMapEditor, including plain mouse hover — writing into a field the
  // user is mid-edit in (before its own "change" commits) would silently overwrite what they just
  // typed. Only fields NOT currently focused get their value replaced.
  const writeIfIdle = (id, value) => {
    const el = document.getElementById(id);
    if (el !== document.activeElement) el.value = value;
  };
  if (!many) {
    writeIfIdle("patch-cx", mapSelectedSet[0].patch.cx);
    writeIfIdle("patch-cy", mapSelectedSet[0].patch.cy);
  }
  const radius = commonAcrossSelection((s) => s.patch.radius);
  writeIfIdle("patch-radius", radius === null ? "" : radius); // "" leaves the "mixed" placeholder showing
  const type = selectionType();
  const typeSelect = document.getElementById("patch-type");
  if (typeSelect !== document.activeElement) {
    // -1, not a made-up "mixed" option: an option would be a value the change handler could be
    // asked to apply, and "make these patches mixed" isn't an operation. Same trick renderMapsPicker
    // uses for a map that matches nothing in its own list.
    typeSelect.selectedIndex = -1;
    if (type !== null) typeSelect.value = type;
  }
  const ore = selectionOre();
  document.getElementById("patch-ore-field").classList.toggle("hidden", false); // every patch names an item now, terrain included
  if (ore !== null) {
    const select = document.getElementById("patch-ore-item");
    // Cheap on purpose (this runs on every frame, hence the plain loop rather than a spread into
    // [...select.options]): the full rebuild happens only when the selection's item genuinely
    // isn't among the options — a map authored before the list was narrowed to raw resources can
    // name a crafted item, and without an option of its own the <select> would render as an empty
    // box over a patch that does have an item. At most one rebuild per selection: after it, the
    // value is there (fillOreSelect keeps it), so the next frame finds it and does nothing.
    let listed = false;
    for (let i = 0; i < select.options.length && !listed; i++) {
      listed = select.options[i].value === ore;
    }
    if (!listed) {
      renderPatchInspectorOreOptions();
    }
    writeIfIdle("patch-ore-item", ore);
  } else if (document.getElementById("patch-ore-item") !== document.activeElement) {
    document.getElementById("patch-ore-item").selectedIndex = -1; // ore patches selected, but not all the same item
  }
}

function worldToScreen(wx, wy) {
  return { x: (wx - mapView.offsetX) * mapView.scale, y: (wy - mapView.offsetY) * mapView.scale };
}

function worldFromEvent(e) {
  const canvas = document.getElementById("map-canvas");
  const rect = canvas.getBoundingClientRect();
  const px = (e.clientX - rect.left) / rect.width * canvas.width;
  const py = (e.clientY - rect.top) / rect.height * canvas.height;
  return { x: mapView.offsetX + px / mapView.scale, y: mapView.offsetY + py / mapView.scale };
}

/** Separate visible extents per axis, not one shared "visible" — the canvas isn't square (see
 * {@link mapCanvasWidth}'s own comment), so how much world-space fits horizontally and vertically
 * are two different numbers now. Two different regimes per axis, not one clamp formula: once
 * zoomed in enough that the map fills MORE than the visible extent, panning clamps at the map's
 * own edges as usual (can't show anything beyond them) — but whenever there's room to SPARE (the
 * whole map plus margin fits, which is the normal case at "Fit whole map" on a wide/tall canvas),
 * the map is centered in that extra space instead of pinned to the origin corner. Pinning used to
 * leave the map sitting in a corner with a huge empty gap on one side — centering is what every
 * reference editor (and image viewer, and PDF reader) actually does once the document is smaller
 * than the viewport. */
function clampView() {
  const canvas = document.getElementById("map-canvas");
  const visibleX = canvas.width / mapView.scale;
  const visibleY = canvas.height / mapView.scale;
  mapView.offsetX = visibleX >= MAP_SIZE ? (MAP_SIZE - visibleX) / 2 : Math.max(0, Math.min(MAP_SIZE - visibleX, mapView.offsetX));
  mapView.offsetY = visibleY >= MAP_SIZE ? (MAP_SIZE - visibleY) / 2 : Math.max(0, Math.min(MAP_SIZE - visibleY, mapView.offsetY));
}

/** Pans (without changing zoom) so a world point sits at the canvas center. {@link clampView} still
 * has the last word, so asking for a point near an edge lands as close as the map allows. */
function centerViewOn(wx, wy) {
  mapView.offsetX = wx - mapCanvasWidth / mapView.scale / 2;
  mapView.offsetY = wy - mapCanvasHeight / mapView.scale / 2;
  clampView();
}

/** "Fit whole map" — the button's own meaning, and what clicking the zoom percentage or pressing 0
 * does. Kept as its own function, NOT merged with {@link defaultView}: a control that says it fits
 * the whole map has to keep fitting the whole map, whatever zoom a freshly opened map starts at. */
function resetView() {
  mapView = { scale: mapMinScale(), offsetX: 0, offsetY: 0 };
  clampView(); // centers whichever axis has room to spare — see clampView's own comment
}

/** The camera the editor sets ITSELF — opening a map, and the canvas changing size underneath one.
 * {@link DEFAULT_ZOOM}, centered on the middle of the map: past "fit," offset 0,0 would park the
 * view in the map's top-left corner, which is the emptiest part of most authored maps. */
function defaultView() {
  mapView = { scale: mapMinScale() * DEFAULT_ZOOM, offsetX: 0, offsetY: 0 };
  centerViewOn(MAP_SIZE / 2, MAP_SIZE / 2);
}

/**
 * How much one notch of a standard mouse wheel (a 100-pixel delta) zooms — 1.1x, down from the flat
 * 1.2x this used to apply per EVENT, because the owner asked for a slower, less twitchy zoom.
 * Expressed per pixel of scroll so the factor scales with how hard the gesture actually was: a
 * trackpad pinch arrives as a stream of events carrying single-digit deltas, so charging a full
 * notch for each of them rocketed through the whole zoom range on the smallest pinch, while one
 * mouse notch moved sensibly. The buttons and the +/- keys keep their own fixed 1.2 step — those
 * are one deliberate press, not a gesture with a magnitude to be proportional to.
 */
const ZOOM_PER_PIXEL = Math.log(1.1) / 100;

/** The zoom factor for one wheel/pinch event. {@code exp} rather than a linear multiplier keeps the
 * gesture reversible: scrolling out by the same distance you scrolled in lands on exactly the scale
 * you started from, which a linear factor doesn't. Deltas are normalized to pixels first — {@code
 * deltaMode} is LINES for a mouse wheel in Firefox (deltaY of ~3, not ~100), and reading it as
 * pixels would make the wheel there feel about thirty times weaker than it does here. */
function wheelZoomFactor(e) {
  const perLine = 16; // a typical line box; the browsers that report lines don't say how tall theirs is
  const pixels = e.deltaMode === 1 ? e.deltaY * perLine
      : e.deltaMode === 2 ? e.deltaY * mapCanvasHeight
      : e.deltaY;
  return Math.exp(-pixels * ZOOM_PER_PIXEL);
}

function zoomAt(px, py, factor) {
  const worldX = mapView.offsetX + px / mapView.scale;
  const worldY = mapView.offsetY + py / mapView.scale;
  mapView.scale = Math.max(mapMinScale(), Math.min(mapMaxScale(), mapView.scale * factor));
  mapView.offsetX = worldX - px / mapView.scale;
  mapView.offsetY = worldY - py / mapView.scale;
  clampView();
}

/** Pans (without changing zoom) so the CENTROID of the current selection sits at the canvas
 * center — for finding a patch again after panning/zooming away from it, not for the initial
 * placement (that's what "Fit whole map" and the ghost preview are for). A no-op with nothing
 * selected — bound to a key, not a button, so there's nothing to disable/hide in that case. */
function focusOnSelection() {
  if (mapSelectedSet.length === 0) return;
  const cx = mapSelectedSet.reduce((sum, s) => sum + s.patch.cx, 0) / mapSelectedSet.length;
  const cy = mapSelectedSet.reduce((sum, s) => sum + s.patch.cy, 0) / mapSelectedSet.length;
  centerViewOn(cx, cy);
  renderMapEditor();
}

/** Fills the available space of {@code .map-canvas-wrap} — {@code position: absolute; inset: 0}
 * over the ENTIRE stage (see style.css), so this measures the full stage, not "the stage minus a
 * sidebar" — {@code .map-tools} floats on top of the canvas rather than sharing a row with it —
 * instead of a fixed box (see {@link mapCanvasWidth}'s own comment). Called whenever the maps tab
 * becomes visible and on window resize while it's active. A no-op if the measured size didn't
 * actually change, so switching back to an unchanged window doesn't reset the camera on every tab
 * click. Goes back to {@link defaultView} rather than trying to preserve the exact pan/zoom across
 * a resolution change — simpler, and a resize is rare enough that losing the current pan isn't a
 * real cost. */
function resizeMapCanvas() {
  const wrap = mapCanvasEl.parentElement; // .map-canvas-wrap
  const availableW = wrap.clientWidth;
  const availableH = wrap.clientHeight;
  // Either being 0 means .map-canvas-wrap itself is hidden right now (JSON-view toggle, or this
  // tab isn't the active one at the moment a debounced resize fires) — every caller of this
  // function already means to only measure while it's visible, so 0 means "don't have a real
  // number yet," not "shrink to the floor." Leaving the canvas size alone until a real one arrives.
  if (availableW <= 0 || availableH <= 0) return;
  // The canvas is set to EXACTLY the wrap's own box — not forced square and centered inside it.
  // A square canvas used to leave a margin on the wrap's longer axis, painted the same background
  // color as the canvas (see mapCanvasWidth's own comment) — same color, but dead: mouse listeners
  // are bound to the canvas element itself, not the wrap, so that margin looked like map and
  // wasn't. No -2 for the wrap's border: clientWidth/clientHeight already exclude it (padding-box,
  // not border-box), so subtracting again would just leave an unexplained gap on two edges instead
  // of filling the wrap exactly.
  if (availableW === mapCanvasWidth && availableH === mapCanvasHeight) return;
  mapCanvasWidth = availableW;
  mapCanvasHeight = availableH;
  mapCanvasEl.width = availableW;
  mapCanvasEl.height = availableH;
  defaultView();
  renderMapEditor();
}

/** Front-to-back hit test: ore patches first (they always visually/logically win over terrain), each array checked index 0 upward (paint-priority order == visual front). {@code onEdge} means "near the boundary" — the caller's cue to resize instead of move. */
function hitTestPatch(worldX, worldY) {
  const edgeTol = 5 / mapView.scale;
  for (const list of [mapOrePatches, mapTerrainPatches]) {
    if ((list === mapOrePatches && !mapLayerVisible.ore) || (list === mapTerrainPatches && !mapLayerVisible.terrain)) {
      continue;
    }
    for (let i = 0; i < list.length; i++) {
      const p = list[i];
      const d = Math.hypot(worldX - p.cx, worldY - p.cy);
      if (d <= p.radius + edgeTol) {
        return { list, index: i, patch: p, onEdge: d >= p.radius - edgeTol };
      }
    }
  }
  return null;
}

/** Whether a world coordinate is actually inside the 256x256 map — as opposed to the "pasteboard"
 * area a non-square canvas can show beyond it (see drawMapCanvas's own comment). Placement tools
 * treat outside-the-map the same as outside-the-canvas: a no-op, not a click that lands somewhere
 * you didn't aim for via clampCell. */
function isInsideMap(x, y) {
  return x >= 0 && x < MAP_SIZE && y >= 0 && y < MAP_SIZE;
}

/** {@link drawMapCanvas}'s pasteboard fill, read off the {@code --bg} custom property so it's
 * correct in both this app's themes — cached rather than looked up fresh every call, because
 * drawMapCanvas itself runs on every {@link renderMapEditor}, including every single mousemove
 * while idly hovering the canvas or mid-drag; getComputedStyle forces a style recalc, and paying
 * that ~60 times a second for a value that only ever changes on an OS-level color-scheme flip was
 * measurable jank while dragging a patch around. Invalidated by the one event that can actually
 * change it, below. */
let cachedPasteboardColor = null;
function pasteboardColor() {
  if (cachedPasteboardColor === null) {
    cachedPasteboardColor = getComputedStyle(document.documentElement).getPropertyValue("--bg").trim() || "#14161b";
  }
  return cachedPasteboardColor;
}
window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
  cachedPasteboardColor = null;
  if (activeTab() === "maps") renderMapEditor();
});

function drawMapCanvas() {
  const canvas = document.getElementById("map-canvas");
  const ctx = canvas.getContext("2d");

  // "Pasteboard," not more ground: the canvas isn't square (see mapCanvasWidth's own comment) but
  // the map always is, so at most zoom levels — especially "Fit whole map," which only fits the
  // SHORTER axis — the longer axis shows more canvas than there is actual map. The first version
  // of this fix darkened that leftover strip on top of the SAME ground fill, which read as a
  // broken render (a flat color seam) rather than an intentional boundary. Every reference editor
  // (Photoshop, Illustrator, Figma, Tiled) instead treats the document as a distinct sheet sitting
  // on a neutral surface — same idea here: this pasteboard is the app's own background color, and
  // the map gets its own shadowed rectangle on top of it, not a same-toned block cut in half.
  ctx.fillStyle = pasteboardColor();
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const mapTopLeft = worldToScreen(0, 0);
  const mapBottomRight = worldToScreen(MAP_SIZE, MAP_SIZE);
  const mapX = mapTopLeft.x;
  const mapY = mapTopLeft.y;
  const mapW = mapBottomRight.x - mapTopLeft.x;
  const mapH = mapBottomRight.y - mapTopLeft.y;

  // The sheet itself, lifted off the pasteboard with a soft shadow instead of a hard-edged color
  // seam — "no terrain assigned" gray, same as before, just now confined to where the map actually is.
  ctx.save();
  ctx.shadowColor = "rgba(0,0,0,0.45)";
  ctx.shadowBlur = 16;
  ctx.fillStyle = "#2b2e35";
  ctx.fillRect(mapX, mapY, mapW, mapH);
  ctx.restore();

  // Everything below (grid, patches, hover ghost) is clipped to the sheet's own rectangle — none
  // of it can ever legitimately exist outside the map, so nothing gets to bleed past its edge
  // either (a patch dragged right up against the boundary used to visibly poke past it).
  ctx.save();
  ctx.beginPath();
  ctx.rect(mapX, mapY, mapW, mapH);
  ctx.clip();

  // Cell grid, only once cells are actually spaced out on screen: at mapMinScale() the whole
  // 256-cell map is visible at once, one line per cell would be solid noise (256 of them) for no
  // benefit — the grid exists to help PRECISE placement, which only matters once you're zoomed in.
  if (mapView.scale >= 8) {
    // Separate X/Y extents, not one shared "visible" — the canvas isn't square (see
    // mapCanvasWidth's own comment), so reusing a width-derived value for the Y axis used to
    // under/over-run the actual visible rows whenever width and height actually differed.
    const visibleX = canvas.width / mapView.scale;
    const visibleY = canvas.height / mapView.scale;
    const fromX = Math.floor(mapView.offsetX);
    const toX = Math.ceil(mapView.offsetX + visibleX);
    const fromY = Math.floor(mapView.offsetY);
    const toY = Math.ceil(mapView.offsetY + visibleY);
    ctx.strokeStyle = "rgba(255,255,255,0.08)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let gx = fromX; gx <= toX; gx++) {
      const sx = (gx - mapView.offsetX) * mapView.scale;
      ctx.moveTo(sx, 0);
      ctx.lineTo(sx, canvas.height);
    }
    for (let gy = fromY; gy <= toY; gy++) {
      const sy = (gy - mapView.offsetY) * mapView.scale;
      ctx.moveTo(0, sy);
      ctx.lineTo(canvas.width, sy);
    }
    ctx.stroke();
  }

  const drawPatch = (p, color, selected) => {
    const center = worldToScreen(p.cx + 0.5, p.cy + 0.5);
    const r = p.radius * mapView.scale;
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(center.x, center.y, r, 0, Math.PI * 2);
    ctx.fill();
    if (selected) {
      ctx.strokeStyle = "#ffffff";
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 4]);
      ctx.stroke();
      ctx.setLineDash([]);
    }
  };

  // Reversed: the FIRST patch in each array wins an overlap (see AuthoredOreLayout's rasterizer),
  // so it has to be painted LAST here to land visually on top — matching what the game renders.
  // Hiding a layer (mapLayerVisible) skips its loop here entirely, same as it does in
  // hitTestPatch/renderMapPatchList/finishRubberBand — "hidden" means genuinely out of the way,
  // not just invisible while still catching clicks underneath whatever IS drawn.
  if (mapLayerVisible.terrain) {
    for (let i = mapTerrainPatches.length - 1; i >= 0; i--) {
      const p = mapTerrainPatches[i];
      drawPatch(p, terrainColorFor(p.terrain), isSelectedPatch(mapTerrainPatches, p));
    }
  }
  if (mapLayerVisible.ore) {
    for (let i = mapOrePatches.length - 1; i >= 0; i--) {
      const p = mapOrePatches[i];
      drawPatch(p, oreColorFor(p.ore), isSelectedPatch(mapOrePatches, p));
    }
  }

  if (mapPendingPatches.length > 0) {
    const ghost = previewColorForTool(document.getElementById("map-tool").value) + "aa";
    for (const pending of mapPendingPatches) {
      drawPatch(pending, ghost, false);
    }
  } else if (mapHoverWorld && !mapDrag && isInsideMap(mapHoverWorld.x, mapHoverWorld.y) && !hitTestPatch(mapHoverWorld.x, mapHoverWorld.y)) {
    // isInsideMap here, not just at click time — showing a "you could place here" ghost over the
    // pasteboard would be a lie now that clicking there is a no-op (see the mousedown handler).
    const tool = document.getElementById("map-tool").value;
    const centerX = Math.round(mapHoverWorld.x);
    const centerY = Math.round(mapHoverWorld.y);
    if (STAMPS[tool]) {
      // Every sub-patch a click would actually place, so a multi-offset stamp (a cluster, a lake)
      // shows its WHOLE shape before committing — previously only single-cell tools got this
      // preview at all; a stamp painted blind, one click at a time, until it looked right.
      const stamp = STAMPS[tool];
      const radius = Math.max(1, currentRadius() + stamp.radiusDelta);
      const color = previewColorForTool(tool) + "77";
      for (const [dx, dy] of stamp.offsets) {
        drawPatch({ cx: clampCell(centerX + dx), cy: clampCell(centerY + dy), radius }, color, false);
      }
    } else if (tool !== "select") {
      const ghost = { cx: centerX, cy: centerY, radius: currentRadius() };
      drawPatch(ghost, previewColorForTool(tool) + "77", false);
    }
  }

  ctx.restore(); // end of the clip-to-the-sheet region opened above

  // Crisp 1px edge around the sheet, drawn AFTER unclipping so the stroke itself isn't cut in
  // half by its own clip region.
  ctx.strokeStyle = "rgba(255,255,255,0.15)";
  ctx.lineWidth = 1;
  ctx.strokeRect(Math.round(mapX) + 0.5, Math.round(mapY) + 0.5, Math.round(mapW) - 1, Math.round(mapH) - 1);

  // Rubber-band box is a selection-UI overlay, not map content — drawn unclipped, since starting
  // or ending the drag out on the pasteboard (to fully enclose patches near the map's edge) is
  // normal and the box itself should stay visible while doing that.
  if (mapDrag && mapDrag.mode === "rubberband") {
    const p1 = worldToScreen(mapDrag.startWorld.x, mapDrag.startWorld.y);
    const p2 = worldToScreen(mapDrag.currentWorld.x, mapDrag.currentWorld.y);
    const x = Math.min(p1.x, p2.x);
    const y = Math.min(p1.y, p2.y);
    const w = Math.abs(p2.x - p1.x);
    const h = Math.abs(p2.y - p1.y);
    ctx.fillStyle = "rgba(239,155,61,0.15)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = "rgba(239,155,61,0.8)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x, y, w, h);
  }

  // The selection's own frame, which OUTLIVES the drag that made it — the rubber band above exists
  // only while the button is down, and without this the moment you let go there was nothing left
  // saying "this area is what the panel is about to act on". Deliberately the selection's bounding
  // box, not the rectangle that was dragged: patches move, get deleted, get added by Shift-click,
  // and a frozen copy of the original drag rectangle would start lying about the selection the
  // first time any of that happens. No fill, unlike the live band — a tint that never goes away
  // would dim the map underneath for as long as anything stays selected. Two patches minimum: a
  // box drawn snugly around ONE circle says nothing its own dashed outline doesn't already say.
  if (mapSelectedSet.length > 1) {
    const rect = selectionFrameRect();
    ctx.strokeStyle = "rgba(239,155,61,0.9)";
    ctx.lineWidth = 1;
    ctx.setLineDash([6, 4]);
    ctx.strokeRect(rect.x, rect.y, rect.w, rect.h);
    ctx.setLineDash([]);
    // Corner handles: solid squares, so they read as grabbable against the dashed frame they sit
    // on. Filled AND stroked because the map underneath them is any color at all — a plain white
    // square disappears over pale terrain, a plain dark one over the pasteboard.
    for (const handle of selectionHandles()) {
      ctx.fillStyle = "#ffffff";
      ctx.strokeStyle = "rgba(239,155,61,1)";
      ctx.fillRect(handle.x - SELECTION_HANDLE, handle.y - SELECTION_HANDLE, SELECTION_HANDLE * 2, SELECTION_HANDLE * 2);
      ctx.strokeRect(handle.x - SELECTION_HANDLE, handle.y - SELECTION_HANDLE, SELECTION_HANDLE * 2, SELECTION_HANDLE * 2);
    }
  }
}

function updateMapCoordsReadout(world) {
  document.getElementById("map-coords").textContent = world ? `(${Math.round(world.x)}, ${Math.round(world.y)})` : "";
}

let mapDragRow = null;

/** Live counts, not the saved file's own (map-list used to show these before it got replaced by
 * #maps-picker for the full-page layout — see #panel-maps.workspace.active in style.css) — reads
 * straight off mapOrePatches/mapTerrainPatches so it reflects unsaved edits too, not just what's
 * on disk. */
function renderMapPatchCount() {
  const oreCount = mapOrePatches.length;
  const terrainCount = mapTerrainPatches.length;
  document.getElementById("map-patch-count").textContent =
      `${oreCount} ore patch${oreCount === 1 ? "" : "es"}, ${terrainCount} terrain patch${terrainCount === 1 ? "" : "es"}`;
}

function renderMapPatchList() {
  renderMapPatchCount();
  const ul = document.getElementById("map-patch-list");
  ul.innerHTML = "";
  const rows = [
    ...(mapLayerVisible.ore ? mapOrePatches.map((p, index) => ({
      index, patch: p, list: mapOrePatches, color: oreColorFor(p.ore),
      desc: `${itemLabelByPath(p.ore)} ore @ (${p.cx}, ${p.cy}) r=${p.radius}`,
    })) : []),
    ...(mapLayerVisible.terrain ? mapTerrainPatches.map((p, index) => ({
      index, patch: p, list: mapTerrainPatches, color: terrainColorFor(p.terrain),
      desc: `${itemLabelByPath(p.terrain)} terrain @ (${p.cx}, ${p.cy}) r=${p.radius}`,
    })) : []),
  ];
  for (const row of rows) {
    const li = document.createElement("li");
    li.className = "patch-row" + (isSelectedPatch(row.list, row.patch) ? " selected" : "");
    li.draggable = true;
    li.innerHTML = `<span class="drag-handle">⋮⋮</span><span class="patch-swatch" style="background:${row.color}"></span><span class="patch-desc"></span>`;
    li.querySelector(".patch-desc").textContent = row.desc;
    li.addEventListener("click", (e) => {
      if (e.shiftKey) {
        toggleSelected(row.list, row.patch);
      } else {
        selectPatch(row.list, row.patch);
      }
    });
    // Enter/Space picks this row (the plain-click behavior) — a keyboard equivalent of Shift-click
    // isn't offered, same as nowhere else in this file synthesizes a modifier key for one either;
    // the rubber-band ("Select" tool) is still there on the canvas for a keyboard user who needs a
    // multi-selection built up.
    makeKeyboardClickable(li);
    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.innerHTML = icon("close", 12);
    removeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      row.list.splice(row.index, 1);
      // Only drops THIS patch from the selection — deleting some other row must not silently
      // clear a selection (or the rest of a multi-selection) that has nothing to do with it.
      mapSelectedSet = mapSelectedSet.filter((s) => s.patch !== row.patch);
      markDirty("maps");
      pushMapHistory();
      renderMapEditor();
    });
    li.appendChild(removeBtn);
    // Reordering is priority reordering (see the class-level comment on paint order) — restricted
    // to rows of the SAME array; ore and terrain patches have separate priority sequences, mixing
    // them across arrays via drag wouldn't mean anything.
    li.addEventListener("dragstart", () => {
      mapDragRow = { list: row.list, index: row.index };
      li.classList.add("dragging");
    });
    li.addEventListener("dragend", () => {
      li.classList.remove("dragging");
      mapDragRow = null;
    });
    li.addEventListener("dragover", (e) => {
      if (mapDragRow && mapDragRow.list === row.list) e.preventDefault();
    });
    li.addEventListener("drop", (e) => {
      if (!mapDragRow || mapDragRow.list !== row.list || mapDragRow.index === row.index) return;
      e.preventDefault();
      const [moved] = row.list.splice(mapDragRow.index, 1);
      row.list.splice(row.index, 0, moved);
      markDirty("maps");
      pushMapHistory();
      renderMapEditor();
    });
    ul.appendChild(li);
  }
}

/** "100%" means {@link mapMinScale}, i.e. "Fit whole map" — there's no natural "1 screen pixel per
 * world unit" baseline here the way an image editor has (a map cell isn't a pixel), so the one
 * zoom level the user already has a button for is the more useful 100% to anchor against. */
function renderMapZoomHud() {
  document.getElementById("map-zoom-level").textContent = `${Math.round((mapView.scale / mapMinScale()) * 100)}%`;
}

/** What {@link renderHoverToolbar} is CURRENTLY showing its pill for — {@code {kind: "selection"}}
 * for the whole selection, {@code {kind: "hover", list, patch}} for one patch under the cursor with
 * nothing selected. Set by that function on every render and read by the pill's own two button
 * handlers (wired once, below, not per-render), so they never re-derive "act on what?" themselves —
 * and, more to the point, can't disagree with what the user is looking at. */
let currentHoverToolbarTarget = null;

/**
 * The quick Duplicate/Delete pill. Anchored to the SELECTION's own frame (see {@link
 * patchesBounds}) whenever anything is selected, and only otherwise to the patch under the cursor.
 *
 * <p>That anchor is the whole point: previously the pill appeared for a single selected patch or
 * for whatever the mouse was over, so after painting a stroke — which selects every patch in it —
 * it either hid entirely or hopped from circle to circle as the cursor crossed them. Pinned to the
 * selection's box, it doesn't move unless the selection itself does, and its buttons act on all of
 * it (the frame drawn on the canvas is the same box, so what it acts on is exactly what's outlined).
 *
 * <p>A stale {@link mapHoverPatch} left over from a since-deleted patch (deleted via the keyboard,
 * the side panel, OR this very pill) is caught here with one {@link liveListOf} lookup rather than
 * hunting it down in every one of those separate deletion code paths. That lookup, not the {@code
 * mapHoverPatch.list.includes(...)} this used to do: the list captured at hover time can itself be
 * a dead copy, and a dead copy still contains the deleted patch, so the check passed and the pill
 * kept offering Duplicate/Delete for something that no longer existed.
 */
function renderHoverToolbar() {
  const hoverList = mapHoverPatch ? liveListOf(mapHoverPatch.patch) : null;
  if (mapHoverPatch && !hoverList) {
    mapHoverPatch = null;
  }
  let target = null;
  let bounds = null;
  if (mapSelectedSet.length > 0) {
    target = { kind: "selection" };
    bounds = selectionBounds();
  } else if (mapHoverPatch) {
    target = { kind: "hover", list: hoverList, patch: mapHoverPatch.patch };
    bounds = patchesBounds([mapHoverPatch.patch]);
  }
  if (!target || mapDrag || mapPendingPatches.length > 0) {
    hoverToolbarEl.classList.add("hidden");
    currentHoverToolbarTarget = null;
    return;
  }
  currentHoverToolbarTarget = target;
  const above = worldToScreen((bounds.x1 + bounds.x2) / 2, bounds.y1);
  const below = worldToScreen((bounds.x1 + bounds.x2) / 2, bounds.y2);
  // Flips below the target instead of above whenever "above" would run the pill off the canvas's own
  // top edge (a selection sitting near world y=0) — same reasoning a tooltip/popover flips an
  // edge-anchored target, just on the one axis that actually needs it (the pill is narrow enough
  // never to run off the left/right edges at any zoom level this tool allows).
  const flipped = above.y < 44;
  const point = flipped ? below : above;
  hoverToolbarEl.style.left = `${point.x}px`;
  hoverToolbarEl.style.top = `${point.y}px`;
  hoverToolbarEl.classList.toggle("flip-below", flipped);
  hoverToolbarEl.classList.remove("hidden");
}

function renderMapEditor() {
  drawMapCanvas();
  renderMapPatchList();
  renderPatchInspector();
  renderMapZoomHud();
  renderHoverToolbar();
}

/* ---- undo/redo — a plain snapshot stack, small arrays, cheap to copy wholesale ---- */

function snapshotMapState() {
  return { ore: mapOrePatches.map((p) => ({ ...p })), terrain: mapTerrainPatches.map((p) => ({ ...p })) };
}

function resetMapHistory() {
  mapHistory = [snapshotMapState()];
  mapHistoryIndex = 0;
}

function pushMapHistory() {
  mapHistory = mapHistory.slice(0, mapHistoryIndex + 1);
  mapHistory.push(snapshotMapState());
  if (mapHistory.length > MAP_HISTORY_LIMIT) mapHistory.shift();
  mapHistoryIndex = mapHistory.length - 1;
}

function undoMap() {
  if (mapHistoryIndex <= 0) return;
  mapHistoryIndex--;
  restoreMapSnapshot(mapHistory[mapHistoryIndex]);
}

function redoMap() {
  if (mapHistoryIndex >= mapHistory.length - 1) return;
  mapHistoryIndex++;
  restoreMapSnapshot(mapHistory[mapHistoryIndex]);
}

function restoreMapSnapshot(snapshot) {
  mapOrePatches = snapshot.ore.map((p) => ({ ...p }));
  mapTerrainPatches = snapshot.terrain.map((p) => ({ ...p }));
  mapSelectedSet = [];
  markDirty("maps");
  renderMapEditor();
}

function placeStamp(toolKey, centerX, centerY) {
  const stamp = STAMPS[toolKey];
  const radius = Math.max(1, currentRadius() + stamp.radiusDelta);
  // Both flavors of stamp read the same picker now — a lake is made of whatever the Water tool is
  // set to, exactly as a vein is made of whatever the Ore tool is set to.
  const ore = document.getElementById("map-ore-item").value;
  if (!ore) {
    toast(`Nothing for the ${baseToolOf(toolKey)} tool to place — add an item for it on the Items tab first`, true);
    return;
  }
  if (stamp.kind === "ore") {
    // Front of the list, not the back: paint priority is index order (see the class-level comment
    // on the canvas draw loop), so a patch appended at the end used to lose any overlap against
    // everything already there — the opposite of what drawing on top of something means everywhere
    // else. A stamp's own sub-patches keep their relative order, just all moved to the front.
    const placed = stamp.offsets.map(([dx, dy]) => ({ cx: clampCell(centerX + dx), cy: clampCell(centerY + dy), radius, ore }));
    mapOrePatches.unshift(...placed);
  } else {
    const placed = stamp.offsets.map(([dx, dy]) => ({ cx: clampCell(centerX + dx), cy: clampCell(centerY + dy), radius, terrain: ore }));
    mapTerrainPatches.unshift(...placed);
  }
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/**
 * How far the cursor travels between two stamps of a stroke, in cells. Equal to the patch's own
 * radius on purpose: circles of radius r whose centers sit r apart merge into one continuous band
 * 2r wide, which is what dragging a brush is supposed to leave behind. A full 2r (circles merely
 * touching) leaves pinched gaps at every seam once the game rasterizes them into cells, and
 * anything much below r just piles up near-identical patches that all have to be saved.
 */
function brushSpacing(radius) {
  return Math.max(1, radius);
}

/** One stamp of the stroke's brush, at a world point. Off-map points stamp nothing at all (same
 * rule as the mousedown handler: placing out on the pasteboard means nothing) rather than being
 * clamped onto the nearest edge cell, which would pile a drag's whole overshoot into one spot.
 * {@code drag.stamped} remembers the cells this stroke already covered: a spacing of 1 can round
 * two steps onto the same cell, and dragging back over your own trail crosses cells it painted on
 * the way out — either way the result would be two patches saying exactly the same thing, both
 * saved into the map file. */
function stampPlaceStroke(drag, x, y) {
  if (!isInsideMap(x, y)) return;
  const cx = clampCell(Math.round(x));
  const cy = clampCell(Math.round(y));
  const cell = `${cx},${cy}`;
  if (drag.stamped.has(cell)) return;
  drag.stamped.add(cell);
  mapPendingPatches.push({ cx, cy, radius: drag.radius });
}

/**
 * Continues the current "place" stroke up to {@code world}, stamping a copy of the stroke's patch
 * every {@link brushSpacing} cells along the way. Steps along the straight segment from the last
 * stamp to the cursor rather than stamping the cursor position itself: mousemove fires at whatever
 * rate the browser feels like, so a fast drag delivers samples tens of cells apart and stamping
 * only those would leave a dotted line with holes in it.
 *
 * <p>{@code drag.brush} walks in unrounded world coordinates, and only the stamp it produces is
 * rounded to a cell — advancing a ROUNDED cursor would let rounding eat part of each step, and a
 * step that doesn't shorten the remaining distance by the full spacing is a loop that might not
 * end. The cursor keeps advancing across off-map stretches even though those stamp nothing, so
 * dragging out over the pasteboard and back resumes painting on the far side.
 */
function extendPlaceStroke(drag, world) {
  const spacing = brushSpacing(drag.radius);
  let dx = world.x - drag.brush.x;
  let dy = world.y - drag.brush.y;
  let remaining = Math.hypot(dx, dy);
  while (remaining >= spacing) {
    drag.brush.x += dx / remaining * spacing;
    drag.brush.y += dy / remaining * spacing;
    stampPlaceStroke(drag, drag.brush.x, drag.brush.y);
    dx = world.x - drag.brush.x;
    dy = world.y - drag.brush.y;
    remaining = Math.hypot(dx, dy);
  }
}

/** Returns whether the stroke actually got committed — false for the "no item registered yet" bail
 * (toast only), so the mouseup handler knows not to mark the map dirty or push a no-op history
 * entry for a placement that never happened. */
function commitPendingPatches() {
  const tool = document.getElementById("map-tool").value; // guaranteed a plain placing tool — stamps commit on mousedown, never reach a "place" drag
  // Copies, not the ghost objects themselves: the ghosts are cleared right after this, and handing
  // the map's real array objects that something else still holds a reference to is how a "cleared"
  // preview ends up mutating committed patches.
  const shape = (p) => ({ cx: p.cx, cy: p.cy, radius: p.radius });
  const ore = document.getElementById("map-ore-item").value;
  if (!ore) {
    toast(`Nothing for the ${tool} tool to place — add an item for it on the Items tab first`, true);
    return false;
  }
  if (tool === "ore") {
    // unshift (front = top priority), not push — see placeStamp's own comment on why; the stroke
    // keeps its own painting order within that. Auto-select the result so the new patches' exact
    // positions/radius are immediately visible and nudgeable in the inspector, instead of needing
    // a second click to find what you just drew — a whole stroke selects as a group, so it can be
    // nudged or deleted in one go.
    const placed = mapPendingPatches.map((p) => ({ ...shape(p), ore }));
    mapOrePatches.unshift(...placed);
    mapSelectedSet = placed.map((patch) => ({ list: mapOrePatches, patch }));
  } else {
    const placed = mapPendingPatches.map((p) => ({ ...shape(p), terrain: ore }));
    mapTerrainPatches.unshift(...placed);
    mapSelectedSet = placed.map((patch) => ({ list: mapTerrainPatches, patch }));
  }
  return true;
}

/** Space held down right now — the tool-agnostic "temporary pan" gesture every pro editor (Figma,
 * Miro, Photoshop) offers as an alternative to Shift-drag/middle-drag: unlike those two, holding
 * Space overrides whatever the CURRENT tool would otherwise do with a drag, so it works mid-way
 * through placing a patch just as well as it does over empty space. Declared before the first
 * {@link updateMapCursor} call (from updateMapToolFieldVisibility() right below) can read it. */
let spaceHeld = false;

document.getElementById("map-tool").addEventListener("change", updateMapToolFieldVisibility);
updateMapToolFieldVisibility();

const mapCanvasEl = document.getElementById("map-canvas");
const hoverToolbarEl = document.getElementById("map-hover-toolbar");

mapCanvasEl.addEventListener("mousedown", (e) => {
  // mousedown fires BEFORE the browser's default focus-change — if an inspector field is mid-edit
  // (typed, not yet committed via change/blur), selecting a different patch below would leave that
  // typed value sitting in a field that no longer describes what it's about to be applied to; the
  // subsequent blur's "change" event would then silently apply it to the NEWLY selected patch
  // instead. Blurring first forces that commit to land on the patch it was actually typed for.
  const focused = document.activeElement;
  if (focused && focused.closest && focused.closest("#map-patch-inspector")) focused.blur();
  // Middle-mouse-drag and Space-drag pan regardless of what's under the cursor — a patch mid-hover,
  // the "select" tool, a half-placed patch, doesn't matter, same as every pro editor's own
  // tool-agnostic pan gesture. Checked before hit-testing even runs: unlike Shift+drag below (which
  // only pans when it DIDN'T land on a patch, so Shift can still toggle one into the selection),
  // these two always mean "pan," full stop.
  if (e.button === 1 || spaceHeld) {
    e.preventDefault(); // middle-click's own default is usually autoscroll/paste — neither belongs here
    mapDrag = { mode: "pan", startClientX: e.clientX, startClientY: e.clientY, startOffsetX: mapView.offsetX, startOffsetY: mapView.offsetY };
    updateMapCursor();
    return;
  }
  const world = worldFromEvent(e);
  // Corner handles are checked BEFORE any patch hit test: they're drawn on top of the frame, which
  // is drawn on top of the patches, and a handle sitting over a circle has to win the click it
  // visually invites. Screen space, like the handles themselves — see selectionFrameRect.
  const rect = mapCanvasEl.getBoundingClientRect();
  const handle = selectionHandleAt(
      (e.clientX - rect.left) / rect.width * mapCanvasEl.width,
      (e.clientY - rect.top) / rect.height * mapCanvasEl.height);
  if (handle) {
    const bounds = selectionBounds();
    const anchor = { x: mapView.offsetX + handle.anchorX / mapView.scale, y: mapView.offsetY + handle.anchorY / mapView.scale };
    mapDrag = {
      mode: "scale",
      anchor,
      bounds,
      // Distance from the anchor to the corner actually grabbed — the denominator of the factor, so
      // the very first mousemove reports 1x and the area doesn't jump the instant it's touched.
      grabDistance: Math.max(0.001, Math.hypot(world.x - anchor.x, world.y - anchor.y)),
      start: mapSelectedSet.map((s) => ({ patch: s.patch, cx: s.patch.cx, cy: s.patch.cy, radius: s.patch.radius })),
    };
    updateMapCursor();
    return;
  }
  const hit = hitTestPatch(world.x, world.y);
  // Shift+empty-space still pans; Shift+an-actual-patch toggles it in/out of the selection instead
  // (checked below, once hit is known) — the two never conflict, since they're on different targets.
  if (e.shiftKey && !hit) {
    mapDrag = { mode: "pan", startClientX: e.clientX, startClientY: e.clientY, startOffsetX: mapView.offsetX, startOffsetY: mapView.offsetY };
    updateMapCursor();
    return;
  }
  const tool = document.getElementById("map-tool").value;
  if (hit) {
    if (e.shiftKey) {
      toggleSelected(hit.list, hit.patch);
      return;
    }
    // Clicking a patch that's ALREADY part of a multi-selection drags the whole group; clicking
    // any other patch (or the only one already selected) replaces the selection with just it —
    // the usual "click one of several selected things to move all of them" convention.
    const partOfGroup = mapSelectedSet.length > 1 && isSelectedPatch(hit.list, hit.patch);
    if (!partOfGroup) {
      selectPatch(hit.list, hit.patch);
    }
    if (mapSelectedSet.length > 1) {
      // Group move only — resizing several differently-sized patches at once by one shared handle
      // has no single sensible meaning, so multi-selection never offers it, even from an edge hit.
      mapDrag = { mode: "move", startWorld: world, group: mapSelectedSet.map((s) => ({ patch: s.patch, startCx: s.patch.cx, startCy: s.patch.cy })) };
    } else {
      // Keyed by the patch OBJECT, not list+index — a keyboard shortcut (⌘D, Delete) can fire
      // mid-drag and mutate the list (unshift/filter) while the mouse button is still down, which
      // would silently shift every index and make an index-based lookup grab the wrong patch (see
      // mapSelectedSet's own comment for the same reasoning). startRadius doubles as the
      // Escape-cancel rollback value (see the maps keydown handler) as well as the drag math itself.
      mapDrag = hit.onEdge
          ? { mode: "resize", patch: hit.patch, center: { x: hit.patch.cx, y: hit.patch.cy }, startRadius: hit.patch.radius }
          : { mode: "move", startWorld: world, group: [{ patch: hit.patch, startCx: hit.patch.cx, startCy: hit.patch.cy }] };
    }
    return;
  }
  if (tool === "select") {
    // Resolved at mouseup (finishRubberBand), against whatever the box ends up covering — nothing
    // about the CURRENT selection is touched here, so the existing one stays visible while dragging.
    // Deliberately NOT bounds-checked below like placement is: starting (or ending) a rubber-band
    // out on the pasteboard, to fully enclose patches sitting right against the map's edge, is
    // completely normal — only PLACING something out there doesn't mean anything.
    mapDrag = { mode: "rubberband", startWorld: world, currentWorld: world };
    return;
  }
  // Clicked the pasteboard around the map (see drawMapCanvas), not the map itself — same as
  // clicking outside a document's canvas in any other editor, this does nothing. Previously
  // clampCell pulled a click way out here back to the map's nearest edge instead, which looked
  // like the placement had randomly landed somewhere other than where you clicked.
  if (!isInsideMap(world.x, world.y)) {
    return;
  }
  // Placing into a HIDDEN layer would violate mapLayerVisible's own contract (see its comment) —
  // the new patch would exist in the data (and get saved) while being invisible, unclickable, and
  // absent from the list, which is a much worse surprise than just refusing the click.
  if (!mapLayerVisible[toolLayerKind(tool)]) {
    toast(`The ${toolLayerKind(tool)} layer is hidden — show it before adding to it`, true);
    return;
  }
  if (STAMPS[tool]) {
    placeStamp(tool, Math.round(world.x), Math.round(world.y));
    return;
  }
  deselectPatch();
  // The radius is read ONCE, here, and stays fixed for the whole stroke — a drag now stamps copies
  // of this patch (see extendPlaceStroke) instead of sizing one live. Resizing didn't disappear
  // with it: the Radius field sets the size before you draw, and dragging an existing patch's edge
  // still resizes that patch.
  const radius = currentRadius();
  mapDrag = { mode: "place", radius, brush: { x: world.x, y: world.y }, stamped: new Set() };
  mapPendingPatches = [];
  stampPlaceStroke(mapDrag, world.x, world.y); // the click itself is the stroke's first stamp — a click that never moves places exactly this one patch
  renderMapEditor();
});

// Idle hover only (mapDrag is null) — the placement-ghost/coords-readout logic below only means
// anything while the cursor is actually over the canvas, so this one stays canvas-scoped.
// Continuing an ACTIVE drag is a separate, window-scoped listener right after this one — see its
// own comment for why it can't just be folded in here.
mapCanvasEl.addEventListener("mousemove", (e) => {
  if (mapDrag) return;
  const world = worldFromEvent(e);
  updateMapCoordsReadout(world);
  mapHoverWorld = world;
  mapHoverPatch = isInsideMap(world.x, world.y) ? hitTestPatch(world.x, world.y) : null;
  updateMapCursor(); // the cursor now depends on the hover position too — see its own comment on the scale handles
  renderMapEditor();
});

// window-scoped, not canvas-scoped, DELIBERATELY: .map-tools floats ON TOP of the canvas
// (position:absolute, z-index:1 — see style.css), so once the cursor slides under it mid-drag, the
// browser stops delivering mousemove to the canvas element at all (it goes to whatever's on top
// instead) — a canvas-only listener would freeze the drag right there until the cursor came back
// out from under the panel. worldFromEvent's own math (canvas.getBoundingClientRect()) is already
// coordinate-space-correct regardless of which element the event actually landed on.
window.addEventListener("mousemove", (e) => {
  if (!mapDrag) return;
  const world = worldFromEvent(e);
  updateMapCoordsReadout(world);
  if (mapDrag.mode === "pan") {
    mapView.offsetX = mapDrag.startOffsetX - (e.clientX - mapDrag.startClientX) / mapView.scale;
    mapView.offsetY = mapDrag.startOffsetY - (e.clientY - mapDrag.startClientY) / mapView.scale;
    clampView();
  } else if (mapDrag.mode === "place") {
    extendPlaceStroke(mapDrag, world);
  } else if (mapDrag.mode === "move") {
    // Clamp the DELTA against the group's shared bounds, not each patch's new position on its
    // own — clamping per-patch let whichever one was closest to an edge stop first while the
    // rest kept going, silently squeezing the group's relative layout together every time any
    // one member neared a map edge (and that squeeze then got saved).
    let dx = Math.round(world.x - mapDrag.startWorld.x);
    let dy = Math.round(world.y - mapDrag.startWorld.y);
    const minStartCx = Math.min(...mapDrag.group.map((g) => g.startCx));
    const maxStartCx = Math.max(...mapDrag.group.map((g) => g.startCx));
    const minStartCy = Math.min(...mapDrag.group.map((g) => g.startCy));
    const maxStartCy = Math.max(...mapDrag.group.map((g) => g.startCy));
    dx = Math.max(-minStartCx, Math.min(MAP_SIZE - 1 - maxStartCx, dx));
    dy = Math.max(-minStartCy, Math.min(MAP_SIZE - 1 - maxStartCy, dy));
    for (const g of mapDrag.group) {
      g.patch.cx = g.startCx + dx;
      g.patch.cy = g.startCy + dy;
    }
  } else if (mapDrag.mode === "resize") {
    mapDrag.patch.radius = Math.max(1, Math.round(Math.hypot(world.x - mapDrag.center.x, world.y - mapDrag.center.y)));
  } else if (mapDrag.mode === "scale") {
    // Distance ratio, not a per-axis one: it behaves the same whichever corner was grabbed, and it
    // has no discontinuity when the cursor crosses the anchor's own row or column. The floor keeps
    // a drag through the anchor from collapsing the area onto a single cell.
    const reach = Math.hypot(world.x - mapDrag.anchor.x, world.y - mapDrag.anchor.y);
    const factor = Math.max(0.05, Math.min(reach / mapDrag.grabDistance, maxSelectionScale(mapDrag)));
    applySelectionScale(mapDrag, factor);
  } else if (mapDrag.mode === "rubberband") {
    mapDrag.currentWorld = world;
  }
  renderMapEditor();
});

mapCanvasEl.addEventListener("mouseleave", (e) => {
  // The hover toolbar (renderHoverToolbar) floats OVER the canvas as a separate DOM element, not
  // inside it — moving the cursor onto it is, DOM-wise, leaving the canvas, and would otherwise wipe
  // mapHoverPatch out from under the very toolbar the user is trying to click. Its own mouseleave
  // (right below) is the one that clears state once the cursor actually leaves both.
  if (e.relatedTarget && hoverToolbarEl.contains(e.relatedTarget)) return;
  mapHoverWorld = null;
  mapHoverPatch = null;
  updateMapCoordsReadout(null);
  renderMapEditor();
});

mapCanvasEl.addEventListener("contextmenu", (e) => {
  e.preventDefault();
  const world = worldFromEvent(e);
  const hit = hitTestPatch(world.x, world.y);
  if (!hit) return;
  hit.list.splice(hit.index, 1);
  mapSelectedSet = mapSelectedSet.filter((s) => s.patch !== hit.patch);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

// Figma/Miro convention, not "wheel always zooms": plain scroll (mouse wheel OR trackpad two-finger
// pan) PANS the canvas — deltaX too, not just deltaY, since a trackpad's horizontal swipe is a real,
// common gesture a deltaY-only handler would silently swallow. Zoom needs a modifier (Ctrl/Cmd+wheel)
// specifically because browsers report a trackpad PINCH as a wheel event with ctrlKey set (there is
// no separate "pinch" DOM event) — this one check is simultaneously "the zoom shortcut" AND "how a
// pinch gesture is even detectable" at once, not two unrelated things that happen to share a key.
mapCanvasEl.addEventListener("wheel", (e) => {
  e.preventDefault();
  const rect = mapCanvasEl.getBoundingClientRect();
  const px = (e.clientX - rect.left) / rect.width * mapCanvasEl.width;
  const py = (e.clientY - rect.top) / rect.height * mapCanvasEl.height;
  if (e.ctrlKey || e.metaKey) {
    // deltaY's sign is what both a real Ctrl+wheel AND a trackpad pinch already use for "in"/"out"
    // (pinching out — spreading fingers — reports negative deltaY, same as scrolling up) — no
    // separate gesture-direction mapping needed beyond what zoomAt/the plain-scroll branch already do.
    zoomAt(px, py, wheelZoomFactor(e));
  } else {
    // Screen-pixel deltas straight off the event, converted through the current scale — matches the
    // WASD pan step's own conversion (see the keydown handler) so the two feel like the same camera
    // regardless of which one is driving it.
    mapView.offsetX += e.deltaX / mapView.scale;
    mapView.offsetY += e.deltaY / mapView.scale;
    clampView();
  }
  renderMapEditor();
}, { passive: false });

/** Selects everything (from BOTH lists) whose center falls inside the box the "Select" tool's
 * drag traced out — the existing selection stays untouched until this runs, so dragging never
 * flickers it away mid-gesture; a zero-size box (a plain click on empty space) simply selects
 * nothing, same as clicking empty space with any other tool deselects. */
function finishRubberBand(drag) {
  const x1 = Math.min(drag.startWorld.x, drag.currentWorld.x);
  const x2 = Math.max(drag.startWorld.x, drag.currentWorld.x);
  const y1 = Math.min(drag.startWorld.y, drag.currentWorld.y);
  const y2 = Math.max(drag.startWorld.y, drag.currentWorld.y);
  mapSelectedSet = [];
  if (mapLayerVisible.ore) {
    for (const patch of mapOrePatches) {
      if (patch.cx >= x1 && patch.cx <= x2 && patch.cy >= y1 && patch.cy <= y2) {
        mapSelectedSet.push({ list: mapOrePatches, patch });
      }
    }
  }
  if (mapLayerVisible.terrain) {
    for (const patch of mapTerrainPatches) {
      if (patch.cx >= x1 && patch.cx <= x2 && patch.cy >= y1 && patch.cy <= y2) {
        mapSelectedSet.push({ list: mapTerrainPatches, patch });
      }
    }
  }
}

window.addEventListener("mouseup", () => {
  if (!mapDrag) return;
  const mode = mapDrag.mode;
  if (mode === "place") {
    const committed = commitPendingPatches();
    mapPendingPatches = [];
    if (committed) {
      markDirty("maps");
      pushMapHistory();
    }
  } else if (mode === "move") {
    // Only if something actually moved — a plain click-to-select starts and immediately ends a
    // "move" drag with zero distance, and that's a selection change, not an edit; pushing history
    // for it would silently eat a redo step every time someone just clicks a patch.
    const moved = mapDrag.group.some((g) => g.patch.cx !== g.startCx || g.patch.cy !== g.startCy);
    if (moved) {
      markDirty("maps");
      pushMapHistory();
    }
  } else if (mode === "resize") {
    if (mapDrag.patch.radius !== mapDrag.startRadius) {
      markDirty("maps");
      pushMapHistory();
    }
  } else if (mode === "scale") {
    // Same "only if something actually changed" rule the move drag explains: grabbing a handle and
    // letting go without moving is not an edit, and a history entry for it would eat a redo step.
    const scaled = mapDrag.start.some((s) => s.patch.cx !== s.cx || s.patch.cy !== s.cy || s.patch.radius !== s.radius);
    if (scaled) {
      markDirty("maps");
      pushMapHistory();
    }
  } else if (mode === "rubberband") {
    finishRubberBand(mapDrag);
  }
  // "pan" touches no patch data at all — nothing here to mark dirty or push to history.
  mapDrag = null;
  updateMapCursor(); // drops out of "grabbing" back to "grab" (Space still down) or the tool's own cursor
  renderMapEditor();
});

document.getElementById("map-zoom-reset").addEventListener("click", () => {
  resetView();
  renderMapEditor();
});

/* ---- full screen: the WHOLE stage (canvas + floating tools panel + both HUDs), via the browser's
   own Fullscreen API — not a CSS-only "hide the sidebar" mode, since the point is showing more map
   than the browser chrome/OS taskbar would otherwise leave room for, the same as a video player's
   own fullscreen button. ---- */

const mapStageEl = document.querySelector(".map-stage");
const mapFullscreenBtn = document.getElementById("map-fullscreen-btn");

function updateMapFullscreenBtn() {
  const isFull = document.fullscreenElement === mapStageEl;
  mapFullscreenBtn.innerHTML = icon(isFull ? "collapse" : "expand", 15);
  mapFullscreenBtn.title = isFull ? "Exit full screen (Esc)" : "Fill the screen with the map";
}
updateMapFullscreenBtn();

mapFullscreenBtn.addEventListener("click", () => {
  if (document.fullscreenElement) {
    document.exitFullscreen();
  } else {
    // Rejects (rather than throwing synchronously) if the browser refuses — e.g. a user gesture
    // requirement it decided this click somehow didn't satisfy, or a sandboxed embed — surfaced as
    // a toast instead of an uncaught promise rejection silently doing nothing.
    mapStageEl.requestFullscreen().catch((err) => toast(`Couldn't enter full screen: ${err.message}`, true));
  }
});

// Fires for ANY fullscreen change, not just ones this button caused (the browser's own Esc-to-exit
// bypasses the click handler above entirely) — the one place both the icon/title AND the canvas's
// own drawing-buffer size actually get resynced, regardless of what triggered the change.
document.addEventListener("fullscreenchange", () => {
  updateMapFullscreenBtn();
  // The stage's box changes size the INSTANT fullscreen engages/releases — measure and redraw right
  // away rather than waiting for the debounced window "resize" handler (120ms, see its own comment)
  // to eventually catch up, since this size change is already fully known right now.
  if (activeTab() === "maps") resizeMapCanvas();
});

// Zoom HUD (bottom-left of the canvas — see .map-zoom-hud): +/- zoom toward the canvas's own
// center (there's no cursor position to zoom toward for a button click, unlike the wheel), and
// the percentage readout doubles as a reset button, the same dual-purpose convention Figma uses.
document.getElementById("map-zoom-in-btn").addEventListener("click", () => {
  zoomAt(mapCanvasWidth / 2, mapCanvasHeight / 2, 1.2);
  renderMapEditor();
});
document.getElementById("map-zoom-out-btn").addEventListener("click", () => {
  zoomAt(mapCanvasWidth / 2, mapCanvasHeight / 2, 1 / 1.2);
  renderMapEditor();
});
document.getElementById("map-zoom-level").addEventListener("click", () => {
  resetView();
  renderMapEditor();
});

document.getElementById("map-hover-toolbar-duplicate").innerHTML = icon("copy", 14);
document.getElementById("map-hover-toolbar-delete").innerHTML = icon("trash", 14);

// Both buttons act on currentHoverToolbarTarget (set by renderHoverToolbar, not re-derived here),
// through duplicateSelectedPatches/deleteSelectedPatches — the ONE place that knows how to mutate
// mapOrePatches/mapTerrainPatches and push history, instead of a second copy of that logic here.
// A "hover" target is selected first so those two have something to act on; a "selection" target
// must NOT be, because selectPatch collapses a whole selection down to one patch — the pill would
// then delete a single circle out of an area the user had just outlined and aimed this at.
function actOnHoverToolbarTarget(act) {
  if (!currentHoverToolbarTarget) return;
  if (currentHoverToolbarTarget.kind === "hover") {
    selectPatch(currentHoverToolbarTarget.list, currentHoverToolbarTarget.patch);
  }
  act();
}

document.getElementById("map-hover-toolbar-duplicate").addEventListener("click", () => {
  actOnHoverToolbarTarget(duplicateSelectedPatches);
});
document.getElementById("map-hover-toolbar-delete").addEventListener("click", () => {
  actOnHoverToolbarTarget(() => {
    // Whatever this pill was showing for is about to stop existing — clear the hover reference
    // explicitly rather than relying on the next mousemove: renderHoverToolbar's own list.includes
    // guard WOULD catch it either way, but only once something re-renders, and nothing does until
    // the mouse actually moves again if the click came from a hover (not a selection).
    mapHoverPatch = null;
    deleteSelectedPatches();
  });
});

// Moving from the canvas onto this floating pill fires the canvas's own "mouseleave" (see its
// comment) — this element's matching "leave the pill" case, for when the cursor goes somewhere that
// ISN'T back onto the canvas (a genuine move-away, not just crossing the seam between the two).
hoverToolbarEl.addEventListener("mouseleave", (e) => {
  if (e.relatedTarget && mapCanvasEl.contains(e.relatedTarget)) return;
  mapHoverPatch = null;
  renderMapEditor();
});

let mapResizeDebounce = null;
window.addEventListener("resize", () => {
  if (activeTab() !== "maps") return; // resized while hidden — the tab-switch handler resizes it when it's shown again
  clearTimeout(mapResizeDebounce);
  // Re-checks activeTab() again INSIDE the timeout, not just in this listener — the user can switch
  // away from maps during the 120ms wait, and resizeMapCanvas measuring a since-hidden form would
  // otherwise be caught by its own clientWidth<=0 guard anyway, but silently skipping here reads
  // clearer than relying on that guard to save a redundant call.
  mapResizeDebounce = setTimeout(() => {
    if (activeTab() === "maps") resizeMapCanvas();
  }, 120);
});

document.addEventListener("keydown", (e) => {
  if (activeTab() !== "maps") return;
  const tag = document.activeElement.tagName;
  const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
  if (e.key === "Escape" && (mapDrag || mapPendingPatches.length > 0)) {
    // A "place"/"rubberband" drag never writes into an actual patch object until mouseup (see that
    // handler) — dropping the in-progress state is enough to cancel those. "pan"/"move"/"resize"
    // mutate real, already-committed state LIVE on every mousemove, so cancelling has to put it
    // back the way it was, not just stop moving it further.
    if (mapDrag && mapDrag.mode === "pan") {
      mapView.offsetX = mapDrag.startOffsetX;
      mapView.offsetY = mapDrag.startOffsetY;
    } else if (mapDrag && mapDrag.mode === "move") {
      for (const g of mapDrag.group) {
        g.patch.cx = g.startCx;
        g.patch.cy = g.startCy;
      }
    } else if (mapDrag && mapDrag.mode === "resize") {
      mapDrag.patch.radius = mapDrag.startRadius;
    } else if (mapDrag && mapDrag.mode === "scale") {
      for (const start of mapDrag.start) {
        start.patch.cx = start.cx;
        start.patch.cy = start.cy;
        start.patch.radius = start.radius;
      }
    }
    mapDrag = null;
    mapPendingPatches = [];
    updateMapCursor();
    renderMapEditor();
    return;
  }
  // e.repeat guards against the flood of keydowns an OS auto-repeats while a key stays held — every
  // one AFTER the first is a no-op here anyway (spaceHeld's already true), but skipping them avoids
  // pointlessly restyling the cursor dozens of times a second for as long as Space stays down.
  // code, not key: "Space" is unambiguous across keyboard layouts where e.key for the spacebar can
  // differ (some layouts/browsers report it as "Spacebar").
  if (!typing && e.code === "Space" && !e.repeat) {
    e.preventDefault(); // Space's own default (scroll the page down) would fight with panning
    spaceHeld = true;
    updateMapCursor();
    return;
  }
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "z" && !typing) {
    e.preventDefault();
    if (e.shiftKey) redoMap(); else undoMap();
    return;
  }
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "d" && !typing) {
    e.preventDefault();
    duplicateSelectedPatches();
    return;
  }
  if ((e.key === "Delete" || e.key === "Backspace") && !typing && mapSelectedSet.length > 0) {
    e.preventDefault();
    deleteSelectedPatches();
    return;
  }
  if (mapSelectedSet.length > 0 && !typing && (e.key === "ArrowUp" || e.key === "ArrowDown" || e.key === "ArrowLeft" || e.key === "ArrowRight")) {
    e.preventDefault();
    const step = e.shiftKey ? 10 : 1;
    // Clamp the shared delta against the WHOLE selection's bounds, not each patch on its own —
    // same reasoning as the mousemove "move" handler: clamping per-patch lets whichever one is
    // closest to a map edge stop first while the rest keep going, squeezing the group together.
    let dx = e.key === "ArrowLeft" ? -step : e.key === "ArrowRight" ? step : 0;
    let dy = e.key === "ArrowUp" ? -step : e.key === "ArrowDown" ? step : 0;
    const minCx = Math.min(...mapSelectedSet.map((s) => s.patch.cx));
    const maxCx = Math.max(...mapSelectedSet.map((s) => s.patch.cx));
    const minCy = Math.min(...mapSelectedSet.map((s) => s.patch.cy));
    const maxCy = Math.max(...mapSelectedSet.map((s) => s.patch.cy));
    dx = Math.max(-minCx, Math.min(MAP_SIZE - 1 - maxCx, dx));
    dy = Math.max(-minCy, Math.min(MAP_SIZE - 1 - maxCy, dy));
    for (const { patch } of mapSelectedSet) {
      patch.cx += dx;
      patch.cy += dy;
    }
    // History is pushed on keyUP below, not here — holding a key auto-repeats keydown many times a
    // second, and one undo step per repeat would blow through MAP_HISTORY_LIMIT in under a second
    // (the same "one entry per gesture" rule a mouse drag already gets, which only pushes on mouseup).
    // mapNudgePending records that THIS handler is the one that just moved something — the keyup
    // listener only pushes when it's set, so an arrow key released while typing in an unrelated
    // field (moving a caret, not a patch — this branch never ran, the flag was never set) can't
    // push a phantom no-op snapshot that silently eats a redo step.
    mapNudgePending = true;
    markDirty("maps");
    renderMapEditor();
    return;
  }
  // Every branch below also excludes metaKey/ctrlKey — plain +/-/0/f are free real estate, but
  // ⌘+/⌘−/⌘0 are the BROWSER's own zoom shortcuts and ⌘F/Ctrl+F is find-on-page; stealing those
  // from underneath the user because they happen to be on the maps tab would be a bad trade for
  // one-key map shortcuts that could just as easily ask for a bare keypress instead.
  const noModifier = !e.metaKey && !e.ctrlKey;
  // "=" too, not just "+" — on a US keyboard "+" needs Shift, "=" doesn't, and zooming shouldn't
  // require holding a modifier the wheel never needed either.
  if (!typing && noModifier && (e.key === "+" || e.key === "=")) {
    e.preventDefault();
    zoomAt(mapCanvasWidth / 2, mapCanvasHeight / 2, 1.2);
    renderMapEditor();
    return;
  }
  if (!typing && noModifier && e.key === "-") {
    e.preventDefault();
    zoomAt(mapCanvasWidth / 2, mapCanvasHeight / 2, 1 / 1.2);
    renderMapEditor();
    return;
  }
  if (!typing && noModifier && e.key === "0") {
    e.preventDefault();
    resetView();
    renderMapEditor();
    return;
  }
  if (!typing && noModifier && e.key.toLowerCase() === "f") {
    e.preventDefault();
    focusOnSelection();
    return;
  }
  // WASD pans the CAMERA, arrow keys nudge the SELECTED PATCH (see the arrow-key branch above) —
  // deliberately two different key sets so the two never fight over the same keypress. Step is in
  // screen pixels, not world units, converted through the current scale — holding Shift for a
  // bigger step matches the same convention the arrow-key nudge already uses.
  const wasd = { w: "up", s: "down", a: "left", d: "right" }[e.key.toLowerCase()];
  if (!typing && noModifier && wasd) {
    e.preventDefault();
    const panStep = (e.shiftKey ? 180 : 60) / mapView.scale;
    if (wasd === "up") mapView.offsetY -= panStep;
    if (wasd === "down") mapView.offsetY += panStep;
    if (wasd === "left") mapView.offsetX -= panStep;
    if (wasd === "right") mapView.offsetX += panStep;
    clampView();
    renderMapEditor();
  }
});

document.addEventListener("keyup", (e) => {
  // Not tab-gated like its keydown counterpart — Space can still be physically held while switching
  // tabs (a mouse click doesn't release it), and the flag has to come back down wherever it lets go,
  // not just while the maps tab happens to still be the active one.
  if (e.code === "Space" && spaceHeld) {
    spaceHeld = false;
    // A pan that's still in progress (mouse never released) keeps going as an ordinary Shift/
    // middle-drag pan would — only the CURSOR reverts, since the drag itself doesn't have a
    // "half-Space" state to fall back into; mouseup ends it the normal way either way.
    if (!mapDrag || mapDrag.mode !== "pan") updateMapCursor();
  }
  if (!mapNudgePending) return;
  if (e.key === "ArrowUp" || e.key === "ArrowDown" || e.key === "ArrowLeft" || e.key === "ArrowRight") {
    mapNudgePending = false;
    pushMapHistory();
  }
});

document.getElementById("map-clear-all").addEventListener("click", async () => {
  if (mapOrePatches.length === 0 && mapTerrainPatches.length === 0) return;
  if (!(await confirmModal("Clear all patches?", "Every ore and terrain patch on this map will be removed (still undoable with ⌘Z)."))) return;
  mapOrePatches = [];
  mapTerrainPatches = [];
  deselectPatch();
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

/** Toggles one layer's visibility — display-only, see mapLayerVisible's own comment. Drops any
 * currently-selected patch that belongs to the layer being hidden: nudging/deleting something you
 * can no longer see on the canvas is more confusing than just losing its selection. */
function toggleLayer(kind) {
  mapLayerVisible[kind] = !mapLayerVisible[kind];
  const btn = document.getElementById(`toggle-layer-${kind}`);
  btn.classList.toggle("active", mapLayerVisible[kind]);
  btn.title = mapLayerVisible[kind] ? `Hide ${kind} patches` : `Show ${kind} patches`;
  const list = kind === "ore" ? mapOrePatches : mapTerrainPatches;
  mapSelectedSet = mapSelectedSet.filter((s) => s.list !== list);
  renderMapEditor();
}
document.getElementById("toggle-layer-ore").addEventListener("click", () => toggleLayer("ore"));
document.getElementById("toggle-layer-terrain").addEventListener("click", () => toggleLayer("terrain"));

/* ---- resizable tools panel — drag the handle on its left edge; width persists across reloads.
   Resizing never touches the canvas (see .map-tools-resize-handle's own CSS comment), so this is
   the one map-editor interaction that doesn't call renderMapEditor/resizeMapCanvas at all. ---- */

const MAP_TOOLS_WIDTH_KEY = "rustorio-editor-map-tools-width";
let mapToolsWidth = Number(localStorage.getItem(MAP_TOOLS_WIDTH_KEY)) || 300;
document.getElementById("map-tools").style.width = mapToolsWidth + "px";

let mapToolsResizing = false;
document.getElementById("map-tools-resize-handle").addEventListener("mousedown", (e) => {
  e.preventDefault(); // dragging shouldn't select the panel's own text
  mapToolsResizing = true;
  document.getElementById("map-tools-resize-handle").classList.add("dragging");
});
window.addEventListener("mousemove", (e) => {
  if (!mapToolsResizing) return;
  const rightEdge = document.getElementById("map-tools").getBoundingClientRect().right;
  mapToolsWidth = Math.max(260, Math.min(600, rightEdge - e.clientX));
  document.getElementById("map-tools").style.width = mapToolsWidth + "px";
});
window.addEventListener("mouseup", () => {
  if (!mapToolsResizing) return;
  mapToolsResizing = false;
  document.getElementById("map-tools-resize-handle").classList.remove("dragging");
  localStorage.setItem(MAP_TOOLS_WIDTH_KEY, String(Math.round(mapToolsWidth)));
});

/* ---- selected-patch inspector: type-in editing instead of drag-only ---- */

// X and Y only ever show/act when EXACTLY one patch is selected (renderPatchInspector hides
// #patch-inspector-position otherwise), so mapSelectedSet[0] is safe wherever mapSelectedSet itself
// isn't empty; the length check alone guards against a stray change event firing after the
// selection changed to 0 or many between focus and blur. Type/Radius/Item below act on the whole
// selection, however big it is — see convertSelectionTo and its two neighbors.
document.getElementById("patch-cx").addEventListener("change", (e) => {
  if (mapSelectedSet.length !== 1) return;
  mapSelectedSet[0].patch.cx = clampCell(Math.round(Number(e.target.value)) || 0);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-cy").addEventListener("change", (e) => {
  if (mapSelectedSet.length !== 1) return;
  mapSelectedSet[0].patch.cy = clampCell(Math.round(Number(e.target.value)) || 0);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-radius").addEventListener("change", (e) => {
  // Empty means the field is showing its "mixed" placeholder and the user tabbed straight back out
  // of it — leaving several different radii alone is the right answer there, not collapsing them
  // all onto 1 because Number("") is 0.
  if (e.target.value.trim() === "") return;
  resizeSelectedPatches(Math.max(1, Math.round(Number(e.target.value)) || 1));
});

document.getElementById("patch-type").addEventListener("change", (e) => {
  convertSelectionTo(e.target.value);
});

document.getElementById("patch-ore-item").addEventListener("change", (e) => {
  setSelectedPatchesOre(e.target.value);
});

document.getElementById("patch-duplicate").addEventListener("click", duplicateSelectedPatches);
document.getElementById("patch-delete").addEventListener("click", deleteSelectedPatches);

function selectMap(map) {
  fillMap(map);
  document.getElementById("maps-form-title").textContent = `Edit "${entryKey(map)}"`;
  formError("maps", "");
  state.selected.maps = entryKey(map);
  clearDirty("maps");
  renderMapsList();
}

// Same "don't clobber a localized label the player never touched" reasoning as editingItemLabel.
let editingMapLabel;

function collectMap() {
  const f = document.getElementById("maps-form");
  const typed = f.label.value.trim();
  const label = editingMapLabel && typeof editingMapLabel === "object" && typed === labelText(editingMapLabel)
      ? editingMapLabel
      : typed;
  return {
    path: f.path.value.trim(),
    label,
    orePatches: mapOrePatches.map((p) => ({ cx: p.cx, cy: p.cy, radius: p.radius, ore: p.ore })),
    terrainPatches: mapTerrainPatches.map((p) => ({ cx: p.cx, cy: p.cy, radius: p.radius, terrain: p.terrain })),
  };
}
collectors.maps = collectMap;

function fillMap(body) {
  const f = document.getElementById("maps-form");
  editingMapLabel = body.label;
  f.path.value = body.path || "";
  f.label.value = labelText(body.label);
  mapOrePatches = (body.orePatches || []).map((p) => ({ ...p }));
  mapTerrainPatches = (body.terrainPatches || []).map((p) => ({ ...p }));
  mapSelectedSet = [];
  mapDrag = null;
  mapPendingPatches = [];
  // Otherwise a layer hidden on one map stays hidden (with no visible cue why) on the NEXT map you
  // open — the patch count would say "12 ore patches" while the canvas/list quietly show none.
  if (!mapLayerVisible.ore) toggleLayer("ore");
  if (!mapLayerVisible.terrain) toggleLayer("terrain");
  defaultView(); // opening a map is the editor setting the camera itself — see defaultView vs resetView
  resetMapHistory();
  renderMapEditor();
}
fillers.maps = fillMap;

/* ---- "New map" template picker ---- */

function openNewMapModal() {
  document.getElementById("new-map-duplicate-picker").classList.add("hidden");
  const toggle = document.getElementById("new-map-duplicate-toggle");
  toggle.disabled = state.maps.length === 0;
  const select = document.getElementById("new-map-duplicate-select");
  select.innerHTML = state.maps.map((m) => `<option value="${entryKey(m)}"></option>`).join("");
  [...select.options].forEach((opt, idx) => (opt.textContent = `${labelText(state.maps[idx].label)} (${entryKey(state.maps[idx])})`));
  document.getElementById("new-map-backdrop").classList.remove("hidden");
}

function closeNewMapModal() {
  document.getElementById("new-map-backdrop").classList.add("hidden");
}

function startNewMap(body) {
  if (blockedByUnsavedChanges()) return;
  fillMap(body || {});
  document.getElementById("maps-form-title").textContent = "New map";
  formError("maps", "");
  state.selected.maps = null;
  if (body && (body.orePatches || body.terrainPatches)) {
    markDirty("maps"); // imported/duplicated content is unsaved even though nothing was typed
  } else {
    clearDirty("maps");
  }
  renderMapsList();
  closeNewMapModal();
}

document.querySelector('[data-new="maps"]').addEventListener("click", openNewMapModal);
document.getElementById("new-map-blank").addEventListener("click", () => startNewMap({}));

document.getElementById("maps-picker").addEventListener("change", (e) => {
  // Both this select AND #maps-picker-filter live inside <form id="maps-form"> (so their own
  // fields can sit next to Path/Label in the same floating panel) — wireDirtyTracking listens for
  // "change"/"input" on the WHOLE form, so without stopPropagation, switching maps here (or
  // typing/blurring the filter below) would mark the map you just SWITCHED TO as "unsaved" before
  // you've touched a single field of it.
  e.stopPropagation();
  if (blockedByUnsavedChanges()) {
    e.target.value = state.selected.maps || ""; // revert — the select already changed itself before "change" fired
    return;
  }
  const map = state.maps.find((m) => entryKey(m) === e.target.value);
  if (map) selectMap(map);
});
function stopMapsPickerFilterPropagation(e) {
  e.stopPropagation();
}
document.getElementById("maps-picker-filter").addEventListener("input", (e) => {
  stopMapsPickerFilterPropagation(e);
  renderMapsPicker();
});
// "change" too, not just "input" — a text input fires "change" on blur if its value was edited
// since focus, which happens whenever you finish typing a filter and click elsewhere (the canvas,
// say) — that blur-triggered "change" bubbles to the form exactly like the picker's own does above.
document.getElementById("maps-picker-filter").addEventListener("change", stopMapsPickerFilterPropagation);
document.getElementById("maps-picker-filter").addEventListener("keydown", (e) => {
  // This input lives inside <form id="maps-form">, which has a submit button (Save) — without
  // this, Enter here (the obvious thing to press after typing a search) does an implicit form
  // submission instead of just... being a filter box.
  if (e.key === "Enter") e.preventDefault();
});
document.getElementById("maps-picker-new").innerHTML = icon("plus", 15);
document.getElementById("maps-picker-new").addEventListener("click", openNewMapModal);

document.getElementById("new-map-vanilla").addEventListener("click", async () => {
  try {
    const template = await api("GET", "/api/vanilla-map-template");
    // The template's ore refs are bare, meaning "rustorio" (PatchOreLayout IS the vanilla game) —
    // requalify them for whichever mod this import actually lands in, see requalifyRef.
    startNewMap({
      orePatches: (template.orePatches || []).map((p) => ({ ...p, ore: requalifyRef(p.ore, "rustorio") })),
      terrainPatches: template.terrainPatches,
    });
  } catch (err) {
    toast(`Couldn't load the built-in layout: ${err.message}`, true);
  }
});

document.getElementById("new-map-duplicate-toggle").addEventListener("click", () => {
  document.getElementById("new-map-duplicate-picker").classList.toggle("hidden");
});

document.getElementById("new-map-duplicate-confirm").addEventListener("click", () => {
  const key = document.getElementById("new-map-duplicate-select").value;
  const source = state.maps.find((m) => entryKey(m) === key);
  if (!source) return;
  // Same requalification as importing the vanilla template — a bare ore ref meant "source's own
  // mod" in the original file, which is only still correct if you're duplicating within that mod.
  startNewMap({
    label: `${labelText(source.label)} copy`,
    orePatches: (source.orePatches || []).map((p) => ({ ...p, ore: requalifyRef(p.ore, source.__mod) })),
    terrainPatches: source.terrainPatches,
  });
});

document.getElementById("new-map-cancel").addEventListener("click", closeNewMapModal);
document.getElementById("new-map-backdrop").addEventListener("click", (e) => {
  if (e.target.id === "new-map-backdrop") closeNewMapModal();
});

document.getElementById("maps-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  let body;
  try {
    body = currentBody("maps");
  } catch (err) {
    formError("maps", `Invalid JSON: ${err.message}`);
    return;
  }
  const targetMod = state.selected.maps ? splitEntryKey(state.selected.maps).mod : state.mod;
  try {
    if (state.selected.maps) {
      const { path } = splitEntryKey(state.selected.maps);
      await api("PUT", `/api/maps/${path}`, body, targetMod);
    } else {
      await api("POST", "/api/maps", body, targetMod);
    }
    formError("maps", "");
    toast(`Saved "${targetMod}:${body.path}"`);
    await loadAll();
    selectMap({ ...body, __mod: targetMod });
  } catch (err) {
    formError("maps", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="maps"]').addEventListener("click", async () => {
  const key = state.selected.maps;
  if (!key) return;
  const { mod, path } = splitEntryKey(key);
  if (!(await confirmModal("Delete map?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/maps/${path}`, undefined, mod);
    toast(`Deleted "${key}"`);
    await loadAll();
    fillMap({});
    document.getElementById("maps-form-title").textContent = "New map";
    state.selected.maps = null;
    clearDirty("maps");
    renderMapsList();
  } catch (err) {
    formError("maps", err.message);
    toast(err.message, true);
  }
});

wireDirtyTracking("maps", document.getElementById("maps-form"));
wireJsonToggle("maps");

/* ================= TEXTURES ================= */

function renderTextureGrids() {
  renderTextureGrid("vanilla-texture-grid", state.textures.vanilla);
  renderTextureGrid("mod-texture-grid", state.textures.mod);
  document.getElementById("count-textures").textContent = state.textures.vanilla.length + state.textures.mod.length;
  // The building form's texture picker/preview reference this same texture list.
  if (document.getElementById("building-texture-value")) {
    renderBuildingTexturePicker();
    updateBuildingGlyphPreview();
  }
}

function renderTextureGrid(elementId, entries) {
  const el = document.getElementById(elementId);
  el.innerHTML = "";
  for (const t of entries) {
    const fig = document.createElement("figure");
    fig.innerHTML = `<img src="${t.assetUrl}" alt="${t.id}"><figcaption></figcaption>`;
    fig.querySelector("figcaption").textContent = t.id;
    el.appendChild(fig);
  }
  if (entries.length === 0) {
    el.innerHTML = '<p style="color:var(--muted-2);font-size:13px;">(none yet)</p>';
  }
}

const dropzone = document.getElementById("texture-dropzone");
const textureFileInput = document.getElementById("texture-file-input");
textureFileInput.addEventListener("change", () => {
  const file = textureFileInput.files[0];
  document.getElementById("texture-dropzone-label").textContent = file ? file.name : "Drop a PNG here or click to choose";
});
["dragenter", "dragover"].forEach((evt) =>
  dropzone.addEventListener(evt, (e) => {
    e.preventDefault();
    dropzone.classList.add("drag-over");
  })
);
["dragleave", "drop"].forEach((evt) =>
  dropzone.addEventListener(evt, (e) => {
    e.preventDefault();
    dropzone.classList.remove("drag-over");
  })
);
dropzone.addEventListener("drop", (e) => {
  const file = e.dataTransfer.files[0];
  if (file) {
    textureFileInput.files = e.dataTransfer.files;
    document.getElementById("texture-dropzone-label").textContent = file.name;
  }
});

document.getElementById("texture-upload-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target;
  const name = form.name.value.trim();
  const file = textureFileInput.files[0];
  if (!file) return;
  try {
    const res = await fetch(withModParam(`/api/textures/${encodeURIComponent(name)}`), {
      method: "POST",
      headers: { "Content-Type": "image/png" },
      body: await file.arrayBuffer(),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || `${res.status}`);
    document.getElementById("texture-form-error").textContent = "";
    form.reset();
    document.getElementById("texture-dropzone-label").textContent = "Drop a PNG here or click to choose";
    toast(`Uploaded "${data.id}"`);
    await loadAll();
  } catch (err) {
    document.getElementById("texture-form-error").textContent = err.message;
    toast(err.message, true);
  }
});

/* ============================================================
   global search (⌘/ jump-to)
   ============================================================ */

const globalSearchInput = document.getElementById("global-search");
const searchResultsEl = document.getElementById("search-results");

globalSearchInput.addEventListener("input", () => {
  const q = globalSearchInput.value.trim().toLowerCase();
  if (!q) {
    searchResultsEl.classList.add("hidden");
    return;
  }
  const groups = [
    { kind: "items", label: "Items", entries: state.items.filter((i) => `${labelText(i.label)} ${i.path}`.toLowerCase().includes(q)), info: itemRowInfo, select: selectItem },
    { kind: "recipes", label: "Recipes", entries: state.recipes.filter((r) => `${r.file} ${r.output}`.toLowerCase().includes(q)), info: recipeRowInfo, select: selectRecipe },
    { kind: "buildings", label: "Buildings", entries: state.buildings.filter((b) => `${labelText(b.label)} ${b.path}`.toLowerCase().includes(q)), info: buildingRowInfo, select: selectBuilding },
    { kind: "kinds", label: "Kinds", entries: (state.kindsIndex || []).filter((k) => `${k.label} ${k.id}`.toLowerCase().includes(q)), info: kindRowInfo, select: selectKindEntry },
    { kind: "maps", label: "Maps", entries: state.maps.filter((m) => `${labelText(m.label)} ${m.path}`.toLowerCase().includes(q)), info: mapRowInfo, select: selectMap },
  ];
  searchResultsEl.innerHTML = "";
  let any = false;
  for (const group of groups) {
    if (group.entries.length === 0) continue;
    any = true;
    const header = document.createElement("div");
    header.className = "search-result-group";
    header.textContent = group.label;
    searchResultsEl.appendChild(header);
    for (const entry of group.entries.slice(0, 6)) {
      const info = group.info(entry);
      const row = document.createElement("div");
      row.className = "search-result-row";
      row.innerHTML = `<span class="thumb">${info.thumb}</span><span></span>`;
      row.querySelector("span:last-child").textContent = info.title;
      row.addEventListener("click", () => {
        if (!switchToTab(group.kind)) return;
        group.select(entry);
        searchResultsEl.classList.add("hidden");
        globalSearchInput.value = "";
      });
      makeKeyboardClickable(row);
      searchResultsEl.appendChild(row);
    }
  }
  if (!any) {
    searchResultsEl.innerHTML = '<div class="search-result-row" style="cursor:default;color:var(--muted-2);">No matches</div>';
  }
  searchResultsEl.classList.remove("hidden");
});

document.addEventListener("click", (e) => {
  if (!e.target.closest(".search-box")) searchResultsEl.classList.add("hidden");
});

Object.entries({ items: "filter-items", recipes: "filter-recipes", buildings: "filter-buildings", kinds: "filter-kinds", maps: "filter-maps" }).forEach(([kind, id]) => {
  document.getElementById(id).addEventListener("input", () => {
    if (kind === "items") renderItemsList();
    if (kind === "recipes") renderRecipesList();
    if (kind === "buildings") renderBuildingsList();
    if (kind === "kinds") renderKindsList();
    if (kind === "maps") renderMapsList();
  });
});

/* ============================================================
   keyboard shortcuts
   ============================================================ */

document.addEventListener("keydown", (e) => {
  const tag = document.activeElement.tagName;
  const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";

  if (e.key === "/" && !typing) {
    e.preventDefault();
    globalSearchInput.focus();
    return;
  }
  if (e.key === "Escape") {
    if (!document.getElementById("modal-backdrop").classList.contains("hidden")) {
      document.getElementById("modal-cancel").click();
    }
    searchResultsEl.classList.add("hidden");
    document.activeElement.blur();
    return;
  }
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
    e.preventDefault();
    const tab = activeTab();
    if (tab === "textures") return;
    const form = document.getElementById(`${tab}-form`);
    // requestSubmit() runs native constraint validation first — if a `required` field (e.g. the
    // recipe kind <select>) is blank while the form itself is hidden (JSON mode has it swapped
    // out for the textarea), Chrome can't focus it to show the native error and just silently
    // drops the submit ("An invalid form control ... is not focusable", no feedback to the user).
    // The JSON textarea is the actual source of truth in that mode (see currentBody()), so skip
    // native validation entirely and dispatch the submit event directly.
    if (state.jsonMode[tab]) {
      form.dispatchEvent(new Event("submit", { cancelable: true }));
    } else {
      form.requestSubmit();
    }
  }
});

/* ============================================================
   content status (background /api/validate) + Play / relaunch
   ============================================================ */

let lastValidateError = null;

async function refreshContentStatus() {
  const dot = document.getElementById("content-status-dot");
  const text = document.getElementById("content-status-text");
  const footer = document.getElementById("content-status");
  try {
    const result = await api("POST", "/api/validate");
    lastValidateError = result.ok ? null : result.error;
    dot.className = "status-dot " + (result.ok ? "ok" : "bad");
    text.textContent = result.ok ? "Content is valid" : "Content has a problem — click to see";
    footer.classList.toggle("clickable", !result.ok);
  } catch (err) {
    dot.className = "status-dot bad";
    text.textContent = "Couldn't reach the editor server";
    lastValidateError = err.message;
    footer.classList.add("clickable");
  }
}

document.getElementById("content-status").addEventListener("click", () => {
  if (lastValidateError) toast(lastValidateError, true);
});

const banner = { text: "" }; // kept trivial; toasts carry the real messaging now
const statusPill = document.getElementById("status-pill");
const playBtn = document.getElementById("play-btn");
const stopBtn = document.getElementById("stop-btn");
let pollTimer = null;

stopBtn.innerHTML = `${icon("stop", 13)} Stop`;

function setStatusPill(s) {
  statusPill.textContent = s;
  statusPill.className = `pill pill-${s}`;
  // Only something to stop while the game process is actually up — "starting"/"running", not
  // "stopped"/"crashed" (DELETE /api/relaunch on an already-dead process is a harmless no-op
  // either way, but a permanently-enabled button would look clickable when there's nothing to do).
  stopBtn.disabled = s !== "starting" && s !== "running";
}

/** The Maps tab has an existing (saved) map open right now -> full "rustorio:<path>" id; otherwise {@code null}, meaning "play the default fixed map," same as before this existed. */
/** {@code state.selected.maps} is already a fully-qualified {@code "modId:path"} entryKey (see the cross-mod rework — every tab lists content from every mod now), not a bare path — do NOT re-prefix it with a mod id here, that's exactly what produced the "rustorio:sandbox:test_map" bug. */
function openMapIdForPlay() {
  return activeTab() === "maps" && state.selected.maps ? state.selected.maps : null;
}

playBtn.addEventListener("click", async () => {
  playBtn.disabled = true;
  try {
    const result = await api("POST", "/api/validate");
    if (!result.ok) {
      toast(`Can't play — content is broken:\n${result.error}`, true);
      playBtn.disabled = false;
      return;
    }
    // POST /api/relaunch always stops whatever's already running first (see GameProcess.relaunch)
    // — so Play doubles as Restart with no separate button needed for that half.
    const mapId = openMapIdForPlay();
    toast(mapId
        ? `Content is valid. Launching the game on "${mapId}" (first boot with --no-daemon can take a while)…`
        : "Content is valid. Launching the game (first boot with --no-daemon can take a while)…");
    await api("POST", "/api/relaunch", mapId ? { map: mapId } : undefined);
    startPolling();
  } catch (err) {
    toast(`Relaunch failed: ${err.message}`, true);
  } finally {
    playBtn.disabled = false;
  }
});

stopBtn.addEventListener("click", async () => {
  stopBtn.disabled = true;
  try {
    const status = await api("DELETE", "/api/relaunch");
    setStatusPill(status.state);
    toast("Game stopped.");
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  } catch (err) {
    toast(`Stop failed: ${err.message}`, true);
  }
});

function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(pollStatus, 1500);
  pollStatus();
}

async function pollStatus() {
  try {
    const status = await api("GET", "/api/relaunch");
    setStatusPill(status.state);
    if (status.state === "crashed") {
      toast(`Game process exited with code ${status.exitCode}:\n${status.tailLog.slice(-500)}`, true);
      clearInterval(pollTimer);
    }
  } catch (err) {
    // editor server briefly unreachable — ignore, keep polling
  }
}

/* ============================================================
   boot
   ============================================================ */

loadMods()
  .then(loadAll)
  .catch((err) => toast(`Failed to load content: ${err.message}`, true))
  .finally(() => document.getElementById("loading-overlay").classList.add("hidden"));
pollStatus();
