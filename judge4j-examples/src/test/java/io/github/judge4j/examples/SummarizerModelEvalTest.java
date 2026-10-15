package io.github.judge4j.examples;

import io.github.judge4j.core.EvalAssertions;
import io.github.judge4j.examples.summarization.SummarizationApplication;
import io.github.judge4j.examples.summarization.api.SummarizeRequest;
import io.github.judge4j.examples.summarization.api.SummaryResponse;
import io.github.judge4j.examples.summarization.service.SummarizationService;
import io.github.judge4j.junit5.EvalTest;
import io.github.judge4j.junit5.EvalTestCases;
import io.github.judge4j.junit5.Input;
import io.github.judge4j.matchers.Matchers;
import io.github.judge4j.spring.EvalVariant;
import io.github.judge4j.spring.EvalVariantFactory;
import io.github.judge4j.spring.EvalVariants;
import io.github.judge4j.springai.RubricEvaluator;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * <b>Model comparison.</b> Runs the same summarization service with the same system prompt against
 * three different models and reports them side by side - answer quality, a conciseness limit, and
 * how long each model took.
 *
 * <p>How the model is swapped: each variant stubs {@link SummarizationService#chatClient()} on the
 * spy from {@link SummarizerTestSupport.SpyConfiguration} to return a client whose default options
 * name that model. Because a variant that silently fails to apply would make every column identical,
 * the test also asserts that the response says it came from the requested model.
 *
 * <p>The model ids are Google Gemini ids, matching {@code spring.ai.model.chat: google-genai} in
 * {@code application.yml}; replace them to compare models of another provider. The rubric judge is a
 * separate model (see {@link SummarizerTestSupport#judge}), so none of the models grades itself.
 *
 * <p>Needs credentials for the configured provider, so it runs only under the {@code evals} Maven
 * profile: {@code mvn test -Pevals}.
 */
@SpringBootTest(classes = SummarizationApplication.class)
@Import(SummarizerTestSupport.SpyConfiguration.class)
@EvalVariants(SummarizerModelEvalTest.Models.class)
@EvalTestCases("judge4j/summarizer-cases.yaml")
class SummarizerModelEvalTest {

    private static final int MAX_WORDS = 70;

    @Autowired
    private SummarizationService summarizationService;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private SummarizerTestSupport.ActiveModel activeModel;

    @EvalTest
    void summaryQualityAcrossModels(EvalAssertions evaluations, @Input("document") String document,
            @Input("mustMention") String mustMention) {
        SummaryResponse response = summarizationService.summarize(new SummarizeRequest(document, null, null, null));
        String summary = response.summary();

        // The first assertion's value is what the report shows as this run's output.
        evaluations.assertThat(summary)
                .named("keeps the key fact")
                .expecting("mentions '" + mustMention + "'")
                .satisfies(Matchers.icontains(mustMention));

        evaluations.assertThat(summary)
                .named("is concise")
                .expecting("at most " + MAX_WORDS + " words")
                .satisfies(SummarizerTestSupport.atMostWords(MAX_WORDS));

        evaluations.assertThat(new RubricEvaluator.Input(document, summary))
                .named("is factually grounded in the document")
                .expecting("every claim in the summary is supported by the source document")
                .satisfies(new SummaryGroundedInDocumentEvaluator(SummarizerTestSupport.judge(chatClientBuilder)));

        evaluations.assertThat(response.model().model())
                .named("was answered by the requested model")
                .expecting("the response reports model '" + activeModel.id + "'")
                .satisfies(Matchers.icontains(activeModel.id));
    }

    /** The models under comparison; the first column is the application's default. */
    public static final class Models implements EvalVariantFactory {
        @Override
        public List<? extends EvalVariant> createVariants() {
            return List.of(
                    model("gemini-3.5-flash-lite"),
                    model("gemini-3.1-flash-lite"),
                    model("gemini-3.8-flash"));
        }

        private static EvalVariant model(String modelId) {
            return EvalVariant.named(modelId).configure(context -> {
                ChatClient client = context.bean(ChatClient.Builder.class)
                        .defaultOptions(ChatOptions.builder().model(modelId).build())
                        .build();
                SummarizationService spy = SummarizerTestSupport.freshSpy(context);
                Mockito.doReturn(client).when(spy).chatClient();
                context.bean(SummarizerTestSupport.ActiveModel.class).id = modelId;
            });
        }
    }
}
