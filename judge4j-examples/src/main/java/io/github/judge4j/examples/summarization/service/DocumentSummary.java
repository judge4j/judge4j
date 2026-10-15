package io.github.judge4j.examples.summarization.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The structured payload the language model is asked to return. Spring AI's
 * {@code BeanOutputConverter} derives a JSON schema from this record and appends it to the
 * prompt, then binds the model's JSON reply back into an instance.
 *
 * @param suggestedTitle   a concise title for the document (max ~10 words)
 * @param summary          the summary text, honouring the requested style and length budget
 * @param keyPoints        3-7 standalone key points, each a full sentence
 * @param detectedLanguage the natural language of the source document, in English (e.g. "English")
 */
@JsonClassDescription("A faithful, self-contained summary of a document supplied by the user.")
public record DocumentSummary(
        @JsonPropertyDescription("A concise title for the document, at most ten words.")
        String suggestedTitle,

        @JsonPropertyDescription("The summary text. Must honour the requested style and word budget "
                + "and must only use information present in the document.")
        String summary,

        @JsonPropertyDescription("Between three and seven key points, each a standalone full sentence "
                + "drawn only from the document.")
        List<String> keyPoints,

        @JsonPropertyDescription("The natural language of the source document, named in English, e.g. \"English\".")
        String detectedLanguage) {
}
