package io.github.judge4j.examples.summarization.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/**
 * Request body for {@code POST /api/v1/summaries}.
 *
 * <p>Only {@link #document()} is required. Bean Validation here guards the transport contract
 * (non-blank, sane upper bounds); the configurable minimum/maximum document size business rule
 * is enforced in the service so it can be tuned without a recompile.
 *
 * @param document the full text to summarize
 * @param style    desired shape of the summary; defaults to the configured style when omitted
 * @param maxWords soft word budget for the summary; defaults to the configured budget when omitted
 * @param focus    optional aspect the summary should emphasise (e.g. "financial risks")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummarizeRequest(
        @NotBlank(message = "document must not be blank")
        String document,

        @Nullable
        SummaryStyle style,

        @Nullable
        @Positive(message = "maxWords must be positive")
        @Max(value = 1000, message = "maxWords must not exceed 1000")
        Integer maxWords,

        @Nullable
        @Size(max = 200, message = "focus must not exceed 200 characters")
        String focus) {
}
