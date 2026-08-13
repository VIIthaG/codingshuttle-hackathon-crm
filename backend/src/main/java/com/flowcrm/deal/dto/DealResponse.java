package com.flowcrm.deal.dto;

import com.flowcrm.enums.DealStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Deal response with flat account, contact, and owner references")
public record DealResponse(
        UUID id,
        String name,
        UUID accountId,
        String accountName,
        UUID primaryContactId,
        String primaryContactName,
        UUID ownerId,
        String ownerName,
        DealStage stage,
        BigDecimal amount,
        String currency,
        int probability,
        LocalDate expectedCloseDate,
        String description,
        String lostReason,
        Instant createdAt,
        Instant updatedAt) {
}
