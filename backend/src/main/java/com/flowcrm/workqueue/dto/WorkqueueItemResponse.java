package com.flowcrm.workqueue.dto;

import com.flowcrm.enums.CalendarItemType;
import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A single workqueue item")
public record WorkqueueItemResponse(
        UUID id,
        CalendarItemType itemType,
        String title,
        Instant timestamp,
        String status,
        String urgency,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName,
        String assignedToName) {}
