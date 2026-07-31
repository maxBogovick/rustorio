package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;

/**
 * One building's position and captured state — a save file's building row, matching the format
 * ADR-4 asks for: {@code prototypeId} names the exact governing {@code BuildingPrototype} (vanilla
 * or modded — this is the only source of truth for "what kind is this," not inferred from the
 * shape of {@code state}), and {@code state} is whatever that prototype's own {@code Codec} wrote
 * — a plain JSON-shaped value ({@code Map}/{@code List}/{@code String}/numbers/{@code Boolean}),
 * Jackson serializes it directly with no custom (de)serializer needed. No separate {@code
 * speedLevel} field anymore — the archetypes that track it fold it into their own {@code state}
 * (see {@code MinerState} and friends).
 */
record PlacedBuilding(int x, int y, ContentId prototypeId, Object state) {
}
