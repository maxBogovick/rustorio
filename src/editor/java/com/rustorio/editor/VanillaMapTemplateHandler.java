package com.rustorio.editor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rustorio.api.content.model.OrePatch;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.api.content.model.TerrainPatch;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 * {@code GET /api/vanilla-map-template} — the built-in map's own patches ({@link
 * PatchOreLayout#patches()}/{@link PatchOreLayout#terrainPatches()}), in the exact shape a {@code
 * content/maps/*.json} file uses. Backs the "New map -> Import vanilla layout" option: rather than
 * starting from a blank canvas, a mod author can pull in the real game's own ore/terrain layout and
 * tweak it, instead of re-placing sixteen patches by hand from scratch.
 */
final class VanillaMapTemplateHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
            return;
        }
        ObjectNode body = EditorJson.MAPPER.createObjectNode();
        ArrayNode orePatches = body.putArray("orePatches");
        for (OrePatch patch : PatchOreLayout.patches()) {
            ObjectNode node = orePatches.addObject();
            node.put("cx", patch.cx());
            node.put("cy", patch.cy());
            node.put("radius", patch.radius());
            node.put("ore", patch.ore().id().path());
        }
        ArrayNode terrainPatches = body.putArray("terrainPatches");
        for (TerrainPatch patch : PatchOreLayout.terrainPatches()) {
            ObjectNode node = terrainPatches.addObject();
            node.put("cx", patch.cx());
            node.put("cy", patch.cy());
            node.put("radius", patch.radius());
            // The item's own path, exactly like an ore patch above — terrain is a content
            // reference now, not an enum constant's name. Bare, because this template is a vanilla
            // map being handed to whichever mod is about to save it; the editor requalifies a
            // cross-mod reference on its way into the file (see requalifyRef in app.js).
            node.put("terrain", patch.terrain().id().path());
        }
        EditorHttp.sendJson(exchange, 200, body.toString());
    }
}
