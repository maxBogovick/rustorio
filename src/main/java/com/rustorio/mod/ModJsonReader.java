package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustorio.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads one mod's {@code mod.json} into a {@link ModDescriptor}. Parses through a raw {@link
 * JsonNode} tree rather than letting Jackson bind straight to a target type — every failure needs
 * to name the exact file and field at fault, which needs full control over the error message that
 * Jackson's own binding exceptions don't give by default.
 */
public final class ModJsonReader {

    private final ObjectMapper mapper = new ObjectMapper();

    /** @throws ModLoadException if {@code modJsonFile} can't be read or doesn't match the expected shape. */
    public ModDescriptor read(Path modJsonFile) {
        JsonNode root = parseTree(modJsonFile);
        ModId id = parseId(root, modJsonFile);
        SemVer version = parseVersion(root, modJsonFile);
        @Nullable String entryPoint = optionalText(root, "entryPoint");
        List<ModDependency> dependencies = parseDependencies(root, modJsonFile);
        Map<ContentId, ContentId> renames = parseRenames(root, modJsonFile);
        ModMetadata metadata = parseMetadata(root, id);
        @Nullable SemVer minEngineVersion = parseMinEngineVersion(root, modJsonFile);
        return new ModDescriptor(id, version, entryPoint, dependencies, renames, metadata, minEngineVersion);
    }

    private JsonNode parseTree(Path modJsonFile) {
        try {
            return mapper.readTree(modJsonFile.toFile());
        } catch (IOException e) {
            throw new ModLoadException(modJsonFile + ": could not read mod.json (" + e.getMessage() + ")", e);
        }
    }

    private static ModId parseId(JsonNode root, Path modJsonFile) {
        String idText = requireText(root, "id", modJsonFile);
        try {
            return new ModId(idText);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(modJsonFile + ": field 'id' " + e.getMessage());
        }
    }

    private static SemVer parseVersion(JsonNode root, Path modJsonFile) {
        String versionText = requireText(root, "version", modJsonFile);
        try {
            return SemVer.parse(versionText);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(modJsonFile + ": field 'version' " + e.getMessage());
        }
    }

    private static List<ModDependency> parseDependencies(JsonNode root, Path modJsonFile) {
        JsonNode depsNode = root.get("dependencies");
        if (depsNode == null) {
            return List.of();
        }
        if (!depsNode.isArray()) {
            throw new ModLoadException(modJsonFile + ": field 'dependencies' must be an array");
        }
        List<ModDependency> dependencies = new ArrayList<>();
        for (JsonNode dep : depsNode) {
            String depIdText = requireText(dep, "modId", modJsonFile);
            ModId depId;
            try {
                depId = new ModId(depIdText);
            } catch (IllegalArgumentException e) {
                throw new ModLoadException(modJsonFile + ": dependency field 'modId' " + e.getMessage());
            }
            String rangeText = optionalText(dep, "range");
            VersionRange range;
            try {
                range = VersionRange.parse(rangeText == null ? "*" : rangeText);
            } catch (IllegalArgumentException e) {
                throw new ModLoadException(modJsonFile + ": dependency on '" + depIdText + "': " + e.getMessage());
            }
            dependencies.add(new ModDependency(depId, range));
        }
        return dependencies;
    }

    /** Reads the optional human-facing fields — see {@link ModMetadata} for why none of them are required. */
    private static ModMetadata parseMetadata(JsonNode root, ModId id) {
        String name = optionalText(root, "name");
        return new ModMetadata(
                name == null ? id.value() : name,
                optionalText(root, "description"),
                parseAuthors(root),
                optionalText(root, "homepage"),
                optionalText(root, "license"));
    }

    /** {@code "authors"} is an array; a single {@code "author"} string is accepted as the one-author shorthand. */
    private static List<String> parseAuthors(JsonNode root) {
        JsonNode authors = root.get("authors");
        if (authors != null && authors.isArray()) {
            List<String> names = new ArrayList<>();
            for (JsonNode entry : authors) {
                if (entry.isTextual()) {
                    names.add(entry.asText());
                }
            }
            return names;
        }
        String single = optionalText(root, "author");
        return single == null ? List.of() : List.of(single);
    }

    private static @Nullable SemVer parseMinEngineVersion(JsonNode root, Path modJsonFile) {
        String text = optionalText(root, "minEngineVersion");
        if (text == null) {
            return null;
        }
        try {
            return SemVer.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(modJsonFile + ": field 'minEngineVersion' " + e.getMessage());
        }
    }

    /**
     * Reads the optional {@code "renames"} object — {@code {"old:id": "new:id"}} — that a mod uses
     * to declare a building prototype it renamed since a previous release of itself. Absent means
     * "renamed nothing", not an error.
     *
     * <p>Both sides are parsed as a full {@link ContentId} rather than a bare path under this
     * mod's own namespace: a mod is allowed to adopt content another mod dropped, and forcing the
     * namespace here would make that undeclarable. A rename to itself is refused — it can only be
     * a copy-paste slip, and silently keeping it would make the merged map's conflict check report
     * a confusing collision later.
     */
    private static Map<ContentId, ContentId> parseRenames(JsonNode root, Path modJsonFile) {
        JsonNode renamesNode = root.get("renames");
        if (renamesNode == null || renamesNode.isNull()) {
            return Map.of();
        }
        if (!renamesNode.isObject()) {
            throw new ModLoadException(modJsonFile + ": field 'renames' must be an object of \"old:id\": \"new:id\"");
        }
        Map<ContentId, ContentId> renames = new LinkedHashMap<>();
        Iterator<String> fieldNames = renamesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String oldIdText = fieldNames.next();
            JsonNode newIdNode = renamesNode.get(oldIdText);
            if (!newIdNode.isTextual()) {
                throw new ModLoadException(
                        modJsonFile + ": rename target for '" + oldIdText + "' must be a \"namespace:path\" string");
            }
            ContentId oldId = parseContentId(oldIdText, modJsonFile, "rename key");
            ContentId newId = parseContentId(newIdNode.asText(), modJsonFile, "rename target for '" + oldIdText + "'");
            if (oldId.equals(newId)) {
                throw new ModLoadException(modJsonFile + ": rename of '" + oldId + "' points at itself");
            }
            renames.put(oldId, newId);
        }
        return renames;
    }

    private static ContentId parseContentId(String text, Path modJsonFile, String role) {
        try {
            return ContentId.of(text);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(modJsonFile + ": " + role + " is not a valid content id — " + e.getMessage());
        }
    }

    private static String requireText(JsonNode node, String field, Path modJsonFile) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new ModLoadException(modJsonFile + ": missing or non-string required field '" + field + "'");
        }
        return value;
    }

    private static @Nullable String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }
}
