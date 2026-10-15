package io.github.judge4j.core;

/**
 * Entry point for assertions inside an {@code @EvalTest} method; declare it as a method parameter
 * and Judge4j injects it.
 *
 * <pre>{@code
 * evaluations.assertThat(summary).named("concise").satisfies(Matchers.maxWords(40));
 * }</pre>
 */
public interface EvalAssertions {

    /** Starts an assertion on {@code actual}, the value produced by the system under test. */
    <T> EvalAssertion<T> assertThat(T actual);
}
