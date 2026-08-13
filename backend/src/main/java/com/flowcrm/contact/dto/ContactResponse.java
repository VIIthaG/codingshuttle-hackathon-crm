package com.flowcrm.contact.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Contact resource")
public record ContactResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        String notes,
        UUID accountId,
        String accountName,
        UUID ownerId,
        String ownerName,
        Instant createdAt,
        Instant updatedAt) {
}
