package io.github.judge4j.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;

/**
 * Marks a method that is run once for every test case and variant of an evaluation class, instead
 * of once like a plain {@code @Test}. The method may declare:
 * <ul>
 *   <li>{@code EvalAssertions} - to record assertions on the output under test;</li>
 *   <li>any number of {@link Input @Input("name")} parameters - filled from the test case's variables.</li>
 * </ul>
 * The runs are supplied by the evaluation class's extension, which {@code @EvalVariants} (module
 * {@code judge4j-spring-test}) registers; on its own this annotation has no invocation provider.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
public @interface EvalTest {
}
