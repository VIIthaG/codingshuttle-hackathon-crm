package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Pipeline status transition request")
public record LeadStatusUpdateRequest(
        @Schema(
                description = "New pipeline status. Allowed: NEW→CONTACTED→QUALIFIED; LOST from active stages. CONVERTED is not allowed here.",
                example = "CONTACTED")
        @NotNull(message = "Status is required")
        LeadStatus status) {
}
