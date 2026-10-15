package io.github.judge4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads a dataset of {@link TestCase}s from a YAML list on the classpath:
 *
 * <pre>
 * - id: quarterly-report
 *   description: Summarize a quarterly update     # optional
 *   vars:                                         # optional
 *     document: The product team shipped ...
 * </pre>
 */
public final class TestCaseLoader {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    /**
     * @param resourcePath classpath location, with or without a leading slash
     * @return the cases in file order; never empty
     * @throws IOException              if the resource is missing or unreadable
     * @throws IllegalArgumentException if the file is empty, malformed, not a list, or has duplicate or blank ids
     */
    public List<TestCase> loadFromClasspath(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("test case resource path must not be blank");
        }
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(normalizedPath)) {
            if (input == null) {
                throw new IOException("Judge4j test case resource was not found on the classpath: " + resourcePath);
            }
            List<TestCase> cases;
            try {
                cases = objectMapper.readValue(input,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, TestCase.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid Judge4j test case file '" + resourcePath + "': "
                        + e.getOriginalMessage() + (e.getLocation() != null
                                ? " (line " + e.getLocation().getLineNr() + ")" : ""), e);
            }
            return validated(cases, resourcePath);
        }
    }

    private static List<TestCase> validated(List<TestCase> cases, String resourcePath) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Judge4j test case file '" + resourcePath + "' contains no test cases");
        }
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (TestCase testCase : cases) {
            if (testCase == null) {
                throw new IllegalArgumentException("Judge4j test case file '" + resourcePath + "' has an empty list entry");
            }
            if (!seen.add(testCase.id())) {
                duplicates.add(testCase.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Judge4j test case file '" + resourcePath
                    + "' has duplicate test case ids: " + duplicates);
        }
        return List.copyOf(cases);
    }
}
