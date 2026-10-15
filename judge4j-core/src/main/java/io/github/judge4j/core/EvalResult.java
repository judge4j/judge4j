package io.github.judge4j.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The outcome of one test case run against one variant: one cell of the comparison report.
 *
 * @param testCaseId          {@code Class#method:caseId} for evaluations; free-form for hand-recorded results
 * @param testCaseDescription the case description from the dataset; may be {@code null}
 * @param inputs              the case's input variables
 * @param variantLabel        the variant this ran against
 * @param output              the value under test (the value passed to the first assertion)
 * @param score               weighted share of passing assertions, {@code 0.0} to {@code 1.0}
 * @param pass                {@code true} only if every assertion passed and nothing threw
 * @param latencyMs           wall time of the test method excluding assertion evaluation (so judge and
 *                            embedding calls are not counted), in milliseconds
 * @param error               the exception text if the test method threw; otherwise {@code null}
 * @param assertions          the individual assertion outcomes; empty for hand-recorded results
 */
public record EvalResult(
        String testCaseId,
        String testCaseDescription,
        Map<String, Object> inputs,
        String variantLabel,
        Object output,
        double score,
        boolean pass,
        long latencyMs,
        String error,
        List<AssertionResult> assertions) {

    public EvalResult {
        Objects.requireNonNull(testCaseId, "testCaseId must not be null");
        Objects.requireNonNull(variantLabel, "variantLabel must not be null");
        inputs = inputs == null ? Map.of() : inputs;
        assertions = List.copyOf(Objects.requireNonNullElseGet(assertions, List::of));
    }
}
