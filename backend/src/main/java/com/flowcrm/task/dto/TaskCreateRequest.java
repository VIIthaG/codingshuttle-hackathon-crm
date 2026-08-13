package com.flowcrm.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Create follow-up task request. Supply exactly one of leadId, accountId, contactId, dealId.")
public record TaskCreateRequest(
        @Schema(description = "Relate to this lead (exactly one related id)")
        UUID leadId,

        @Schema(description = "Relate to this account")
        UUID accountId,

        @Schema(description = "Relate to this contact")
        UUID contactId,

        @Schema(description = "Relate to this deal")
        UUID dealId,

        @Schema(description = "Optional assignee; defaults to current user. Only ADMIN may assign others.")
        UUID assignedToId,

        @Schema(example = "Call prospect")
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Schema(example = "Discuss pricing")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Due timestamp (ISO-8601)", example = "2026-08-10T17:00:00Z")
        @NotNull(message = "dueAt is required")
        Instant dueAt,

        @Schema(description = "Optional reminder time; must not be after dueAt", example = "2026-08-09T17:00:00Z")
        Instant reminderAt) {

    @AssertTrue(message = "reminderAt must not be after dueAt")
    @Schema(hidden = true)
    public boolean isReminderNotAfterDue() {
        return reminderAt == null || dueAt == null || !reminderAt.isAfter(dueAt);
    }

    @AssertTrue(message = "Exactly one of leadId, accountId, contactId, or dealId is required")
    @Schema(hidden = true)
    public boolean isExactlyOneRelatedRecord() {
        return relatedCount() == 1;
    }

    private int relatedCount() {
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
        return count;
    }
}
