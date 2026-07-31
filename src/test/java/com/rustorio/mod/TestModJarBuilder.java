package com.rustorio.mod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles Java source in-process and packages the result into a real {@code .jar} — used by tests
 * that need to prove the mod loader works against an ACTUAL jar file (E7-04, E7-09), not a fixture
 * of already-compiled {@code .class} files or a directory pretending to be one. Deliberately does
 * NOT touch {@code build.gradle}/add a Gradle source set: {@link javax.tools.JavaCompiler} (the JDK
 * itself, always on the test classpath) and {@link JarOutputStream} are enough to build a
 * self-contained fixture at test time.
 */
final class TestModJarBuilder {

    private TestModJarBuilder() {
    }

    /**
     * @param sourcesByClassName fully-qualified class name → complete {@code .java} source text
     * @param serviceRegistrations {@code META-INF/services} file name (the service interface's
     *                              fully-qualified name) → file content (the provider class name)
     */
    static void build(Path jarFile, Map<String, String> sourcesByClassName, Map<String, String> serviceRegistrations) {
        try {
            Path work = Files.createTempDirectory("modjar-build");
            Path sourcesDir = work.resolve("src");
            Path classesDir = work.resolve("classes");
            Files.createDirectories(sourcesDir);
            Files.createDirectories(classesDir);

            List<Path> sourceFiles = new ArrayList<>();
            for (Map.Entry<String, String> entry : sourcesByClassName.entrySet()) {
                Path sourceFile = sourcesDir.resolve(entry.getKey().replace('.', '/') + ".java");
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, entry.getValue());
                sourceFiles.add(sourceFile);
            }

            compile(sourceFiles, classesDir);
            packageJar(jarFile, classesDir, serviceRegistrations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void compile(List<Path> sourceFiles, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // The mod source needs to see com.rustorio.api.mod/com.rustorio.domain/etc. — the same
        // classpath this test JVM itself was launched with already has every one of those.
        String classpath = System.getProperty("java.class.path");
        List<String> options = List.of("-d", classesDir.toString(), "-classpath", classpath);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            boolean ok = compiler.getTask(null, fileManager, null, options, null, units).call();
            if (!ok) {
                throw new IllegalStateException("in-process compilation of the test mod fixture failed");
            }
        }
    }

    private static void packageJar(Path jarFile, Path classesDir, Map<String, String> serviceRegistrations) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                List<Path> classFiles = walk.filter(Files::isRegularFile).toList();
                for (Path classFile : classFiles) {
                    String entryName = classesDir.relativize(classFile).toString().replace('\\', '/');
                    addEntry(jar, entryName, Files.readAllBytes(classFile));
                }
            }
            for (Map.Entry<String, String> entry : serviceRegistrations.entrySet()) {
                String entryName = "META-INF/services/" + entry.getKey();
                addEntry(jar, entryName, entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void addEntry(JarOutputStream jar, String name, byte[] content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }
}
