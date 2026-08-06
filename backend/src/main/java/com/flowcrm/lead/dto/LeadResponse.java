package com.flowcrm.lead.dto;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import java.time.Instant;
import java.util.UUID;

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
        Instant updatedAt) {
}
