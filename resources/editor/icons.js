"use strict";

/* Minimal inline stroke-icon set (24x24 viewBox, currentColor) — no external icon font/CDN. */
const ICON_PATHS = {
  box: '<path d="M21 8 12 3 3 8v8l9 5 9-5V8Z"/><path d="M3 8l9 5 9-5"/><path d="M12 13v8"/>',
  flask: '<path d="M9 3h6M10 3v5.5L4.5 18a2 2 0 0 0 1.8 3h11.4a2 2 0 0 0 1.8-3L14 8.5V3"/><path d="M7 15h10"/>',
  factory: '<path d="M3 20V9l6 4V9l6 4V6h6v14H3Z"/><path d="M7 20v-4M12 20v-4M17 20v-4"/>',
  image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  trash: '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0-1 14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2L4 6"/>',
  play: '<path d="M6 3v18l15-9L6 3Z"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="1.5" fill="currentColor" stroke="none"/>',
  save: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
  close: '<path d="M18 6 6 18M6 6l12 12"/>',
  check: '<circle cx="12" cy="12" r="9"/><path d="m9 12 2 2 4-4"/>',
  alert: '<circle cx="12" cy="12" r="9"/><path d="M12 8v4"/><path d="M12 16h.01"/>',
  code: '<path d="m8 6-6 6 6 6M16 6l6 6-6 6"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M17 8l-5-5-5 5M12 3v12"/>',
  loader: '<path d="M12 2v4M12 18v4M4.9 4.9l2.9 2.9M16.2 16.2l2.9 2.9M2 12h4M18 12h4M4.9 19.1l2.9-2.9M16.2 7.8l2.9-2.9"/>',
  tag: '<path d="M20.6 12.6 12 21.2 2.8 12A2 2 0 0 1 2.2 10.6L3 4a1 1 0 0 1 1-1l6.6-.8A2 2 0 0 1 12 2.8l8.6 8.6a2 2 0 0 1 0 2.8Z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
};

function icon(name, size = 18) {
  const inner = ICON_PATHS[name] || "";
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-${name}">${inner}</svg>`;
}

/* Tiny inline SVG "sprite" for an item that has no PNG texture — a filled shape in its own color, so item rows/pickers are recognizable at a glance instead of a bare color chip. */
function itemGlyph(colorRgb, shape) {
  const fill = colorRgb || "#888";
  let shapeSvg;
  if (shape === "CIRCLE") {
    shapeSvg = `<circle cx="12" cy="12" r="8" fill="${fill}"/>`;
  } else if (shape === "TRIANGLE") {
    shapeSvg = `<polygon points="12,4 20,19 4,19" fill="${fill}"/>`;
  } else {
    shapeSvg = `<rect x="5" y="5" width="14" height="14" rx="2" fill="${fill}"/>`;
  }
  return `<svg width="100%" height="100%" viewBox="0 0 24 24">${shapeSvg}</svg>`;
}
