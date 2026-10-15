package io.github.judge4j.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.judge4j.examples.summarization.api.SummarizeRequest;
import io.github.judge4j.examples.summarization.api.SummaryResponse;
import io.github.judge4j.examples.summarization.api.SummaryStyle;
import io.github.judge4j.examples.summarization.config.SummarizationProperties;
import io.github.judge4j.examples.summarization.service.InvalidDocumentException;
import io.github.judge4j.examples.summarization.service.SummarizationException;
import io.github.judge4j.examples.summarization.service.SummarizationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;

/**
 * The application's own pipeline - validation, prompt assembly, response parsing, enrichment - tested
 * offline with a stand-in model. These run in the default build; the live-model evaluations of the same
 * service are in {@code SummarizerPromptEvalTest} and {@code SummarizerModelEvalTest}.
 */
class SummarizationServiceTest {

    private static final String VALID_REPLY = """
            {"suggestedTitle": "Quarterly update", "summary": "The team shipped three features.",
             "keyPoints": ["Three features shipped", "Load time fell"], "detectedLanguage": "English"}
            """;

    /** 40+ words, comfortably above the 200-character minimum. */
    private static final String DOCUMENT = ("The product team shipped three major features this quarter and reduced average page "
            + "load time by forty percent. Customer support tickets dropped by fifteen percent compared to last "
            + "quarter. The team plans to focus on mobile performance next quarter.");

    private static SummarizationService service(RecordingChatModel model) {
        return new SummarizationService(
                ChatClient.builder(model).build(),
                new SummarizationProperties(200, 50_000, SummaryStyle.PARAGRAPH, 150),
                new ClassPathResource("prompts/summarize-system.st"),
                new ClassPathResource("prompts/summarize-user.st"),
                "test-provider");
    }

    private static SummaryResponse summarize(String reply, SummarizeRequest request) {
        return service(new RecordingChatModel(reply)).summarize(request);
    }

    private static SummarizeRequest request(String document) {
        return new SummarizeRequest(document, null, null, null);
    }

    @Test
    void aWellFormedReplyIsParsedAndEnriched() {
        SummaryResponse response = summarize(VALID_REPLY, request(DOCUMENT));

        assertEquals("Quarterly update", response.suggestedTitle());
        assertEquals("The team shipped three features.", response.summary());
        assertEquals(2, response.keyPoints().size());
        assertEquals("English", response.detectedLanguage());
        assertEquals(SummaryStyle.PARAGRAPH, response.style(), "falls back to the configured default style");
        assertEquals(5, response.summaryWordCount());
        assertTrue(response.originalWordCount() > 30);
        assertEquals(Math.round((double) 5 / response.originalWordCount() * 100.0) / 100.0, response.compressionRatio());
        assertEquals("test-provider", response.model().provider());
    }

    @Test
    void malformedJsonFromTheModelBecomesASummarizationException() {
        SummarizationException error = assertThrows(SummarizationException.class,
                () -> summarize("Sure! Here is your summary: it was great.", request(DOCUMENT)));

        assertTrue(error.getMessage().contains("could not be read"), error.getMessage());
    }

    @Test
    void truncatedJsonFromTheModelBecomesASummarizationException() {
        assertThrows(SummarizationException.class,
                () -> summarize("{\"suggestedTitle\": \"x\", \"summary\": \"cut off mid-sen", request(DOCUMENT)));
    }

    @Test
    void jsonOfTheWrongShapeBecomesASummarizationException() {
        assertThrows(SummarizationException.class, () -> summarize("[1, 2, 3]", request(DOCUMENT)));
        assertThrows(SummarizationException.class, () -> summarize("\"just a string\"", request(DOCUMENT)));
    }

    @Test
    void aBlankSummaryIsAnUnusableResult() {
        SummarizationException error = assertThrows(SummarizationException.class,
                () -> summarize("{\"summary\": \"   \", \"keyPoints\": []}", request(DOCUMENT)));

        assertTrue(error.getMessage().contains("empty summary"), error.getMessage());
    }

    @Test
    void missingOptionalFieldsAreTolerated() {
        SummaryResponse response = summarize("{\"summary\": \"Short.\"}", request(DOCUMENT));

        assertEquals("Short.", response.summary());
        assertTrue(response.keyPoints().isEmpty());
        assertNull(response.suggestedTitle());
        assertNull(response.detectedLanguage());
    }

    @Test
    void documentsOutsideTheConfiguredSizeAreRejectedBeforeCallingTheModel() {
        RecordingChatModel model = new RecordingChatModel(VALID_REPLY);
        SummarizationService service = service(model);

        assertThrows(InvalidDocumentException.class, () -> service.summarize(request("too short")));
        assertThrows(InvalidDocumentException.class, () -> service.summarize(request("x".repeat(50_001))));
        assertTrue(model.prompts.isEmpty(), "no model call is made for an invalid document");
    }

    @Test
    void surroundingWhitespaceDoesNotCountTowardsTheMinimumLength() {
        RecordingChatModel model = new RecordingChatModel(VALID_REPLY);

        assertThrows(InvalidDocumentException.class,
                () -> service(model).summarize(request("   " + "x".repeat(150) + "   ")));
    }

    @Test
    void theUserPromptCarriesTheDocumentStyleBudgetAndFocus() {
        RecordingChatModel model = new RecordingChatModel(VALID_REPLY);

        service(model).summarize(new SummarizeRequest(DOCUMENT, SummaryStyle.BULLETS, 42, "mobile performance"));

        String user = model.lastPrompt().getUserMessage().getText();
        assertTrue(user.contains(DOCUMENT));
        assertTrue(user.contains(SummaryStyle.BULLETS.instruction()), user);
        assertTrue(user.contains("42 words"), user);
        assertTrue(user.contains("Pay particular attention to the following aspect: mobile performance."), user);
    }

    @Test
    void aBlankFocusAddsNoFocusInstruction() {
        RecordingChatModel model = new RecordingChatModel(VALID_REPLY);

        service(model).summarize(new SummarizeRequest(DOCUMENT, null, null, "   "));

        assertTrue(!model.lastPrompt().getUserMessage().getText().contains("particular attention"));
    }

    @Test
    void theSystemPromptComesFromTheTemplateFile() {
        RecordingChatModel model = new RecordingChatModel(VALID_REPLY);
        SummarizationService service = service(model);

        service.summarize(request(DOCUMENT));

        String system = model.lastPrompt().getSystemMessage().getText();
        assertEquals(service.buildSystemMessage().getText(), system);
        assertTrue(system.contains("summarization assistant"));
    }

    @Test
    void theEvaluationSeamsAreAvailable() {
        SummarizationService service = service(new RecordingChatModel(VALID_REPLY));

        assertTrue(service.chatClient() != null);
        assertTrue(service.buildSystemMessage() != null);
    }
}
