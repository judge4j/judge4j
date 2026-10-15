package io.github.judge4j.examples.summarization.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the application's {@link ChatClient} from the Spring AI auto-configured
 * {@link ChatClient.Builder}.
 *
 * <p>{@code spring.ai.model.chat} in {@code application.yml} selects which provider is active
 * (Anthropic, OpenAI, or Google GenAI/Gemini); the model, temperature and token budget for
 * whichever one is active are configured the idiomatic Spring AI way via that provider's own
 * {@code spring.ai.{anthropic,openai,google.genai}.chat.options.*} block. The system and user
 * prompts are built per request in the service from {@code SystemPromptTemplate} /
 * {@code PromptTemplate}, so the only thing layered on here is request/response logging.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient summarizationChatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
