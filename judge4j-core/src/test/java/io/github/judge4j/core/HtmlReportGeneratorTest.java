package io.github.judge4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    private final HtmlReportGenerator generator = new HtmlReportGenerator();

    private static EvalResult result(String caseId, String variant, Object output) {
        return new EvalResult("Suite#m:" + caseId, "desc " + caseId, Map.of("document", "d"), variant, output, 0.5,
                false, 10, null,
                List.of(new AssertionResult("a", "e", output, 100.0, false, 0.5, "feedback")));
    }

    private static RunResult run(String label, EvalResult... results) {
        return new RunResult("run-" + label, label, Instant.parse("2026-09-02T10:15:30Z"), List.of(results));
    }

    /** Extracts and parses the JSON embedded in the report. */
    private static JsonNode embeddedData(String html) throws Exception {
        String marker = "<script id=\"judge4j-data\" type=\"application/json\">";
        int start = html.indexOf(marker) + marker.length();
        int end = html.indexOf("</script>", start);
        return JsonMapper.builder().build().readTree(html.substring(start, end));
    }

    @Test
    void writesASelfContainedPageWithTheRunEmbedded() throws Exception {
        Path file = tempDir.resolve("deep/report.html");

        generator.generate(run("my-run", result("c1", "v1", "out")), file);

        String html = Files.readString(file);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("<title>Judge4j Report</title>"));
        assertFalse(html.contains("__DATA__"));
        assertFalse(html.contains("__TITLE__"));
        JsonNode data = embeddedData(html);
        assertEquals("my-run", data.get("run").get("label").asText());
        assertEquals("2026-09-02T10:15:30Z", data.get("run").get("timestamp").asText());
        assertEquals("Suite#m:c1", data.get("run").get("results").get(0).get("testCaseId").asText());
    }

    @Test
    void makesNoNetworkRequests() throws Exception {
        Path file = tempDir.resolve("report.html");

        generator.generate(run("r", result("c", "v", "o")), file);

        String html = Files.readString(file);
        assertFalse(html.contains("http://"));
        assertFalse(html.contains("https://"));
        assertFalse(html.contains("<link "));
        assertFalse(html.contains("<script src"));
    }

    @Test
    void valuesCannotBreakOutOfTheScriptElement() throws Exception {
        String hostile = "</script><script>alert(1)</script><!--  ";
        Path file = tempDir.resolve("report.html");

        generator.generate(run("</script>label", result("c", "<b>v</b>", hostile)), file);

        String html = Files.readString(file);
        int dataStart = html.indexOf("<script id=\"judge4j-data\"");
        int dataEnd = html.indexOf("</script>", dataStart);
        // The first closing tag after the data block opens must be the block's own, past all data.
        assertFalse(html.substring(dataStart, dataEnd).contains("alert(1)</script>"));
        JsonNode data = embeddedData(html);
        assertEquals(hostile, data.get("run").get("results").get(0).get("output").asText());
        assertEquals("</script>label", data.get("run").get("label").asText());
    }

    @Test
    void anEmptyRunStillProducesAReport() throws Exception {
        Path file = tempDir.resolve("empty.html");

        generator.generate(run("empty"), file);

        assertEquals(0, embeddedData(Files.readString(file)).get("run").get("results").size());
    }

    @Test
    void unserializableOutputsDoNotBreakTheReport() throws Exception {
        List<Object> cyclic = new java.util.ArrayList<>();
        cyclic.add(cyclic);
        Path file = tempDir.resolve("report.html");

        generator.generate(run("r", result("c", "v", cyclic)), file);

        assertTrue(Files.exists(file));
    }

    @Test
    void comparisonReportEmbedsBothRuns() throws Exception {
        Path file = tempDir.resolve("compare.html");

        generator.generateDiff(run("baseline", result("c", "v1", "old")), run("candidate", result("c", "v1", "new")),
                file);

        String html = Files.readString(file);
        assertTrue(html.contains("<title>Judge4j Run Comparison</title>"));
        JsonNode data = embeddedData(html);
        assertEquals("baseline", data.get("baseline").get("label").asText());
        assertEquals("candidate", data.get("candidate").get("label").asText());
    }
}
