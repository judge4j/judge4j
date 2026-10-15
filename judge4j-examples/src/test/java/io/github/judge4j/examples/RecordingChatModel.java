package io.github.judge4j.examples;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Offline stand-in for a model: replies with a fixed string and remembers every prompt it was sent. */
final class RecordingChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final String reply;

    RecordingChatModel(String reply) {
        this.reply = reply;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    Prompt lastPrompt() {
        return prompts.get(prompts.size() - 1);
    }
}
