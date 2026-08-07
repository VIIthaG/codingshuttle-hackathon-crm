package com.flowcrm.idempotency;

import com.flowcrm.common.exception.BadRequestException;

/**
 * Validates optional Idempotency-Key header values.
 */
public final class IdempotencyKeyValidator {

    public static final int MAX_LENGTH = 255;

    private IdempotencyKeyValidator() {
    }

    /**
     * @return normalized key, or {@code null} when the header is absent
     */
    public static String normalizeOptional(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Idempotency-Key must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
