package com.flowcrm.meeting.dto;

import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.RelatedRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Meeting resource")
public record MeetingResponse(
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
        Instant startAt,
        Instant endAt,
        String location,
        String meetingUrl,
        MeetingStatus status,
        Instant createdAt,
        Instant updatedAt) {}
