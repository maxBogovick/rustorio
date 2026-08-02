package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.List;

/**
 * A map a mod author hand-placed through the content editor's canvas — pure data, the {@link
 * OrePatch}/{@link TerrainPatch} lists {@link AuthoredOreLayout} rasterizes into a live map. Mirrors
 * {@link RecipeKind}: an identity plus the data a registered content id needs, nothing behavioral of
 * its own (that's {@link AuthoredOreLayout}'s job, the same split {@code BuildingPrototype} and
 * {@code Building} already use).
 *
 * <p>No width/height field: unlike {@link RandomOreLayout}, an authored map is always {@value
 * PatchOreLayout#STANDARD_WIDTH}x{@value PatchOreLayout#STANDARD_HEIGHT} — the same fixed size
 * {@link PatchOreLayout} uses (owner decision: a variable-size authored map is a real follow-up, not
 * something this first version needs).
 */
public record AuthoredMap(ContentId id, List<OrePatch> orePatches, List<TerrainPatch> terrainPatches) {

    public AuthoredMap {
        orePatches = List.copyOf(orePatches);
        terrainPatches = List.copyOf(terrainPatches);
    }
}
