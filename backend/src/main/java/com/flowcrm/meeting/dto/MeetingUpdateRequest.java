package com.flowcrm.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Update meeting. Status changes should use PATCH /status.")
public record MeetingUpdateRequest(
        UUID leadId,
        UUID accountId,
        UUID contactId,
        UUID dealId,
        @NotNull UUID assignedToId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Size(max = 255) String location,
        @Size(max = 500) String meetingUrl) {

    @AssertTrue(message = "endAt must be after startAt")
    @Schema(hidden = true)
    public boolean isEndAfterStart() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    @AssertTrue(message = "Exactly one of leadId, accountId, contactId, or dealId is required")
    @Schema(hidden = true)
    public boolean isExactlyOneRelatedRecord() {
        int count = 0;
        if (leadId != null) {
            count++;
        }
        if (accountId != null) {
            count++;
        }
        if (contactId != null) {
            count++;
        }
        if (dealId != null) {
            count++;
        }
        return count == 1;
    }
}
