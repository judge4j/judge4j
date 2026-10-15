package io.github.judge4j.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Points an evaluation class at its dataset: a YAML list of test cases on the classpath, e.g.
 * {@code @EvalTestCases("judge4j/summarizer-cases.yaml")}. Each {@code @EvalTest} method runs once
 * per case (and per variant).
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EvalTestCases {

    /** Classpath location of the YAML dataset. */
    String value();
}
