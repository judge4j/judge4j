package io.github.judge4j.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.judge4j.core.Evaluation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EmbeddingSimilarityEvaluatorTest {

    private static StubEmbeddingModel model() {
        return new StubEmbeddingModel(Map.of(
                "a", new float[] {1f, 0f},
                "same", new float[] {1f, 0f},
                "orthogonal", new float[] {0f, 1f},
                "opposite", new float[] {-1f, 0f},
                "close", new float[] {1f, 0.1f},
                "zero", new float[] {0f, 0f},
                "three-d", new float[] {1f, 0f, 0f},
                "dotty", new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f}));
    }

    private static Evaluation evaluate(double threshold, String actual, String expected) {
        return new EmbeddingSimilarityEvaluator(threshold, model())
                .evaluate(new EmbeddingSimilarityEvaluator.Input(actual, expected));
    }

    @Test
    void identicalVectorsScoreExactlyOneWithoutOverflowingTheScoreRange() {
        Evaluation evaluation = evaluate(0.9, "a", "same");

        assertTrue(evaluation.pass());
        assertEquals(1.0, evaluation.score(), 1e-9);
        assertTrue(evaluation.score() <= 1.0, "floating point error must not push the score above 1");
    }

    @Test
    void floatingPointNoiseOnIdenticalVectorsNeverBreaksTheEvaluationContract() {
        EmbeddingSimilarityEvaluator evaluator = new EmbeddingSimilarityEvaluator(0.99, model());

        Evaluation evaluation = evaluator.evaluate(new EmbeddingSimilarityEvaluator.Input("dotty", "dotty"));

        assertTrue(evaluation.pass());
        assertTrue(evaluation.score() <= 1.0);
    }

    @Test
    void orthogonalVectorsFailAWorthwhileThreshold() {
        Evaluation evaluation = evaluate(0.5, "a", "orthogonal");

        assertFalse(evaluation.pass());
        assertEquals(0.0, evaluation.score(), 1e-9);
        assertTrue(evaluation.feedback().contains("0.000") && evaluation.feedback().endsWith("FAIL"), evaluation.feedback());
    }

    @Test
    void oppositeVectorsScoreZeroButTheFeedbackShowsTheRawNegativeSimilarity() {
        Evaluation evaluation = evaluate(0.0, "a", "opposite");

        assertFalse(evaluation.pass());
        assertEquals(0.0, evaluation.score());
        assertTrue(evaluation.feedback().contains("-1.000"), evaluation.feedback());
    }

    @Test
    void aSimilarityEqualToTheThresholdPasses() {
        assertTrue(evaluate(0.0, "a", "orthogonal").pass(), "similarity 0.0 meets threshold 0.0");
    }

    @Test
    void nearbyVectorsPassAtAModerateThreshold() {
        Evaluation evaluation = evaluate(0.9, "a", "close");

        assertTrue(evaluation.pass());
        assertEquals(0.995, evaluation.score(), 1e-3);
    }

    @Test
    void bothTextsAreEmbeddedInASingleCall() {
        StubEmbeddingModel model = model();

        new EmbeddingSimilarityEvaluator(0.5, model).evaluate(new EmbeddingSimilarityEvaluator.Input("a", "same"));

        assertEquals(1, model.calls);
    }

    @Test
    void emptyOrBlankTextFailsWithoutCallingTheModel() {
        StubEmbeddingModel model = model();
        EmbeddingSimilarityEvaluator evaluator = new EmbeddingSimilarityEvaluator(0.5, model);

        Evaluation noActual = evaluator.evaluate(new EmbeddingSimilarityEvaluator.Input("  ", "a"));
        Evaluation noExpected = evaluator.evaluate(new EmbeddingSimilarityEvaluator.Input("a", null));

        assertFalse(noActual.pass());
        assertTrue(noActual.feedback().contains("actual"), noActual.feedback());
        assertFalse(noExpected.pass());
        assertTrue(noExpected.feedback().contains("expected"), noExpected.feedback());
        assertEquals(0, model.calls);
    }

    @Test
    void aZeroMagnitudeEmbeddingFailsInsteadOfProducingNaN() {
        Evaluation evaluation = evaluate(0.5, "a", "zero");

        assertFalse(evaluation.pass());
        assertEquals(0.0, evaluation.score());
        assertTrue(evaluation.feedback().contains("zero magnitude"), evaluation.feedback());
    }

    @Test
    void mismatchedDimensionsAreReported() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> evaluate(0.5, "a", "three-d"));

        assertTrue(error.getMessage().contains("dimensions"), error.getMessage());
    }

    @Test
    void anInvalidThresholdIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingSimilarityEvaluator(1.01));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingSimilarityEvaluator(-1.01));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingSimilarityEvaluator(Double.NaN));
        new EmbeddingSimilarityEvaluator(-1.0);
        new EmbeddingSimilarityEvaluator(1.0);
    }

    @Test
    void aNullInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingSimilarityEvaluator(0.5, model()).evaluate(null));
    }

    @Test
    void withoutAModelTheErrorExplainsHowToProvideOne() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new EmbeddingSimilarityEvaluator(0.5).evaluate(new EmbeddingSimilarityEvaluator.Input("a", "b")));

        assertTrue(error.getMessage().contains("EmbeddingModel"), error.getMessage());
    }

    @Test
    void negatingTheEvaluatorChecksThatTwoTextsAreUnrelated() {
        var unrelated = new EmbeddingSimilarityEvaluator(0.5, model()).negate();

        assertTrue(unrelated.evaluate(new EmbeddingSimilarityEvaluator.Input("a", "orthogonal")).pass());
        assertFalse(unrelated.evaluate(new EmbeddingSimilarityEvaluator.Input("a", "same")).pass());
    }

    @Test
    void theModelIsAutowiredWhenTheEvaluatorIsASpringBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AutowireConfig.class)) {
            Evaluation evaluation = context.getBean(EmbeddingSimilarityEvaluator.class)
                    .evaluate(new EmbeddingSimilarityEvaluator.Input("a", "same"));

            assertTrue(evaluation.pass());
        }
    }

    @Test
    void aConstructorSuppliedModelWinsOverTheAutowiredOne() {
        StubEmbeddingModel explicit = new StubEmbeddingModel(Map.of("a", new float[] {1f}, "b", new float[] {-1f}));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AutowireConfig.class)) {
            EmbeddingSimilarityEvaluator evaluator = new EmbeddingSimilarityEvaluator(0.5, explicit);
            context.getAutowireCapableBeanFactory().autowireBean(evaluator);

            assertFalse(evaluator.evaluate(new EmbeddingSimilarityEvaluator.Input("a", "b")).pass());
            assertEquals(1, explicit.calls);
        }
    }

    @Configuration
    static class AutowireConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            return model();
        }

        @Bean
        EmbeddingSimilarityEvaluator evaluator() {
            return new EmbeddingSimilarityEvaluator(0.5);
        }
    }
}
