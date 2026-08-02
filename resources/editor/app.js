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

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
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

function switchToTab(tab) {
  document.querySelector(`.nav-item[data-tab="${tab}"]`).click();
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
    li.addEventListener("click", () => onSelect(entry));
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
  return {
    path: f.path.value.trim(),
    label,
    colorRgb: f.colorRgb.value,
    shape: f.shape.value,
    researchGrade: f.researchGrade.checked,
  };
}
collectors.items = collectItem;

function fillItem(body) {
  const f = document.getElementById("items-form");
  editingItemLabel = body.label;
  f.path.value = body.path || "";
  f.label.value = labelText(body.label);
  f.colorRgb.value = body.colorRgb || "#aaaaaa";
  f.shape.value = body.shape || "CIRCLE";
  f.researchGrade.checked = !!body.researchGrade;
  updateItemGlyphPreview();
}
fillers.items = fillItem;

document.querySelector('[data-new="items"]').addEventListener("click", () => {
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
    el.appendChild(li);
  }
}

function renderKindUsagePanel(e) {
  document.getElementById("kinds-usage-panel").classList.remove("hidden");
  renderUsageList("kind-usage-buildings", e.buildings,
      (b) => `${labelText(b.label)} (${entryKey(b)})${entryKey(b) === e.id ? " — own pool" : " — shares this pool"}`,
      (b) => { switchToTab("buildings"); selectBuilding(b); });
  renderUsageList("kind-usage-recipes", e.recipes,
      (r) => `${r.file} — ${r.ingredients.join(" + ")} → ${r.output}`,
      (r) => { switchToTab("recipes"); selectRecipe(r); });
  const outputPaths = [...new Set(e.recipes.map((r) => r.output))];
  const outputItems = outputPaths.map((path) => resolveItemRef(path)).filter(Boolean);
  renderUsageList("kind-usage-items", outputItems,
      (item) => labelText(item.label) + originSuffix(item),
      (item) => { switchToTab("items"); selectItem(item); });
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

const TERRAIN_COLORS = { WATER: "#3a6ea5", ROCK: "#7a7a76" };

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
  "stamp-lake": { kind: "terrain", terrain: "WATER", radiusDelta: 3, offsets: [[0, 0], [2, 1], [-2, 1], [0, -2]] },
  "stamp-ridge": { kind: "terrain", terrain: "ROCK", radiusDelta: 0, offsets: [[-5, -2], [-2, 0], [1, 1], [4, 2]] },
};

/** The canvas element's own drawing-buffer resolution (both width and height — always square),
 * recomputed by {@link resizeMapCanvas} to fill the available panel width instead of the fixed
 * 512px box this used to be hardcoded to (which left most of a normal-width browser window empty
 * and made precise placement on a 256x256 map needlessly hard). {@link mapMinScale}/{@link
 * mapMaxScale} derive from this instead of from fixed constants, so zoom bounds stay correct at
 * whatever size the canvas actually ends up. */
let mapCanvasSize = 512;

/** The whole 256x256 map fits — can't zoom out further than this. */
function mapMinScale() {
  return mapCanvasSize / MAP_SIZE;
}

/** 16x16 cells visible — individually clickable, same ratio the editor has always used. */
function mapMaxScale() {
  return mapCanvasSize / 16;
}

/** Pan/zoom camera over the 256x256 world — this mapping changes on wheel/shift-drag; the canvas element's OWN size is {@link mapCanvasSize}, changed separately by {@link resizeMapCanvas}. */
let mapView = { scale: mapMinScale(), offsetX: 0, offsetY: 0 };
/** {@code {list, patch}} of the currently selected existing patch, or {@code null} — keyed by the
 * patch OBJECT itself, not its index: an index goes stale the moment any OTHER row in the same
 * list is deleted or drag-reordered, silently "selecting" whatever patch happens to have slid into
 * that slot. */
let mapSelected = null;
/** In-progress mouse interaction — {@code null} when idle. See the canvas {@code mousedown} handler for the shapes this takes. */
let mapDrag = null;
/** The not-yet-committed patch a "place" drag is sizing live — drawn as a translucent ghost, pushed into the real array on mouseup. */
let mapPendingPatch = null;
/** World coordinates under the cursor right now (idle hover, not dragging) — drives the coordinate readout and the placement preview ghost. */
let mapHoverWorld = null;
/** Set by the keydown arrow-nudge branch, consumed by the keyup listener right below it — coalesces a whole key-repeat sequence into one undo step, the same way a mouse drag only pushes history on mouseup. */
let mapNudgePending = false;

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
}

