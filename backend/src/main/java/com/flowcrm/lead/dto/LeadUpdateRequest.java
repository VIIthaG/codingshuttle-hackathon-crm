package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Full lead update request")
public record LeadUpdateRequest(
        @Schema(example = "Jane Doe")
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Schema(example = "jane@acme.com")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Schema(example = "555-0100")
        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Schema(example = "Acme Corp")
        @Size(max = 255, message = "Company must be at most 255 characters")
        String company,

        @Schema(example = "WEB")
        @NotNull(message = "Source is required")
        LeadSource source,

        @Schema(description = "Target status; must be a valid transition from current status", example = "CONTACTED")
        @NotNull(message = "Status is required")
        LeadStatus status,

        @Schema(description = "Assignee user id")
        @NotNull(message = "Assigned user is required")
        UUID assignedToId) {
}
