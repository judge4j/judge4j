package io.github.judge4j.springai;

import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.Evaluator;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Semantic-similarity evaluator: embeds the actual and an expected text with an
 * {@link EmbeddingModel} (in one call) and passes when their cosine similarity is at least
 * {@code threshold}. It catches an output that has drifted off-topic regardless of its exact wording.
 *
 * <p>The score is the cosine similarity clamped to {@code [0, 1]} (negative similarity scores 0); the
 * feedback shows the raw value. Empty or blank text on either side fails without calling the model,
 * and an embedding with no magnitude fails with an explanation. Similarity values depend on the
 * embedding model, so pick thresholds by looking at real scores from your model.
 *
 * <pre>{@code
 * evaluations.assertThat(new EmbeddingSimilarityEvaluator.Input(summary, document))
 *         .satisfies(new EmbeddingSimilarityEvaluator(0.6, embeddingModel));
 * }</pre>
 *
 * <p>The embedding model can be passed to the constructor or - when this evaluator is a Spring bean -
 * autowired.
 */
public final class EmbeddingSimilarityEvaluator implements Evaluator<EmbeddingSimilarityEvaluator.Input> {

    private final double threshold;
    private EmbeddingModel embeddingModel;

    /** For use as a Spring bean: the {@link EmbeddingModel} is autowired. */
    public EmbeddingSimilarityEvaluator(double threshold) {
        this(threshold, null);
    }

    /**
     * @param threshold      minimum cosine similarity to pass, between -1.0 and 1.0
     * @param embeddingModel the model to embed with; may be null if it will be autowired
     * @throws IllegalArgumentException if the threshold is outside [-1, 1] or NaN
     */
    public EmbeddingSimilarityEvaluator(double threshold, EmbeddingModel embeddingModel) {
        if (Double.isNaN(threshold) || threshold < -1.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be between -1.0 and 1.0, was " + threshold);
        }
        this.threshold = threshold;
        this.embeddingModel = embeddingModel;
    }

    /** Autowired by Spring when this evaluator is a managed bean; a constructor-supplied model wins. */
    @Autowired(required = false)
    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        if (this.embeddingModel == null) {
            this.embeddingModel = embeddingModel;
        }
    }

    @Override
    public Evaluation evaluate(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (embeddingModel == null) {
            throw new IllegalStateException("No EmbeddingModel configured for " + getClass().getSimpleName()
                    + " - pass one to the constructor, or register this evaluator as a Spring bean so it can be autowired");
        }
        if (isBlank(input.actual()) || isBlank(input.expected())) {
            return new Evaluation(false, 0.0, "cannot compare: the %s text is empty"
                    .formatted(isBlank(input.actual()) ? "actual" : "expected"));
        }

        List<float[]> embeddings = embeddingModel.embed(List.of(input.actual(), input.expected()));
        if (embeddings == null || embeddings.size() != 2 || embeddings.get(0) == null || embeddings.get(1) == null) {
            throw new IllegalStateException("The embedding model did not return two embeddings");
        }
        float[] actual = embeddings.get(0);
        float[] expected = embeddings.get(1);
        if (actual.length != expected.length) {
            throw new IllegalStateException("Embeddings have different dimensions (" + actual.length + " vs "
                    + expected.length + ")");
        }

        double dot = 0;
        double normActual = 0;
        double normExpected = 0;
        for (int i = 0; i < actual.length; i++) {
            dot += (double) actual[i] * expected[i];
            normActual += (double) actual[i] * actual[i];
            normExpected += (double) expected[i] * expected[i];
        }
        if (normActual == 0 || normExpected == 0) {
            return new Evaluation(false, 0.0, "cannot compare: an embedding has zero magnitude");
        }
        double similarity = dot / (Math.sqrt(normActual) * Math.sqrt(normExpected));
        boolean pass = similarity >= threshold;
        double score = Math.min(1.0, Math.max(0.0, similarity));
        return new Evaluation(pass, score, "cosine similarity %.3f (threshold %.3f): %s"
                .formatted(similarity, threshold, pass ? "PASS" : "FAIL"));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * What is compared.
     *
     * @param actual   the text the system produced
     * @param expected the reference text it should be semantically close to
     */
    public record Input(String actual, String expected) {
    }
}
