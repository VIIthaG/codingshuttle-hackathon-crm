package com.flowcrm.call.dto;

import com.flowcrm.enums.CallDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Create call. Supply exactly one related record id.")
public record CallCreateRequest(
        UUID leadId,
        UUID accountId,
        UUID contactId,
        UUID dealId,
        UUID assignedToId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull Instant scheduledAt,
        @Min(0) Integer durationMinutes,
        @NotNull CallDirection direction,
        @Size(max = 50) String phoneNumber,
        @Size(max = 2000) String outcome) {

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
