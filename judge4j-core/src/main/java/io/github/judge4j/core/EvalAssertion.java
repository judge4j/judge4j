package io.github.judge4j.core;

/**
 * One assertion under construction, created by {@link EvalAssertions#assertThat(Object)}. The
 * optional {@link #named}, {@link #expecting} and {@link #weight} calls describe it in the report;
 * {@link #satisfies} runs the evaluator and records the result.
 *
 * @param <T> the type of the value being asserted on
 */
public interface EvalAssertion<T> {

    /** Sets the label shown in the report. Defaults to the test case id. */
    EvalAssertion<T> named(String label);

    /** Sets the human-readable expectation shown in the report. Defaults to the test case description. */
    EvalAssertion<T> expecting(String expectation);

    /**
     * Sets this assertion's share of the test case's score, as a percentage in {@code (0, 100]}.
     * Weights of all assertions in one test method may total at most 100; assertions without an
     * explicit weight share the remainder equally.
     *
     * @throws IllegalArgumentException if the weight is out of range or the weights so far exceed 100
     */
    EvalAssertion<T> weight(double weightPercent);

    /**
     * Runs {@code evaluator} on the asserted value now and records the outcome. A failing result
     * does not throw here; the test case is failed once the test method completes, so every
     * assertion in the method gets to run.
     */
    void satisfies(Evaluator<? super T> evaluator);
}
