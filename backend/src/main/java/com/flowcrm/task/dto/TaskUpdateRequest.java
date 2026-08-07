package com.flowcrm.task.dto;

import com.flowcrm.enums.TaskStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TaskUpdateRequest(
        @NotNull(message = "Lead id is required")
        UUID leadId,

        @NotNull(message = "Assigned user is required")
        UUID assignedToId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "dueAt is required")
        Instant dueAt,

        Instant reminderAt,

        @NotNull(message = "Status is required")
        TaskStatus status) {

    @AssertTrue(message = "reminderAt must not be after dueAt")
    public boolean isReminderNotAfterDue() {
        return reminderAt == null || dueAt == null || !reminderAt.isAfter(dueAt);
    }
}
