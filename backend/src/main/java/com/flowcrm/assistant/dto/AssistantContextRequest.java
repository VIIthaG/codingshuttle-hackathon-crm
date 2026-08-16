package com.flowcrm.assistant.dto;

import com.flowcrm.enums.RelatedRecordType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssistantContextRequest(
        @NotNull(message = "entityType is required when context is provided") RelatedRecordType entityType,
        @NotNull(message = "entityId is required when context is provided") UUID entityId) {}
