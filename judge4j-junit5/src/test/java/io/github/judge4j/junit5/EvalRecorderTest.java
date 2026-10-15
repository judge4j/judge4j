package io.github.judge4j.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.judge4j.core.EvalContext;
import io.github.judge4j.core.EvalResult;
import io.github.judge4j.core.RunResult;
import io.github.judge4j.core.RunResultStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class EvalRecorderTest {

    @TempDir
    Path output;

    @AfterEach
    void clearProperties() {
        System.clearProperty(EvalRecorder.OUTPUT_DIRECTORY_PROPERTY);
        System.clearProperty(EvalRecorder.LABEL_PROPERTY);
    }

    private void run(Class<?> suite) {
        System.setProperty(EvalRecorder.OUTPUT_DIRECTORY_PROPERTY, output.toString());
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(suite)).build());
    }

    @Test
    void recordsEveryKindOfResultAndWritesBothArtifacts() throws Exception {
        run(RecordedSuite.class);

        assertTrue(Files.exists(output.resolve("RecordedSuite.html")));
        RunResult run = new RunResultStore().read(output.resolve("RecordedSuite.json"));
        assertEquals("RecordedSuite", run.label());
        assertEquals(4, run.results().size());

        EvalResult scored = run.results().get(0);
        assertEquals("booking-parse", scored.testCaseId());
        assertEquals("mock-v1", scored.variantLabel());
        assertTrue(scored.pass());

        EvalResult defaultVariant = run.results().get(1);
        assertEquals(EvalContext.DEFAULT_VARIANT, defaultVariant.variantLabel());
        assertFalse(defaultVariant.pass(), "0.4 is below the pass threshold");

        EvalResult atThreshold = run.results().get(2);
        assertEquals(EvalContext.PASS_THRESHOLD, atThreshold.score());
        assertTrue(atThreshold.pass(), "a score equal to the threshold passes");

        EvalResult error = run.results().get(3);
        assertFalse(error.pass());
        assertEquals(0.0, error.score());
        assertTrue(error.error().contains("IllegalStateException: boom"));
    }

    @Test
    void labelAndOutputDirectoryComeFromSystemProperties() throws Exception {
        System.setProperty(EvalRecorder.LABEL_PROPERTY, "nightly-run");

        run(LabelledSuite.class);

        assertEquals("nightly-run", new RunResultStore().read(output.resolve("LabelledSuite.json")).label());
    }

    @Test
    void blankPropertiesFallBackToDefaults() throws Exception {
        System.setProperty(EvalRecorder.LABEL_PROPERTY, "  ");

        run(BlankLabelSuite.class);

        assertEquals("BlankLabelSuite", new RunResultStore().read(output.resolve("BlankLabelSuite.json")).label());
    }

    @Test
    void aClassThatRecordsNothingStillGetsAReport() throws Exception {
        run(SilentSuite.class);

        assertTrue(new RunResultStore().read(output.resolve("SilentSuite.json")).results().isEmpty());
        assertTrue(Files.exists(output.resolve("SilentSuite.html")));
    }

    @Test
    void invalidScoresAreRejectedAtTheCallSite() throws Exception {
        run(InvalidScoreSuite.class);

        // The suite asserts inside its own test; if it failed, the launcher would have reported it
        // and no result would have been recorded for a bad score.
        assertTrue(new RunResultStore().read(output.resolve("InvalidScoreSuite.json")).results().isEmpty());
        assertTrue(InvalidScoreSuite.rejected, "all invalid scores must be rejected");
    }

    @Test
    void reportNamesNeverCollideAndNeverOverwrite() {
        Path directory = output.resolve("collisions");

        Path first = EvalRecorder.claimBasePath(directory, "SameName");
        Path second = EvalRecorder.claimBasePath(directory, "SameName");
        Path third = EvalRecorder.claimBasePath(directory, "SameName");

        assertEquals("SameName", first.getFileName().toString());
        assertEquals("SameName-2", second.getFileName().toString());
        assertEquals("SameName-3", third.getFileName().toString());
    }

    @Test
    void anonymousClassesFallBackToTheirBinaryName() {
        Object anonymous = new Object() { };

        assertFalse(EvalRecorder.displayName(anonymous.getClass()).isEmpty());
        assertTrue(EvalRecorder.displayName(anonymous.getClass()).contains("EvalRecorderTest"));
        assertEquals("EvalRecorderTest", EvalRecorder.displayName(EvalRecorderTest.class));
    }

    @Test
    void usingTheRecorderWithoutTheExtensionGivesAHelpfulError() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> EvalRecorder.record(null, null));

        assertTrue(error.getMessage().contains("@ExtendWith(EvalRecorder.class)"), error.getMessage());
    }

    @ExtendWith(EvalRecorder.class)
    static class RecordedSuite {
        @Test
        void recordsInAllFourWays(EvalContext context) {
            context.record("booking-parse", "confirmed", 1.0, "mock-v1");
            context.record("no-variant", "unsure", 0.4);
            context.record("boundary", "meh", EvalContext.PASS_THRESHOLD);
            context.recordError("exploded", new IllegalStateException("boom"));
        }
    }

    @ExtendWith(EvalRecorder.class)
    static class LabelledSuite {
        @Test
        void records(EvalContext context) {
            context.record("c", "o", 1.0);
        }
    }

    @ExtendWith(EvalRecorder.class)
    static class BlankLabelSuite {
        @Test
        void records(EvalContext context) {
            context.record("c", "o", 1.0);
        }
    }

    @ExtendWith(EvalRecorder.class)
    static class SilentSuite {
        @Test
        void doesNothing() {
        }
    }

    @ExtendWith(EvalRecorder.class)
    static class InvalidScoreSuite {
        static boolean rejected;

        @Test
        void rejectsBadScores(EvalContext context) {
            rejected = assertThrowsAll(context);
        }

        private static boolean assertThrowsAll(EvalContext context) {
            for (double bad : List.of(-0.1, 1.5, Double.NaN)) {
                try {
                    context.record("bad", "x", bad);
                    return false;
                } catch (IllegalArgumentException expected) {
                    // good
                }
            }
            try {
                context.record(null, "x", 0.5);
                return false;
            } catch (NullPointerException expected) {
                return true;
            }
        }
    }
}
