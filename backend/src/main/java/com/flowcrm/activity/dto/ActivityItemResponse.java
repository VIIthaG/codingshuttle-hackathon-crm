package com.flowcrm.activity.dto;

import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A single activity timeline item. Types are stable strings so later phases can add Meeting/Call/Email/Note.")
public record ActivityItemResponse(
        String id,
        String type,
        String title,
        String description,
        Instant timestamp,
        String actorName,
        String status,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName,
        Map<String, Object> metadata) {
}
