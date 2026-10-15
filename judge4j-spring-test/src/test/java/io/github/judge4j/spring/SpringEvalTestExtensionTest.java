package io.github.judge4j.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.judge4j.core.EvalAssertions;
import io.github.judge4j.core.EvalResult;
import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.RunResult;
import io.github.judge4j.core.RunResultStore;
import io.github.judge4j.junit5.EvalTest;
import io.github.judge4j.junit5.EvalTestCases;
import io.github.judge4j.junit5.Input;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Exercises the evaluation engine end to end with tiny in-memory suites, run through the JUnit
 * Launcher. No model is involved: {@link Greeter} is a stand-in whose output depends on the active
 * variant, which is all the engine needs to be exercised.
 */
class SpringEvalTestExtensionTest {

    @TempDir
    Path output;

    @AfterEach
    void clearProperty() {
        System.clearProperty("judge4j.output-directory");
    }

    private record Outcome(RunResult run, TestExecutionSummary summary) {
        List<EvalResult> results() {
            return run.results();
        }

        EvalResult result(String caseId, String variant) {
            return run.results().stream()
                    .filter(r -> r.testCaseId().endsWith(":" + caseId) && r.variantLabel().equals(variant))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no result for " + caseId + " / " + variant + " in " + results()));
        }

        String failureMessages() {
            return summary.getFailures().stream().map(f -> String.valueOf(f.getException().getMessage()))
                    .collect(Collectors.joining(" || "));
        }
    }

