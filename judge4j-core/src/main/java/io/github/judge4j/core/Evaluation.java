package io.github.judge4j.core;

/**
 * The verdict of one {@link Evaluator} on one value. Construction throws
 * {@link IllegalArgumentException} if the score is {@code NaN} or outside {@code [0, 1]}.
 *
 * @param pass     whether the value satisfied the check
 * @param score    how well it did, from {@code 0.0} (worst) to {@code 1.0} (best); must be finite
 * @param feedback a short human-readable explanation shown in the report; may be {@code null}
 */
public record Evaluation(boolean pass, double score, String feedback) {

    public Evaluation {
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0, was " + score);
        }
    }
}
