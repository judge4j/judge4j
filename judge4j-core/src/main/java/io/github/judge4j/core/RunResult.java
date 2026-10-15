package io.github.judge4j.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything recorded by one test class execution: the unit that is written to JSON and rendered
 * as one HTML report.
 *
 * @param runId     unique id of this run
 * @param label     human-readable name shown in the report (the test class name by default)
 * @param timestamp when the run finished
 * @param results   one {@link EvalResult} per test case and variant; immutable
 */
public record RunResult(String runId, String label, Instant timestamp, List<EvalResult> results) {

    public RunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
    }
}
