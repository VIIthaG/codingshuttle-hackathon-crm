package com.flowcrm.activity.dto;

import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Activity timeline for one CRM record. Not a full audit log.")
public record ActivityTimelineResponse(
        RelatedRecordType entityType,
        UUID entityId,
        String entityName,
        List<ActivityItemResponse> items) {
}
