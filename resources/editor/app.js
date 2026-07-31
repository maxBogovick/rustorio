"use strict";

/* ============================================================
   fetch wrapper
   ============================================================ */

async function api(method, path, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
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

const state = {
  items: [],
  recipes: [],
  buildings: [],
  textures: { vanilla: [], mod: [] },
  selected: { items: null, recipes: null, buildings: null },
  dirty: { items: false, recipes: false, buildings: false },
  jsonMode: { items: false, recipes: false, buildings: false },
};

let recipeIngredients = [];
let recipeOutput = null;
let buildingCostItem = null;

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

async function loadAll() {
  const [items, recipes, buildings, textures] = await Promise.all([
    api("GET", "/api/items"),
    api("GET", "/api/recipes"),
    api("GET", "/api/buildings"),
    api("GET", "/api/textures"),
  ]);
  state.items = items;
  state.recipes = recipes;
  state.buildings = buildings;
  state.textures = textures;
  renderItemsList();
  renderRecipesList();
  renderBuildingsList();
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
}

/* ============================================================
   static chrome: icons, nav, tabs
   ============================================================ */

document.getElementById("nav-items").innerHTML = `${icon("box")}<span>Items</span><span class="count" id="count-items">0</span>`;
document.getElementById("nav-recipes").innerHTML = `${icon("flask")}<span>Recipes</span><span class="count" id="count-recipes">0</span>`;
document.getElementById("nav-buildings").innerHTML = `${icon("factory")}<span>Buildings</span><span class="count" id="count-buildings">0</span>`;
document.getElementById("nav-textures").innerHTML = `${icon("image")}<span>Textures</span><span class="count" id="count-textures">0</span>`;
document.getElementById("topbar-search-icon").innerHTML = icon("search", 15);
document.querySelectorAll(".mini-search-icon").forEach((el) => (el.innerHTML = icon("search", 14)));
document.querySelectorAll('[data-new]').forEach((btn) => (btn.innerHTML = icon("plus", 16)));
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
  });
});

function activeTab() {
  return document.querySelector(".nav-item.active").dataset.tab;
}

function switchToTab(tab) {
  document.querySelector(`.nav-item[data-tab="${tab}"]`).click();
}

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

function renderItemOptionsInto(container, selectedPaths) {
  container.innerHTML = "";
  for (const item of state.items) {
    const opt = document.createElement("div");
    opt.className = "option" + (selectedPaths.includes(item.path) ? " picked" : "");
    opt.dataset.path = item.path;
    const thumb = document.createElement("span");
    thumb.className = "thumb";
    thumb.innerHTML = itemGlyph(item.colorRgb, item.shape);
    opt.appendChild(thumb);
    opt.appendChild(document.createTextNode(labelText(item.label)));
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
    key: item.path,
    title: label,
    sub: `rustorio:${item.path}`,
    search: `${label} ${item.path}`,
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
  document.getElementById("items-form-title").textContent = `Edit "${item.path}"`;
  formError("items", "");
  state.selected.items = item.path;
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
  try {
    if (state.selected.items) {
      await api("PUT", `/api/items/${state.selected.items}`, body);
    } else {
      await api("POST", "/api/items", body);
    }
    formError("items", "");
    toast(`Saved "${body.path}"`);
    await loadAll();
    selectItem(body);
  } catch (err) {
    formError("items", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="items"]').addEventListener("click", async () => {
  const key = state.selected.items;
  if (!key) return;
  if (!(await confirmModal("Delete item?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/items/${key}`);
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

function recipeRowInfo(recipe) {
  const outputItem = state.items.find((i) => i.path === recipe.output);
  return {
    key: recipe.file,
    title: recipe.file,
    sub: `${recipe.ingredients.join(" + ")} → ${recipe.output}`,
    search: `${recipe.file} ${recipe.ingredients.join(" ")} ${recipe.output}`,
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
    const item = state.items.find((i) => i.path === path);
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.innerHTML = `<span class="chip-thumb">${item ? itemGlyph(item.colorRgb, item.shape) : ""}</span>`;
    chip.appendChild(document.createTextNode(item ? labelText(item.label) : path));
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
  document.getElementById("recipes-form-title").textContent = `Edit "${recipe.file}"`;
  formError("recipes", "");
  state.selected.recipes = recipe.file;
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
  f.kind.value = body.kind || "FURNACE";
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
  try {
    if (state.selected.recipes) {
      await api("PUT", `/api/recipes/${state.selected.recipes}`, body);
    } else {
      await api("POST", `/api/recipes?file=${encodeURIComponent(fileName)}`, body);
    }
    formError("recipes", "");
    toast(`Saved "${fileName}"`);
    await loadAll();
    selectRecipe({ ...body, file: fileName });
  } catch (err) {
    formError("recipes", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="recipes"]').addEventListener("click", async () => {
  const key = state.selected.recipes;
  if (!key) return;
  if (!(await confirmModal("Delete recipe?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/recipes/${key}`);
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
    key: b.path,
    title: label,
    sub: `${b.archetype} · rustorio:${b.path}`,
    search: `${label} ${b.path} ${b.archetype}`,
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
  document.getElementById("buildings-form-title").textContent = `Edit "${b.path}"`;
  formError("buildings", "");
  state.selected.buildings = b.path;
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
  return {
    path: f.path.value.trim(),
    label,
    archetype: f.archetype.value,
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
  renderBuildingTexturePicker();
  updateBuildingGlyphPreview();
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
  try {
    if (state.selected.buildings) {
      await api("PUT", `/api/buildings/${state.selected.buildings}`, body);
    } else {
      await api("POST", "/api/buildings", body);
    }
    formError("buildings", "");
    toast(`Saved "${body.path}"`);
    await loadAll();
    selectBuilding(body);
  } catch (err) {
    formError("buildings", err.message);
    toast(err.message, true);
  }
});

document.querySelector('[data-delete="buildings"]').addEventListener("click", async () => {
  const key = state.selected.buildings;
  if (!key) return;
  if (!(await confirmModal("Delete building?", `"${key}" will be removed permanently.`))) return;
  try {
    await api("DELETE", `/api/buildings/${key}`);
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

function formError(kind, message) {
  document.getElementById(`${kind}-form-error`).textContent = message || "";
}

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
    const res = await fetch(`/api/textures/${encodeURIComponent(name)}`, {
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

Object.entries({ items: "filter-items", recipes: "filter-recipes", buildings: "filter-buildings" }).forEach(([kind, id]) => {
  document.getElementById(id).addEventListener("input", () => {
    if (kind === "items") renderItemsList();
    if (kind === "recipes") renderRecipesList();
    if (kind === "buildings") renderBuildingsList();
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
    document.getElementById(`${tab}-form`).requestSubmit();
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
    toast("Content is valid. Launching the game (first boot with --no-daemon can take a while)…");
    await api("POST", "/api/relaunch");
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

loadAll()
  .catch((err) => toast(`Failed to load content: ${err.message}`, true))
  .finally(() => document.getElementById("loading-overlay").classList.add("hidden"));
pollStatus();
