package com.flowcrm.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Create account request")
public record AccountCreateRequest(
        @Schema(example = "Acme Corp")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Schema(example = "https://acme.example")
        @Size(max = 255, message = "Website must be at most 255 characters")
        String website,

        @Schema(example = "555-0100")
        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,

        @Schema(example = "Software")
        @Size(max = 255, message = "Industry must be at most 255 characters")
        String industry,

        @Schema(example = "Enterprise customer")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Optional owner; defaults to current user. Only ADMIN may assign others.")
        UUID ownerId) {
}
