package com.flowcrm.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Read-only Flow AI answer. Suggestions are follow-up prompts, not performed actions.")
public record AssistantChatResponse(
        String answer, AssistantContextUsedResponse contextUsed, List<String> suggestions) {}
