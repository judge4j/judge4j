package io.github.judge4j.core;

import java.io.IOException;
import java.nio.file.Path;

/** Turns recorded runs into a report file. {@link HtmlReportGenerator} is the bundled implementation. */
public interface ReportGenerator {

    /** Writes a report for {@code run} to {@code outputPath}, creating parent directories. */
    void generate(RunResult run, Path outputPath) throws IOException;

    /**
     * Writes one report that puts {@code baseline} and {@code candidate} side by side, with every
     * variant of each as its own column prefixed by the run label. It shows both runs; it does not
     * compute a pass/fail difference.
     */
    void generateDiff(RunResult baseline, RunResult candidate, Path outputPath) throws IOException;
}
