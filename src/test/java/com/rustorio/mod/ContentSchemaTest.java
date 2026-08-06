package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The published schemas under {@code docs/schemas} are the only field-by-field reference a modder
 * has, and a reference that drifts from the code is worse than none: it sends people looking for a
 * field that no longer exists. This test holds the two together from both sides — every field the
 * shipped content actually uses must be in its schema, and every field a schema calls required must
 * really be present in that content.
 *
 * <p>Deliberately not a full JSON Schema validator: this project adds no dependencies, and a
 * hand-rolled one would be a large thing to trust. What is checked here is the failure that
 * actually happens — a field appearing, disappearing or being renamed in the loader while the
 * schema keeps describing the old shape.
 */
class ContentSchemaTest {

    private static final Path SCHEMAS = Path.of("docs", "schemas");
    /** Content directory name to the schema that describes one file in it. */
    private static final Map<String, String> SCHEMA_BY_DIRECTORY = Map.of(
            "items", "item", "fluids", "fluid", "recipes", "recipe", "buildings", "building",
            "kinds", "kind", "maps", "map", "techs", "tech");
    /** Every mod shipped in this repository: the vanilla game, the sandbox, and the example a modder is pointed at. */
    private static final List<Path> SHIPPED_MOD_ROOTS =
            List.of(Path.of("resources", "mods"), Path.of("examples"));

    /** The vanilla mod alone ships more than this; the bound only has to be high enough to catch a traversal that finds nothing. */
    private static final int EXPECTED_MINIMUM_CONTENT_FILES = 30;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyShippedContentFileOnlyUsesFieldsItsSchemaDeclares() {
        List<String> problems = new ArrayList<>();
        forEachContentFile((directoryName, file) -> {
            JsonNode schema = readSchema(SCHEMA_BY_DIRECTORY.get(directoryName));
            Set<String> declared = fieldNames(schema.get("properties"));
            for (String used : fieldNames(readJson(file))) {
                if (!declared.contains(used)) {
                    problems.add(file + ": field '" + used + "' is not in "
                            + SCHEMA_BY_DIRECTORY.get(directoryName) + ".schema.json");
                }
            }
        });
        assertTrue(problems.isEmpty(),
                "схема должна описывать каждое поле, которым пользуется поставляемый контент:\n" + String.join("\n", problems));
    }

    @Test
    void everyShippedContentFileCarriesTheFieldsItsSchemaCallsRequired() {
        List<String> problems = new ArrayList<>();
        forEachContentFile((directoryName, file) -> {
            JsonNode schema = readSchema(SCHEMA_BY_DIRECTORY.get(directoryName));
            JsonNode content = readJson(file);
            for (JsonNode required : schema.get("required")) {
                if (!content.has(required.asText())) {
                    problems.add(file + ": missing required field '" + required.asText() + "'");
                }
            }
        });
        assertTrue(problems.isEmpty(),
                "схема не должна требовать поля, которых нет в поставляемом контенте:\n" + String.join("\n", problems));
    }

    @Test
    void everyModManifestMatchesTheModSchema() {
        List<String> problems = new ArrayList<>();
        JsonNode schema = readSchema("mod");
        Set<String> declared = fieldNames(schema.get("properties"));
        for (Path root : SHIPPED_MOD_ROOTS) {
            for (Path modDir : subdirectories(root)) {
                Path manifest = modDir.resolve("mod.json");
                if (!Files.isRegularFile(manifest)) {
                    continue;
                }
                JsonNode content = readJson(manifest);
                for (String used : fieldNames(content)) {
                    if (!declared.contains(used)) {
                        problems.add(manifest + ": field '" + used + "' is not in mod.schema.json");
                    }
                }
                for (JsonNode required : schema.get("required")) {
                    if (!content.has(required.asText())) {
                        problems.add(manifest + ": missing required field '" + required.asText() + "'");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "mod.json и его схема разошлись:\n" + String.join("\n", problems));
    }

    private interface ContentFileVisitor {
        void visit(String directoryName, Path file);
    }

    /** Every shipped content file, paired with the name of the directory that says which schema describes it. */
    private void forEachContentFile(ContentFileVisitor visitor) {
        int visited = 0;
        for (Path root : SHIPPED_MOD_ROOTS) {
            for (Path modDir : subdirectories(root)) {
                // Sorted, so a failure list reads the same way twice.
                for (String directoryName : new TreeSet<>(SCHEMA_BY_DIRECTORY.keySet())) {
                    Path contentDir = modDir.resolve("content").resolve(directoryName);
                    if (!Files.isDirectory(contentDir)) {
                        continue;
                    }
                    for (Path file : jsonFiles(contentDir)) {
                        visitor.visit(directoryName, file);
                        visited++;
                    }
                }
            }
        }
        // A traversal that silently finds nothing would make both tests above pass while checking
        // nothing at all — the single most likely way for this guard to rot.
        assertTrue(visited >= EXPECTED_MINIMUM_CONTENT_FILES,
                "обход нашёл всего " + visited + " файлов контента — столько их быть не может, "
                        + "значит сломан сам обход, а не контент");
    }

    private static List<Path> subdirectories(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> jsonFiles(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode readSchema(String name) {
        return readJson(SCHEMAS.resolve(name + ".schema.json"));
    }

    private JsonNode readJson(Path file) {
        try {
            return mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("не читается " + file, e);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }
}
