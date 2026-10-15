package io.github.judge4j.examples.summarization.api;

import io.github.judge4j.examples.summarization.service.InvalidDocumentException;
import io.github.judge4j.examples.summarization.service.SummarizationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates failures into RFC 9457 {@link ProblemDetail} responses.
 *
 * <ul>
 *   <li>{@code 400} — request body fails Bean Validation</li>
 *   <li>{@code 422} — document violates a configurable size rule</li>
 *   <li>{@code 502} — the downstream language model call failed or returned nothing usable</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI TYPE_VALIDATION = URI.create("urn:judge4j:summarizer:validation-error");
    private static final URI TYPE_INVALID_DOCUMENT = URI.create("urn:judge4j:summarizer:invalid-document");
    private static final URI TYPE_MODEL_FAILURE = URI.create("urn:judge4j:summarizer:model-failure");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body failed validation.");
        problem.setTitle("Invalid request");
        problem.setType(TYPE_VALIDATION);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ProblemDetail handleInvalidDocument(InvalidDocumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Document cannot be summarized");
        problem.setType(TYPE_INVALID_DOCUMENT);
        return problem;
    }

    @ExceptionHandler(SummarizationException.class)
    public ProblemDetail handleModelFailure(SummarizationException ex) {
        log.warn("Summarization failed", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Summarization failed");
        problem.setType(TYPE_MODEL_FAILURE);
        return problem;
    }

    private static Map<String, String> describe(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage());
    }
}
