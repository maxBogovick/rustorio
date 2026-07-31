package com.rustorio.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Owns the single "try it in the game" process the editor's Play button spawns — at most one at a
 * time, killed and replaced on every relaunch.
 *
 * <p>Runs {@code --no-daemon}: with the (default) Gradle daemon, the actual {@code com.graphics.Main}
 * JVM ends up a child of the long-lived background daemon process, not of whatever we spawn here —
 * so destroying our own {@link Process} handle wouldn't close the previous game window at all, it'd
 * just leave it running alongside a brand new one. {@code --no-daemon} keeps the whole chain (this
 * JVM's {@code gradlew} child -> its own forked game JVM) inside a process tree we actually spawned,
 * so {@link ProcessHandle#descendants()} can find and kill every part of it. The tradeoff is a
 * slower relaunch (full Gradle bootstrap every time, no warm daemon) — worth it for a dev loop where
 * "the old window reliably closes" matters more than a couple of saved seconds.
 */
final class GameProcess {

    private static final Path LOG_FILE = Path.of("build", "editor-run.log");
    private static final long STARTING_GRACE_MILLIS = 4000;

    private static Process current;
    private static long startedAtMillis;

    private GameProcess() {
    }

    static synchronized void relaunch() throws IOException {
        stop();
        Files.createDirectories(LOG_FILE.getParent());
        ProcessBuilder builder = new ProcessBuilder(gradlewCommand(), "--no-daemon", "--console=plain", "run");
        builder.directory(EditorPaths.REPO_ROOT.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.to(LOG_FILE.toFile()));
        current = builder.start();
        startedAtMillis = System.currentTimeMillis();
    }

    static synchronized void stop() {
        if (current != null) {
            current.descendants().forEach(ProcessHandle::destroyForcibly);
            current.destroyForcibly();
        }
        current = null;
    }

    static synchronized Status status() {
        if (current == null) {
            return new Status("stopped", null, tailLog());
        }
        if (current.isAlive()) {
            boolean starting = System.currentTimeMillis() - startedAtMillis < STARTING_GRACE_MILLIS;
            return new Status(starting ? "starting" : "running", null, tailLog());
        }
        int exitCode = current.exitValue();
        return new Status(exitCode == 0 ? "stopped" : "crashed", exitCode, tailLog());
    }

    private static String gradlewCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return windows ? "gradlew.bat" : "./gradlew";
    }

    private static String tailLog() {
        if (!Files.isRegularFile(LOG_FILE)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(LOG_FILE);
            int from = Math.max(0, lines.size() - 60);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "";
        }
    }

    record Status(String state, Integer exitCode, String tailLog) {
    }
}
