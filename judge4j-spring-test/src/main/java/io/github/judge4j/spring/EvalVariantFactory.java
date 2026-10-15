package io.github.judge4j.spring;

import java.util.List;

/**
 * Supplies the variants an evaluation compares. Implementations need a no-argument constructor
 * (non-public is fine) and are named in {@link EvalVariants @EvalVariants(...)}. Labels must be
 * unique and the list must not be empty.
 */
public interface EvalVariantFactory {

    List<? extends EvalVariant> createVariants();
}
