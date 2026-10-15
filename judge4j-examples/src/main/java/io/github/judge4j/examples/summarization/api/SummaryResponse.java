package io.github.judge4j.examples.summarization.api;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Response body for {@code POST /api/v1/summaries}.
 *
 * @param suggestedTitle    a short title the model inferred for the document
 * @param summary           the summary text, shaped per the requested {@link SummaryStyle}
 * @param keyPoints         standalone key points extracted from the document
 * @param detectedLanguage  language the model detected in the source document
 * @param style             the style actually applied (after defaulting)
 * @param originalWordCount word count of the submitted document
 * @param summaryWordCount  word count of the produced summary
 * @param compressionRatio  {@code summaryWordCount / originalWordCount}, rounded to two decimals
 * @param model             model provenance
 * @param createdAt         server timestamp the summary was produced
 */
public record SummaryResponse(
        @Nullable String suggestedTitle,
        String summary,
        List<String> keyPoints,
        @Nullable String detectedLanguage,
        SummaryStyle style,
        int originalWordCount,
        int summaryWordCount,
        double compressionRatio,
        ModelInfo model,
        Instant createdAt) {
}
