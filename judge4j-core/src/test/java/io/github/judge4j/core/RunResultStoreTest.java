package io.github.judge4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunResultStoreTest {

    @TempDir
    Path tempDir;

    private final RunResultStore store = new RunResultStore();

    private static EvalResult result(Object output, Map<String, Object> inputs, List<AssertionResult> assertions) {
        return new EvalResult("Suite#method:case-1", "A case", inputs, "variant-a", output, 0.8, true, 120, null,
                assertions);
    }

    private static RunResult run(EvalResult... results) {
        return new RunResult("run-1", "baseline", Instant.parse("2026-09-02T10:15:30Z"), List.of(results));
    }

    @Test
    void roundTripsAFullRun() throws Exception {
        RunResult run = run(result("hello", Map.of("q", "x"),
                List.of(new AssertionResult("contains", "has text", "hello", 60.0, true, 1.0, "ok"))));
        Path file = tempDir.resolve("nested/dir/run.json");

        store.write(run, file);

        assertEquals(run, store.read(file));
    }

    @Test
    void writesTimestampsAsIso8601Strings() throws Exception {
        Path file = tempDir.resolve("run.json");

        store.write(run(result("x", Map.of(), List.of())), file);

        assertTrue(Files.readString(file).contains("\"timestamp\" : \"2026-09-02T10:15:30Z\""));
    }

    @Test
    void readsLegacyNumericTimestamps() throws Exception {
        Path file = tempDir.resolve("legacy.json");
        Files.writeString(file, """
                {"runId":"r","label":"l","timestamp":1790510227.5,"results":[]}
                """);

        assertNotNull(store.read(file).timestamp());
    }

    @Test
    void ignoresUnknownFieldsWrittenByNewerVersions() throws Exception {
        Path file = tempDir.resolve("future.json");
        Files.writeString(file, """
                {"runId":"r","label":"l","timestamp":"2026-01-01T00:00:00Z","results":[],"somethingNew":42}
                """);

        assertEquals("r", store.read(file).runId());
    }

    @Test
    void anUnserializableOutputIsWrittenAsItsToStringInsteadOfFailing() throws Exception {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        Object hostile = new Object() {
            @Override
            public String toString() {
                return "hostile-output";
            }

            @SuppressWarnings("unused")
            public Object getBoom() {
                throw new IllegalStateException("getter blew up");
            }
        };
        Path file = tempDir.resolve("run.json");

        store.write(run(result(hostile, Map.of("cyclic", cyclic), List.of())), file);

        JsonNode written = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(file.toFile());
        JsonNode first = written.get("results").get(0);
        assertEquals("hostile-output", first.get("output").asText());
        assertTrue(first.get("inputs").get("cyclic").isTextual());
    }

    @Test
    void structuredOutputsSurviveAsJson() throws Exception {
        Path file = tempDir.resolve("run.json");

        store.write(run(result(Map.of("title", "T", "points", List.of(1, 2)), Map.of(), List.of())), file);

        RunResult read = store.read(file);
        assertTrue(read.results().get(0).output() instanceof Map<?, ?>);
        assertEquals("T", ((Map<?, ?>) read.results().get(0).output()).get("title"));
    }

    @Test
    void aRunWithNoResultsIsValid() throws Exception {
        Path file = tempDir.resolve("empty.json");

        store.write(run(), file);

        assertTrue(store.read(file).results().isEmpty());
    }

    @Test
    void nullOutputAndErrorResultsRoundTrip() throws Exception {
        EvalResult failed = new EvalResult("Suite#m:c", null, Map.of(), "v", null, 0.0, false, 5,
                "java.lang.IllegalStateException: boom", List.of());
        Path file = tempDir.resolve("err.json");

        store.write(run(failed), file);

        assertEquals(run(failed), store.read(file));
    }
}