    private Outcome run(Class<?> suite) throws Exception {
        System.setProperty("judge4j.output-directory", output.toString());
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(suite)).build(),
                listener);
        Path json = output.resolve(suite.getSimpleName() + ".json");
        RunResult run = Files.exists(json) ? new RunResultStore().read(json) : null;
        return new Outcome(run, listener.getSummary());
    }

    // ------------------------------------------------------------------------------------------
    // Fan-out, variants, weights, pass/fail
    // ------------------------------------------------------------------------------------------

    @Test
    void runsEveryCaseAgainstEveryVariantAndScoresByWeight() throws Exception {
        Outcome outcome = run(HappyPathSuite.class);

        assertEquals(List.of("alpha/formal", "alpha/casual", "beta/formal", "beta/casual"),
                outcome.results().stream().map(r -> r.testCaseId().substring(r.testCaseId().indexOf(':') + 1)
                        + "/" + r.variantLabel()).toList());

        EvalResult formal = outcome.result("alpha", "formal");
        assertEquals(0.6, formal.score(), 1e-9, "60% weight passes, the 40% remainder fails");
        assertFalse(formal.pass());
        assertEquals(List.of(60.0, 40.0), formal.assertions().stream().map(a -> a.weight()).toList());
        assertEquals(List.of(true, false), formal.assertions().stream().map(a -> a.pass()).toList());

        EvalResult casual = outcome.result("alpha", "casual");
        assertEquals(1.0, casual.score(), 1e-9);
        assertTrue(casual.pass());

        assertEquals("Good day: hello", outcome.result("alpha", "formal").output());
        assertEquals("Hey: world", outcome.result("beta", "casual").output());
        assertEquals("First case", formal.testCaseDescription());
        assertEquals("hello", formal.inputs().get("text"));
        assertEquals("Greeting is formal", formal.assertions().get(0).label());
        assertEquals("the greeting uses the active prompt", formal.assertions().get(0).expectation());
    }

    @Test
    void failedAssertionsFailTheTestWithAMessageNamingCaseVariantAndAssertion() throws Exception {
        Outcome outcome = run(HappyPathSuite.class);

        assertEquals(2, outcome.summary().getTestsFailedCount(), "the two formal runs fail");
        assertEquals(2, outcome.summary().getTestsSucceededCount(), "the two casual runs pass");
        assertTrue(outcome.failureMessages().contains("HappyPathSuite#greets:alpha [formal]"), outcome.failureMessages());
        assertTrue(outcome.failureMessages().contains("is casual"), outcome.failureMessages());
        assertTrue(outcome.failureMessages().contains("not casual"), "evaluator feedback is included");
    }

    @Test
    void writesTheReportEvenWhenEvaluationsFail() throws Exception {
        run(HappyPathSuite.class);

        assertTrue(Files.exists(output.resolve("HappyPathSuite.html")));
        assertTrue(Files.exists(output.resolve("HappyPathSuite.json")));
    }

    @Test
    void theOutputIsTheValuePassedToTheFirstAssertion() throws Exception {
        assertEquals("Good day: hello", run(HappyPathSuite.class).result("alpha", "formal").output());
    }

    // ------------------------------------------------------------------------------------------
    // Errors still produce a recorded result
    // ------------------------------------------------------------------------------------------

    @Test
    void aMethodThatThrowsIsRecordedAsAnErrorAndKeepsEarlierAssertions() throws Exception {
        Outcome outcome = run(ThrowingSuite.class);

        assertEquals(4, outcome.results().size());
        EvalResult result = outcome.result("alpha", "formal");
        assertFalse(result.pass());
        assertEquals(0.0, result.score());
        assertTrue(result.error().contains("kaboom"), result.error());
        assertEquals(1, result.assertions().size(), "the assertion made before the throw is kept");
        assertNull(result.output());
    }

    @Test
    void aMethodWithNoAssertionsIsAnErrorWithAHelpfulMessage() throws Exception {
        Outcome outcome = run(NoAssertionsSuite.class);

        assertEquals(4, outcome.results().size());
        assertTrue(outcome.results().stream().allMatch(r -> !r.pass() && r.error().contains("No assertions were recorded")));
        assertTrue(outcome.failureMessages().contains("EvalAssertions"), outcome.failureMessages());
    }

    @Test
    void anEvaluatorThatThrowsIsRecordedAsAnError() throws Exception {
        Outcome outcome = run(EvaluatorProblemsSuite.class);

        assertTrue(outcome.result("alpha", "formal").error().contains("judge unavailable"));
        assertTrue(outcome.result("beta", "formal").error().contains("returned null"),
                outcome.result("beta", "formal").error());
    }

    // ------------------------------------------------------------------------------------------
    // Weights
    // ------------------------------------------------------------------------------------------

    @Test
    void weightsOverOneHundredFailBeforeTheEvaluatorRuns() throws Exception {
        WeightSuite.evaluatorRuns.set(0);

        Outcome outcome = run(WeightSuite.class);

        EvalResult result = outcome.result("alpha", "formal");
        assertTrue(result.error().contains("exceeds 100%"), result.error());
        assertEquals(1, result.assertions().size(), "only the first assertion was recorded");
        assertEquals(4, WeightSuite.evaluatorRuns.get(), "once per run: the over-budget assertion never evaluated");
    }

    @Test
    void invalidWeightsAreRejectedImmediately() throws Exception {
        Outcome outcome = run(BadWeightValuesSuite.class);

        assertTrue(outcome.results().stream().allMatch(EvalResult::pass), outcome.failureMessages());
    }

    // ------------------------------------------------------------------------------------------
    // @Input conversion
    // ------------------------------------------------------------------------------------------

    @Test
    void inputsAreConvertedToThePrimitiveBoxedStringAndListTypesRequested() throws Exception {
        Outcome outcome = run(InputConversionSuite.class);

        assertTrue(outcome.results().stream().allMatch(EvalResult::pass), outcome.failureMessages());
        assertEquals(4, outcome.results().size());
    }

    @Test
    void aMissingVariableIsReportedByName() throws Exception {
        Outcome outcome = run(MissingInputSuite.class);

        String error = outcome.result("alpha", "formal").error();
        assertTrue(error.contains("absent"), error);
        assertTrue(error.contains("alpha"), error);
        assertTrue(error.contains("text"), "lists the variables that do exist: " + error);
    }

    @Test
    void anUnconvertibleVariableIsReportedWithItsValueAndTargetType() throws Exception {
        String error = run(BadConversionSuite.class).result("alpha", "formal").error();

        assertTrue(error.contains("'text'"), error);
        assertTrue(error.contains("hello"), error);
        assertTrue(error.contains("int"), error);
    }

    @Test
    void aNullVariableCannotFillAPrimitive() throws Exception {
        String error = run(NullPrimitiveSuite.class).result("alpha", "formal").error();

        assertTrue(error.contains("'nothing'") && error.contains("primitive"), error);
    }

    // ------------------------------------------------------------------------------------------
    // Variants
    // ------------------------------------------------------------------------------------------

    @Test
    void aVariantWhoseSetupThrowsIsRecordedAsAnErrorWithoutAffectingOthers() throws Exception {
        Outcome outcome = run(BrokenVariantSuite.class);

        assertEquals(4, outcome.results().size());
        assertTrue(outcome.result("alpha", "ok").pass());
        EvalResult broken = outcome.result("alpha", "broken");
        assertFalse(broken.pass());
        assertTrue(broken.error().contains("variant setup failed"), broken.error());
    }

    @Test
    void duplicateVariantLabelsAreRejectedWithTheLabel() throws Exception {
        String messages = run(DuplicateLabelSuite.class).failureMessages();

        assertTrue(messages.contains("two variants labelled 'same'"), messages);
    }

    @Test
    void anEmptyVariantListIsRejected() throws Exception {
        assertTrue(run(NoVariantsSuite.class).failureMessages().contains("returned no variants"));
    }

    @Test
    void aFactoryWithoutANoArgConstructorIsRejectedClearly() throws Exception {
        assertTrue(run(BadFactorySuite.class).failureMessages().contains("no-argument constructor"));
    }

    @Test
    void aFactoryThatThrowsIsReportedWithItsCause() throws Exception {
        assertTrue(run(ThrowingFactorySuite.class).failureMessages().contains("factory exploded"));
    }

    @Test
    void aNonPublicFactoryClassWorks() throws Exception {
        assertEquals(4, run(HappyPathSuite.class).results().size()); // TwoVariants is package-private
    }

    // ------------------------------------------------------------------------------------------
    // Dataset configuration
    // ------------------------------------------------------------------------------------------

    @Test
    void aMissingDatasetAnnotationIsExplained() throws Exception {
        assertTrue(run(NoDatasetAnnotationSuite.class).failureMessages().contains("@EvalTestCases"));
    }

    @Test
    void aMissingDatasetFileNamesThePath() throws Exception {
        assertTrue(run(MissingDatasetFileSuite.class).failureMessages().contains("cases/does-not-exist.yaml"));
    }

    @Test
    void anEmptyDatasetIsRejected() throws Exception {
        assertTrue(run(EmptyDatasetSuite.class).failureMessages().contains("no test cases"));
    }

    // ------------------------------------------------------------------------------------------
    // Measurement and inheritance
    // ------------------------------------------------------------------------------------------

    @Test
    void latencyExcludesTimeSpentInEvaluators() throws Exception {
        EvalResult result = run(LatencySuite.class).result("alpha", "formal");

        assertTrue(result.latencyMs() >= 100, "method time is counted: " + result.latencyMs());
        assertTrue(result.latencyMs() < 280, "300 ms inside the evaluator is not: " + result.latencyMs());
    }

    @Test
    void annotationsOnASuperclassApplyToSubclasses() throws Exception {
        Outcome outcome = run(InheritingSuite.class);

        assertEquals(4, outcome.results().size());
        assertTrue(outcome.results().stream().allMatch(EvalResult::pass));
    }

    @Test
    void evaluationClassesNeverRunConcurrentlyEvenWhenJUnitParallelismIsOn() throws Exception {
        OVERLAP_LOG.clear();
        System.setProperty("judge4j.output-directory", output.toString());
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(SlowSuiteA.class), selectClass(SlowSuiteB.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .build(), listener);

        assertEquals(0, listener.getSummary().getTestsFailedCount());
        List<long[]> spans;
        synchronized (OVERLAP_LOG) {
            spans = List.copyOf(OVERLAP_LOG);
        }
        assertEquals(8, spans.size(), "2 suites x 2 cases x 2 variants");
        for (int i = 0; i < spans.size(); i++) {
            for (int j = i + 1; j < spans.size(); j++) {
                boolean overlap = spans.get(i)[0] < spans.get(j)[1] && spans.get(j)[0] < spans.get(i)[1];
                assertFalse(overlap, "runs " + i + " and " + j + " overlapped in time");
            }
        }
    }

    // ==========================================================================================
    // Fixtures
    // ==========================================================================================

    static class Prompts {
        volatile String text = "unset";
    }

    static class Greeter {
        private final Prompts prompts;

        Greeter(Prompts prompts) {
            this.prompts = prompts;
        }

        String greet(String name) {
            return prompts.text + ": " + name;
        }
    }

    @Configuration
    static class Config {
        @Bean
        Prompts prompts() {
            return new Prompts();
        }

        @Bean
        Greeter greeter(Prompts prompts) {
            return new Greeter(prompts);
        }
    }

    static EvalVariant prompt(String label, String text) {
        return EvalVariant.named(label).configure(ctx -> ctx.bean(Prompts.class).text = text);
    }

    /** Package-private on purpose: non-public factories must work. */
    static class TwoVariants implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(prompt("formal", "Good day"), prompt("casual", "Hey"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class HappyPathSuite {
        @Autowired
        Greeter greeter;

        @EvalTest
        void greets(EvalAssertions evaluations, @Input("text") String text) {
            String greeting = greeter.greet(text);
            evaluations.assertThat(greeting)
                    .named("Greeting is formal")
                    .expecting("the greeting uses the active prompt")
                    .weight(60)
                    .satisfies(value -> new Evaluation(value.endsWith(text), 1.0, "ends with the name"));
            evaluations.assertThat(greeting)
                    .named("is casual")
                    .satisfies(value -> value.startsWith("Hey")
                            ? new Evaluation(true, 1.0, "casual")
                            : new Evaluation(false, 0.0, "not casual"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class ThrowingSuite {
        @EvalTest
        void explodes(EvalAssertions evaluations) {
            evaluations.assertThat("fine").satisfies(v -> new Evaluation(true, 1.0, "ok"));
            throw new IllegalStateException("kaboom");
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class NoAssertionsSuite {
        @EvalTest
        void forgetsToAssert(@Input("text") String text) {
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class EvaluatorProblemsSuite {
        @EvalTest
        void evaluatorFails(EvalAssertions evaluations, @Input("text") String text) {
            if (text.equals("hello")) {
                evaluations.assertThat("x").satisfies(v -> {
                    throw new IllegalStateException("judge unavailable");
                });
            } else {
                evaluations.assertThat("x").satisfies(v -> null);
            }
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class WeightSuite {
        static final AtomicInteger evaluatorRuns = new AtomicInteger();

        @EvalTest
        void tooHeavy(EvalAssertions evaluations) {
            evaluations.assertThat("a").weight(70).satisfies(v -> {
                evaluatorRuns.incrementAndGet();
                return new Evaluation(true, 1.0, "ok");
            });
            evaluations.assertThat("b").weight(50).satisfies(v -> {
                evaluatorRuns.incrementAndGet();
                return new Evaluation(true, 1.0, "ok");
            });
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class BadWeightValuesSuite {
        @EvalTest
        void rejectsBadWeights(EvalAssertions evaluations) {
            for (double bad : new double[] {0, -5, 100.01, Double.NaN}) {
                try {
                    evaluations.assertThat("x").weight(bad);
                    throw new AssertionError("weight " + bad + " should have been rejected");
                } catch (IllegalArgumentException expected) {
                    // good
                }
            }
            try {
                evaluations.assertThat("x").named(" ");
                throw new AssertionError("blank name should have been rejected");
            } catch (IllegalArgumentException expected) {
                // good
            }
            evaluations.assertThat("x").weight(100).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class InputConversionSuite {
        @EvalTest
        void converts(EvalAssertions evaluations, @Input("count") int count, @Input("count") Integer boxed,
                @Input("count") long widened, @Input("big") long big, @Input("count") String asText,
                @Input("flags") List<String> flags, @Input("nothing") String nothing, @Input("text") Object any) {
            boolean ok = count > 0 && boxed == count && widened == count && big > 1_000_000_000L
                    && asText.equals(String.valueOf(count)) && !flags.isEmpty() && nothing == null && any != null;
            evaluations.assertThat(ok).satisfies(v -> new Evaluation(v, v ? 1.0 : 0.0, "conversions " + v));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class MissingInputSuite {
        @EvalTest
        void needsAbsent(EvalAssertions evaluations, @Input("absent") String absent) {
            evaluations.assertThat(absent).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class BadConversionSuite {
        @EvalTest
        void textAsInt(EvalAssertions evaluations, @Input("text") int number) {
            evaluations.assertThat(number).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class NullPrimitiveSuite {
        @EvalTest
        void nullAsInt(EvalAssertions evaluations, @Input("nothing") int number) {
            evaluations.assertThat(number).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    static class BrokenAndOkVariants implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(prompt("ok", "Fine"),
                    EvalVariant.named("broken").configure(ctx -> {
                        throw new IllegalStateException("variant setup failed");
                    }));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(BrokenAndOkVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class BrokenVariantSuite {
        @Autowired
        Greeter greeter;

        @EvalTest
        void greets(EvalAssertions evaluations, @Input("text") String text) {
            evaluations.assertThat(greeter.greet(text)).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    static class DuplicateLabels implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(prompt("same", "a"), prompt("same", "b"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(DuplicateLabels.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class DuplicateLabelSuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    static class NoVariants implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of();
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(NoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class NoVariantsSuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    static class ArgFactory implements EvalVariantFactory {
        ArgFactory(String required) {
        }

        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(prompt("a", "a"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(ArgFactory.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class BadFactorySuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    static class ExplodingFactory implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            throw new IllegalStateException("factory exploded");
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(ExplodingFactory.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class ThrowingFactorySuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    static class NoDatasetAnnotationSuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/does-not-exist.yaml")
    static class MissingDatasetFileSuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-empty.yaml")
    static class EmptyDatasetSuite {
        @EvalTest
        void anything(EvalAssertions evaluations) {
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class LatencySuite {
        @EvalTest
        void slowEvaluatorFastMethod(EvalAssertions evaluations) throws InterruptedException {
            Thread.sleep(120);
            evaluations.assertThat("x").satisfies(v -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new Evaluation(true, 1.0, "slow judge");
            });
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    abstract static class InheritableBase {
        @Autowired
        Greeter greeter;

        @EvalTest
        void greets(EvalAssertions evaluations, @Input("text") String text) {
            evaluations.assertThat(greeter.greet(text)).satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    static class InheritingSuite extends InheritableBase {
    }

    /** Start/end nanos of every slow run, appended by the suites below. */
    static final List<long[]> OVERLAP_LOG = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    abstract static class SlowBase {
        @EvalTest
        void sleeps(EvalAssertions evaluations) throws InterruptedException {
            long start = System.nanoTime();
            Thread.sleep(120);
            OVERLAP_LOG.add(new long[] {start, System.nanoTime()});
            evaluations.assertThat("x").satisfies(v -> new Evaluation(true, 1.0, "ok"));
        }
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class SlowSuiteA extends SlowBase {
    }

    @ContextConfiguration(classes = Config.class)
    @EvalVariants(TwoVariants.class)
    @EvalTestCases("cases/engine-cases.yaml")
    static class SlowSuiteB extends SlowBase {
    }
}
