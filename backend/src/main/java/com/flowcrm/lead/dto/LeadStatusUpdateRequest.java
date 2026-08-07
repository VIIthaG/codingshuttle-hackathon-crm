package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record LeadStatusUpdateRequest(
        @NotNull(message = "Status is required")
        LeadStatus status) {
}
