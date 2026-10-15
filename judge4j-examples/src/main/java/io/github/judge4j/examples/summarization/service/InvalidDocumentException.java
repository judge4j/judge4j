package io.github.judge4j.examples.summarization.service;

/**
 * Thrown when a submitted document violates a configurable business rule (too short, too long).
 * Surfaces as HTTP 422 Unprocessable Entity.
 */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
