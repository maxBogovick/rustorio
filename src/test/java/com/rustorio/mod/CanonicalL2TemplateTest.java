package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.rustorio.architecture.BuildOutputs;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Keeps the published starter path on one truth: {@code examples/external-mod-template} is L2
 * (ContentDsl + SimpleCrafter), not a hand-rolled {@code BuildingPrototype}/{@code Codec} demo.
 * {@code initMod} copies that tree, so a regression here teaches both humans and agents the wrong
 * first lesson.
 *
 * <p>Substring checks catch the old SparkGenerator shape; compiling the template against the built
 * {@code rustorio-api} jar catches a renamed/removed DSL method that text search would miss.
 */
class CanonicalL2TemplateTest {

    private static final Path TEMPLATE_JAVA =
            Path.of("examples", "external-mod-template", "src", "main", "java");

    @Test
    void externalModTemplateIsDslSimpleCrafterNotHandBuiltPrototype() {
        String sources = readAllJavaUnder(TEMPLATE_JAVA);

        assertTrue(sources.contains("simpleCrafter("),
                "canonical L2 template must register via simpleCrafter(...)");
        assertTrue(sources.contains("content()"),
                "canonical L2 template must use ctx.content() DSL");
        assertFalse(sources.contains("new BuildingPrototype"),
                "BuildingPrototype belongs to L3 samples, not the starter template");
        assertFalse(sources.contains("new Codec"),
                "custom Codec belongs to L3 samples, not the starter template");
        assertFalse(sources.contains("SparkGenerator"),
                "SparkGenerator was the old L3-shaped template demo — keep it out of the starter");
    }

    @Test
    void externalModTemplateCompilesAgainstPublishedApiJar() throws IOException {
        Path apiJar = BuildOutputs.apiJar();
        List<Path> sources = listJavaFiles(TEMPLATE_JAVA);
        assertFalse(sources.isEmpty(), "no .java under " + TEMPLATE_JAVA);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StringWriter errors = new StringWriter();
        Path out = Files.createTempDirectory("l2-template-classes");
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of(
                    "-d", out.toString(),
                    "-classpath", apiJar.toAbsolutePath().toString(),
                    "--release", "25");
            boolean ok = Boolean.TRUE.equals(compiler.getTask(
                    errors,
                    files,
                    null,
                    options,
                    null,
                    files.getJavaFileObjectsFromPaths(sources)).call());
            if (!ok) {
                fail("canonical L2 template must compile against rustorio-api; javac said:\n" + errors);
            }
        }
    }

    private static List<Path> listJavaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("missing " + root.toAbsolutePath());
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String readAllJavaUnder(Path root) {
        StringBuilder all = new StringBuilder();
        try {
            for (Path p : listJavaFiles(root)) {
                all.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(all.isEmpty(), "no .java under " + root);
        return all.toString();
    }
}
