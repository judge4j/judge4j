package io.github.judge4j.examples;

import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.Evaluator;
import io.github.judge4j.examples.summarization.service.SummarizationService;
import io.github.judge4j.spring.EvalVariantContext;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Shared plumbing for the summarizer evaluations. */
final class SummarizerTestSupport {

    /** System property naming the model that grades rubric assertions. Provider specific. */
    static final String JUDGE_MODEL_PROPERTY = "judge4j.example.judge-model";
    static final String DEFAULT_JUDGE_MODEL = "gemini-3-flash-preview";

    private SummarizerTestSupport() {
    }

    /**
     * Wraps the real {@link SummarizationService} in a Mockito spy published as the primary bean.
     * Everything runs for real except the seam methods a variant chooses to stub.
     */
    @TestConfiguration
    static class SpyConfiguration {

        @Bean
        @Primary
        SummarizationService summarizationServiceSpy(SummarizationService summarizationService) {
            return Mockito.spy(summarizationService);
        }

        @Bean
        ActiveModel activeModel() {
            return new ActiveModel();
        }
    }

    /** Which model the current variant asked for, so the test can verify the variant took effect. */
    static final class ActiveModel {
        volatile String id;
    }

    /**
     * Returns the spy with every earlier stub removed. Both evaluation classes share one cached
     * Spring context, so each variant must start from the real behaviour; otherwise a stub left by
     * the previous variant (or the other class) would silently leak into this one.
     */
    static SummarizationService freshSpy(EvalVariantContext context) {
        SummarizationService spy = context.bean(SummarizationService.class);
        Mockito.reset(spy);
        return spy;
    }

    /**
     * A builder for the judge model, deliberately different from the model under test so that the
     * system does not grade its own output.
     */
    static ChatClient.Builder judge(ChatClient.Builder applicationBuilder) {
        String model = System.getProperty(JUDGE_MODEL_PROPERTY, DEFAULT_JUDGE_MODEL);
        return applicationBuilder.clone().defaultOptions(ChatOptions.builder().model(model).build());
    }

    /** A custom evaluator in a few lines: passes when the text has at most {@code max} words. */
    static Evaluator<String> atMostWords(int max) {
        return text -> {
            int words = text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length;
            boolean pass = words <= max;
            return new Evaluation(pass, pass ? 1.0 : 0.0, "%d words (limit %d)".formatted(words, max));
        };
    }
}
