package com.flowcrm.assistant.dto;

import com.flowcrm.enums.RelatedRecordType;
import java.util.UUID;

public record AssistantContextUsedResponse(RelatedRecordType entityType, UUID entityId, String label) {}
