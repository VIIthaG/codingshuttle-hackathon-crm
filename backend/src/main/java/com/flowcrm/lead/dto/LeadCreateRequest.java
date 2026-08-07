package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Create lead request")
public record LeadCreateRequest(
        @Schema(description = "Lead full name", example = "Jane Doe")
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Schema(description = "Optional email", example = "jane@acme.com")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Schema(example = "555-0100")
        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Schema(example = "Acme Corp")
        @Size(max = 255, message = "Company must be at most 255 characters")
        String company,

        @Schema(description = "Lead acquisition source", example = "WEB")
        @NotNull(message = "Source is required")
        LeadSource source,

        @Schema(description = "Optional initial status; defaults to NEW", example = "NEW")
        LeadStatus status,

        @Schema(description = "Optional assignee; defaults to current user. Only ADMIN may assign others.")
        UUID assignedToId) {
}
