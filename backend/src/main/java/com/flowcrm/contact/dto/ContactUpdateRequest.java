package com.flowcrm.contact.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Full contact update request")
public record ContactUpdateRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 255, message = "First name must be at most 255 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 255, message = "Last name must be at most 255 characters")
        String lastName,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Size(max = 255, message = "Job title must be at most 255 characters")
        String jobTitle,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @Schema(description = "Optional account id; omit or null to unlink")
        UUID accountId,

        @NotNull(message = "Owner is required")
        UUID ownerId) {
}
