package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Lead resource")
public record LeadResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String company,
        LeadSource source,
        LeadStatus status,
        UUID assignedToId,
        String assignedToName,
        Instant createdAt,
        Instant updatedAt,
        Instant convertedAt,
        UUID convertedAccountId,
        String convertedAccountName,
        UUID convertedContactId,
        String convertedContactName,
        UUID convertedDealId,
        String convertedDealName) {
}
