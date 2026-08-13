package com.flowcrm.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Full account update request")
public record AccountUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 255, message = "Website must be at most 255 characters")
        String website,

        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Size(max = 255, message = "Industry must be at most 255 characters")
        String industry,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Account owner user id")
        @NotNull(message = "Owner is required")
        UUID ownerId) {
}
