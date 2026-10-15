package io.github.judge4j.examples.summarization.config;

import io.github.judge4j.examples.summarization.api.SummaryStyle;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable business rules for the summarization service, bound from the {@code summarizer.*}
 * configuration namespace.
 *
 * @param minDocumentChars smallest document (after trimming) the service will accept
 * @param maxDocumentChars largest document the service will send to the model
 * @param defaultStyle     summary style used when the request does not specify one
 * @param defaultMaxWords  summary word budget used when the request does not specify one
 */
@ConfigurationProperties(prefix = "summarizer")
public record SummarizationProperties(
        @DefaultValue("200") int minDocumentChars,
        @DefaultValue("50000") int maxDocumentChars,
        @DefaultValue("PARAGRAPH") SummaryStyle defaultStyle,
        @DefaultValue("150") int defaultMaxWords) {
}
