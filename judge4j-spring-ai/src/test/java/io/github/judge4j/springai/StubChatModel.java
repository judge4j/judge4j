package io.github.judge4j.springai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Test double for the judge model: replies with a fixed string and remembers every prompt it received. */
final class StubChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final String reply;

    StubChatModel(String reply) {
        this.reply = reply;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    /** The text of the user message of the most recent prompt. */
    String lastUserText() {
        return prompts.get(prompts.size() - 1).getUserMessage().getText();
    }
}
