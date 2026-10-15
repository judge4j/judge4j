package io.github.judge4j.core;

/**
 * A check applied to one value, such as a model's output. Implementations are plain functions, so
 * a lambda is enough for a custom check:
 *
 * <pre>{@code
 * Evaluator<String> notEmpty = actual -> actual.isBlank()
 *         ? new Evaluation(false, 0.0, "empty")
 *         : new Evaluation(true, 1.0, "ok");
 * }</pre>
 *
 * @param <T> the type of value being checked
 */
@FunctionalInterface
public interface Evaluator<T> {

    /** Checks {@code actual} and reports the verdict. */
    Evaluation evaluate(T actual);

    /**
     * An evaluator that passes exactly when this one fails: the pass flag is inverted and the score
     * becomes {@code 1 - score}. The feedback says what the original check found, e.g.
     * {@code "Negated: the original check failed - contains "x": FAIL"}.
     */
    default Evaluator<T> negate() {
        return actual -> {
            Evaluation evaluation = evaluate(actual);
            String feedback = "Negated: the original check " + (evaluation.pass() ? "passed" : "failed")
                    + (evaluation.feedback() == null ? "" : " - " + evaluation.feedback());
            return new Evaluation(!evaluation.pass(), 1.0 - evaluation.score(), feedback);
        };
    }
}
