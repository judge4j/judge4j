package io.github.judge4j.examples;

import io.github.judge4j.springai.RubricEvaluator;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Rubric fixed to the summarization use case: every claim in the summary (the graded response)
 * must be supported by the source document (the request). Catches hallucination — a claim the
 * model invented that isn't actually in the source.
 */
final class SummaryGroundedInDocumentEvaluator extends RubricEvaluator {

    SummaryGroundedInDocumentEvaluator(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder);
    }

    @Override
    protected String rubric() {
        return "Every factual claim in the response is directly supported by the request. "
                + "The response introduces no fact, number, or detail that isn't present in the request.";
    }
}
