package io.github.judge4j.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects one variable of the current test case into an {@code @EvalTest} parameter:
 * {@code void run(@Input("document") String document)}. The value is converted to the parameter's
 * type where a standard conversion exists (for example the YAML number {@code 5} to {@code int},
 * {@code long} or {@code String}); a missing variable or an impossible conversion fails that run
 * with a message naming the case and variable.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Input {

    /** The variable name under {@code vars} in the dataset. */
    String value();
}
