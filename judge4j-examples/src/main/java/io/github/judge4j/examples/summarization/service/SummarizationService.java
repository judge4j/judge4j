package io.github.judge4j.examples.summarization.service;

import io.github.judge4j.examples.summarization.api.ModelInfo;
import io.github.judge4j.examples.summarization.api.SummarizeRequest;
import io.github.judge4j.examples.summarization.api.SummaryResponse;
import io.github.judge4j.examples.summarization.api.SummaryStyle;
import io.github.judge4j.examples.summarization.config.SummarizationProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Turns a caller's document into a structured summary by making a real LLM call through Spring
 * AI's {@link ChatClient}.
 *
 * <p>Each request is built as an explicit {@link Prompt} of two messages:
 * <ul>
 *   <li>a {@link SystemMessage} rendered from {@code prompts/summarize-system.st} via a
 *       {@link SystemPromptTemplate} — it fixes the assistant's role and rules;</li>
 *   <li>a {@link UserMessage} rendered from {@code prompts/summarize-user.st} via a
 *       {@link PromptTemplate} — it carries the task parameters and the document.</li>
 * </ul>
 * The reply is bound straight to JSON with {@code call().responseEntity(DocumentSummary.class)}:
 * Spring AI derives a schema from {@link DocumentSummary}, appends it to the user message, parses
 * the model's JSON response back into the record, and hands back the raw {@link ChatResponse}
 * alongside it so the actual model id can be read off its metadata.
 *
 * <p>The application-owned pipeline around the call — input validation, prompt assembly,
 * structured-output binding, and response enrichment (word counts, compression ratio) — is
 * exactly what the Judge4j example tests exercise end to end.
 */
@Service
public class SummarizationService {

    private static final Logger log = LoggerFactory.getLogger(SummarizationService.class);

    private final ChatClient chatClient;
    private final SummarizationProperties properties;
    private final SystemPromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final String provider;

    public SummarizationService(
            ChatClient summarizationChatClient,
            SummarizationProperties properties,
            @Value("classpath:prompts/summarize-system.st") Resource systemPrompt,
            @Value("classpath:prompts/summarize-user.st") Resource userPrompt,
            @Value("${spring.ai.model.chat}") String provider) {
        this.chatClient = summarizationChatClient;
        this.properties = properties;
        this.systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        this.userPromptTemplate = new PromptTemplate(userPrompt);
        this.provider = provider;
    }

    /**
     * Summarize {@code request.document()} and return an enriched, structured response.
     *
     * @throws InvalidDocumentException if the document violates the configured size rules
     * @throws SummarizationException   if the model call fails or yields an unusable result
     */
    public SummaryResponse summarize(SummarizeRequest request) {
        String document = request.document().strip();
        assertAcceptable(document);

        SummaryStyle style = request.style() != null ? request.style() : properties.defaultStyle();
        int maxWords = request.maxWords() != null ? request.maxWords() : properties.defaultMaxWords();

        Prompt prompt = buildPrompt(document, style, maxWords, request.focus());
        log.debug("Summarizing document: {} chars, style={}, maxWords={}", document.length(), style, maxWords);

        ResponseEntity<ChatResponse, DocumentSummary> response = callModel(prompt);
        DocumentSummary summary = response.entity();
        if (summary == null || summary.summary() == null || summary.summary().isBlank()) {
            throw new SummarizationException("The language model returned an empty summary.");
        }
        String modelName = response.response().getMetadata().getModel();

        int originalWords = wordCount(document);
        int summaryWords = wordCount(summary.summary());

        return new SummaryResponse(
                blankToNull(summary.suggestedTitle()),
                summary.summary().strip(),
                summary.keyPoints() == null ? List.of() : List.copyOf(summary.keyPoints()),
                blankToNull(summary.detectedLanguage()),
                style,
                originalWords,
                summaryWords,
                compressionRatio(originalWords, summaryWords),
                new ModelInfo(provider, modelName),
                Instant.now());
    }

    /** Compose the system + user messages into a single {@link Prompt}. */
    private Prompt buildPrompt(String document, SummaryStyle style, int maxWords, String focus) {
        SystemMessage systemMessage = buildSystemMessage();

        UserMessage userMessage = buildUserMessage(document, style, maxWords, focus);

        return new Prompt(List.<Message>of(systemMessage, userMessage));
    }

	private UserMessage buildUserMessage(String document, SummaryStyle style, int maxWords, String focus) {
		Map<String, Object> userVariables = Map.of(
                "style", style.instruction(),
                "maxWords", maxWords,
                "focusInstruction", focusInstruction(focus),
                "document", document);
        UserMessage userMessage = (UserMessage) userPromptTemplate.createMessage(userVariables);
		return userMessage;
	}

    /**
     * Render the system prompt template into a {@link SystemMessage}.
     *
     * <p>Public so that evaluations can substitute a different system prompt per variant (see
     * {@code SummarizerPromptEvalTest}); the application itself only calls it internally.
     */
    public SystemMessage buildSystemMessage() {
        return (SystemMessage) systemPromptTemplate.createMessage();
    }

    /**
     * The client used for model calls.
     *
     * <p>Public so that evaluations can substitute a client configured for a different model per
     * variant (see {@code SummarizerModelEvalTest}); the application itself only calls it internally.
     */
    public ChatClient chatClient() {
        return chatClient;
    }

    /**
     * Make the real model call, binding the JSON reply to {@link DocumentSummary} while keeping
     * the {@link ChatResponse} around so the actual model id can be read off its metadata —
     * provider-agnostic, unlike echoing back a hardcoded config property.
     */
    private ResponseEntity<ChatResponse, DocumentSummary> callModel(Prompt prompt) {
        try {
            return chatClient().prompt(prompt)
                    .call()
                    .responseEntity(DocumentSummary.class);
        }
        catch (TransientAiException ex) {
            throw new SummarizationException("The language model is temporarily unavailable.", ex);
        }
        catch (NonTransientAiException ex) {
            throw new SummarizationException("The language model rejected the request: " + ex.getMessage(), ex);
        }
        catch (RuntimeException ex) {
            // Models occasionally return malformed or truncated JSON; surface that as the documented
            // "unusable result" error rather than leaking a parsing exception to callers.
            throw new SummarizationException("The language model returned a response that could not be read.", ex);
        }
    }

    private void assertAcceptable(String document) {
        if (document.length() < properties.minDocumentChars()) {
            throw new InvalidDocumentException(
                    "document must be at least %d characters after trimming (was %d)"
                            .formatted(properties.minDocumentChars(), document.length()));
        }
        if (document.length() > properties.maxDocumentChars()) {
            throw new InvalidDocumentException(
                    "document must not exceed %d characters (was %d)"
                            .formatted(properties.maxDocumentChars(), document.length()));
        }
    }

    private static String focusInstruction(String focus) {
        if (focus == null || focus.isBlank()) {
            return "";
        }
        return "Pay particular attention to the following aspect: " + focus.strip() + ".";
    }

    private static int wordCount(String text) {
        String trimmed = text == null ? "" : text.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private static double compressionRatio(int originalWords, int summaryWords) {
        if (originalWords == 0) {
            return 0.0;
        }
        return Math.round((double) summaryWords / originalWords * 100.0) / 100.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
