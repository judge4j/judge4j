package io.github.judge4j.examples.summarization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the document summarization service.
 *
 * <p>This is the "real use case" the Judge4j examples module evaluates: a Spring MVC HTTP API
 * backed by a Spring AI {@link org.springframework.ai.chat.client.ChatClient} that performs an
 * actual large language model call against a configurable provider (Anthropic, OpenAI, or
 * Gemini — see {@code spring.ai.model.chat} in {@code application.yml}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SummarizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SummarizationApplication.class, args);
    }
}
