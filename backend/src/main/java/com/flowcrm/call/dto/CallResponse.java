package com.flowcrm.call.dto;

import com.flowcrm.enums.CallDirection;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Call resource")
public record CallResponse(
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
        Instant scheduledAt,
        Integer durationMinutes,
        CallDirection direction,
        CallStatus status,
        String phoneNumber,
        String outcome,
        Instant createdAt,
        Instant updatedAt) {}
