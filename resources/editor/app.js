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

/** Which layer (see mapLayerVisible) a given #map-tool value would actually draw into — "ore" for
 * the Ore tool and the two ore-flavored stamps, "terrain" for Water/Rock and the two
 * terrain-flavored stamps. Used to refuse placing into a currently-hidden layer (mousedown). */
function toolLayerKind(tool) {
  if (tool === "ore") return "ore";
  if (STAMPS[tool]) return STAMPS[tool].kind;
  return "terrain";
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

/** Pan/zoom camera over the 256x256 world — this mapping changes on wheel/shift-drag; the canvas element's OWN size is {@link mapCanvasWidth}/{@link mapCanvasHeight}, changed separately by {@link resizeMapCanvas}. */
let mapView = { scale: mapMinScale(), offsetX: 0, offsetY: 0 };
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
/** The not-yet-committed patch a "place" drag is sizing live — drawn as a translucent ghost, pushed into the real array on mouseup. */
let mapPendingPatch = null;
/** World coordinates under the cursor right now (idle hover, not dragging) — drives the coordinate readout and the placement preview ghost. */
let mapHoverWorld = null;
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
  // Crosshair reads as "about to draw"; Select isn't drawing anything, so it gets the ordinary
  // pointer instead — a small cue for which mode a freshly-opened map defaults into. Looked up
  // fresh rather than through the module-level mapCanvasEl const: this function's very first call
  // (right below its own definition) runs before that const's declaration line does.
  document.getElementById("map-canvas").style.cursor = tool === "select" ? "default" : "crosshair";
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
  if (STAMPS[tool]) {
    // A stamp is either ore-flavored (cluster/seam) or terrain-flavored (lake/ridge) — same color
    // resolution as its own two kinds, just keyed off the stamp definition instead of the raw tool
    // value directly. Previously fell through to the terrain branch below, which doesn't know
    // "stamp-ore-cluster" from any other unrecognized string and returned a flat white.
    const stamp = STAMPS[tool];
    return stamp.kind === "ore" ? oreColorFor(document.getElementById("map-ore-item").value) : (TERRAIN_COLORS[stamp.terrain] || "#ffffff");
  }
  return TERRAIN_COLORS[tool] || "#ffffff";
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
 * its own list, same "just placed" priority as any freshly drawn patch (see commitPendingPatch).
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
function renderPatchInspector() {
  const panel = document.getElementById("map-patch-inspector");
  const single = document.getElementById("patch-inspector-single");
  const multi = document.getElementById("patch-inspector-multi");
  if (mapSelectedSet.length === 0) {
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  if (mapSelectedSet.length > 1) {
    single.classList.add("hidden");
    multi.classList.remove("hidden");
    document.getElementById("patch-inspector-count").textContent = `${mapSelectedSet.length} patches selected`;
    return;
  }
  single.classList.remove("hidden");
  multi.classList.add("hidden");
  const { list, patch } = mapSelectedSet[0];
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

/** Separate visible extents per axis, not one shared "visible" — the canvas isn't square (see
 * {@link mapCanvasWidth}'s own comment), so how much world-space fits horizontally and vertically
 * are two different numbers now. On the LONGER axis {@code MAP_SIZE - visible} goes negative
 * (more world-space fits than the map actually has); clamping against 0 pins that axis to the
 * map's own origin rather than trying to center the extra space — simple, and "Fit whole map"
 * already guarantees the origin is what you see first. */
function clampView() {
  const canvas = document.getElementById("map-canvas");
  const visibleX = canvas.width / mapView.scale;
  const visibleY = canvas.height / mapView.scale;
  mapView.offsetX = Math.max(0, Math.min(MAP_SIZE - visibleX, mapView.offsetX));
  mapView.offsetY = Math.max(0, Math.min(MAP_SIZE - visibleY, mapView.offsetY));
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

/** Pans (without changing zoom) so the CENTROID of the current selection sits at the canvas
 * center — for finding a patch again after panning/zooming away from it, not for the initial
 * placement (that's what "Fit whole map" and the ghost preview are for). A no-op with nothing
 * selected — bound to a key, not a button, so there's nothing to disable/hide in that case. */
function focusOnSelection() {
  if (mapSelectedSet.length === 0) return;
  const cx = mapSelectedSet.reduce((sum, s) => sum + s.patch.cx, 0) / mapSelectedSet.length;
  const cy = mapSelectedSet.reduce((sum, s) => sum + s.patch.cy, 0) / mapSelectedSet.length;
  mapView.offsetX = cx - mapCanvasWidth / mapView.scale / 2;
  mapView.offsetY = cy - mapCanvasHeight / mapView.scale / 2;
  clampView();
  renderMapEditor();
}

/** Fills the available space of {@code .map-canvas-wrap} — {@code position: absolute; inset: 0}
 * over the ENTIRE stage (see style.css), so this measures the full stage, not "the stage minus a
 * sidebar" — {@code .map-tools} floats on top of the canvas rather than sharing a row with it —
 * instead of a fixed box (see {@link mapCanvasWidth}'s own comment). Called whenever the maps tab
 * becomes visible and on window resize while it's active. A no-op if the measured size didn't
 * actually change, so switching back to an unchanged window doesn't reset the camera on every tab
 * click. Resets to "fit whole map" rather than trying to preserve the exact pan/zoom across a
 * resolution change — simpler, and a resize is rare enough that losing the current pan isn't a
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
  resetView();
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

function drawMapCanvas() {
  const canvas = document.getElementById("map-canvas");
  const ctx = canvas.getContext("2d");
  // Dark neutral gray, not the green this used to be — this is "no terrain assigned" (the base
  // ground layer, not water/rock/an ore patch), and a saturated color read as if it MEANT
  // something, the way the actual ore/terrain tints do. Matches the app's own dark theme instead
  // of standing out from it.
  ctx.fillStyle = "#2b2e35";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // The canvas isn't square (see mapCanvasWidth's own comment) but the map itself always is —
  // at most zoom levels (especially "Fit whole map", which only fits the SHORTER axis) the wider
  // axis shows MORE canvas than there is actual map, with nothing visually marking where the real
  // 256x256 area stops. Clicking in that "extra" strip still hits the canvas and places a patch —
  // clampCell just pulls it back to the map's true edge, which without this shading reads as "my
  // click landed somewhere else entirely" instead of "I clicked outside the map." Darkening that
  // strip and outlining the real boundary makes the clamp an expected edge case, not a mystery.
  {
    const mapTopLeft = worldToScreen(0, 0);
    const mapBottomRight = worldToScreen(MAP_SIZE, MAP_SIZE);
    const x0 = Math.max(0, mapTopLeft.x);
    const y0 = Math.max(0, mapTopLeft.y);
    const x1 = Math.min(canvas.width, mapBottomRight.x);
    const y1 = Math.min(canvas.height, mapBottomRight.y);
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    if (y0 > 0) ctx.fillRect(0, 0, canvas.width, y0); // above the map
    if (y1 < canvas.height) ctx.fillRect(0, y1, canvas.width, canvas.height - y1); // below
    if (x0 > 0) ctx.fillRect(0, y0, x0, y1 - y0); // left of it (excludes the already-shaded corners)
    if (x1 < canvas.width) ctx.fillRect(x1, y0, canvas.width - x1, y1 - y0); // right of it
    if (x0 > 0 || y0 > 0 || x1 < canvas.width || y1 < canvas.height) {
      ctx.strokeStyle = "rgba(255,255,255,0.3)";
      ctx.lineWidth = 1;
      ctx.strokeRect(mapTopLeft.x, mapTopLeft.y, mapBottomRight.x - mapTopLeft.x, mapBottomRight.y - mapTopLeft.y);
    }
  }

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
      drawPatch(p, TERRAIN_COLORS[p.terrain] || "#7a7a76", isSelectedPatch(mapTerrainPatches, p));
    }
  }
  if (mapLayerVisible.ore) {
    for (let i = mapOrePatches.length - 1; i >= 0; i--) {
      const p = mapOrePatches[i];
      drawPatch(p, oreColorFor(p.ore), isSelectedPatch(mapOrePatches, p));
    }
  }

  if (mapPendingPatch) {
    drawPatch(mapPendingPatch, previewColorForTool(document.getElementById("map-tool").value) + "aa", false);
  } else if (mapHoverWorld && !mapDrag && !hitTestPatch(mapHoverWorld.x, mapHoverWorld.y)) {
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
      index, patch: p, list: mapTerrainPatches, color: TERRAIN_COLORS[p.terrain] || "#7a7a76",
      desc: `${p.terrain} @ (${p.cx}, ${p.cy}) r=${p.radius}`,
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
  mapSelectedSet = [];
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

/** Returns whether a patch actually got committed — false for the "no item registered yet" bail
 * (toast only), so the mouseup handler knows not to mark the map dirty or push a no-op history
 * entry for a placement that never happened. */
function commitPendingPatch() {
  const tool = document.getElementById("map-tool").value; // guaranteed "ore"/"WATER"/"ROCK" — stamps commit on mousedown, never reach a "place" drag
  if (tool === "ore") {
    const ore = document.getElementById("map-ore-item").value;
    if (!ore) {
      toast("Add an item on the Items tab first", true);
      return false;
    }
    // unshift (front = top priority), not push — see placeStamp's own comment on why. Auto-select
    // the result so the new patch's exact position/radius is immediately visible and nudgeable in
    // the inspector, instead of needing a second click to find what you just drew.
    const patch = { cx: mapPendingPatch.cx, cy: mapPendingPatch.cy, radius: mapPendingPatch.radius, ore };
    mapOrePatches.unshift(patch);
    mapSelectedSet = [{ list: mapOrePatches, patch }];
  } else {
    const patch = { cx: mapPendingPatch.cx, cy: mapPendingPatch.cy, radius: mapPendingPatch.radius, terrain: tool };
    mapTerrainPatches.unshift(patch);
    mapSelectedSet = [{ list: mapTerrainPatches, patch }];
  }
  return true;
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
  const hit = hitTestPatch(world.x, world.y);
  // Shift+empty-space still pans; Shift+an-actual-patch toggles it in/out of the selection instead
  // (checked below, once hit is known) — the two never conflict, since they're on different targets.
  if (e.shiftKey && !hit) {
    mapDrag = { mode: "pan", startClientX: e.clientX, startClientY: e.clientY, startOffsetX: mapView.offsetX, startOffsetY: mapView.offsetY };
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
    mapDrag = { mode: "rubberband", startWorld: world, currentWorld: world };
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
  const start = { x: clampCell(Math.round(world.x)), y: clampCell(Math.round(world.y)) };
  mapDrag = { mode: "place", startWorld: start };
  mapPendingPatch = { cx: start.x, cy: start.y, radius: currentRadius() };
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
    const dist = Math.round(Math.hypot(world.x - mapDrag.startWorld.x, world.y - mapDrag.startWorld.y));
    mapPendingPatch.radius = dist >= 1 ? dist : currentRadius();
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
  } else if (mapDrag.mode === "rubberband") {
    mapDrag.currentWorld = world;
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
  mapSelectedSet = mapSelectedSet.filter((s) => s.patch !== hit.patch);
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
    const committed = commitPendingPatch();
    mapPendingPatch = null;
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
  } else if (mode === "rubberband") {
    finishRubberBand(mapDrag);
  }
  // "pan" touches no patch data at all — nothing here to mark dirty or push to history.
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

// These four fields only ever show/act when EXACTLY one patch is selected (renderPatchInspector
// hides #patch-inspector-single otherwise), so mapSelectedSet[0] is safe wherever mapSelectedSet
// itself isn't empty; the length check alone guards against a stray change event firing after the
// selection changed to 0 or many between focus and blur.
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
  if (mapSelectedSet.length !== 1) return;
  mapSelectedSet[0].patch.radius = Math.max(1, Math.round(Number(e.target.value)) || 1);
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
});

document.getElementById("patch-ore-item").addEventListener("change", (e) => {
  if (mapSelectedSet.length !== 1) return;
  mapSelectedSet[0].patch.ore = e.target.value;
  markDirty("maps");
  pushMapHistory();
  renderMapEditor();
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
  mapPendingPatch = null;
  // Otherwise a layer hidden on one map stays hidden (with no visible cue why) on the NEXT map you
  // open — the patch count would say "12 ore patches" while the canvas/list quietly show none.
  if (!mapLayerVisible.ore) toggleLayer("ore");
  if (!mapLayerVisible.terrain) toggleLayer("terrain");
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

document.getElementById("maps-picker").addEventListener("change", (e) => {
  // Both this select AND #maps-picker-filter live inside <form id="maps-form"> (so their own
  // fields can sit next to Path/Label in the same floating panel) — wireDirtyTracking listens for
  // "change"/"input" on the WHOLE form, so without stopPropagation, switching maps here (or
  // typing/blurring the filter below) would mark the map you just SWITCHED TO as "unsaved" before
  // you've touched a single field of it.
  e.stopPropagation();
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
