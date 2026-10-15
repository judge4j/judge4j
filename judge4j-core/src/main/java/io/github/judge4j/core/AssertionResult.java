package io.github.judge4j.core;

import java.util.Objects;

/**
 * The recorded outcome of one assertion inside an {@link EvalResult}.
 *
 * @param label       the assertion's name
 * @param expectation what the assertion expected, in words; may be {@code null}
 * @param input       the value that was evaluated
 * @param weight      this assertion's share of the case score, as a percentage
 * @param pass        whether the assertion passed
 * @param score       the evaluator's score, {@code 0.0} to {@code 1.0}
 * @param feedback    the evaluator's explanation; may be {@code null}
 */
public record AssertionResult(
        String label,
        String expectation,
        Object input,
        double weight,
        boolean pass,
        double score,
        String feedback) {

    public AssertionResult {
        Objects.requireNonNull(label, "label must not be null");
    }
}
