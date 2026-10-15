package io.github.judge4j.spring;

import io.github.judge4j.junit5.EvalRecorder;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Turns a Spring test class into a Judge4j evaluation: its {@code @EvalTest} methods run once per
 * test case (see {@code @EvalTestCases}) and per variant produced by {@link #value()}, and a JSON
 * and HTML report is written when the class finishes.
 *
 * <p>Also tags the class {@code "eval"}. Evaluations usually call a live model (slow, billable,
 * needs credentials), so the tag lets a build exclude them from a plain {@code mvn test} and run
 * them on demand through Surefire's {@code groups} / {@code excludedGroups}.
 *
 * <p>Variants change state in the shared Spring context (stubs, spies), so evaluations must never
 * overlap. The class is therefore run on a single thread and holds an exclusive JUnit resource lock,
 * which keeps evaluation classes from running concurrently even when JUnit parallel execution is
 * enabled.
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("eval")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("io.github.judge4j.evaluation")
@ExtendWith({SpringExtension.class, EvalRecorder.class, SpringEvalTestExtension.class})
public @interface EvalVariants {

    /** The factory that creates the variants to compare. */
    Class<? extends EvalVariantFactory> value();
}