/** The ore-item <select> the "Ore" tool (and the ore-flavored stamps) read from — refreshed whenever the item list changes, same reasoning as renderKindSelectOptions. */
function renderMapOreItemSelect() {
  const select = document.getElementById("map-ore-item");
  const previous = select.value;
  const pool = state.items;
  select.innerHTML = pool.map((i) => `<option value="${refValueFor(i)}"></option>`).join("");
  [...select.options].forEach((opt, idx) => (opt.textContent = labelText(pool[idx].label) + originSuffix(pool[idx])));
  const refs = pool.map(refValueFor);
  select.value = refs.includes(previous) ? previous : (refs[0] || "");
}

/** The selected-patch inspector's OWN ore <select> — same item pool as {@link renderMapOreItemSelect}
 * but refreshed separately from {@link renderPatchInspector} (called on every render, including every
 * mousemove while dragging): rebuilding a <select>'s full option list is real DOM work, so it only
 * happens here, when the item list itself actually changed, not on every frame. */
function renderPatchInspectorOreOptions() {
  const select = document.getElementById("patch-ore-item");
  const pool = state.items;
  select.innerHTML = pool.map((i) => `<option value="${refValueFor(i)}"></option>`).join("");
  [...select.options].forEach((opt, idx) => (opt.textContent = labelText(pool[idx].label) + originSuffix(pool[idx])));
}

function updateMapToolFieldVisibility() {
  const tool = document.getElementById("map-tool").value;
  const needsItem = tool === "ore" || (STAMPS[tool] && STAMPS[tool].kind === "ore");
  document.getElementById("map-ore-item-field").classList.toggle("hidden", !needsItem);
}

function itemLabelByPath(ref) {
  const item = resolveItemRef(ref);
  return item ? labelText(item.label) + originSuffix(item) : ref;
}

function oreColorFor(ref) {
  const item = resolveItemRef(ref);
  return item ? item.colorRgb : "#999999";
}

function previewColorForTool(tool) {
  if (tool === "ore") return oreColorFor(document.getElementById("map-ore-item").value);
  return TERRAIN_COLORS[tool] || "#ffffff";
}

function currentRadius() {
  return Math.max(1, Number(document.getElementById("map-radius").value) || 3);
}

function clampCell(v) {
  return Math.max(0, Math.min(MAP_SIZE - 1, v));
}

function isSelectedPatch(list, patch) {
  return !!mapSelected && mapSelected.list === list && mapSelected.patch === patch;
}

function selectPatch(list, patch) {
  mapSelected = { list, patch };
  renderMapEditor();
}

function deselectPatch() {
  if (mapSelected) {
    mapSelected = null;
    renderMapEditor();
  }
}

