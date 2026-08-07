package com.webminer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulls a handful of TOP-LEVEL fields out of a JSON response body — the one class in this whole
 * feature allowed to import Jackson outside {@code com.rustorio.persistence}/{@code
 * com.rustorio.mod} (see {@code PackageBoundaryRulesTest}), which is exactly why {@code
 * com.rustorio.domain.building.Interpreter} calls this from {@code
 * com.graphics.render.InspectionPanelLayout} instead of doing its own parsing: that class's own
 * javadoc explains why it genuinely cannot.
 *
 * <p>No dotted/nested paths in this first cut — {@code fieldNames} are matched against the JSON
 * object's own top-level keys only. Best-effort throughout: a field the response doesn't have, or a
 * body that isn't valid JSON at all, shows up as a value in the result rather than throwing —
 * {@code InspectionPanelLayout} has nowhere sensible to catch an exception from inside building a
 * list of display lines.
 */
public final class JsonFieldExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonFieldExtractor() {
    }

    /** One entry per {@code fieldNames}, in the SAME order — {@code "(missing)"}/{@code "(invalid JSON)"} stand in for a field this body doesn't have or a body that couldn't be parsed at all. */
    public static Map<String, String> extract(String json, List<String> fieldNames) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            for (String field : fieldNames) {
                result.put(field, "(invalid JSON)");
            }
            return result;
        }
        for (String field : fieldNames) {
            JsonNode value = root.get(field);
            result.put(field, value == null ? "(missing)" : value.asText());
        }
        return result;
    }
}
