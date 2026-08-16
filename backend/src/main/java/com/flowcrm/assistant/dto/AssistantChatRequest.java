package com.flowcrm.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Flow AI chat request. The backend builds CRM context; do not send raw CRM payloads.")
public record AssistantChatRequest(
        @NotBlank(message = "Message is required")
                @Size(max = 2000, message = "Message must be at most 2000 characters")
                String message,
        @Valid AssistantContextRequest context,
        @Valid @Size(max = 8, message = "At most 8 history turns are allowed")
                List<AssistantHistoryTurnRequest> history) {}
