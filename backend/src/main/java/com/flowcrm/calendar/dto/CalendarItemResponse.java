package com.flowcrm.calendar.dto;

import com.flowcrm.enums.CalendarItemType;
import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Scheduled CRM work item. Only OPEN tasks, SCHEDULED meetings, and PLANNED calls are included.")
public record CalendarItemResponse(
        UUID id,
        CalendarItemType itemType,
        String title,
        Instant startAt,
        Instant endAt,
        String status,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName,
        UUID assignedToId,
        String assignedToName,
        java.util.Map<String, Object> metadata) {}
