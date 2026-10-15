package io.github.judge4j.examples;

import io.github.judge4j.core.EvalAssertions;
import io.github.judge4j.examples.summarization.SummarizationApplication;
import io.github.judge4j.examples.summarization.api.SummarizeRequest;
import io.github.judge4j.examples.summarization.service.SummarizationService;
import io.github.judge4j.junit5.EvalTest;
import io.github.judge4j.junit5.EvalTestCases;
import io.github.judge4j.junit5.Input;
import io.github.judge4j.matchers.Matchers;
import io.github.judge4j.spring.EvalVariant;
import io.github.judge4j.spring.EvalVariantFactory;
import io.github.judge4j.spring.EvalVariants;
import io.github.judge4j.springai.EmbeddingSimilarityEvaluator;
import io.github.judge4j.springai.RubricEvaluator;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

/**
 * <b>Prompt comparison.</b> Runs the real summarization service against the real configured chat
 * model twice - once with the standard system prompt ({@code prompts/summarize-system.st}) and once
 * with a terse one ({@code prompts/summarize-system-concise.st}) - for every case in
 * {@code summarizer-cases.yaml}, and reports both side by side.
 *
 * <p>How the prompt is swapped: {@link SummarizerTestSupport.SpyConfiguration} wraps the service
 * in a Mockito spy, and each variant stubs only {@link SummarizationService#buildSystemMessage()}.
 * Everything else - input validation, user-prompt assembly, structured-output parsing,
 * post-processing - is the application's real code.
 *
 * <p>Assertion kinds shown: deterministic {@code Matchers}, a custom evaluator, an LLM judge
 * ({@link RubricEvaluator}) and embedding similarity ({@link EmbeddingSimilarityEvaluator}). The two
 * {@code [demo failure]} assertions can never pass; they exist so you can see how a failing
 * assertion is reported.
 *
 * <p>Needs credentials for the configured provider, so it runs only under the {@code evals} Maven
 * profile: {@code mvn test -Pevals}.
 */
@SpringBootTest(classes = SummarizationApplication.class)
@Import(SummarizerTestSupport.SpyConfiguration.class)
@EvalVariants(SummarizerPromptEvalTest.SystemPrompts.class)
@EvalTestCases("judge4j/summarizer-cases.yaml")
class SummarizerPromptEvalTest {

    private static final int MAX_WORDS = 40;

    /** Cosine similarity a summary needs against its source to count as on-topic. */
    private static final double MIN_TOPIC_SIMILARITY = 0.7;

    /** Text about something else entirely, to show that unrelated content scores low. */
    private static final String UNRELATED_TEXT =
            "Preheat the oven to 180 degrees, whisk the eggs with sugar and fold in the flour "
                    + "before baking the sponge cake for thirty minutes.";

    @Autowired
    private SummarizationService summarizationService;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private EmbeddingModel embeddingModel;

    @EvalTest
    void summaryQualityChecks(EvalAssertions evaluations, @Input("document") String document,
            @Input("mustMention") String mustMention) {
        // The value passed to the FIRST assertion is what the report shows as this run's output.
        String summary = summarizationService.summarize(new SummarizeRequest(document, null, null, null)).summary();

        evaluations.assertThat(summary)
                .named("keeps the key fact")
                .expecting("mentions '" + mustMention + "'")
                .satisfies(Matchers.icontains(mustMention));

        evaluations.assertThat(summary)
                .named("is concise")
                .expecting("at most " + MAX_WORDS + " words")
                .satisfies(SummarizerTestSupport.atMostWords(MAX_WORDS));

        evaluations.assertThat(summary)
                .named("avoids hedging language")
                .expecting("no hedging words like 'might', 'probably', 'perhaps', 'i think'")
                .satisfies(Matchers.icontainsAny("might", "probably", "perhaps", "i think").negate());

        evaluations.assertThat(summary)
                .named("is not just the source document verbatim")
                .expecting("summary differs from the raw input document")
                .satisfies(Matchers.equals(document).negate());

        evaluations.assertThat(new RubricEvaluator.Input(document, summary))
                .named("is factually grounded in the document")
                .expecting("every claim in the summary is supported by the source document")
                .satisfies(new SummaryGroundedInDocumentEvaluator(SummarizerTestSupport.judge(chatClientBuilder)));

        // Intentionally failing assertions, to show how a failure renders in the report.
        evaluations.assertThat(summary)
                .named("[demo failure] contains an impossible marker phrase")
                .expecting("intentionally impossible: summary contains the literal phrase 'ZZZ_INTENTIONAL_FAILURE_ZZZ'")
                .satisfies(Matchers.contains("ZZZ_INTENTIONAL_FAILURE_ZZZ"));

        evaluations.assertThat(summary)
                .named("[demo failure] is an empty string")
                .expecting("intentionally impossible: summary equals an empty string")
                .satisfies(Matchers.equals(""));
    }

    /**
     * Embedding-similarity assertions. The first checks the summary stays close in meaning to its
     * source; the second negates the same evaluator to check it is <em>not</em> close to a text about
     * something unrelated, which shows the embedding really separates topics.
     */
    @EvalTest
    void summaryStaysSemanticallyOnTopic(EvalAssertions evaluations, @Input("document") String document) {
        String summary = summarizationService.summarize(new SummarizeRequest(document, null, null, null)).summary();
        EmbeddingSimilarityEvaluator onTopic = new EmbeddingSimilarityEvaluator(MIN_TOPIC_SIMILARITY, embeddingModel);

        // The first assertion's value is what the report shows as this run's output.
        evaluations.assertThat(summary)
                .named("is not blank")
                .expecting("the model returned some text")
                .satisfies(Matchers.regex("\\S"));

        evaluations.assertThat(new EmbeddingSimilarityEvaluator.Input(summary, document))
                .named("is semantically close to the source")
                .expecting("cosine similarity to the document is at least " + MIN_TOPIC_SIMILARITY)
                .satisfies(onTopic);

        evaluations.assertThat(new EmbeddingSimilarityEvaluator.Input(summary, UNRELATED_TEXT))
                .named("is not close to unrelated text")
                .expecting("cosine similarity to a cooking recipe stays below " + MIN_TOPIC_SIMILARITY)
                .satisfies(onTopic.negate());
    }

    /** The two system prompts under comparison. */
    public static final class SystemPrompts implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(
                    systemPrompt("standard-prompt", "prompts/summarize-system.st"),
                    systemPrompt("concise-prompt", "prompts/summarize-system-concise.st"));
        }

        private static EvalVariant systemPrompt(String label, String resource) {
            return EvalVariant.named(label).configure(context -> {
                SystemMessage message = (SystemMessage) new SystemPromptTemplate(new ClassPathResource(resource))
                        .createMessage();
                SummarizationService spy = SummarizerTestSupport.freshSpy(context);
                Mockito.doReturn(message).when(spy).buildSystemMessage();
            });
        }
    }
}
