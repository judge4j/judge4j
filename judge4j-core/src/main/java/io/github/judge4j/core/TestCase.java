package io.github.judge4j.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One row of an evaluation dataset. Construction throws {@link IllegalArgumentException} if the id
 * is blank.
 *
 * @param id          unique within its dataset; shown in the report
 * @param description what the case checks; optional, defaults to empty
 * @param vars        named inputs, injected into the test method with {@code @Input}; optional,
 *                    defaults to empty. Iteration order (the order in the dataset) is preserved.
 */
public record TestCase(String id, String description, Map<String, Object> vars) {

    public TestCase {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        description = description == null ? "" : description;
        vars = vars == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(vars));
    }
}
