package io.github.judge4j.examples.summarization.service;

/**
 * Thrown when the downstream language model call fails or returns an unusable result.
 * Surfaces as HTTP 502 Bad Gateway.
 */
public class SummarizationException extends RuntimeException {

    public SummarizationException(String message) {
        super(message);
    }

    public SummarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
