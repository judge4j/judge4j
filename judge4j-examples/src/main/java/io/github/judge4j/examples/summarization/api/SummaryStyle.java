package io.github.judge4j.examples.summarization.api;

/**
 * Shape of the summary the caller wants back. The {@link #instruction()} text is injected into
 * the user prompt so the model knows how to structure the {@code summary} field.
 */
public enum SummaryStyle {

    /** One or more flowing prose paragraphs. */
    PARAGRAPH("Write the summary as one or more flowing prose paragraphs."),

    /** A short newline-separated list of bullet-style sentences. */
    BULLETS("Write the summary as a short list of bullet-style sentences, one per line, each starting with \"- \"."),

    /** A decision-maker oriented brief that leads with the outcome. */
    EXECUTIVE("Write the summary as an executive brief: lead with the single most important takeaway, "
            + "then the supporting context a decision maker needs."),

    /** A single, very short sentence. */
    TLDR("Write the summary as a single plain sentence of at most 30 words.");

    private final String instruction;

    SummaryStyle(String instruction) {
        this.instruction = instruction;
    }

    public String instruction() {
        return instruction;
    }
}
