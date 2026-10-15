package io.github.judge4j.core;

/**
 * Low-level recorder for tests that do not use {@code @EvalTest}. Add
 * {@code @ExtendWith(EvalRecorder.class)} to a plain JUnit test class and declare an
 * {@code EvalContext} parameter; every recorded result lands in the same JSON/HTML report.
 */
public interface EvalContext {

    /** Pass mark used by the score-only {@code record} overloads. */
    double PASS_THRESHOLD = 0.5;

    /** Variant label used when none is given. */
    String DEFAULT_VARIANT = "default";

    /**
     * Records a result under {@link #DEFAULT_VARIANT}; it passes when {@code score >= PASS_THRESHOLD}.
     *
     * @throws IllegalArgumentException if {@code score} is outside {@code [0, 1]}
     */
    void record(String testCaseId, Object output, double score);

    /** As {@link #record(String, Object, double)}, for the named variant. */
    void record(String testCaseId, Object output, double score, String variantLabel);

    /** Records a fully specified result. */
    void record(EvalResult result);

    /** Records a failed result carrying {@code error}'s text, with score 0. */
    void recordError(String testCaseId, Throwable error);
}
