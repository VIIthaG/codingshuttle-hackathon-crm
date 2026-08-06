package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LeadUpdateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Size(max = 255, message = "Company must be at most 255 characters")
        String company,

        @NotNull(message = "Source is required")
        LeadSource source,

        @NotNull(message = "Status is required")
        LeadStatus status,

        @NotNull(message = "Assigned user is required")
        UUID assignedToId) {
}
