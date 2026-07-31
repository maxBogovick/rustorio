package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        return new ModDescriptor(id, version, entryPoint, dependencies);
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
