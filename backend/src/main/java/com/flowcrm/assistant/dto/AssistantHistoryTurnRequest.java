package com.flowcrm.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssistantHistoryTurnRequest(
        @NotBlank @Pattern(regexp = "user|assistant", message = "role must be user or assistant") String role,
        @NotBlank @Size(max = 2000, message = "history content must be at most 2000 characters") String content) {}
