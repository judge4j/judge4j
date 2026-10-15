package io.github.judge4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCaseLoaderTest {

    private final TestCaseLoader loader = new TestCaseLoader();

    @Test
    void loadsCasesInFileOrderPreservingVariableOrder() throws IOException {
        List<TestCase> cases = loader.loadFromClasspath("cases/valid.yaml");

        assertEquals(List.of("first", "second", "third"), cases.stream().map(TestCase::id).toList());
        assertEquals("The first case", cases.get(0).description());
        assertEquals(List.of("zeta", "alpha", "mid"), List.copyOf(cases.get(0).vars().keySet()));
        assertEquals(1, cases.get(0).vars().get("zeta"));
        assertEquals(List.of("a", "b"), cases.get(0).vars().get("mid"));
    }

    @Test
    void missingDescriptionAndVarsDefaultToEmpty() throws IOException {
        TestCase third = loader.loadFromClasspath("cases/valid.yaml").get(2);

        assertEquals("", third.description());
        assertTrue(third.vars().isEmpty());
    }

    @Test
    void nullVariableValuesAreKept() throws IOException {
        TestCase second = loader.loadFromClasspath("cases/valid.yaml").get(1);

        assertTrue(second.vars().containsKey("nothing"));
        assertNull(second.vars().get("nothing"));
        assertEquals(5, second.vars().get("count"));
    }

    @Test
    void acceptsALeadingSlash() throws IOException {
        assertEquals(3, loader.loadFromClasspath("/cases/valid.yaml").size());
    }

    @Test
    void loadedVariablesAreImmutable() throws IOException {
        TestCase first = loader.loadFromClasspath("cases/valid.yaml").get(0);

        assertThrows(UnsupportedOperationException.class, () -> first.vars().put("x", 1));
    }

    @Test
    void missingResourceIsAnIoErrorNamingThePath() {
        IOException error = assertThrows(IOException.class, () -> loader.loadFromClasspath("cases/nope.yaml"));

        assertTrue(error.getMessage().contains("cases/nope.yaml"));
    }

    @Test
    void blankPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromClasspath(" "));
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromClasspath(null));
    }

    @Test
    void emptyListIsRejectedWithTheFileName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromClasspath("cases/empty-list.yaml"));

        assertTrue(error.getMessage().contains("empty-list.yaml"));
        assertTrue(error.getMessage().contains("no test cases"));
    }

    @Test
    void emptyFileIsRejectedWithAClearMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromClasspath("cases/empty-file.yaml"));

        assertTrue(error.getMessage().contains("empty-file.yaml"));
    }

    @Test
    void duplicateIdsAreRejectedAndListed() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromClasspath("cases/duplicate-ids.yaml"));

        assertTrue(error.getMessage().contains("duplicate"));
        assertTrue(error.getMessage().contains("same"));
    }

    @Test
    void aMapInsteadOfAListIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromClasspath("cases/not-a-list.yaml"));

        assertTrue(error.getMessage().contains("not-a-list.yaml"));
    }

    @Test
    void malformedYamlIsReportedWithTheFileName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromClasspath("cases/malformed.yaml"));

        assertTrue(error.getMessage().contains("malformed.yaml"));
    }

    @Test
    void blankAndMissingIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromClasspath("cases/blank-id.yaml"));
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromClasspath("cases/missing-id.yaml"));
    }
}
