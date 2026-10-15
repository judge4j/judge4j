package io.github.judge4j.examples.summarization.api;

/**
 * Provenance for a single summarization, echoed back so callers (and the Judge4j report) can
 * see exactly which model produced the output.
 *
 * @param provider model provider, whichever of {@code anthropic}, {@code openai} or
 *                 {@code google-genai} is configured via {@code spring.ai.model.chat}
 * @param model    configured model id the request was sent to
 */
public record ModelInfo(String provider, String model) {
}
