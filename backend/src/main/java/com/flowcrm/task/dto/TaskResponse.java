package com.flowcrm.task.dto;

import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Task resource")
public record TaskResponse(
        UUID id,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName,
        UUID leadId,
        String leadName,
        UUID accountId,
        String accountName,
        UUID contactId,
        String contactName,
        UUID dealId,
        String dealName,
        UUID assignedToId,
        String assignedToName,
        String title,
        String description,
        Instant dueAt,
        Instant reminderAt,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
