package io.github.judge4j.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes a {@link RunResult} as pretty-printed JSON. Timestamps are ISO-8601 strings;
 * unknown fields are ignored on read, so files written by a newer version still load.
 */
public final class RunResultStore {

    /**
     * Writes {@code run} to {@code outputPath}, creating parent directories. Values that cannot be
     * serialized are written as their {@code toString()} instead of failing the write.
     */
    public void write(RunResult run, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Json.mapper().writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), Json.sanitize(run));
    }

    /** Reads a run previously written by {@link #write}. */
    public RunResult read(Path inputPath) throws IOException {
        return Json.mapper().readValue(inputPath.toFile(), RunResult.class);
    }
}
