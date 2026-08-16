package com.flowcrm.assistant;

import java.util.List;

public record AiRequest(
        String systemPrompt, List<AiChatMessage> history, String userPrompt, int maxOutputTokens) {

    public AiRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
