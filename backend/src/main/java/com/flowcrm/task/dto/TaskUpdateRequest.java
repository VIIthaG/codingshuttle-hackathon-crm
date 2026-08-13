package com.flowcrm.task.dto;

import com.flowcrm.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Full task update request. Supply exactly one of leadId, accountId, contactId, dealId.")
public record TaskUpdateRequest(
        UUID leadId,
        UUID accountId,
        UUID contactId,
        UUID dealId,

        @NotNull(message = "Assigned user is required")
        UUID assignedToId,

        @Schema(example = "Call prospect")
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "dueAt is required")
        Instant dueAt,

        Instant reminderAt,

        @Schema(example = "OPEN")
        @NotNull(message = "Status is required")
        TaskStatus status) {

    @AssertTrue(message = "reminderAt must not be after dueAt")
    @Schema(hidden = true)
    public boolean isReminderNotAfterDue() {
        return reminderAt == null || dueAt == null || !reminderAt.isAfter(dueAt);
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