function deleteSelectedPatch() {
  if (!mapSelected) return;
  const { list, patch } = mapSelected;
  const index = list.indexOf(patch);
  if (index < 0) return; // stale selection (shouldn't happen — every array reassignment nulls mapSelected) — splice(-1, 1) would silently delete the LAST patch instead of doing nothing
  list.splice(index, 1);
  deselectPatch();
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Offsets the copy a few cells over (clamped) so it doesn't land exactly on the original — front
 * of its list, same "just placed" priority as any freshly drawn patch (see commitPendingPatch). */
function duplicateSelectedPatch() {
  if (!mapSelected) return;
  const { list, patch } = mapSelected;
  const copy = { ...patch, cx: clampCell(patch.cx + 5), cy: clampCell(patch.cy + 5) };
  list.unshift(copy);
  mapSelected = { list, patch: copy };
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

/** Exact-value editing for whichever patch is selected — dragging on the canvas is the only other
 * way to move/resize a patch, and there was previously no way at all to type a precise coordinate
 * or reassign an ore patch's item short of deleting it and drawing a fresh one. Cheap on purpose:
 * called on every {@link renderMapEditor} (including every mousemove while idle), so it only ever
 * sets values/toggles visibility — the ore <select>'s own <option> list is rebuilt separately, by
 * {@link renderPatchInspectorOreOptions}, only when the item list itself changes. */
function renderPatchInspector() {
  const panel = document.getElementById("map-patch-inspector");
  if (!mapSelected) {
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  const { list, patch } = mapSelected;
  const isOre = list === mapOrePatches;
  // This runs on every renderMapEditor, including plain mouse hover — writing into a field the
  // user is mid-edit in (before its own "change" commits) would silently overwrite what they just
  // typed. Only fields NOT currently focused get their value replaced.
  const writeIfIdle = (id, value) => {
    const el = document.getElementById(id);
    if (el !== document.activeElement) el.value = value;
  };
  writeIfIdle("patch-cx", patch.cx);
  writeIfIdle("patch-cy", patch.cy);
  writeIfIdle("patch-radius", patch.radius);
  document.getElementById("patch-ore-field").classList.toggle("hidden", !isOre);
  if (isOre) {
    writeIfIdle("patch-ore-item", patch.ore);
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

function clampView() {
  const canvas = document.getElementById("map-canvas");
  const visible = canvas.width / mapView.scale;
  mapView.offsetX = Math.max(0, Math.min(MAP_SIZE - visible, mapView.offsetX));
  mapView.offsetY = Math.max(0, Math.min(MAP_SIZE - visible, mapView.offsetY));
}

function resetView() {
  mapView = { scale: mapMinScale(), offsetX: 0, offsetY: 0 };
}

function zoomAt(px, py, factor) {
  const worldX = mapView.offsetX + px / mapView.scale;
  const worldY = mapView.offsetY + py / mapView.scale;
  mapView.scale = Math.max(mapMinScale(), Math.min(mapMaxScale(), mapView.scale * factor));
  mapView.offsetX = worldX - px / mapView.scale;
  mapView.offsetY = worldY - py / mapView.scale;
  clampView();
}

/** Fills the available panel width instead of a fixed box (see {@link mapCanvasSize}'s own
 * comment) — called whenever the maps tab becomes visible and on window resize while it's active.
 * A no-op if the measured size didn't actually change, so switching back to an unchanged window
 * doesn't reset the camera on every tab click. Resets to "fit whole map" rather than trying to
 * preserve the exact pan/zoom across a resolution change — simpler, and a resize is rare enough
 * that losing the current pan isn't a real cost. */
function resizeMapCanvas() {
  const available = mapCanvasEl.parentElement.parentElement.clientWidth;
  // clientWidth is 0 whenever #maps-form itself is hidden (JSON-view toggle, or this tab isn't the
  // active one at the moment a debounced resize fires) — every caller of this function already
  // means to only measure while the form is visible, so a 0 here means "don't have a real number
  // yet," not "shrink to the floor." Leaving mapCanvasSize alone until a real measurement arrives.
  if (available <= 0) return;
  // -2: .map-canvas-wrap's own 1px border on each side, so the canvas plus its border still fits
  // inside the measured width instead of overflowing it by 2px.
  const size = Math.max(420, Math.min(880, available - 2));
  if (size === mapCanvasSize) return;
  mapCanvasSize = size;
  mapCanvasEl.width = size;
  mapCanvasEl.height = size;
  resetView();
  renderMapEditor();
}

/** Front-to-back hit test: ore patches first (they always visually/logically win over terrain), each array checked index 0 upward (paint-priority order == visual front). {@code onEdge} means "near the boundary" — the caller's cue to resize instead of move. */
function hitTestPatch(worldX, worldY) {
  const edgeTol = 5 / mapView.scale;
  for (const list of [mapOrePatches, mapTerrainPatches]) {
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

function drawMapCanvas() {
  const canvas = document.getElementById("map-canvas");
  const ctx = canvas.getContext("2d");
  ctx.fillStyle = "#3f5a3a";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Cell grid, only once cells are actually spaced out on screen: at mapMinScale() the whole
  // 256-cell map is visible at once, one line per cell would be solid noise (256 of them) for no
  // benefit — the grid exists to help PRECISE placement, which only matters once you're zoomed in.
  if (mapView.scale >= 8) {
    const visible = canvas.width / mapView.scale;
    const fromX = Math.floor(mapView.offsetX);
    const toX = Math.ceil(mapView.offsetX + visible);
    const fromY = Math.floor(mapView.offsetY);
    const toY = Math.ceil(mapView.offsetY + visible);
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
  for (let i = mapTerrainPatches.length - 1; i >= 0; i--) {
    const p = mapTerrainPatches[i];
    drawPatch(p, TERRAIN_COLORS[p.terrain] || "#7a7a76", isSelectedPatch(mapTerrainPatches, p));
  }
  for (let i = mapOrePatches.length - 1; i >= 0; i--) {
    const p = mapOrePatches[i];
    drawPatch(p, oreColorFor(p.ore), isSelectedPatch(mapOrePatches, p));
  }

  if (mapPendingPatch) {
    drawPatch(mapPendingPatch, previewColorForTool(document.getElementById("map-tool").value) + "aa", false);
  } else if (mapHoverWorld && !mapDrag && !hitTestPatch(mapHoverWorld.x, mapHoverWorld.y)) {
    const tool = document.getElementById("map-tool").value;
    if (!STAMPS[tool]) {
      const ghost = { cx: Math.round(mapHoverWorld.x), cy: Math.round(mapHoverWorld.y), radius: currentRadius() };
      drawPatch(ghost, previewColorForTool(tool) + "77", false);
    }
  }
}

function updateMapCoordsReadout(world) {
  document.getElementById("map-coords").textContent = world ? `(${Math.round(world.x)}, ${Math.round(world.y)})` : "";
}

let mapDragRow = null;

function renderMapPatchList() {
  const ul = document.getElementById("map-patch-list");
  ul.innerHTML = "";
  const rows = [
    ...mapOrePatches.map((p, index) => ({
      index, patch: p, list: mapOrePatches, color: oreColorFor(p.ore),
      desc: `${itemLabelByPath(p.ore)} ore @ (${p.cx}, ${p.cy}) r=${p.radius}`,
    })),
    ...mapTerrainPatches.map((p, index) => ({
      index, patch: p, list: mapTerrainPatches, color: TERRAIN_COLORS[p.terrain] || "#7a7a76",
      desc: `${p.terrain} @ (${p.cx}, ${p.cy}) r=${p.radius}`,
    })),
  ];
  for (const row of rows) {
    const li = document.createElement("li");
    li.className = "patch-row" + (isSelectedPatch(row.list, row.patch) ? " selected" : "");
    li.draggable = true;
    li.innerHTML = `<span class="drag-handle">⋮⋮</span><span class="patch-swatch" style="background:${row.color}"></span><span class="patch-desc"></span>`;
    li.querySelector(".patch-desc").textContent = row.desc;
    li.addEventListener("click", () => selectPatch(row.list, row.patch));
    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.innerHTML = icon("close", 12);
    removeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      row.list.splice(row.index, 1);
      // Only clears the selection if THIS row was the one selected — deleting some other row must
      // not silently drop a selection that has nothing to do with it.
      if (mapSelected && mapSelected.patch === row.patch) deselectPatch();
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

function renderMapEditor() {
  drawMapCanvas();
  renderMapPatchList();
  renderPatchInspector();
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
  mapSelected = null;
  markDirty("maps");
  renderMapEditor();
}

function placeStamp(toolKey, centerX, centerY) {
  const stamp = STAMPS[toolKey];
  const radius = Math.max(1, currentRadius() + stamp.radiusDelta);
  if (stamp.kind === "ore") {
    const ore = document.getElementById("map-ore-item").value;
    if (!ore) {
      toast("Add an item on the Items tab first", true);
      return;
    }
    // Front of the list, not the back: paint priority is index order (see the class-level comment
    // on the canvas draw loop), so a patch appended at the end used to lose any overlap against
    // everything already there — the opposite of what drawing on top of something means everywhere
    // else. A stamp's own sub-patches keep their relative order, just all moved to the front.
    const placed = stamp.offsets.map(([dx, dy]) => ({ cx: clampCell(centerX + dx), cy: clampCell(centerY + dy), radius, ore }));
    mapOrePatches.unshift(...placed);
  } else {
    const placed = stamp.offsets.map(([dx, dy]) => ({ cx: clampCell(centerX + dx), cy: clampCell(centerY + dy), radius, terrain: stamp.terrain }));
    mapTerrainPatches.unshift(...placed);
  }
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
}

function commitPendingPatch() {
  const tool = document.getElementById("map-tool").value; // guaranteed "ore"/"WATER"/"ROCK" — stamps commit on mousedown, never reach a "place" drag
  if (tool === "ore") {
    const ore = document.getElementById("map-ore-item").value;
    if (!ore) {
      toast("Add an item on the Items tab first", true);
      return;
    }
    // unshift (front = top priority), not push — see placeStamp's own comment on why. Auto-select
    // the result so the new patch's exact position/radius is immediately visible and nudgeable in
    // the inspector, instead of needing a second click to find what you just drew.
    const patch = { cx: mapPendingPatch.cx, cy: mapPendingPatch.cy, radius: mapPendingPatch.radius, ore };
    mapOrePatches.unshift(patch);
    mapSelected = { list: mapOrePatches, patch };
  } else {
    const patch = { cx: mapPendingPatch.cx, cy: mapPendingPatch.cy, radius: mapPendingPatch.radius, terrain: tool };
    mapTerrainPatches.unshift(patch);
    mapSelected = { list: mapTerrainPatches, patch };
  }
}

document.getElementById("map-tool").addEventListener("change", updateMapToolFieldVisibility);
updateMapToolFieldVisibility();

const mapCanvasEl = document.getElementById("map-canvas");

mapCanvasEl.addEventListener("mousedown", (e) => {
  // mousedown fires BEFORE the browser's default focus-change — if an inspector field is mid-edit
  // (typed, not yet committed via change/blur), selecting a different patch below would leave that
  // typed value sitting in a field that no longer describes what it's about to be applied to; the
  // subsequent blur's "change" event would then silently apply it to the NEWLY selected patch
  // instead. Blurring first forces that commit to land on the patch it was actually typed for.
  const focused = document.activeElement;
  if (focused && focused.closest && focused.closest("#map-patch-inspector")) focused.blur();
  const world = worldFromEvent(e);
  if (e.shiftKey) {
    mapDrag = { mode: "pan", startClientX: e.clientX, startClientY: e.clientY, startOffsetX: mapView.offsetX, startOffsetY: mapView.offsetY };
    return;
  }
  const tool = document.getElementById("map-tool").value;
  if (STAMPS[tool]) {
    placeStamp(tool, Math.round(world.x), Math.round(world.y));
    return;
  }
  const hit = hitTestPatch(world.x, world.y);
  if (hit) {
    selectPatch(hit.list, hit.patch);
    // Keyed by the patch OBJECT (hit.patch), not list+index — a keyboard shortcut (⌘D, Delete) can
    // fire mid-drag and mutate the list (unshift/splice) while the mouse button is still down,
    // which would silently shift every index and make an index-based lookup grab the wrong patch
    // (see mapSelected's own comment for the same reasoning). startRadius/startCenter double as the
    // Escape-cancel rollback value (see the maps keydown handler) as well as the drag math itself —
    // a resize needs the pre-drag radius for the same reason a move needs the pre-drag center: to
    // have something to put back.
    mapDrag = hit.onEdge
        ? { mode: "resize", list: hit.list, patch: hit.patch, center: { x: hit.patch.cx, y: hit.patch.cy }, startRadius: hit.patch.radius }
        : { mode: "move", list: hit.list, patch: hit.patch, startWorld: world, startCenter: { x: hit.patch.cx, y: hit.patch.cy } };
    return;
  }
  deselectPatch();
  const start = { x: clampCell(Math.round(world.x)), y: clampCell(Math.round(world.y)) };
  mapDrag = { mode: "place", startWorld: start };
  mapPendingPatch = { cx: start.x, cy: start.y, radius: currentRadius() };
  renderMapEditor();
});

mapCanvasEl.addEventListener("mousemove", (e) => {
  const world = worldFromEvent(e);
  updateMapCoordsReadout(world);
  if (!mapDrag) {
    mapHoverWorld = world;
    renderMapEditor();
    return;
  }
  if (mapDrag.mode === "pan") {
    mapView.offsetX = mapDrag.startOffsetX - (e.clientX - mapDrag.startClientX) / mapView.scale;
    mapView.offsetY = mapDrag.startOffsetY - (e.clientY - mapDrag.startClientY) / mapView.scale;
    clampView();
  } else if (mapDrag.mode === "place") {
    const dist = Math.round(Math.hypot(world.x - mapDrag.startWorld.x, world.y - mapDrag.startWorld.y));
    mapPendingPatch.radius = dist >= 1 ? dist : currentRadius();
  } else if (mapDrag.mode === "move") {
    mapDrag.patch.cx = clampCell(Math.round(mapDrag.startCenter.x + (world.x - mapDrag.startWorld.x)));
    mapDrag.patch.cy = clampCell(Math.round(mapDrag.startCenter.y + (world.y - mapDrag.startWorld.y)));
  } else if (mapDrag.mode === "resize") {
    mapDrag.patch.radius = Math.max(1, Math.round(Math.hypot(world.x - mapDrag.center.x, world.y - mapDrag.center.y)));
  }
  renderMapEditor();
});

mapCanvasEl.addEventListener("mouseleave", () => {
  mapHoverWorld = null;
  updateMapCoordsReadout(null);
  renderMapEditor();
});

mapCanvasEl.addEventListener("contextmenu", (e) => {
  e.preventDefault();
  const world = worldFromEvent(e);
  const hit = hitTestPatch(world.x, world.y);
  if (!hit) return;
  hit.list.splice(hit.index, 1);
  if (mapSelected && mapSelected.patch === hit.patch) deselectPatch();
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

mapCanvasEl.addEventListener("wheel", (e) => {
  e.preventDefault();
  const rect = mapCanvasEl.getBoundingClientRect();
  const px = (e.clientX - rect.left) / rect.width * mapCanvasEl.width;
  const py = (e.clientY - rect.top) / rect.height * mapCanvasEl.height;
  zoomAt(px, py, e.deltaY < 0 ? 1.2 : 1 / 1.2);
  renderMapEditor();
}, { passive: false });

window.addEventListener("mouseup", () => {
  if (!mapDrag) return;
  const finishedPlacing = mapDrag.mode === "place";
  if (finishedPlacing) {
    commitPendingPatch();
    mapPendingPatch = null;
  }
  markDirty("maps");
  pushMapHistory();
  mapDrag = null;
  renderMapEditor();
});

document.getElementById("map-zoom-reset").addEventListener("click", () => {
  resetView();
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
  if (e.key === "Escape" && (mapDrag || mapPendingPatch)) {
    // A "place" drag never writes into an actual patch object until mouseup (see that handler) —
    // dropping the in-progress state is enough to cancel it. The other three modes mutate real,
    // already-committed state LIVE on every mousemove, so cancelling has to put it back the way it
    // was, not just stop moving it further.
    if (mapDrag && mapDrag.mode === "pan") {
      mapView.offsetX = mapDrag.startOffsetX;
      mapView.offsetY = mapDrag.startOffsetY;
    } else if (mapDrag && mapDrag.mode === "move") {
      mapDrag.patch.cx = mapDrag.startCenter.x;
      mapDrag.patch.cy = mapDrag.startCenter.y;
    } else if (mapDrag && mapDrag.mode === "resize") {
      mapDrag.patch.radius = mapDrag.startRadius;
    }
    mapDrag = null;
    mapPendingPatch = null;
    renderMapEditor();
    return;
  }
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "z" && !typing) {
    e.preventDefault();
    if (e.shiftKey) redoMap(); else undoMap();
    return;
  }
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "d" && !typing) {
    e.preventDefault();
    duplicateSelectedPatch();
    return;
  }
  if ((e.key === "Delete" || e.key === "Backspace") && !typing && mapSelected) {
    e.preventDefault();
    deleteSelectedPatch();
    return;
  }
  if (mapSelected && !typing && (e.key === "ArrowUp" || e.key === "ArrowDown" || e.key === "ArrowLeft" || e.key === "ArrowRight")) {
    e.preventDefault();
    const step = e.shiftKey ? 10 : 1;
    const { patch } = mapSelected;
    if (e.key === "ArrowUp") patch.cy = clampCell(patch.cy - step);
    if (e.key === "ArrowDown") patch.cy = clampCell(patch.cy + step);
    if (e.key === "ArrowLeft") patch.cx = clampCell(patch.cx - step);
    if (e.key === "ArrowRight") patch.cx = clampCell(patch.cx + step);
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
  }
});

document.addEventListener("keyup", (e) => {
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

/* ---- selected-patch inspector: type-in editing instead of drag-only ---- */

document.getElementById("patch-cx").addEventListener("change", (e) => {
  if (!mapSelected) return;
  mapSelected.patch.cx = clampCell(Math.round(Number(e.target.value)) || 0);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-cy").addEventListener("change", (e) => {
  if (!mapSelected) return;
  mapSelected.patch.cy = clampCell(Math.round(Number(e.target.value)) || 0);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-radius").addEventListener("change", (e) => {
  if (!mapSelected) return;
  mapSelected.patch.radius = Math.max(1, Math.round(Number(e.target.value)) || 1);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-ore-item").addEventListener("change", (e) => {
  if (!mapSelected) return;
  mapSelected.patch.ore = e.target.value;
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-duplicate").addEventListener("click", duplicateSelectedPatch);
document.getElementById("patch-delete").addEventListener("click", deleteSelectedPatch);

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
  mapSelected = null;
  mapDrag = null;
  mapPendingPatch = null;
  resetView();
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
        switchToTab(group.kind);
        group.select(entry);
        searchResultsEl.classList.add("hidden");
        globalSearchInput.value = "";
      });
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
