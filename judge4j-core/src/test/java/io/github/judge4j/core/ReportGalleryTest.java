package io.github.judge4j.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Renders a gallery of awkward-but-legal runs to {@code target/report-gallery/}. The assertions only
 * check that each file is produced; the point of the gallery is to open the files in a browser and
 * confirm the report copes with every shape of data (no blank page, no script errors).
 */
class ReportGalleryTest {

    private static final Path GALLERY = Path.of("target/report-gallery");
    private static final Instant WHEN = Instant.parse("2026-10-01T09:30:00Z");
    private final HtmlReportGenerator generator = new HtmlReportGenerator();

    private static RunResult run(String label, EvalResult... results) {
        return new RunResult("run-" + label, label, WHEN, List.of(results));
    }

    private static AssertionResult assertion(String label, boolean pass, String feedback) {
        return new AssertionResult(label, "expectation of " + label, "input", 50.0, pass, pass ? 1.0 : 0.0, feedback);
    }

    private static EvalResult result(String caseId, String variant, boolean pass, String output) {
        return new EvalResult("Gallery#method:" + caseId, "Description of " + caseId, Map.of("q", "question " + caseId),
                variant, output, pass ? 1.0 : 0.5, pass, 120, null,
                List.of(assertion("first", true, "fine"), assertion("second", pass, pass ? "fine" : "went wrong")));
    }

    private void render(String name, RunResult run) throws Exception {
        generator.generate(run, GALLERY.resolve(name + ".html"));
        assertTrue(Files.size(GALLERY.resolve(name + ".html")) > 10_000, name);
    }

    @Test
    void rendersTheGallery() throws Exception {
        render("1-empty-run", run("empty"));

        render("2-single-variant", run("single",
                result("c1", "only", true, "out 1"), result("c2", "only", false, "out 2")));

        render("3-hand-recorded-no-assertions", run("hand-recorded",
                new EvalResult("booking-parse", null, Map.of(), "mock-v1", "confirmed", 1.0, true, 0, null, List.of()),
                new EvalResult("booking-fail", null, Map.of(), "default", null, 0.2, false, 0, null, List.of())));

        render("4-errors-and-nulls", run("errors",
                new EvalResult("Gallery#m:boom", "Throws", Map.of("q", "x"), "v1", null, 0.0, false, 3,
                        "java.lang.IllegalStateException: kaboom", List.of(assertion("before-the-throw", true, "ok"))),
                new EvalResult("Gallery#m:boom", "Throws", Map.of("q", "x"), "v2", "fine", 1.0, true, 3, null,
                        List.of(assertion("ok", true, null)))));

        List<EvalResult> many = new ArrayList<>();
        for (int v = 1; v <= 10; v++) {
            for (int c = 1; c <= 3; c++) {
                many.add(result("case-" + c, "a-rather-long-variant-label-number-" + v, (v + c) % 3 != 0, "output " + v + "/" + c));
            }
        }
        render("5-ten-variants", run("many", many.toArray(EvalResult[]::new)));

        String hostile = "</script><img src=x onerror=alert(1)> & \"quotes\" 'single' <b>bold</b>   {{template}} ${js}";
        render("6-hostile-strings", run("<h1>label</h1>",
                new EvalResult("Gallery#m:<i>id</i>", hostile, Map.of("<k>", hostile), "<u>variant</u>", hostile, 0.5, false,
                        1, null, List.of(new AssertionResult(hostile, hostile, hostile, 100.0, false, 0.5, hostile)))));

        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("title", "T");
        nested.put("points", List.of(1, 2, 3));
        render("7-structured-and-unserializable-output", run("outputs",
                new EvalResult("Gallery#m:map", "Map output", Map.of(), "v", nested, 1.0, true, 1, null, List.of()),
                new EvalResult("Gallery#m:cyclic", "Cyclic output", Map.of(), "v", cyclic, 1.0, true, 1, null, List.of())));

        String longText = "word ".repeat(4000);
        render("8-huge-text", run("huge", result("c1", "v", true, longText)));

        generator.generateDiff(
                run("baseline", result("c1", "v1", true, "old"), result("c2", "v1", false, "old 2")),
                run("candidate", result("c1", "v1", true, "new"), result("c2", "v1", true, "new 2")),
                GALLERY.resolve("9-run-comparison.html"));
        assertTrue(Files.exists(GALLERY.resolve("9-run-comparison.html")));
    }
}
