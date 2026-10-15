package io.github.judge4j.junit5;

import io.github.judge4j.core.EvalContext;
import io.github.judge4j.core.EvalResult;
import io.github.judge4j.core.HtmlReportGenerator;
import io.github.judge4j.core.RunResult;
import io.github.judge4j.core.RunResultStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit extension that collects the {@link EvalResult}s produced while a test class runs and, when
 * the class finishes, writes them as {@code <ClassName>.json} and a self-contained
 * {@code <ClassName>.html} report.
 *
 * <p>Evaluations get it automatically through {@code @EvalVariants}. A plain JUnit class can use it
 * directly with {@code @ExtendWith(EvalRecorder.class)} and a parameter of type {@link EvalContext}.
 *
 * <p>System properties:
 * <ul>
 *   <li>{@value #OUTPUT_DIRECTORY_PROPERTY} - where reports are written (default {@code target/judge4j})</li>
 *   <li>{@value #LABEL_PROPERTY} - the run label shown in the report (default: the test class name)</li>
 * </ul>
 * A report is written even when no results were recorded. If two test classes in one JVM would
 * produce the same file name, the later one gets a numeric suffix ({@code Name-2.html}) instead of
 * overwriting the first.
 */
public final class EvalRecorder implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    public static final String OUTPUT_DIRECTORY_PROPERTY = "judge4j.output-directory";
    public static final String LABEL_PROPERTY = "judge4j.label";
    static final String DEFAULT_OUTPUT_DIRECTORY = "target/judge4j";

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(EvalRecorder.class);
    private static final String ACCUMULATOR_KEY = "accumulator";

    /** Report base paths already claimed in this JVM, to avoid silently overwriting a sibling's report. */
    private static final Set<Path> CLAIMED = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).put(ACCUMULATOR_KEY, new Accumulator());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == EvalContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return accumulator(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext context) throws IOException {
        Class<?> testClass = context.getRequiredTestClass();
        RunResult run = new RunResult(
                UUID.randomUUID().toString(),
                nonBlankProperty(LABEL_PROPERTY, displayName(testClass)),
                Instant.now(),
                accumulator(context).results());
        Path outputDirectory = Path.of(nonBlankProperty(OUTPUT_DIRECTORY_PROPERTY, DEFAULT_OUTPUT_DIRECTORY));
        Path base = claimBasePath(outputDirectory, displayName(testClass));

        new RunResultStore().write(run, withExtension(base, ".json"));
        Path htmlReport = withExtension(base, ".html");
        new HtmlReportGenerator().generate(run, htmlReport);
        System.out.println("Judge4j report: " + htmlReport.toAbsolutePath().toUri());
    }

    /** Records {@code result} against the test class that {@code context} belongs to. */
    public static void record(ExtensionContext context, EvalResult result) {
        accumulator(context).record(Objects.requireNonNull(result, "result must not be null"));
    }

    static String displayName(Class<?> testClass) {
        String simple = testClass.getSimpleName();
        return simple.isEmpty() ? testClass.getName() : simple;
    }

    /** Returns {@code directory/stem}, or {@code directory/stem-2}, {@code -3}, ... if already claimed. */
    static Path claimBasePath(Path directory, String stem) {
        Path candidate = directory.resolve(stem).toAbsolutePath().normalize();
        int suffix = 2;
        while (!CLAIMED.add(candidate)) {
            candidate = directory.resolve(stem + "-" + suffix++).toAbsolutePath().normalize();
        }
        return candidate;
    }

    private static Path withExtension(Path base, String extension) {
        return base.resolveSibling(base.getFileName() + extension);
    }

    private static String nonBlankProperty(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Accumulator accumulator(ExtensionContext context) {
        ExtensionContext current = context;
        while (current != null) {
            Accumulator accumulator = current.getStore(NAMESPACE).get(ACCUMULATOR_KEY, Accumulator.class);
            if (accumulator != null) {
                return accumulator;
            }
            current = current.getParent().orElse(null);
        }
        throw new IllegalStateException("EvalRecorder was not initialized for this test class; "
                + "add @ExtendWith(EvalRecorder.class) (or @EvalVariants) to it");
    }

    private static final class Accumulator implements EvalContext {

        private final List<EvalResult> results = new ArrayList<>();

        @Override
        public void record(String testCaseId, Object output, double score) {
            record(testCaseId, output, score, DEFAULT_VARIANT);
        }

        @Override
        public synchronized void record(String testCaseId, Object output, double score, String variantLabel) {
            Objects.requireNonNull(testCaseId, "testCaseId must not be null");
            if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0, was " + score);
            }
            String variant = variantLabel == null || variantLabel.isBlank() ? DEFAULT_VARIANT : variantLabel;
            results.add(new EvalResult(testCaseId, null, Map.of(), variant, output, score, score >= PASS_THRESHOLD, 0,
                    null, List.of()));
        }

        @Override
        public synchronized void record(EvalResult result) {
            results.add(Objects.requireNonNull(result, "result must not be null"));
        }

        @Override
        public synchronized void recordError(String testCaseId, Throwable error) {
            Objects.requireNonNull(testCaseId, "testCaseId must not be null");
            Objects.requireNonNull(error, "error must not be null");
            results.add(new EvalResult(testCaseId, null, Map.of(), DEFAULT_VARIANT, null, 0.0, false, 0,
                    error.toString(), List.of()));
        }

        private synchronized List<EvalResult> results() {
            return List.copyOf(results);
        }
    }
}
