package com.flowcrm.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Account resource")
public record AccountResponse(
        UUID id,
        String name,
        String website,
        String phone,
        String industry,
        String description,
        UUID ownerId,
        String ownerName,
        long contactCount,
        Instant createdAt,
        Instant updatedAt) {
}
