package com.flowcrm.lead.dto;

import java.util.UUID;

/**
 * Idempotency fingerprint payload: includes lead id so the same Idempotency-Key
 * cannot replay conversion for a different lead.
 */
public record LeadConvertIdempotencyPayload(UUID leadId, LeadConvertRequest request) {
}
